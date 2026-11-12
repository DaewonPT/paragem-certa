package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.DetalheDasLinhas
import pt.pixellusitano.paragemcerta.dados.DetalheLinha
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.SentidoLinha
import pt.pixellusitano.paragemcerta.dados.paragemInicial
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import java.time.Instant
import java.time.LocalTime

private sealed interface Carga {
    data object ACarregar : Carga
    data class Pronta(val d: DetalheLinha) : Carga
    data object SemLinha : Carga
    data object Falhou : Carga
}

// O que a pessoa escolheu (sentido e paragem; null = o de partida) e o que já se carregou. As chegadas vêm à parte: só com o ecrã à vista.
private data class Interno(
    val carga: Carga = Carga.ACarregar,
    val sentido: Int? = null,
    val paragem: RefParagem? = null,
    val horarioAberto: Boolean = false,
    val horario: List<LocalTime>? = null,
    val horarioFalhou: Boolean = false,
    val relatos: List<RelatoRemoto> = emptyList(),
)

private class Vista(val d: DetalheLinha, val s: SentidoLinha, val p: Paragem, val favorita: LinhaFavorita?) {
    val chave get() = s.sentido to p.ref
}

/**
 * O ecrã de uma linha. [sentidoDaRota] e [paragemDaRota] (uma `RefParagem.chave`) vêm da rota (uma linha favorita); sem eles abre no
 * sentido da favorita (se há) e na paragem de [paragemInicial]. Só a paragem escolhida pede chegadas, e só com o ecrã à vista (pára 5 s
 * depois de deixar de estar); o horário só se pede depois de [verHorario].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinhaViewModel(
    private val ref: RefLinha,
    private val detalhes: DetalheDasLinhas,
    private val repositorio: Repositorio,
    /** Os relatos ativos da linha pelo nome que a pessoa vê; vazio com a comunidade desligada ou sem rede. */
    private val relatosDaLinha: suspend (String) -> List<RelatoRemoto>,
    sentidoDaRota: Int? = null,
    paragemDaRota: String? = null,
    private val posicao: Pair<Double, Double>? = null,
    private val relogio: () -> Instant = Instant::now,
) : ViewModel() {
    private val interno = MutableStateFlow(Interno(sentido = sentidoDaRota, paragem = paragemDaRota?.let(RefParagem::daChave)))
    private val favoritas = repositorio.linhasFavoritas().map { l -> l.filter { it.ref == ref } }
    private var horarioEmCurso: Job? = null

    init {
        viewModelScope.launch {
            val r = detalhes.detalhe(ref)
            val carga = when {
                r is Resultado.Ok -> Carga.Pronta(r.valor)
                r is Resultado.Falha && r.codigo == 404 -> Carga.SemLinha
                else -> Carga.Falhou
            }
            interno.update { it.copy(carga = carga) }
            // Depois do detalhe: a lista das paragens não espera pelos relatos.
            if (r is Resultado.Ok) {
                val relatos = relatosDaLinha(r.valor.nome)
                interno.update { it.copy(relatos = relatos) }
            }
        }
    }

    // Sem escolha, o sentido da favorita (se há) e a paragem de partida; com ela, o que a pessoa escolheu.
    private fun vista(i: Interno, favoritas: List<LinhaFavorita>): Vista? {
        val d = (i.carga as? Carga.Pronta)?.d ?: return null
        val s = d.sentidos.firstOrNull { it.sentido == i.sentido } ?: d.sentidos.firstOrNull { it.sentido == favoritas.firstOrNull()?.sentido } ?: d.sentidos.first()
        val favorita = favoritas.firstOrNull { it.sentido == s.sentido }
        val p = s.paragens.firstOrNull { it.ref == i.paragem } ?: paragemInicial(s, favorita?.paragem?.chave, posicao)
        return Vista(d, s, p, favorita)
    }

    // As chegadas da paragem escolhida; mudar de paragem ou de sentido pára o pedido da anterior e começa o da nova. Vêm com a paragem
    // de que são, para o ecrã nunca juntar as chegadas de uma paragem ao nome de outra.
    private val chegadas: Flow<Pair<Pair<Int, RefParagem>?, EstadoChegadas?>> = combine(interno, favoritas, ::vista)
        .distinctUntilChangedBy { it?.chave }
        .flatMapLatest { v ->
            if (v == null) flowOf(null to null)
            else detalhes.chegadas(v.d, v.s.sentido, v.p).map<EstadoChegadas, EstadoChegadas?> { it }.onStart { emit(null) }.map { v.chave to it }
        }

    val estado: StateFlow<EstadoLinha> = combine(interno, favoritas, chegadas) { i, f, c ->
        val v = vista(i, f)
        EstadoLinha(
            detalhe = v?.d,
            semLinha = i.carga is Carga.SemLinha,
            sentido = v?.s?.sentido ?: 0,
            escolhida = v?.p,
            chegadas = c.second.takeIf { v != null && c.first == v.chave },
            horario = i.horario,
            relatos = i.relatos,
            favorita = v?.favorita,
            horarioAberto = i.horarioAberto,
            horarioFalhou = i.horarioFalhou,
            falhou = i.carga is Carga.Falhou,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoLinha())

    private suspend fun vistaAtual() = vista(interno.value, favoritas.first())

    fun mudarSentido(sentido: Int) {
        // A paragem escolhida era de outro sentido: volta à de partida.
        interno.update { it.copy(sentido = sentido, paragem = null, horario = null, horarioFalhou = false) }
        pedirHorarioSeAberto()
    }

    fun escolherParagem(p: Paragem) {
        interno.update { it.copy(paragem = p.ref, horario = null, horarioFalhou = false) }
        pedirHorarioSeAberto()
    }

    fun verHorario() {
        interno.update { it.copy(horarioAberto = true) }
        pedirHorarioSeAberto()
    }

    private fun pedirHorarioSeAberto() {
        horarioEmCurso?.cancel()
        if (!interno.value.horarioAberto) return
        horarioEmCurso = viewModelScope.launch {
            val v = vistaAtual() ?: return@launch
            // O dia de serviço que ainda corre, pelo relógio de Lisboa (certo nos dias de mudança da hora): a CM conta-o das 04:00 às 04:00,
            // como o site dela; o Transitous pede-o a partir das 03:00 (horarioDoDia).
            // Limite: entre as 03:00 e as 04:00, saltar entre uma linha da CM e uma do Transitous limpa a cache dos horários (outro dia).
            val dia = relogio().atZone(LISBOA_FUSO).toLocalDateTime().minusHours(if (v.d.ref.fonte == Fonte.CM) 4 else 3).toLocalDate()
            when (val r = detalhes.horario(v.d, v.s.sentido, v.p, dia)) {
                is Resultado.Ok -> interno.update { it.copy(horario = r.valor, horarioFalhou = false) }
                is Resultado.Falha -> interno.update { it.copy(horario = null, horarioFalhou = true) }
            }
        }
    }

    /** Guarda (ou tira) a linha favorita no sentido à vista e com a paragem escolhida. */
    fun alternarFavorita() {
        viewModelScope.launch {
            val v = vistaAtual() ?: return@launch
            // O que está à vista fica como está: sem favorita, o sentido e a paragem de partida passariam a ser outros.
            interno.update { it.copy(sentido = v.s.sentido, paragem = v.p.ref) }
            repositorio.alternarLinhaFavorita(v.d.ref, v.s.sentido, v.s.destino, v.p, v.d.nome)
        }
    }

    /** Abre o ecrã da paragem [p]; uma do catálogo (Transitous) que a base ainda não tem guarda-se antes, porque o ecrã da paragem só lê a base. */
    fun abrirParagem(p: Paragem, aoAbrir: (Paragem) -> Unit) {
        viewModelScope.launch {
            if (p.ref.fonte == Fonte.TRANSITOUS) repositorio.guardarParagemSeFalta(p)
            aoAbrir(p)
        }
    }

    /** Numa linha favorita, a paragem escolhida passa a ser a habitual. */
    fun usarComoHabitual() {
        viewModelScope.launch {
            val v = vistaAtual() ?: return@launch
            v.favorita?.let { repositorio.mudarParagemHabitual(it, v.p) }
        }
    }
}
