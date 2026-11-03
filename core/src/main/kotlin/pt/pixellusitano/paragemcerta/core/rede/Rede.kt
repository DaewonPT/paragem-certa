package pt.pixellusitano.paragemcerta.core.rede

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Resposta(val codigo: Int, val corpo: String)

interface Http {
    suspend fun get(url: String): Resposta

    /** Pedido com método e corpo (JSON, quando há). Quem só faz GET não precisa de o implementar. */
    suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String> = emptyMap()): Resposta =
        throw UnsupportedOperationException("Só GET")

    /** Como [enviar], mas com o corpo em bytes e o Content-Type [tipo] (uma foto JPEG). */
    suspend fun enviarBytes(metodo: String, url: String, corpo: ByteArray, tipo: String, cabecalhos: Map<String, String> = emptyMap()): Resposta =
        throw UnsupportedOperationException("Só GET")

    /** Os bytes de um GET; null se a resposta não for 2xx ou não houver rede. */
    suspend fun obterBytes(url: String): ByteArray? = throw UnsupportedOperationException("Só GET")
}

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>

    /**
     * codigo é null quando não houve resposta (sem rede); limite = true em 429 e 503. erro é o "erro" do JSON do servidor
     * da Paragem Certa (`{"erro":"muitos_pedidos"}`), só lido quando se pede com `lerErro`; null noutro corpo (uma página HTML).
     */
    data class Falha(val codigo: Int?, val limite: Boolean, val erro: String? = null) : Resultado<Nothing>
}

/** O servidor disse que passou a quota grátis do dia: tudo o que está nele fica em pausa até à meia-noite UTC. */
val Resultado.Falha.emPausa: Boolean get() = erro == "em_pausa"

fun userAgent(versao: String) = "ParagemCerta/$versao (contact@pixellusitano.com; https://pixellusitano.com)"

class OkHttpHttp(
    private val agente: String,
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
) : Http {
    override suspend fun get(url: String): Resposta = executar(Request.Builder().url(url).header("User-Agent", agente))

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        val pedido = Request.Builder().url(url).header("User-Agent", agente)
        cabecalhos.forEach { (nome, valor) -> pedido.header(nome, valor) }
        // O OkHttp recusa um POST sem corpo (pedir um nonce, sair); o DELETE não precisa dele.
        val texto = corpo ?: if (metodo == "POST") "" else null
        return executar(pedido.method(metodo, texto?.toRequestBody(JSON_UTF8)))
    }

    override suspend fun enviarBytes(metodo: String, url: String, corpo: ByteArray, tipo: String, cabecalhos: Map<String, String>): Resposta {
        val pedido = Request.Builder().url(url).header("User-Agent", agente)
        cabecalhos.forEach { (nome, valor) -> pedido.header(nome, valor) }
        return executar(pedido.method(metodo, corpo.toRequestBody(tipo.toMediaType())))
    }

    override suspend fun obterBytes(url: String): ByteArray? = try {
        executar(Request.Builder().url(url).header("User-Agent", agente)) { if (it.isSuccessful) it.body.bytes() else null }
    } catch (e: IOException) {
        null
    }

    private suspend fun executar(pedido: Request.Builder): Resposta = executar(pedido) { Resposta(it.code, it.body.string()) }

    private suspend fun <T> executar(pedido: Request.Builder, ler: (Response) -> T): T = withContext(Dispatchers.IO) {
        cliente.newCall(pedido.build()).execute().use(ler)
    }

    private companion object {
        val JSON_UTF8 = "application/json; charset=utf-8".toMediaType()
    }
}

/** Faz o pedido e converte: 2xx → Ok(converter(corpo)); outro código → Falha; erro de rede → Falha(null). */
suspend fun <T> Http.pedir(url: String, converter: (String) -> T): Resultado<T> = converterResposta({ get(url) }, false, converter)

/**
 * Como [pedir], mas com método, corpo e cabeçalhos (POST, DELETE…). Com [lerErro], uma falha traz o "erro" do JSON
 * do servidor da Paragem Certa em [Resultado.Falha.erro].
 */
suspend fun <T> Http.pedirCom(
    metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>, lerErro: Boolean = false, converter: (String) -> T,
): Resultado<T> = converterResposta({ enviar(metodo, url, corpo, cabecalhos) }, lerErro, converter)

/** O "erro" de `{"erro":"em_pausa"}`; null se o corpo não for esse JSON (a página HTML da Cloudflare, por exemplo). */
private fun erroDoCorpo(corpo: String): String? {
    val json = try {
        Json.parseToJsonElement(corpo)
    } catch (e: SerializationException) {
        return null
    }
    return ((json as? JsonObject)?.get("erro") as? JsonPrimitive)?.takeIf { it.isString }?.content
}

// internal: o ServidorParagemCerta também a usa para enviar a foto em bytes.
internal suspend fun <T> converterResposta(fazer: suspend () -> Resposta, lerErro: Boolean, converter: (String) -> T): Resultado<T> {
    val r = try {
        fazer()
    } catch (e: IOException) {
        return Resultado.Falha(null, limite = false)
    }
    if (r.codigo !in 200..299) {
        return Resultado.Falha(r.codigo, limite = r.codigo == 429 || r.codigo == 503, erro = if (lerErro) erroDoCorpo(r.corpo) else null)
    }
    return try {
        // A conversão de JSON grande (veículos, rede inteira) não pode correr na linha do ecrã.
        Resultado.Ok(withContext(Dispatchers.Default) { converter(r.corpo) })
    } catch (e: CancellationException) {
        throw e // o cancelamento (sair do ecrã) tem de continuar a funcionar
    } catch (e: SerializationException) {
        Resultado.Falha(r.codigo, limite = false)
    } catch (e: RuntimeException) {
        // Dados estranhos do operador (datas noutro formato, campos em falta) nunca podem rebentar a app.
        Resultado.Falha(r.codigo, limite = false)
    }
}
