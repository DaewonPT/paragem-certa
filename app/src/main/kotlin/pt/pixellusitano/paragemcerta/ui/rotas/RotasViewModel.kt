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
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.core.rotas.ordenar
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

sealed interface EstadoRotas {
    data object Inicial : EstadoRotas
    data object ACarregar : EstadoRotas
    /**
     * anterior e seguinte: os cursores de "mais cedo" e "mais tarde" (null = não há). partidas: as próximas partidas da primeira
     * etapa em veículo, pelo índice do itinerário (só para "partir agora", na primeira página, e só as que a fonte deu).
     */
    data class Prontas(
        val itinerarios: List<Itinerario>,
        val etiquetas: List<Set<Etiqueta>>,
        val anterior: String? = null,
        val seguinte: String? = null,
        val partidas: Map<Int, List<Chegada>> = emptyMap(),
        val aCarregarPagina: Boolean = false,
        val erroPagina: Boolean = false,
    ) : EstadoRotas
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

/** Também vem de fora da app (o widget), por isso recusa coordenadas impossíveis e nomes enormes. */
fun lugarDaRota(texto: String): Lugar? {
    val partes = texto.split('|')
    if (partes.size < 3) return null
    val lon = partes.last().toDoubleOrNull() ?: return null
    val lat = partes[partes.size - 2].toDoubleOrNull() ?: return null
    if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return null
    val nome = partes.dropLast(2).joinToString("|")
    if (nome.length > 500) return null
    return Lugar(nome, lat, lon, TipoLugar.LOCAL)
}

class RotasViewModel(
    private val planear: suspend (Lugar, Lugar, Instant, Boolean, Filtros, String?) -> Resultado<PlanoRotas>,
    private val registarRecente: suspend (Lugar, Lugar) -> Unit,
    linhasComAlerta: suspend () -> Set<String>,
    filtrosGuardados: Flow<Filtros>,
    private val guardarFiltros: suspend (Filtros) -> Unit,
    /** As próximas partidas da linha de uma etapa; null = a fonte não as deu. */
    private val proximasPartidas: suspend (Etapa) -> List<Chegada>? = { null },
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

    /**
     * A etapa do [aberto] cujo autocarro se mostra no mapa; null = nenhuma. Fica aqui, e não no estado guardado do ecrã, para
     * se perder com o processo, tal como o [aberto]: senão voltava sozinha na rota seguinte que se abrisse.
     */
    private val _seguida = MutableStateFlow<Int?>(null)
    val seguida: StateFlow<Int?> = _seguida.asStateFlow()

    /** Se a preparação da viagem do [aberto] está à vista; pelo mesmo motivo que a [seguida], vive aqui e não no estado guardado. */
    private val _preparar = MutableStateFlow(false)
    val preparar: StateFlow<Boolean> = _preparar.asStateFlow()

    private var posicao: Pair<Double, Double>? = null
    private var procura: Job? = null

    /** O pedido da lista que está no ecrã com um cursor: as páginas "mais cedo" e "mais tarde" só valem com o resto do pedido igual. */
    private var pedirPagina: (suspend (String) -> Resultado<PlanoRotas>)? = null

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
        _seguida.value = null
        _preparar.value = false
    }

    fun seguir(etapa: Int) {
        _seguida.value = etapa
    }

    fun preparar(sim: Boolean) {
        _preparar.value = sim
    }

    fun maisCedo() = pagina(seguinte = false)

    fun maisTarde() = pagina(seguinte = true)

    /** Troca a lista pela página do cursor (sem próximas partidas); numa falha a lista fica e [EstadoRotas.Prontas.erroPagina] avisa. */
    private fun pagina(seguinte: Boolean) {
        val atual = _estado.value as? EstadoRotas.Prontas ?: return
        val cursor = (if (seguinte) atual.seguinte else atual.anterior) ?: return
        val pedir = pedirPagina ?: return
        if (atual.aCarregarPagina) return
        procura?.cancel()
        val aCarregar = atual.copy(aCarregarPagina = true, erroPagina = false)
        _estado.value = aCarregar
        procura = viewModelScope.launch {
            _estado.value = when (val r = pedir(cursor)) {
                // Uma página sem rotas não é para mostrar: a lista fica e o botão dessa página desaparece.
                is Resultado.Ok -> when {
                    r.valor.itinerarios.isNotEmpty() -> prontas(r.valor)
                    seguinte -> aCarregar.copy(aCarregarPagina = false, seguinte = null)
                    else -> aCarregar.copy(aCarregarPagina = false, anterior = null)
                }
                is Resultado.Falha -> aCarregar.copy(aCarregarPagina = false, erroPagina = true)
            }
        }
    }

    /** Os itinerários pela ordem das preferências e as etiquetas calculadas sobre a lista já ordenada. */
    private fun prontas(plano: PlanoRotas): EstadoRotas.Prontas {
        val lista = ordenar(plano.itinerarios, _filtros.value.ordem)
        return EstadoRotas.Prontas(lista, etiquetas(lista), plano.anterior, plano.seguinte)
    }

    /**
     * As próximas partidas da primeira etapa em veículo de cada rota, uma rota de cada vez (a lista já está no ecrã e cada
     * cartão ganha as suas quando chegam). Etapas iguais perguntam-se uma vez; as que a fonte não dá ficam sem partidas.
     */
    private suspend fun carregarPartidas(itinerarios: List<Itinerario>) {
        val dadas = mutableMapOf<Etapa, List<Chegada>?>()
        itinerarios.forEachIndexed { i, rota ->
            val etapa = rota.etapas.firstOrNull { e -> !e.aPe } ?: return@forEachIndexed
            val partidas = if (etapa in dadas) dadas[etapa] else proximasPartidas(etapa).also { dadas[etapa] = it }
            if (partidas.isNullOrEmpty()) return@forEachIndexed
            _estado.update { s -> if (s is EstadoRotas.Prontas) s.copy(partidas = s.partidas + (i to partidas)) else s }
        }
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
                    val chegarAs = p.tipoHora == TipoHora.CHEGAR
                    val filtros = _filtros.value
                    pedirPagina = { cursor -> planear(origem, destino, hora, chegarAs, filtros, cursor) }
                    when (val r = planear(origem, destino, hora, chegarAs, filtros, null)) {
                        is Resultado.Ok ->
                            if (r.valor.itinerarios.isEmpty()) _estado.value = EstadoRotas.Vazias(Motivo.SEM_LIGACOES)
                            else {
                                val prontas = prontas(r.valor)
                                _estado.value = prontas
                                // Para sair mais tarde ou chegar a uma hora, as partidas de agora não dizem nada.
                                if (p.tipoHora == TipoHora.AGORA) carregarPartidas(prontas.itinerarios)
                            }
                        is Resultado.Falha -> _estado.value = EstadoRotas.Erro(semLigacao = r.codigo == null)
                    }
                }
            }
        }
    }

    private companion object {
        const val PERTO_METROS = 100.0
    }
}
