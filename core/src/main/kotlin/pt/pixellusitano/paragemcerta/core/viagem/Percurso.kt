package pt.pixellusitano.paragemcerta.core.viagem

import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

private const val RAIO_TERRA = 6_371_000.0

/** Onde uma posição cai na linha: metros desde o início dela (ao) e a que distância da linha fica (afastamento). */
data class Projecao(val ao: Double, val afastamento: Double)

/**
 * A linha de uma etapa em veículo medida em metros, para saber em que ponto dela vai quem viaja.
 * Plano local à volta do início da linha (x = Δlon·cos(lat0)·R, y = Δlat·R): chega para linhas de dezenas de km.
 * Sem desenho (o Metro) a linha é a que une as paragens; precisa de duas ou mais paragens.
 */
class Percurso(desenho: List<Pair<Double, Double>>, private val paragens: List<ParagemEtapa>) {
    private val linha = desenho.ifEmpty { paragens.map { it.lat to it.lon } }

    init {
        require(linha.size >= 2) { "um percurso precisa de uma linha com dois ou mais pontos" }
    }

    private val lat0 = linha.first().first
    private val lon0 = linha.first().second
    private val metrosPorGrauLat = Math.toRadians(1.0) * RAIO_TERRA
    private val metrosPorGrauLon = metrosPorGrauLat * cos(Math.toRadians(lat0))
    private val x = DoubleArray(linha.size) { (linha[it].second - lon0) * metrosPorGrauLon }
    private val y = DoubleArray(linha.size) { (linha[it].first - lat0) * metrosPorGrauLat }
    private val acumulado = DoubleArray(linha.size).also { for (i in 1 until it.size) it[i] = it[i - 1] + hypot(x[i] - x[i - 1], y[i] - y[i - 1]) }
    private val horas = DoubleArray(paragens.size) { paragens[it].hora.toEpochMilli().toDouble() }

    /**
     * Metros ao longo da linha de cada paragem. Cada uma projeta-se a partir da anterior para a frente,
     * por isso um laço ou uma ida e volta pela mesma rua não a puxa para o sítio errado.
     * shortcut: se uma paragem ficar mais perto de uma passagem muito posterior da linha, as seguintes colam-se a ela; passar a emparelhar todas de uma vez se aparecer numa viagem real.
     */
    val noPercurso: DoubleArray = DoubleArray(paragens.size).also { m ->
        var de = 0.0
        for ((i, p) in paragens.withIndex()) {
            de = projetar(p.lat, p.lon, de, acumulado.last())?.ao ?: de
            m[i] = de
        }
    }

    /**
     * O ponto da linha mais perto de (lat, lon) olhando só para o troço de [de, ate] metros; null se a janela
     * não apanhar linha. Os troços cortam-se pela janela, por isso `ao` fica sempre dentro dela.
     */
    fun projetar(lat: Double, lon: Double, de: Double, ate: Double): Projecao? {
        if (de > ate) return null
        val px = (lon - lon0) * metrosPorGrauLon
        val py = (lat - lat0) * metrosPorGrauLat
        var melhor: Projecao? = null
        for (i in 1 until linha.size) {
            val inicio = acumulado[i - 1]
            val comprimento = acumulado[i] - inicio
            if (comprimento == 0.0 || acumulado[i] < de || inicio > ate) continue
            val dx = x[i] - x[i - 1]
            val dy = y[i] - y[i - 1]
            val t = (((px - x[i - 1]) * dx + (py - y[i - 1]) * dy) / (comprimento * comprimento))
                .coerceIn(((de - inicio) / comprimento).coerceAtLeast(0.0), ((ate - inicio) / comprimento).coerceAtMost(1.0))
            val afastamento = hypot(px - (x[i - 1] + t * dx), py - (y[i - 1] + t * dy))
            if (melhor == null || afastamento < melhor.afastamento) melhor = Projecao(inicio + t * comprimento, afastamento)
        }
        return melhor
    }

    /** A hora prevista de passar aos m metros, linear entre as horas das paragens (fora da etapa, a da primeira ou da última). */
    fun horaEm(m: Double): Instant = Instant.ofEpochMilli(interpolar(noPercurso, horas, m).roundToLong())

    /** Os metros em que o veículo devia estar a essa hora; o inverso de horaEm. */
    fun progressoEm(h: Instant): Double = interpolar(horas, noPercurso, h.toEpochMilli().toDouble())
}

/** ys em x, linear entre os pontos (xs não decrescente); fora de xs fica no primeiro ou no último y. */
private fun interpolar(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
    val k = (1 until xs.size).firstOrNull { x <= xs[it] } ?: return ys.last()
    val vao = xs[k] - xs[k - 1]
    if (vao <= 0.0) return ys[k - 1]
    return ys[k - 1] + (ys[k] - ys[k - 1]) * ((x - xs[k - 1]) / vao).coerceIn(0.0, 1.0)
}
