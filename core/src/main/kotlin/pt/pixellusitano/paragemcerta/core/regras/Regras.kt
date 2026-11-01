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

private val SUFIXO_CAIS = Regex("""\s+(?:P|Cais|Plataforma)\s*\d+[A-Za-z]?\s*$""", RegexOption.IGNORE_CASE)

/** O nome sem o cais ou plataforma do fim: "Algés (Estação) P12" -> "Algés (Estação)". */
fun nomeBase(nome: String): String = nome.replace(SUFIXO_CAIS, "").trim()

data class Grupo<T>(val nome: String, val lat: Double, val lon: Double, val itens: List<T>)

private const val RAIO_ESTACAO_METROS = 400.0

/** Junta os itens do mesmo nomeBase a menos de 400 m do primeiro do grupo; lat/lon do grupo são a média. */
fun <T> agruparPorEstacao(itens: List<T>, nome: (T) -> String, lat: (T) -> Double, lon: (T) -> Double): List<Grupo<T>> {
    val grupos = mutableListOf<MutableList<T>>()
    for (item in itens) {
        val base = nomeBase(nome(item))
        val grupo = grupos.firstOrNull { g ->
            val primeiro = g.first()
            nomeBase(nome(primeiro)).equals(base, ignoreCase = true) &&
                distanciaMetros(lat(primeiro), lon(primeiro), lat(item), lon(item)) <= RAIO_ESTACAO_METROS
        }
        if (grupo != null) grupo.add(item) else grupos.add(mutableListOf(item))
    }
    return grupos.map { g -> Grupo(nomeBase(nome(g.first())), g.map(lat).average(), g.map(lon).average(), g) }
}

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
