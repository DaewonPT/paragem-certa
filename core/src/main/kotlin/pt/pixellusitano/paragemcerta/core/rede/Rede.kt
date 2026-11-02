package pt.pixellusitano.paragemcerta.core.rede

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Resposta(val codigo: Int, val corpo: String)

interface Http {
    suspend fun get(url: String): Resposta

    /** Pedido com método e corpo (JSON, quando há). Quem só faz GET não precisa de o implementar. */
    suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String> = emptyMap()): Resposta =
        throw UnsupportedOperationException("Só GET")
}

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>

    /** codigo é null quando não houve resposta (sem rede); limite = true em 429 e 503. */
    data class Falha(val codigo: Int?, val limite: Boolean) : Resultado<Nothing>
}

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

    private suspend fun executar(pedido: Request.Builder): Resposta = withContext(Dispatchers.IO) {
        cliente.newCall(pedido.build()).execute().use { Resposta(it.code, it.body.string()) }
    }

    private companion object {
        val JSON_UTF8 = "application/json; charset=utf-8".toMediaType()
    }
}

/** Faz o pedido e converte: 2xx → Ok(converter(corpo)); outro código → Falha; erro de rede → Falha(null). */
suspend fun <T> Http.pedir(url: String, converter: (String) -> T): Resultado<T> = converterResposta({ get(url) }, converter)

/** Como [pedir], mas com método, corpo e cabeçalhos (POST, DELETE…). */
suspend fun <T> Http.pedirCom(
    metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>, converter: (String) -> T,
): Resultado<T> = converterResposta({ enviar(metodo, url, corpo, cabecalhos) }, converter)

private suspend fun <T> converterResposta(fazer: suspend () -> Resposta, converter: (String) -> T): Resultado<T> {
    val r = try {
        fazer()
    } catch (e: IOException) {
        return Resultado.Falha(null, limite = false)
    }
    if (r.codigo !in 200..299) return Resultado.Falha(r.codigo, limite = r.codigo == 429 || r.codigo == 503)
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
