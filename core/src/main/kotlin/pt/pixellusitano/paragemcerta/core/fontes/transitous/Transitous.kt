package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.linhas.SentidoCatalogo
import pt.pixellusitano.paragemcerta.core.linhas.daLinhaNoSentido
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rotas.descodificarPolilinha
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ids do Transitous que pertencem à Carris Metropolitana: essa vem só da API dela. A paragem converte-se por uma regra fixa:
 * sem este prefixo e com o 3 inicial a 0 (cmParagemDeTransitous).
 */
const val PREFIXO_CM_TRANSITOUS = "pt-Carris-Metropolitana_"

private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable private class LugarJson(
    val stopId: String? = null,
    val parentId: String? = null,
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
    val directionId: String? = null,
    val tripId: String? = null,
    val wheelchairAccessible: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)

@Serializable private class PartidasJson(val stopTimes: List<PartidaJson> = emptyList())

/** Zona administrativa de um resultado do geocoder: adminLevel 7 = concelho, 8 = freguesia (vem como 7.0, por isso Double). */
@Serializable private class AreaJson(val name: String = "", val adminLevel: Double = 0.0)

@Serializable private class LocalJson(
    val id: String? = null,
    val name: String = "",
    val type: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val areas: List<AreaJson> = emptyList(),
)

private const val NIVEL_CONCELHO = 7.0
private const val NIVEL_FREGUESIA = 8.0

/** A freguesia e o concelho para mostrar juntos; só a freguesia quando já começa pelo nome do concelho ("Setúbal (São Julião, …)"). */
fun zonaDe(freguesia: String?, concelho: String?): String? {
    val f = freguesia?.trim()?.takeIf { it.isNotEmpty() }
    val c = concelho?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        f == null -> c
        // "Setúbal (São Julião…)" já diz o concelho; "Moravia" não diz "Mora" (tem de acabar numa palavra inteira).
        c == null || (f.startsWith(c, ignoreCase = true) && f.getOrNull(c.length)?.isLetterOrDigit() != true) -> f
        else -> "$f · $c"
    }
}

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
    // O Transitous escreve "Fergatus" (a gralha é deles); o "Fertagus" fica para o dia em que a corrijam.
    id.startsWith("pt-Fergatus") || id.startsWith("pt-Fertagus") -> "Fertagus"
    id.startsWith("pt-Barreiro") -> "TCB Barreiro"
    id.startsWith("pt-Cascais-Próxima") -> "MobiCascais"
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

@Serializable private class PontoRotaJson(
    val stopId: String? = null,
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val arrival: String? = null,
    val departure: String? = null,
)

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
    val tripId: String? = null,
    val from: PontoRotaJson = PontoRotaJson(),
    val to: PontoRotaJson = PontoRotaJson(),
    val intermediateStops: List<PontoRotaJson> = emptyList(),
    val legGeometry: DesenhoJson? = null,
    val cancelled: Boolean = false,
    val distance: Double? = null,
)

@Serializable private class ItinerarioJson(val startTime: String, val endTime: String, val transfers: Int = 0, val legs: List<EtapaJson> = emptyList())

@Serializable private class PlanoJson(
    val itineraries: List<ItinerarioJson> = emptyList(),
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

// Não usar URLEncoder.encode(String, Charset): só existe a partir do Android 13 e rebentava no Android 8-12.
internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun PontoRotaJson.lugar() = Lugar(name.espacosUnicos(), lat, lon, TipoLugar.LOCAL)

/** A hora é a partida da paragem (ou a chegada, na última); sem nenhuma, a da etapa. */
private fun PontoRotaJson.paragem(horaDaEtapa: String) =
    ParagemEtapa(stopId, name.espacosUnicos(), lat, lon, Instant.parse(departure ?: arrival ?: horaDaEtapa))

// "RAIL" sozinho apanha também o metro no Transitous, por isso o comboio lista os seus modos um a um.
private val ORDEM_MODOS = listOf(
    Modo.AUTOCARRO to "BUS", Modo.ELETRICO to "TRAM", Modo.METRO to "SUBWAY",
    Modo.COMBOIO to "HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_FAST_RAIL,REGIONAL_RAIL,SUBURBAN", Modo.BARCO to "FERRY",
)

private const val MAX_A_PE_MENOS_A_PE_MIN = 10

/**
 * Pedido de rotas do MOTIS (Transitous) com os filtros da Paragem Certa. O tempo máximo a pé vai sempre (o "menos a pé" nunca
 * passa dos 10 min); a ordem não vai (faz-se na app). [cursor] pede a página "mais cedo" ou "mais tarde" de uma resposta anterior.
 */
fun urlPlano(base: String, de: Lugar, para: Lugar, hora: Instant, chegarAs: Boolean, filtros: Filtros, cursor: String? = null): String = buildString {
    append("$base/api/v6/plan?fromPlace=${de.lat},${de.lon}&toPlace=${para.lat},${para.lon}")
    append("&time=${hora.truncatedTo(ChronoUnit.SECONDS)}&arriveBy=$chegarAs&numItineraries=5&maxItineraries=8&language=pt")
    val aPeSegundos = (if (filtros.menosAPe) minOf(filtros.maxAPeMin, MAX_A_PE_MENOS_A_PE_MIN) else filtros.maxAPeMin) * 60
    append("&maxPreTransitTime=$aPeSegundos&maxPostTransitTime=$aPeSegundos")
    // O normal é o do servidor.
    if (filtros.ritmo != Ritmo.NORMAL) append("&pedestrianSpeed=${filtros.ritmo.metrosPorSegundo}")
    if (filtros.menosTransbordos) append("&maxTransfers=1")
    if (filtros.acessivel) append("&pedestrianProfile=WHEELCHAIR")
    // Todos os modos ligados = sem filtro, para não excluir os que a app não lista (autocarros de longo curso, funiculares).
    if (filtros.modos.isNotEmpty() && !filtros.modos.containsAll(ORDEM_MODOS.map { it.first }))
        append("&transitModes=" + ORDEM_MODOS.filter { it.first in filtros.modos }.joinToString(",") { it.second })
    // directModes vazio: as páginas não repetem a rota direta (a pé do princípio ao fim) da primeira.
    if (cursor != null) append("&pageCursor=${enc(cursor)}&directModes=")
}

object TransitousParser {
    /** Itinerários do /api/v6/plan; um itinerário com uma etapa cancelada é descartado. */
    fun rotas(json: String): List<Itinerario> = plano(json).itinerarios

    /** Os itinerários do /api/v6/plan com os cursores das páginas "mais cedo" e "mais tarde". */
    fun plano(json: String): PlanoRotas {
        val plano = JSON.decodeFromString<PlanoJson>(json)
        return PlanoRotas(itinerarios(plano), plano.previousPageCursor, plano.nextPageCursor)
    }

    private fun itinerarios(plano: PlanoJson): List<Itinerario> =
        plano.itineraries
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
                            distancia = l.distance,
                            idViagem = l.tripId,
                            paragens = if (aPe) emptyList() else
                                listOf(l.from.paragem(l.startTime)) + l.intermediateStops.map { s -> s.paragem(l.startTime) } + l.to.paragem(l.endTime),
                        )
                    },
                    partida = Instant.parse(it.startTime),
                    chegada = Instant.parse(it.endTime),
                    transbordos = it.transfers,
                )
            }

    fun chegadas(json: String, paragemId: String, agora: Instant): List<Chegada> =
        partidas(json, paragemId)
            .filter { aindaPorChegar(it, agora) }
            .sortedBy { it.prevista ?: it.programada }

    /** Todas as partidas da paragem na resposta, também as já passadas (o horário do dia precisa do dia inteiro). */
    internal fun partidas(json: String, paragemId: String): List<Chegada> {
        // O pedido de uma estação-mãe do Metro traz os cais dela (parentId) e os das estações ao lado: só os dela ficam.
        val mae = ESTACAO_METRO.matches(paragemId)
        return JSON.decodeFromString<PartidasJson>(json).stopTimes
            .filter { (it.place.stopId == paragemId || (mae && it.place.parentId == paragemId)) && !it.cancelled && !it.tripCancelled && !it.place.cancelled }
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
                    sentido = p.directionId?.toIntOrNull(),
                )
            }
    }

    /** As paragens de uma viagem (o `/api/v6/trip`) pela ordem em que passa nelas: a origem, as intermédias e o destino. */
    fun paragensDaViagem(json: String): List<String> =
        JSON.decodeFromString<ItinerarioJson>(json).legs.firstOrNull()
            ?.let { l -> (listOf(l.from) + l.intermediateStops + l.to).mapNotNull { it.stopId } }
            .orEmpty()

    fun paragensNoMapa(json: String): List<Paragem> =
        JSON.decodeFromString<List<LugarJson>>(json).mapNotNull { l ->
            val id = l.stopId?.takeUnless { it.startsWith(PREFIXO_CM_TRANSITOUS) } ?: return@mapNotNull null
            Paragem(RefParagem(Fonte.TRANSITOUS, id), l.name.espacosUnicos(), l.lat, l.lon, null)
        }.paragensComAcentos()

    /** Moradas, locais e paragens para origem/destino de rotas (aqui a Carris Metropolitana não é escondida). */
    fun lugares(json: String): List<Lugar> =
        JSON.decodeFromString<List<LocalJson>>(json).mapNotNull { l ->
            val tipo = when (l.type) {
                "ADDRESS" -> TipoLugar.MORADA
                "PLACE" -> TipoLugar.LOCAL
                "STOP" -> TipoLugar.PARAGEM
                else -> return@mapNotNull null
            }
            val zona = zonaDe(
                l.areas.firstOrNull { it.adminLevel == NIVEL_FREGUESIA }?.name,
                l.areas.firstOrNull { it.adminLevel == NIVEL_CONCELHO }?.name,
            )
            Lugar(l.name.espacosUnicos(), l.lat, l.lon, tipo, zona).takeIf { emPortugal(l.lat, l.lon) }
        }.lugaresComAcentos()

    /** Nome para um ponto tocado no mapa: a 1.ª morada; sem ela, a paragem mais próxima.
     *  Os sítios soltos (contentores, esgotos, monumentos ao lado) não dão um nome que se reconheça. */
    fun lugarMaisProximo(json: String): Lugar? {
        val perto = JSON.decodeFromString<List<LocalJson>>(json).filter { emPortugal(it.lat, it.lon) }
        val l = perto.firstOrNull { it.type == "ADDRESS" } ?: perto.firstOrNull { it.type == "STOP" } ?: return null
        return Lugar(l.name, l.lat, l.lon, if (l.type == "ADDRESS") TipoLugar.MORADA else TipoLugar.PARAGEM)
    }

    fun pesquisa(json: String): List<Paragem> =
        JSON.decodeFromString<List<LocalJson>>(json).mapNotNull { l ->
            val id = l.id?.takeIf { l.type == "STOP" && !it.startsWith(PREFIXO_CM_TRANSITOUS) && emPortugal(l.lat, l.lon) }
                ?: return@mapNotNull null
            Paragem(RefParagem(Fonte.TRANSITOUS, id), l.name.espacosUnicos(), l.lat, l.lon, null)
        }.paragensComAcentos()
}

// As estações do Metro de Lisboa ("pt-Metro-Lisboa_" e duas letras) e os cais das de correspondência (MP1 a MP4, a mãe é MP).
private val ESTACAO_METRO = Regex("^pt-Metro-Lisboa_[A-Z]{2}\\d?$")

private val LISBOA = ZoneId.of("Europe/Lisbon")

/**
 * Só a própria paragem (radius=0 e exactRadius). Uma estação do Metro dá 0 assim quando é a mãe de cais ou um cais (verificado a
 * 03/11/2026 em MP e MP1): leva o raio de 80 m só com o Metro e o parser fica com as partidas dela. [modos] só filtra quando há radius=0.
 */
private fun zonaDaParagem(paragemId: String, modos: String? = null) =
    if (ESTACAO_METRO.matches(paragemId)) "radius=80&mode=SUBWAY" else "radius=0&exactRadius=true" + (modos?.let { "&mode=$it" } ?: "")

/** Os modos do Transitous de uma linha. Os elétricos da Carris vêm como BUS no GTFS (o 15E, verificado): TRAM sozinho não os apanha. */
private fun modosDe(modo: Modo): String? = when (modo) {
    Modo.ELETRICO -> "TRAM,BUS"
    Modo.OUTRO -> null
    else -> ORDEM_MODOS.first { it.first == modo }.second
}

class Transitous(private val http: Http, private val base: String = "https://api.transitous.org") {
    // Limite: o ecrã da linha e as linhas favoritas filtram estas 30 partidas pela linha; numa paragem cheia uma linha rara pode ficar sem nenhuma, subir o n se acontecer.
    private fun urlChegadas(paragemId: String) = "$base/api/v6/stoptimes?stopId=${enc(paragemId)}&n=30&${zonaDaParagem(paragemId)}"

    suspend fun chegadas(paragemId: String, agora: Instant): Resultado<List<Chegada>> =
        http.pedir(urlChegadas(paragemId)) { TransitousParser.chegadas(it, paragemId, agora) }

    /**
     * As horas a que a linha parte da paragem no [dia] de serviço (das 03:00 de Lisboa às 03:00 do dia seguinte), pela ordem do dia,
     * só do horário (realtimeMode=OFF). Com `window`, o `n=1` devolve a janela inteira (361 partidas, 650 KB, numa estação do Metro).
     */
    suspend fun horarioDoDia(paragemId: String, modo: Modo, rotas: Set<String>, s: SentidoCatalogo, dia: LocalDate): Resultado<List<LocalTime>> {
        val inicio = dia.atTime(3, 0).atZone(LISBOA).toInstant()
        // Na noite da mudança da hora o dia de serviço tem 23 ou 25 horas: a janela acaba às 03:00 de Lisboa do dia seguinte.
        val fim = dia.plusDays(1).atTime(3, 0).atZone(LISBOA).toInstant()
        val url = "$base/api/v6/stoptimes?stopId=${enc(paragemId)}&time=$inicio&window=${fim.epochSecond - inicio.epochSecond}&n=1&realtimeMode=OFF&${zonaDaParagem(paragemId, modosDe(modo))}"
        return http.pedir(url) { json ->
            TransitousParser.partidas(json, paragemId)
                .filter { daLinhaNoSentido(it, rotas, s) }
                .sortedBy { it.programada }
                .map { it.programada.atZone(LISBOA).toLocalTime() }
        }
    }

    /** As paragens da viagem pela ordem em que passa nelas; a posição de uma paragem na viagem é o índice + 1. */
    suspend fun paragensDaViagem(tripId: String): Resultado<List<String>> =
        http.pedir("$base/api/v6/trip?tripId=${enc(tripId)}", TransitousParser::paragensDaViagem)

    suspend fun paragensNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): Resultado<List<Paragem>> =
        http.pedir("$base/api/v6/map/stops?min=$minLat,$minLon&max=$maxLat,$maxLon", TransitousParser::paragensNoMapa)

    suspend fun planear(de: Lugar, para: Lugar, hora: Instant, chegarAs: Boolean, filtros: Filtros, cursor: String? = null): Resultado<PlanoRotas> =
        http.pedir(urlPlano(base, de, para, hora, chegarAs, filtros, cursor), TransitousParser::plano)

    suspend fun lugarEm(lat: Double, lon: Double): Resultado<Lugar?> =
        http.pedir("$base/api/v1/reverse-geocode?place=$lat,$lon", TransitousParser::lugarMaisProximo)

    /** [perto] (lat, lon) põe os resultados mais próximos primeiro. */
    suspend fun pesquisarLugares(texto: String, perto: Pair<Double, Double>? = null): Resultado<List<Lugar>> =
        http.pedir("$base/api/v1/geocode?text=${enc(texto)}" + (perto?.let { "&place=${it.first},${it.second}" } ?: ""), TransitousParser::lugares)

    suspend fun pesquisar(texto: String): Resultado<List<Paragem>> =
        http.pedir("$base/api/v1/geocode?text=${enc(texto)}&type=STOP", TransitousParser::pesquisa)
}
