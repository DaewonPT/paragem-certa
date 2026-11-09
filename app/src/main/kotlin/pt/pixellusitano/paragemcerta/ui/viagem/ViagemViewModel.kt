package pt.pixellusitano.paragemcerta.ui.viagem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.core.viagem.paragensDe
import java.time.Instant

/** Onde está uma etapa em relação a ti. */
enum class Situacao { FEITA, ATUAL, SEGUINTE }

/**
 * Uma paragem na lista da etapa em veículo. passada: o veículo já saiu dela; aqui: onde estás (a última de que saiu, ou a de
 * embarque enquanto esperas); sai: onde sais. hora: a do horário, mais o atraso na etapa atual.
 */
data class ParagemVista(val nome: String, val hora: Instant, val passada: Boolean, val aqui: Boolean, val sai: Boolean)

/** faltam: as paragens até sais (0 a pé e nas etapas feitas; nas seguintes, todas as da etapa). */
data class CartaoEtapa(val etapa: Etapa, val situacao: Situacao, val paragens: List<ParagemVista>, val faltam: Int)

/**
 * Tudo o que o ecrã da viagem mostra. atual: o cartão com que abre. horario: o selo "Posição estimada pelo horário".
 * chegada: a hora prevista de chegar ao destino.
 */
data class EcraViagem(
    val cartoes: List<CartaoEtapa>, val atual: Int, val estado: EstadoViagem, val horario: Boolean,
    val chegada: Instant, val de: String, val para: String, val itinerario: Itinerario,
)

/** O plano e o estado que o serviço escreveu, transformados no que o ecrã mostra. As contas das paragens são as do motor. */
fun ecraDaViagem(plano: PlanoViagem, estado: EstadoViagem, viagem: Viagem): EcraViagem {
    val itinerario = plano.itinerario
    val etapas = itinerario.etapas
    val atual = if (estado.terminada) etapas.lastIndex else estado.etapa
    val passada = viagem.paragemPassada(estado)
    val cartoes = etapas.mapIndexed { i, etapa ->
        val situacao = if (estado.terminada || i < atual) Situacao.FEITA else if (i == atual) Situacao.ATUAL else Situacao.SEGUINTE
        val paragens = if (etapa.aPe) emptyList() else paragensDe(etapa).let { todas ->
            todas.mapIndexed { k, p ->
                ParagemVista(
                    nome = p.nome,
                    hora = if (situacao == Situacao.ATUAL) p.hora.plusSeconds(estado.atrasoS) else p.hora,
                    passada = situacao == Situacao.FEITA || (situacao == Situacao.ATUAL && k < passada),
                    aqui = situacao == Situacao.ATUAL && k == passada,
                    sai = k == todas.lastIndex,
                )
            }
        }
        val faltam = when {
            etapa.aPe || situacao == Situacao.FEITA -> 0
            situacao == Situacao.ATUAL -> viagem.faltam(estado)
            else -> paragens.lastIndex
        }
        CartaoEtapa(etapa, situacao, paragens, faltam)
    }
    return EcraViagem(
        cartoes = cartoes, atual = atual, estado = estado,
        horario = estado.origem == Origem.HORARIO && !estado.terminada,
        // Limite: o atraso da etapa atual passa por inteiro para a chegada (um transbordo com folga absorveria parte); só soma, nunca adianta.
        chegada = itinerario.chegada.plusSeconds(maxOf(0L, estado.atrasoS)),
        de = etapas.first().de.nome, para = etapas.last().para.nome, itinerario = itinerario,
    )
}

sealed interface VistaViagem {
    /** Ainda não leu os ficheiros. */
    data object ALer : VistaViagem
    /** Não há viagem: o ecrã sai (acabou de se parar, ou o ficheiro era velho). */
    data object SemViagem : VistaViagem
    data class Ativa(val ecra: EcraViagem) : VistaViagem
}

/**
 * Lê o plano e o estado da viagem (ficheiros que o serviço do processo :trip escreve) quando o ecrã lho pede: ao abrir,
 * ao voltar à app e a cada aviso do serviço. A leitura corre fora da thread principal.
 */
class ViagemViewModel(
    private val ler: () -> Pair<PlanoViagem, EstadoViagem>?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _vista = MutableStateFlow<VistaViagem>(VistaViagem.ALer)
    val vista: StateFlow<VistaViagem> = _vista.asStateFlow()
    private var leitura: Job? = null

    /** O serviço não arrancou (ver FicheirosViagem.EXTRA_SEM_SERVICO): não há viagem a mostrar, mesmo que os ficheiros fiquem. */
    fun semServico() {
        leitura?.cancel()
        _vista.value = VistaViagem.SemViagem
    }

    /** Um pedido novo substitui o que ainda não acabou: uma leitura velha não pode pisar a nova. */
    fun atualizar() {
        leitura?.cancel()
        leitura = viewModelScope.launch {
            _vista.value = withContext(io) {
                ler()?.let { (plano, estado) -> VistaViagem.Ativa(ecraDaViagem(plano, estado, Viagem(plano.itinerario))) } ?: VistaViagem.SemViagem
            }
        }
    }
}
