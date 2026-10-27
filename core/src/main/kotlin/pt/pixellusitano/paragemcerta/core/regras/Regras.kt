package pt.pixellusitano.paragemcerta.core.regras

import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import java.time.Duration
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAIO_TERRA = 6_371_000.0

/** Distância em metros (haversine). */
fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * RAIO_TERRA * asin(sqrt(a))
}

fun maisProximas(paragens: List<Paragem>, lat: Double, lon: Double, limite: Int = 5, raioMetros: Double = 800.0): List<Paragem> =
    paragens.map { it to distanciaMetros(lat, lon, it.lat, it.lon) }
        .filter { it.second <= raioMetros }
        .sortedBy { it.second }
        .take(limite)
        .map { it.first }

data class Favorita(val ref: RefParagem, val nome: String, val ordem: Int, val lat: Double, val lon: Double)

/** A favorita mais próxima da última posição; sem posição, a primeira pela ordem do utilizador. */
fun favoritaPorOmissao(favoritas: List<Favorita>, ultimaPosicao: Pair<Double, Double>?): Favorita? {
    val (lat, lon) = ultimaPosicao ?: return favoritas.minByOrNull { it.ordem }
    return favoritas.minByOrNull { distanciaMetros(lat, lon, it.lat, it.lon) }
}

/** Ritmo das chegadas com o ecrã visível: o Transitous só lê o tempo real uma vez por minuto. */
fun intervaloNormal(fonte: Fonte): Duration = when (fonte) {
    Fonte.CM -> Duration.ofSeconds(20)
    Fonte.TRANSITOUS -> Duration.ofSeconds(60)
}

private val PAUSA_MAXIMA = Duration.ofMinutes(5)

/** intervaloNormal × 2^tentativa, no máximo 5 min. */
fun pausaDepoisDeFalha(fonte: Fonte, tentativa: Int): Duration {
    val base = intervaloNormal(fonte)
    if (tentativa >= 20) return PAUSA_MAXIMA
    return base.multipliedBy(1L shl tentativa.coerceAtLeast(0)).coerceAtMost(PAUSA_MAXIMA)
}
