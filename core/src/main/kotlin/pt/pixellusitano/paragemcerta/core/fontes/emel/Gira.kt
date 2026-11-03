package pt.pixellusitano.paragemcerta.core.fontes.emel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.fontes.transitous.espacosUnicos
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.converterResposta
import java.io.IOException

/** As estações GIRA da EMEL em JSON aberto (dados.emel.pt, CC BY-SA 4.0). */
const val URL_GIRA =
    "https://dados.emel.pt/dataset/57181518-0708-4fb5-a7d1-69875dee8478/resource/d1950d9d-26be-4ced-b1c4-9af65c8d2c70/download/girastations.json"

data class EstacaoGira(val id: String, val nome: String, val lat: Double, val lon: Double)

/** [estacoes] é null no 304 (não mudou): o que a app tem guardado continua a valer, com o mesmo [etag]. */
data class LeituraGira(val estacoes: List<EstacaoGira>?, val etag: String?)

// O ficheiro é uma lista de objetos; aqui só os campos de que a app precisa (também traz horário, tarifas e contactos).
@Serializable private class EstacaoJson(
    @SerialName("id_expl") val id: String,
    @SerialName("estacaolocalizacao") val nome: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private val NUMERO_DO_INICIO = Regex("""^\s*\d+\s*-\s*""")

/** "105 - CC Vasco da Gama " → "CC Vasco da Gama": sem o número do início nem os espaços ou tabs a mais. */
fun nomeGira(estacaolocalizacao: String): String = estacaolocalizacao.replace(NUMERO_DO_INICIO, "").espacosUnicos()

class Gira(private val http: Http, private val url: String = URL_GIRA) {
    /**
     * Pede a lista com o [etag] da última leitura (If-None-Match). Um ficheiro cortado, vazio ou que não é uma lista é
     * Falha, para o chamador ficar com a lista antiga: um mapa sem pinos é pior do que um com pinos de há dias.
     */
    suspend fun estacoes(etag: String?): Resultado<LeituraGira> {
        val r = try {
            http.getCom(url, if (etag == null) emptyMap() else mapOf("If-None-Match" to etag))
        } catch (e: IOException) {
            return Resultado.Falha(null, limite = false)
        }
        if (r.codigo == 304) return Resultado.Ok(LeituraGira(null, etag)) // antes do converterResposta, que trata o que não é 2xx como falha
        return converterResposta({ r }, false) { LeituraGira(estacoesDe(it), r.etag) }
    }

    private fun estacoesDe(json: String): List<EstacaoGira> {
        val lista = JSON.decodeFromString<List<EstacaoJson>>(json).mapNotNull {
            EstacaoGira(it.id, nomeGira(it.nome), it.latitude ?: return@mapNotNull null, it.longitude ?: return@mapNotNull null)
        }
        require(lista.isNotEmpty()) { "sem estações" }
        return lista
    }
}
