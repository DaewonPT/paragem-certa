package pt.pixellusitano.paragemcerta.core.fontes.servidor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rede.pedirCom

// Campos desconhecidos ignoram-se: uma versão nova do servidor não parte a app.
private val JSON = Json { ignoreUnknownKeys = true }

/** O `config.json` do servidor: interruptores e valores que mudam sem sair uma versão nova da app. */
@Serializable data class Configuracao(
    val versao: Int,
    val interruptores: Interruptores,
    val emails: Emails,
    val canaisReclamacao: List<CanalReclamacaoTexto>,
)

/** Tudo desligado por omissão: um interruptor que o servidor não conhece fica desligado. */
@Serializable data class Interruptores(
    val conta: Boolean = false,
    val comunidade: Boolean = false,
    val fotos: Boolean = false,
    val correcoes: Boolean = false,
)

@Serializable data class Emails(val codigosPorDia: Int)

@Serializable data class CanalReclamacaoTexto(val nome: String, val url: String)

@Serializable data class Sessao(val token: String, val email: String)

/** Um favorito como o servidor o guarda. `revisao` só vem do servidor: nunca se envia. */
@Serializable data class FavoritoRemoto(
    val chave: String,
    val tipo: String,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val ordem: Int,
    val apagado: Boolean,
    val revisao: Long? = null,
)

@Serializable data class Sincronizacao(val revisao: Long, val alteracoes: List<FavoritoRemoto>)

@Serializable private class PedidoSincronizar(val desde: Long, val alteracoes: List<FavoritoRemoto>)

@Serializable private class NonceJson(val nonce: String)

/**
 * O servidor da Paragem Certa. Falhas: o código HTTP (401 código errado ou sessão recusada, 410 código expirado,
 * 429 demasiados pedidos com `limite = true`) ou `Falha(null)` sem rede.
 */
class ServidorParagemCerta(private val http: Http, private val base: String = "https://paragemcerta.pixellusitano.com") {
    suspend fun config(): Resultado<Configuracao> = http.pedir("$base/dados/config.json") { JSON.decodeFromString<Configuracao>(it) }

    suspend fun nonceGoogle(): Resultado<String> =
        enviar("POST", "/api/auth/google/nonce", null) { JSON.decodeFromString<NonceJson>(it).nonce }

    suspend fun entrarComGoogle(idToken: String): Resultado<Sessao> =
        enviar("POST", "/api/auth/google", buildJsonObject { put("idToken", idToken) }, converter = ::sessao)

    suspend fun pedirCodigo(email: String): Resultado<Unit> =
        enviar("POST", "/api/auth/email/pedir", buildJsonObject { put("email", email) }) { }

    suspend fun confirmarCodigo(email: String, codigo: String): Resultado<Sessao> =
        enviar("POST", "/api/auth/email/confirmar", buildJsonObject { put("email", email); put("codigo", codigo) }, converter = ::sessao)

    suspend fun sair(token: String): Resultado<Unit> = enviar("POST", "/api/auth/sair", null, token) { }

    suspend fun apagarConta(token: String): Resultado<Unit> = enviar("DELETE", "/api/conta", null, token) { }

    suspend fun sincronizar(token: String, desde: Long, alteracoes: List<FavoritoRemoto>): Resultado<Sincronizacao> =
        enviar("POST", "/api/sincronizar", JSON.encodeToJsonElement(PedidoSincronizar(desde, alteracoes)), token) {
            JSON.decodeFromString<Sincronizacao>(it)
        }

    private fun sessao(corpo: String) = JSON.decodeFromString<Sessao>(corpo)

    private suspend fun <T> enviar(
        metodo: String, caminho: String, corpo: JsonElement?, token: String? = null, converter: (String) -> T,
    ): Resultado<T> = http.pedirCom(
        metodo, "$base$caminho", corpo?.toString(),
        if (token == null) emptyMap() else mapOf("Authorization" to "Bearer $token"), converter,
    )
}
