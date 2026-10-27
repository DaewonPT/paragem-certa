package pt.pixellusitano.paragemcerta.core.rede

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Resposta(val codigo: Int, val corpo: String)

interface Http {
    suspend fun get(url: String): Resposta
}

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>

    /** codigo é null quando não houve resposta (sem rede); limite = true em 429 e 503. */
    data class Falha(val codigo: Int?, val limite: Boolean) : Resultado<Nothing>
}

fun userAgent(versao: String) = "ParagemCerta/$versao (contact@pixellusitano.com)"

class OkHttpHttp(
    private val agente: String,
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
) : Http {
    override suspend fun get(url: String): Resposta = withContext(Dispatchers.IO) {
        cliente.newCall(Request.Builder().url(url).header("User-Agent", agente).build()).execute().use {
            Resposta(it.code, it.body.string())
        }
    }
}

/** Faz o pedido e converte: 2xx → Ok(converter(corpo)); outro código → Falha; erro de rede → Falha(null). */
suspend fun <T> Http.pedir(url: String, converter: (String) -> T): Resultado<T> {
    val r = try {
        get(url)
    } catch (e: IOException) {
        return Resultado.Falha(null, limite = false)
    }
    if (r.codigo !in 200..299) return Resultado.Falha(r.codigo, limite = r.codigo == 429 || r.codigo == 503)
    return try {
        Resultado.Ok(converter(r.corpo))
    } catch (e: SerializationException) {
        Resultado.Falha(r.codigo, limite = false)
    } catch (e: IllegalArgumentException) {
        Resultado.Falha(r.codigo, limite = false)
    }
}
