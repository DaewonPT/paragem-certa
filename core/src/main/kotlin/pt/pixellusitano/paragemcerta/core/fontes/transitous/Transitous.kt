package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import java.net.URLEncoder
import java.time.Instant

/** Ids do Transitous que pertencem à Carris Metropolitana: essa vem só da API dela (os ids não coincidem). */
const val PREFIXO_CM_TRANSITOUS = "pt-Carris-Metropolitana_"

private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable private class LugarJson(
    val stopId: String? = null,
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val cancelled: Boolean = false,
)

@Serializable private class PartidaJson(
    val place: LugarJson,
    val mode: String = "",
    val realTime: Boolean = false,
    val headsign: String = "",
    val routeId: String = "",
    val routeShortName: String? = null,
    val displayName: String? = null,
    val routeColor: String? = null,
    val tripId: String? = null,
    val wheelchairAccessible: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)

@Serializable private class PartidasJson(val stopTimes: List<PartidaJson> = emptyList())

@Serializable private class LocalJson(
    val id: String? = null,
    val name: String = "",
    val type: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/** Portugal continental, Madeira e Açores (o geocoder devolve resultados do mundo inteiro). */
private fun emPortugal(lat: Double, lon: Double) =
    (lat in 36.8..42.2 && lon in -9.6..-6.1) ||
        (lat in 32.3..33.2 && lon in -17.4..-16.2) ||
        (lat in 36.8..39.8 && lon in -31.4..-24.9)

/** Nome curto do operador a partir do prefixo do id do Transitous (para distinguir paragens com o mesmo nome). */
fun operadorDe(id: String): String? = when {
    id.startsWith("pt-Comboios-de-Portugal") -> "CP"
    id.startsWith("pt-Transtejo") -> "Transtejo"
    id.startsWith("pt-Metro-Lisboa") -> "Metro de Lisboa"
    id.startsWith("pt-Metro-do-Porto") || id.startsWith("pt-Metro-Porto") -> "Metro do Porto"
    id.startsWith("pt-Carris-Metropolitana") -> "Carris Metropolitana"
    id.startsWith("pt-Carris_") -> "Carris"
    id.startsWith("pt-Fertagus") -> "Fertagus"
    id.startsWith("pt-MTS") || id.startsWith("pt-Metro-Transportes-do-Sul") -> "MTS"
    id.startsWith("pt-STCP") -> "STCP"
    else -> null
}

fun modoDe(mode: String): Modo = when (mode) {
    "BUS", "COACH" -> Modo.AUTOCARRO
    "TRAM", "CABLE_CAR", "FUNICULAR" -> Modo.ELETRICO
    "SUBWAY", "METRO" -> Modo.METRO
    "RAIL", "SUBURBAN", "REGIONAL_RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_FAST_RAIL" -> Modo.COMBOIO
    "FERRY" -> Modo.BARCO
    else -> Modo.OUTRO
}

object TransitousParser {
    fun chegadas(json: String, paragemId: String, agora: Instant): List<Chegada> =
        JSON.decodeFromString<PartidasJson>(json).stopTimes
            .filter { it.place.stopId == paragemId && !it.cancelled && !it.tripCancelled && !it.place.cancelled }
            .mapNotNull { p ->
                val programada = (p.place.scheduledDeparture ?: p.place.scheduledArrival)?.let(Instant::parse) ?: return@mapNotNull null
                Chegada(
                    fonte = Fonte.TRANSITOUS,
                    linhaId = p.routeId,
                    linhaNome = p.routeShortName ?: p.displayName ?: "",
                    destino = p.headsign,
                    programada = programada,
                    prevista = if (p.realTime) (p.place.departure ?: p.place.arrival)?.let(Instant::parse) else null,
                    idViagem = p.tripId,
                    idVeiculo = null,
                    modo = modoDe(p.mode),
                    cor = p.routeColor?.let { if (it.startsWith("#")) it else "#$it" },
                    acessivel = when (p.wheelchairAccessible) {
                        "ACCESSIBLE" -> true
                        "NOT_ACCESSIBLE" -> false
                        else -> null
                    },
                )
            }
            .filter { aindaPorChegar(it, agora) }
            .sortedBy { it.prevista ?: it.programada }

    fun paragensNoMapa(json: String): List<Paragem> =
        JSON.decodeFromString<List<LugarJson>>(json).mapNotNull { l ->
            val id = l.stopId?.takeUnless { it.startsWith(PREFIXO_CM_TRANSITOUS) } ?: return@mapNotNull null
            Paragem(RefParagem(Fonte.TRANSITOUS, id), l.name, l.lat, l.lon, null)
        }

    fun pesquisa(json: String): List<Paragem> =
        JSON.decodeFromString<List<LocalJson>>(json).mapNotNull { l ->
            val id = l.id?.takeIf { l.type == "STOP" && !it.startsWith(PREFIXO_CM_TRANSITOUS) && emPortugal(l.lat, l.lon) }
                ?: return@mapNotNull null
            Paragem(RefParagem(Fonte.TRANSITOUS, id), l.name, l.lat, l.lon, null)
        }
}

class Transitous(private val http: Http, private val base: String = "https://api.transitous.org") {
    // Não usar URLEncoder.encode(String, Charset): só existe a partir do Android 13 e rebentava no Android 8-12.
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun chegadas(paragemId: String, agora: Instant): Resultado<List<Chegada>> =
        http.pedir("$base/api/v6/stoptimes?stopId=${enc(paragemId)}&n=30&radius=0&exactRadius=true") { TransitousParser.chegadas(it, paragemId, agora) }

    suspend fun paragensNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): Resultado<List<Paragem>> =
        http.pedir("$base/api/v6/map/stops?min=$minLat,$minLon&max=$maxLat,$maxLon", TransitousParser::paragensNoMapa)

    suspend fun pesquisar(texto: String): Resultado<List<Paragem>> =
        http.pedir("$base/api/v1/geocode?text=${enc(texto)}&type=STOP", TransitousParser::pesquisa)
}
