package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rotas.descodificarPolilinha
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

@Serializable private class PontoRotaJson(val name: String = "", val lat: Double = 0.0, val lon: Double = 0.0)

@Serializable private class DesenhoJson(val points: String = "", val precision: Int = 6)

@Serializable private class EtapaJson(
    val mode: String = "",
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String? = null,
    val realTime: Boolean = false,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val routeId: String? = null,
    val routeColor: String? = null,
    val headsign: String? = null,
    val from: PontoRotaJson = PontoRotaJson(),
    val to: PontoRotaJson = PontoRotaJson(),
    val intermediateStops: List<PontoRotaJson> = emptyList(),
    val legGeometry: DesenhoJson? = null,
    val cancelled: Boolean = false,
)

@Serializable private class ItinerarioJson(val startTime: String, val endTime: String, val transfers: Int = 0, val legs: List<EtapaJson> = emptyList())

@Serializable private class PlanoJson(val itineraries: List<ItinerarioJson> = emptyList())

private fun PontoRotaJson.lugar() = Lugar(name, lat, lon, TipoLugar.LOCAL)

private val ORDEM_MODOS = listOf(Modo.AUTOCARRO to "BUS", Modo.ELETRICO to "TRAM", Modo.METRO to "SUBWAY", Modo.COMBOIO to "RAIL", Modo.BARCO to "FERRY")

/** Pedido de rotas do MOTIS (Transitous) com os filtros da Paragem Certa. */
fun urlPlano(base: String, de: Lugar, para: Lugar, hora: Instant, chegarAs: Boolean, filtros: Filtros): String = buildString {
    append("$base/api/v6/plan?fromPlace=${de.lat},${de.lon}&toPlace=${para.lat},${para.lon}")
    append("&time=$hora&arriveBy=$chegarAs&numItineraries=5")
    if (filtros.menosAPe) append("&maxPreTransitTime=600&maxPostTransitTime=600")
    if (filtros.menosTransbordos) append("&maxTransfers=1")
    if (filtros.acessivel) append("&pedestrianProfile=WHEELCHAIR")
    if (filtros.modos.isNotEmpty()) append("&transitModes=" + ORDEM_MODOS.filter { it.first in filtros.modos }.joinToString(",") { it.second })
}

object TransitousParser {
    /** Itinerários do /api/v6/plan; um itinerário com uma etapa cancelada é descartado. */
    fun rotas(json: String): List<Itinerario> =
        JSON.decodeFromString<PlanoJson>(json).itineraries
            .filter { it.legs.isNotEmpty() && it.legs.none { l -> l.cancelled } }
            .map { it ->
                Itinerario(
                    etapas = it.legs.map { l ->
                        val aPe = l.mode == "WALK"
                        Etapa(
                            aPe = aPe,
                            modo = if (aPe) Modo.OUTRO else modoDe(l.mode),
                            linhaNome = if (aPe) null else (l.routeShortName?.takeIf { n -> n.isNotBlank() } ?: l.displayName),
                            linhaId = l.routeId,
                            destino = l.headsign,
                            de = l.from.lugar(),
                            para = l.to.lugar(),
                            partida = Instant.parse(l.startTime),
                            chegada = Instant.parse(l.endTime),
                            partidaProgramada = Instant.parse(l.scheduledStartTime ?: l.startTime),
                            aoVivo = l.realTime,
                            paragensIntermedias = l.intermediateStops.size,
                            desenho = l.legGeometry?.let { g -> descodificarPolilinha(g.points, g.precision) }.orEmpty(),
                            cor = l.routeColor?.let { c -> if (c.startsWith("#")) c else "#$c" },
                        )
                    },
                    partida = Instant.parse(it.startTime),
                    chegada = Instant.parse(it.endTime),
                    transbordos = it.transfers,
                )
            }

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

    /** Moradas, locais e paragens para origem/destino de rotas (aqui a Carris Metropolitana não é escondida). */
    fun lugares(json: String): List<Lugar> =
        JSON.decodeFromString<List<LocalJson>>(json).mapNotNull { l ->
            val tipo = when (l.type) {
                "ADDRESS" -> TipoLugar.MORADA
                "PLACE" -> TipoLugar.LOCAL
                "STOP" -> TipoLugar.PARAGEM
                else -> return@mapNotNull null
            }
            Lugar(l.name, l.lat, l.lon, tipo).takeIf { emPortugal(l.lat, l.lon) }
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

    suspend fun planear(de: Lugar, para: Lugar, hora: Instant, chegarAs: Boolean, filtros: Filtros): Resultado<List<Itinerario>> =
        http.pedir(urlPlano(base, de, para, hora, chegarAs, filtros), TransitousParser::rotas)

    suspend fun pesquisarLugares(texto: String): Resultado<List<Lugar>> =
        http.pedir("$base/api/v1/geocode?text=${enc(texto)}", TransitousParser::lugares)

    suspend fun pesquisar(texto: String): Resultado<List<Paragem>> =
        http.pedir("$base/api/v1/geocode?text=${enc(texto)}&type=STOP", TransitousParser::pesquisa)
}
