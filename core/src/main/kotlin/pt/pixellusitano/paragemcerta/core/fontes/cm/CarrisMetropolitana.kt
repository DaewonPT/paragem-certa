package pt.pixellusitano.paragemcerta.core.fontes.cm

import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.fontes.transitous.enc
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** O percurso (pattern) inteiro de uma linha: [sentido] é o direction_id; [paragens] pela ordem em que o autocarro passa. */
data class PadraoCm(
    val id: String, val sentido: Int, val destino: String, val paragens: List<String>, val formaId: String?, val horarios: List<HorarioCm>,
)

/**
 * As horas de um grupo de viagens nas paragens do percurso, nos [dias] ("AAAAMMDD") em que corre. Uma hora do GTFS depois da
 * meia-noite ("25:10:00") passa a 01:10 e a paragem fica em [depoisDaMeiaNoite], para o fim do dia de serviço não voltar ao princípio.
 */
data class HorarioCm(val dias: Set<String>, val porParagem: Map<String, LocalTime>, val depoisDaMeiaNoite: Set<String> = emptySet())

/** "25:10:00" → 01:10 e se é depois da meia-noite. */
private fun horaCm(texto: String): Pair<LocalTime, Boolean> {
    val (h, m, s) = texto.split(':').map(String::toInt)
    return LocalTime.of(h % 24, m, s) to (h >= 24)
}

object CmParser {
    /** Descarta as que já passaram (observadas ou com a hora em uso no passado) e ordena pela hora em uso. */
    fun chegadas(json: String, agora: Instant): List<Chegada> =
        JSON.decodeFromString<List<CmChegadaJson>>(json)
            .filter { it.observada == null }
            .map {
                val linha = cmIdLimpo(it.linha)
                Chegada(
                    fonte = Fonte.CM, linhaId = linha, linhaNome = linha, destino = it.headsign,
                    programada = Instant.ofEpochSecond(it.programada), prevista = it.prevista?.let(Instant::ofEpochSecond),
                    idViagem = it.viagem, idVeiculo = it.veiculo?.let(::cmIdLimpo),
                    modo = Modo.AUTOCARRO, cor = null, acessivel = null,
                    sentido = it.padrao?.let(::sentidoDoPadraoCm), padrao = it.padrao?.let(::cmIdLimpo),
                )
            }
            .filter { aindaPorChegar(it, agora) }
            .sortedBy { it.prevista ?: it.programada }

    fun paragens(json: String): List<Paragem> =
        JSON.decodeFromString<List<CmParagemJson>>(json).map {
            Paragem(RefParagem(Fonte.CM, it.id), it.nome ?: it.id, it.lat, it.lon, it.cadeiraDeRodas)
        }

    /** As linhas (line_ids) que passam em cada paragem, pelo id da paragem. */
    fun linhasDasParagens(json: String): Map<String, List<String>> =
        JSON.decodeFromString<List<CmParagemJson>>(json).associate { p -> p.id to p.linhas.map(::cmIdLimpo) }

    fun linhas(json: String): List<Linha> =
        JSON.decodeFromString<List<CmLinhaJson>>(json).map {
            Linha(Fonte.CM, it.id, it.nomeCurto ?: it.id, it.nomeLongo, it.color, it.corTexto, Modo.AUTOCARRO)
        }

    /** Ids dos percursos (patterns) de cada linha, para pedir as paragens de uma linha (Task 13). */
    fun padroesDasLinhas(json: String): Map<String, List<String>> =
        JSON.decodeFromString<List<CmLinhaJson>>(json).associate { it.id to it.padroes }

    /** Ids das paragens de um percurso, pela ordem em que o autocarro passa. A API devolve uma lista com um percurso. */
    fun padrao(json: String): List<String> =
        JSON.decodeFromString<List<CmPadraoJson>>(json).firstOrNull()?.path.orEmpty()
            .sortedBy { it.ordem }.map { cmIdLimpo(it.paragem) }

    /** O percurso com as paragens, a forma e o horário de cada grupo de viagens (o `patterns/{id}` completo). */
    fun padraoCompleto(json: String): PadraoCm {
        val p = JSON.decodeFromString<List<CmPadraoJson>>(json).firstOrNull() ?: error("percurso em falta")
        val id = cmIdLimpo(p.id)
        return PadraoCm(
            id = id,
            sentido = p.sentido ?: sentidoDoPadraoCm(id) ?: error("percurso sem sentido"),
            destino = p.headsign,
            paragens = p.path.sortedBy { it.ordem }.map { cmIdLimpo(it.paragem) },
            formaId = p.forma,
            horarios = p.trips.map { g ->
                // shortcut: uma paragem que o percurso repete (circulares) fica com a 1.ª hora; separar partida e chegada quando houver queixa.
                val horas = g.schedule.asReversed().associate { cmIdLimpo(it.paragem) to horaCm(it.hora) }
                HorarioCm(g.dias.toSet(), horas.mapValues { it.value.first }, horas.filterValues { it.second }.keys)
            },
        )
    }

    /** Os pontos do GeoJSON do `shapes/{id}`, que dá [lon, lat], como (lat, lon). */
    fun forma(json: String): List<Pair<Double, Double>> =
        JSON.decodeFromString<CmFormaJson>(json).geojson.geometry.coordinates.map { it[1] to it[0] }

    fun veiculos(json: String): List<Veiculo> =
        JSON.decodeFromString<List<CmVeiculoJson>>(json).mapNotNull {
            Veiculo(
                id = it.id, // os números repetem-se entre operadores: o prefixo fica
                linhaId = cmIdLimpo(it.linha ?: return@mapNotNull null),
                lat = it.lat ?: return@mapNotNull null,
                lon = it.lon ?: return@mapNotNull null,
                rumo = it.bearing,
                ultimoSinal = Instant.ofEpochMilli(it.timestamp ?: return@mapNotNull null),
                viagem = it.viagem,
                paragem = it.paragem?.let(::cmIdLimpo),
                aChegar = it.estado == "INCOMING_AT" || it.estado == "STOPPED_AT",
            )
        }

    fun alertas(json: String, agora: Instant): List<Alerta> =
        JSON.decodeFromString<List<CmAlertaJson>>(json)
            .filter { a ->
                a.periodos.isEmpty() || a.periodos.any {
                    (it.start == null || it.start <= agora.epochSecond) && (it.end == null || agora.epochSecond <= it.end)
                }
            }
            .map { a ->
                val periodo = a.periodos.firstOrNull()
                val titulo = a.titulo.pt()
                Alerta(
                    // A API não dá id: título + início identificam o alerta (para não o notificar duas vezes).
                    id = "$titulo|${periodo?.start}",
                    linhaIds = a.entidades.mapNotNull { it.rota?.let(::cmLinhaDaRota) }.toSet(),
                    paragemIds = a.entidades.mapNotNull { it.paragem?.let(::cmIdLimpo) }.toSet(),
                    inicio = periodo?.start?.let(Instant::ofEpochSecond),
                    fim = periodo?.end?.let(Instant::ofEpochSecond),
                    titulo = titulo,
                    texto = a.texto.pt(),
                    gravidade = gravidadeDe(a.effect, a.gravidade),
                    imagemUrl = a.image?.imagens?.firstOrNull()?.url,
                )
            }
}

class CarrisMetropolitana(private val http: Http, private val base: String = "https://api.carrismetropolitana.pt/v2/") {
    suspend fun chegadas(paragemId: String, agora: Instant): Resultado<List<Chegada>> =
        http.pedir("${base}arrivals/by_stop/$paragemId") { CmParser.chegadas(it, agora) }

    /** Devolve o corpo em bruto: o repositório tira daí as paragens e as linhas de cada uma. */
    suspend fun paragensJson(): Resultado<String> = http.pedir("${base}stops") { it }

    /** Devolve o corpo em bruto: o repositório tira daí as linhas e os ids dos percursos de cada uma. */
    suspend fun linhasJson(): Resultado<String> = http.pedir("${base}lines") { it }

    suspend fun padrao(id: String): Resultado<List<String>> = http.pedir("${base}patterns/$id", CmParser::padrao)

    suspend fun padraoCompleto(id: String): Resultado<PadraoCm> = http.pedir("${base}patterns/${enc(id)}", CmParser::padraoCompleto)

    /** O id da forma tem "[" e "]" do operador ("[2QDAD]3708_0_1"): vai codificado. */
    suspend fun forma(formaId: String): Resultado<List<Pair<Double, Double>>> = http.pedir("${base}shapes/${enc(formaId)}", CmParser::forma)

    /** As horas a que o percurso passa em [paragem] no [dia], pela ordem do dia de serviço: o que passa da meia-noite fica no fim. */
    fun horarioCmDoDia(p: PadraoCm, paragem: String, dia: LocalDate): List<LocalTime> {
        val d = dia.format(DateTimeFormatter.BASIC_ISO_DATE)
        return p.horarios
            .filter { d in it.dias && paragem in it.porParagem }
            .map { (paragem in it.depoisDaMeiaNoite) to it.porParagem.getValue(paragem) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }
    }

    suspend fun veiculos(): Resultado<List<Veiculo>> = http.pedir("${base}vehicles", CmParser::veiculos)

    suspend fun alertas(agora: Instant): Resultado<List<Alerta>> = http.pedir("${base}alerts") { CmParser.alertas(it, agora) }
}
