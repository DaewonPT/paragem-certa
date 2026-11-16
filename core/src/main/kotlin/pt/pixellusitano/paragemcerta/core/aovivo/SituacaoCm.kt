package pt.pixellusitano.paragemcerta.core.aovivo

import pt.pixellusitano.paragemcerta.core.atraso.TETO_CM
import pt.pixellusitano.paragemcerta.core.fontes.cm.PadraoAoVivo
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Até quantos segundos de diferença (para trás ou, com o relógio do telemóvel errado, para a frente) o sinal de um veículo conta. */
const val IDADE_PROVA_S = 120L

private class Ligacao(val viagem: String?, val emFalta: Int, val delta: Duration)

/**
 * (paragens em falta, Δ, passagens por [paragemId] que o path ainda tem depois dessa) do veículo até [paragemId] pelo [p];
 * null se não está antes dela ou não se sabe onde está.
 */
private fun posicaoAte(v: Veiculo, p: PadraoAoVivo?, paragemId: String): Triple<Int, Duration, Int>? {
    if (p == null || p.segundos.size != p.paragens.size) return null
    val onde = v.paragem ?: return null
    val i = p.paragens.indexOf(onde)
    if (i < 0 || p.paragens.lastIndexOf(onde) != i) return null // fora do path (UNAVAILABLE_STOP_ID) ou repetida (circulares, terminal)
    val j = (i until p.paragens.size).firstOrNull { p.paragens[it] == paragemId } ?: return null // já passou
    val n = paragensEmFalta(j + 1, i + 1) ?: return null
    return Triple(n, Duration.ofSeconds((p.segundos[j] - p.segundos[i]).toLong()), p.paragens.subList(j + 1, p.paragens.size).count { it == paragemId })
}

/**
 * As [chegadas] da CM de [paragemId] que ainda vêm. Com a hora em uso no futuro ou há menos de 30 s ficam como estão (com autocarro
 * ligado ganham [Chegada.paragensEmFalta]); com autocarro ligado e antes da paragem ficam até [TETO_CM] depois, com a prevista pela
 * posição se a da CM faltar ou já tiver passado; as outras saem. Ordem pela hora em uso. [padroes] pelo id limpo.
 */
fun situacaoCm(chegadas: List<Chegada>, veiculos: List<Veiculo>, padroes: Map<String, PadraoAoVivo>, paragemId: String, agora: Instant): List<Chegada> {
    val frescos = veiculos.filter { abs(Duration.between(it.ultimoSinal, agora).seconds) <= IDADE_PROVA_S }
    val ligadas = HashMap<Int, Ligacao>()
    // 1. Pelo trip_id (igual nas duas APIs; nunca pelo número do veículo). Uma viagem que passa duas vezes na paragem (circular,
    //    terminal) tem uma chegada por passagem: liga-se a da passagem para onde o autocarro vai, contada a partir da última
    //    (as primeiras podem já ter saído da janela de 60 min).
    val vistas = HashSet<String>()
    chegadas.indices.filter { chegadas[it].idViagem != null }.groupBy { chegadas[it].idViagem!! }.forEach { (id, ks) ->
        vistas += id
        val v = frescos.firstOrNull { it.viagem == id } ?: return@forEach
        val (n, d, depois) = posicaoAte(v, v.padrao?.let(padroes::get), paragemId) ?: return@forEach
        val daViagem = ks.sortedBy { chegadas[it].programada }
        daViagem.getOrNull(daViagem.size - 1 - depois)?.let { ligadas[it] = Ligacao(null, n, d) }
    }
    // 2. Sem trip_id: os veículos do mesmo padrão, antes da paragem e sem chegada, do mais perto para o mais longe; cada um fica
    //    com a chegada sem trip_id do padrão cuja programada mais se aproxima de agora + Δ. Cada veículo e cada chegada uma vez.
    val livres = chegadas.indices.filter { chegadas[it].idViagem == null && chegadas[it].padrao != null }.toMutableList()
    frescos.filter { it.padrao != null && it.viagem !in vistas }
        .mapNotNull { v -> posicaoAte(v, padroes[v.padrao], paragemId)?.let { (n, d) -> Triple(v, n, d) } }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .forEach { (v, n, d) ->
            val esperada = agora.plus(d)
            val k = livres.filter { chegadas[it].padrao == v.padrao }
                .minByOrNull { abs(Duration.between(chegadas[it].programada, esperada).seconds) } ?: return@forEach
            livres -= k
            ligadas[k] = Ligacao(v.viagem, n, d)
        }
    // 3. Quem fica: ligada e antes da paragem até 30 min depois da hora em uso; as outras até 30 s depois.
    return chegadas.mapIndexedNotNull { k, c ->
        val hora = c.prevista ?: c.programada
        val l = ligadas[k]
        when {
            l != null && !hora.plus(TETO_CM).isBefore(agora) -> {
                val pelaPosicao = c.prevista == null || c.prevista.isBefore(agora)
                c.copy(prevista = if (pelaPosicao) agora.plus(l.delta) else c.prevista, idViagem = l.viagem ?: c.idViagem,
                    paragensEmFalta = l.emFalta, pelaPosicao = pelaPosicao)
            }
            !hora.plusSeconds(30).isBefore(agora) -> c
            else -> null
        }
    }.sortedBy { it.prevista ?: it.programada }
}
