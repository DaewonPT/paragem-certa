package pt.pixellusitano.paragemcerta.core.fontes.cm

import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
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

internal val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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
                )
            }
            .filter { aindaPorChegar(it, agora) }
            .sortedBy { it.prevista ?: it.programada }

    fun paragens(json: String): List<Paragem> =
        JSON.decodeFromString<List<CmParagemJson>>(json).map {
            Paragem(RefParagem(Fonte.CM, it.id), it.nome ?: it.id, it.lat, it.lon, it.cadeiraDeRodas)
        }

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

    fun veiculos(json: String): List<Veiculo> =
        JSON.decodeFromString<List<CmVeiculoJson>>(json).mapNotNull {
            Veiculo(
                id = cmIdLimpo(it.id),
                linhaId = cmIdLimpo(it.linha ?: return@mapNotNull null),
                lat = it.lat ?: return@mapNotNull null,
                lon = it.lon ?: return@mapNotNull null,
                rumo = it.bearing,
                ultimoSinal = Instant.ofEpochMilli(it.timestamp ?: return@mapNotNull null),
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

    suspend fun paragens(): Resultado<List<Paragem>> = http.pedir("${base}stops", CmParser::paragens)

    /** Devolve o corpo em bruto: o repositório tira daí as linhas e os ids dos percursos de cada uma. */
    suspend fun linhasJson(): Resultado<String> = http.pedir("${base}lines") { it }

    suspend fun padrao(id: String): Resultado<List<String>> = http.pedir("${base}patterns/$id", CmParser::padrao)

    suspend fun veiculos(): Resultado<List<Veiculo>> = http.pedir("${base}vehicles", CmParser::veiculos)

    suspend fun alertas(agora: Instant): Resultado<List<Alerta>> = http.pedir("${base}alerts") { CmParser.alertas(it, agora) }
}
