package pt.pixellusitano.paragemcerta.core.viagem

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private fun volta(graus: Double): Double = ((graus % 360.0) + 360.0) % 360.0

/** O rumo (a direção inicial) de um ponto para outro, em graus de 0 a 360: norte 0, este 90, sul 180, oeste 270. */
fun rumo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val f1 = Math.toRadians(lat1)
    val f2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(f2)
    val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dLon)
    return volta(Math.toDegrees(atan2(y, x)))
}

/**
 * O ângulo da seta, de 0 a 360, a contar da frente do telemóvel para a direita. [orientacao] é a da bússola (norte magnético) e
 * [declinacao] passa-a a norte verdadeiro, que é o dos rumos.
 */
fun anguloDaSeta(rumoParagem: Double, orientacao: Double, declinacao: Double): Double =
    volta(rumoParagem - (orientacao + declinacao))

/** A fatia, de 0 a [total] − 1, em que cai um ângulo; a 0 está centrada em 0° (8 dão norte, nordeste, este...; 4 dão frente, direita...). */
fun setor(graus: Double, total: Int): Int {
    val fatia = 360.0 / total
    return floor((volta(graus) + fatia / 2) / fatia).toInt() % total
}
