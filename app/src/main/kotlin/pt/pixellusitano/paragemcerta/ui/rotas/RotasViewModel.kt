package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

sealed interface EstadoRotas {
    data object Inicial : EstadoRotas
    data object ACarregar : EstadoRotas
    data class Prontas(val itinerarios: List<Itinerario>, val etiquetas: List<Set<Etiqueta>>) : EstadoRotas
    data class Vazias(val motivo: Motivo) : EstadoRotas
    data class Erro(val semLigacao: Boolean) : EstadoRotas
}

enum class Motivo { SEM_LIGACOES, MUITO_PERTO, SEM_ORIGEM }

enum class TipoHora { AGORA, PARTIR, CHEGAR }

/** origem null = "A minha posição"; hora só conta com PARTIR ou CHEGAR. */
data class PedidoRotas(
    val origem: Lugar? = null,
    val destino: Lugar? = null,
    val tipoHora: TipoHora = TipoHora.AGORA,
    val hora: LocalTime? = null,
)

const val MINHA_POSICAO = "A minha posição"

/** A mesma hora hoje, ou amanhã se já passou (o minuto em curso ainda conta como hoje). */
fun proximaOcorrencia(hora: LocalTime, agora: ZonedDateTime): ZonedDateTime {
    val hoje = agora.with(hora)
    return if (hoje.isBefore(agora.truncatedTo(ChronoUnit.MINUTES))) agora.plusDays(1).with(hora) else hoje
}

/** Lugar como argumento da rota de navegação: "nome|lat|lon" (o nome pode ter "|"). */
fun Lugar.paraRota(): String = "$nome|$lat|$lon"

fun lugarDaRota(texto: String): Lugar? {
    val partes = texto.split('|')
    if (partes.size < 3) return null
    val lon = partes.last().toDoubleOrNull() ?: return null
    val lat = partes[partes.size - 2].toDoubleOrNull() ?: return null
    return Lugar(partes.dropLast(2).joinToString("|"), lat, lon, TipoLugar.LOCAL)
}

class RotasViewModel(
    private val planear: suspend (Lugar, Lugar, Instant, Boolean, Filtros) -> Resultado<List<Itinerario>>,
    private val registarRecente: suspend (Lugar, Lugar) -> Unit,
    linhasComAlerta: suspend () -> Set<String>,
    filtrosGuardados: Flow<Filtros>,
    private val guardarFiltros: suspend (Filtros) -> Unit,
    private val relogio: () -> Instant = Instant::now,
    origem: Lugar? = null,
    destino: Lugar? = null,
) : ViewModel() {
    private val _pedido = MutableStateFlow(PedidoRotas(origem, destino))
    val pedido: StateFlow<PedidoRotas> = _pedido.asStateFlow()

    private val _estado = MutableStateFlow<EstadoRotas>(EstadoRotas.Inicial)
    val estado: StateFlow<EstadoRotas> = _estado.asStateFlow()

    private val _filtros = MutableStateFlow(Filtros())
    val filtros: StateFlow<Filtros> = _filtros.asStateFlow()

    /** Linhas da Carris Metropolitana com alerta ativo, para o "⚠" dos cartões. */
    private val _alertas = MutableStateFlow(emptySet<String>())
    val alertas: StateFlow<Set<String>> = _alertas.asStateFlow()

    /** Itinerário aberto no detalhe; null = lista. */
    private val _aberto = MutableStateFlow<Itinerario?>(null)
    val aberto: StateFlow<Itinerario?> = _aberto.asStateFlow()

    private var posicao: Pair<Double, Double>? = null
    private var procura: Job? = null

    /** Só depois dos filtros guardados chegarem é que se procura, para não pedir duas vezes ao abrir. */
    private var filtrosChegaram = false

    init {
        filtrosGuardados.distinctUntilChanged().onEach {
            _filtros.value = it
            filtrosChegaram = true
            procurar()
        }.launchIn(viewModelScope)
        viewModelScope.launch { _alertas.value = linhasComAlerta() }
    }

    fun definirOrigem(l: Lugar?) {
        _pedido.update { it.copy(origem = l) }
        procurar()
    }

    fun definirDestino(l: Lugar) {
        _pedido.update { it.copy(destino = l) }
        procurar()
    }

    fun definirHora(tipo: TipoHora, hora: LocalTime?) {
        _pedido.update { it.copy(tipoHora = tipo, hora = hora.takeIf { tipo != TipoHora.AGORA }) }
        procurar()
    }

    /** Volta a procurar na primeira posição e quando a nova está a mais de 100 m da anterior (o fix fresco depois da última conhecida); o ruído do GPS não repete o pedido. */
    fun definirPosicao(p: Pair<Double, Double>) {
        val anterior = posicao
        posicao = p
        val mudou = anterior == null || distanciaMetros(anterior.first, anterior.second, p.first, p.second) > PERTO_METROS
        if (mudou && _pedido.value.origem == null) procurar()
    }

    fun mudarFiltros(f: Filtros) {
        viewModelScope.launch { guardarFiltros(f) }
    }

    fun tentarOutraVez() = procurar()

    fun abrir(i: Itinerario?) {
        _aberto.value = i
    }

    private fun procurar() {
        procura?.cancel()
        val p = _pedido.value
        val destino = p.destino ?: run {
            _estado.value = EstadoRotas.Inicial
            return
        }
        val origem = p.origem ?: posicao?.let { (lat, lon) -> Lugar(MINHA_POSICAO, lat, lon, TipoLugar.POSICAO) }
        when {
            origem == null -> _estado.value = EstadoRotas.Vazias(Motivo.SEM_ORIGEM)
            distanciaMetros(origem.lat, origem.lon, destino.lat, destino.lon) < PERTO_METROS ->
                _estado.value = EstadoRotas.Vazias(Motivo.MUITO_PERTO)
            !filtrosChegaram -> _estado.value = EstadoRotas.ACarregar
            else -> {
                _estado.value = EstadoRotas.ACarregar
                procura = viewModelScope.launch {
                    // A posição atual guarda-se só pelo nome: GPS cru enchia os recentes de cópias.
                    registarRecente(if (origem.tipo == TipoLugar.POSICAO) Lugar(MINHA_POSICAO, 0.0, 0.0, TipoLugar.POSICAO) else origem, destino)
                    val agora = relogio()
                    val hora = p.hora?.let { proximaOcorrencia(it, agora.atZone(LISBOA_FUSO)).toInstant() } ?: agora
                    _estado.value = when (val r = planear(origem, destino, hora, p.tipoHora == TipoHora.CHEGAR, _filtros.value)) {
                        is Resultado.Ok ->
                            if (r.valor.isEmpty()) EstadoRotas.Vazias(Motivo.SEM_LIGACOES)
                            else EstadoRotas.Prontas(r.valor, etiquetas(r.valor))
                        is Resultado.Falha -> EstadoRotas.Erro(semLigacao = r.codigo == null)
                    }
                }
            }
        }
    }

    private companion object {
        const val PERTO_METROS = 100.0
    }
}
