package pt.pixellusitano.paragemcerta.ui.rotas

import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.viagem.VEICULO_IDADE_S
import pt.pixellusitano.paragemcerta.viagem.viagemAoVivo
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** O que o detalhe da rota diz do autocarro que se procura: ainda sem resposta, sem posição ao vivo, ou no mapa. */
enum class EstadoAutocarro { A_PROCURAR, SEM_POSICAO, NO_MAPA }

/**
 * A paragem da Carris Metropolitana onde se entra, nas etapas de autocarro da CM com linha (as únicas com posição ao vivo
 * por trip_id); nas outras, null.
 */
internal fun Etapa.embarqueCm(): String? =
    if (aPe || linhaNome == null) null else paragens.firstOrNull()?.id?.let(::cmParagemDeTransitous)

/**
 * O autocarro da etapa: o mesmo que o modo viagem segue (viagemAoVivo) ou, depois de passar na paragem e sair das chegadas, o
 * da viagem [guardada] (a do autocarro que já se viu no mapa), com uma posição de há 60 s no máximo (ou até 60 s à frente,
 * como no modo viagem: os relógios não batem certo); senão null.
 */
internal fun autocarroDaEtapa(chegadas: List<Chegada>, veiculos: List<Veiculo>, etapa: Etapa, agora: Instant, guardada: String? = null): Veiculo? {
    val viagem = viagemAoVivo(chegadas, etapa) ?: guardada ?: return null
    return veiculos.firstOrNull { it.viagem == viagem && abs(Duration.between(it.ultimoSinal, agora).seconds) <= VEICULO_IDADE_S }
}

/**
 * [voltas]: consultas às chegadas já feitas (com ou sem resposta). "A procurar" só até à primeira, ou até à segunda (30 s)
 * se os veículos ainda não chegaram: sem resposta nenhuma, fica sem posição em vez de procurar para sempre.
 */
internal fun estadoAutocarro(autocarro: Veiculo?, voltas: Int, temVeiculos: Boolean): EstadoAutocarro = when {
    autocarro != null -> EstadoAutocarro.NO_MAPA
    voltas == 0 || (!temVeiculos && voltas < 2) -> EstadoAutocarro.A_PROCURAR
    else -> EstadoAutocarro.SEM_POSICAO
}
