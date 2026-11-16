package pt.pixellusitano.paragemcerta.core.aovivo

import pt.pixellusitano.paragemcerta.core.fontes.servidor.VeiculoCarrisRemoto
import pt.pixellusitano.paragemcerta.core.fontes.transitous.chaveDoNome
import pt.pixellusitano.paragemcerta.core.linhas.Catalogo
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import java.time.Instant

private const val PREFIXO_CARRIS = "pt-Carris_"

/**
 * Um autocarro da Carris como veículo do mapa: id "carris:<id>" (os números repetem-se entre operadores). A linha vem do
 * catálogo pela rota do GTFS-RT, que o servidor manda sem o prefixo do Transitous ("76_0" → "pt-Carris_76_0"); sem o catálogo,
 * ou com uma rota que ele não conhece, [Veiculo.linhaId] fica vazio e o veículo desenha-se sem nome.
 */
fun veiculoDaCarris(v: VeiculoCarrisRemoto, catalogo: Catalogo?): Veiculo = Veiculo(
    id = "carris:${v.id}", linhaId = catalogo?.daRota(PREFIXO_CARRIS + v.rota)?.nome.orEmpty(), lat = v.lat, lon = v.lon, rumo = null,
    ultimoSinal = Instant.ofEpochSecond(v.ts), viagem = v.viagem, paragem = v.paragem,
)

private val VIAGEM_CARRIS = Regex("""^\d{8}_\d\d:\d\d_$PREFIXO_CARRIS""")

/** Viagem do Transitous → trip_id do GTFS-RT da Carris: "20261103_15:56_pt-Carris_5166_20260606_76_0_33" → "5166_20260606_76_0_33"; outro operador → null. */
fun viagemCarrisDeTransitous(tripId: String): String? =
    VIAGEM_CARRIS.find(tripId)?.let { tripId.substring(it.range.last + 1) }?.takeIf { it.isNotEmpty() }

/**
 * Quantas paragens faltam ao autocarro para a [posicao] da tua paragem na viagem (contada a partir de 1) quando vai na
 * paragem [seq] (o `current_stop_sequence`): 0 = a chegar. Null se já passou (posição antes de seq): sem etiqueta, nunca "a −3 paragens".
 */
fun paragensEmFalta(posicao: Int, seq: Int): Int? = (posicao - seq).takeIf { it >= 0 }

// As linhas do Metro pela chave que os relatos e os avisos usam (chaveLinha do nome curto do Transitous: "Az" → "AZ").
private val LINHAS_METRO = mapOf("AZ" to "Azul", "AM" to "Amarela", "VD" to "Verde", "VM" to "Vermelha")

/** "Az", "Azul" ou "Linha Azul" → "AZ" (e o mesmo para AM, VD e VM); outro nome → null. */
fun chaveMetro(nomeLinha: String): String? {
    val n = chaveDoNome(nomeLinha).removePrefix("linha ")
    return LINHAS_METRO.entries.firstOrNull { n == it.key.lowercase() || n == it.value.lowercase() }?.key
}

/** "AZ" → "Azul", "AM" → "Amarela", "VD" → "Verde", "VM" → "Vermelha"; outra chave → null. */
fun nomeLinhaMetro(chave: String): String? = LINHAS_METRO[chave]

// Mais longe do que isto, o ponto da forma não é a paragem: o autocarro já a passou.
private const val TROCO_PERTO_M = 100.0

/**
 * O pedaço da [forma] (lat, lon) do autocarro [de] até à paragem [ate], com os dois nas pontas e os pontos pela ordem da marcha.
 * A paragem procura-se só a partir do ponto do autocarro, para um circular ir até ao fim e não voltar ao início. Vazio sem forma
 * ou com o autocarro depois da paragem.
 */
fun troco(forma: List<Pair<Double, Double>>, de: Pair<Double, Double>, ate: Pair<Double, Double>): List<Pair<Double, Double>> {
    fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>) = distanciaMetros(a.first, a.second, b.first, b.second)
    if (forma.isEmpty()) return emptyList()
    val i = forma.indices.minBy { dist(forma[it], de) }
    val j = (i..forma.lastIndex).minBy { dist(forma[it], ate) }
    if (dist(forma[j], ate) > TROCO_PERTO_M) return emptyList()
    return listOf(de) + forma.subList(minOf(i + 1, j), j) + listOf(ate)
}
