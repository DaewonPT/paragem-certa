package pt.pixellusitano.paragemcerta.core.comunidade

import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.core.viagem.paragensDe

/** O que se relata; `chave` é o `tipo` que o servidor conhece e `deLinha` diz se o relato leva uma linha (senão é da paragem). */
enum class TipoRelato(val chave: String, val deLinha: Boolean) {
    ACIDENTE("acidente", true), DESVIO("desvio", true), EVENTO("evento", true),
    NAO_PASSOU("nao_passou", true), VAI_CHEIO("vai_cheio", true),
    ELEVADOR("elevador", false), PROBLEMA_PARAGEM("problema_paragem", false),
}

enum class MotivoDenuncia(val chave: String) { FALSO("falso"), OFENSIVO("ofensivo"), DADOS_PESSOAIS("dados_pessoais"), PERIGO("perigo"), OUTRO("outro") }

/** O que se corrige numa paragem: a posição, o nome, ou que já não existe. */
sealed interface Correcao {
    data class Posicao(val lat: Double, val lon: Double) : Correcao
    data class Nome(val nome: String) : Correcao
    data object JaNaoExiste : Correcao
}

/** A proximidade só se verifica no telemóvel (a posição nunca sai dele): relata-se a menos de 300 m da paragem. */
const val DISTANCIA_RELATAR_M = 300.0

/** Distância ≤ 300 m e, se o GPS disser a precisão, precisão ≤ 300 m (uma posição de 2 km não prova que estás lá). */
fun pertoParaRelatar(distanciaM: Double, precisaoM: Float?): Boolean =
    distanciaM <= DISTANCIA_RELATAR_M && (precisaoM == null || precisaoM <= DISTANCIA_RELATAR_M)

/** A chave da linha nos relatos: o nome visível ("736", "15E") em maiúsculas, como o servidor a guarda. */
fun chaveLinha(nome: String): String = nome.trim().uppercase()

/** A chave de uma paragem de uma viagem: as da Carris Metropolitana do Transitous passam a "CM:…", as outras a "TRANSITOUS:…". */
fun chaveDaParagemEtapa(id: String?): String? {
    if (id == null) return null
    return cmParagemDeTransitous(id)?.let { RefParagem(Fonte.CM, it).chave } ?: RefParagem(Fonte.TRANSITOUS, id).chave
}

/**
 * A paragem fica à frente de quem viaja: nas paragens da etapa atual depois da última de que o veículo já saiu
 * (a de embarque, antes de sair dela, não conta), ou numa etapa em veículo seguinte.
 */
fun Viagem.paragemAFrente(e: EstadoViagem, chaveParagem: String): Boolean {
    fun tem(paragens: List<ParagemEtapa>) = paragens.any { chaveDaParagemEtapa(it.id) == chaveParagem }
    val etapas = plano.etapas
    if (e.etapa !in etapas.indices) return false
    return tem(paragensDe(etapas[e.etapa]).drop(paragemPassada(e) + 1)) ||
        etapas.drop(e.etapa + 1).any { !it.aPe && tem(paragensDe(it)) }
}
