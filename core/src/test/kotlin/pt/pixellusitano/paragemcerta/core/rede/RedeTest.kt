package pt.pixellusitano.paragemcerta.core.rede

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.PedidoEnviado
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class RedeTest {
    @Test fun converteForaDaLinhaDeQuemPede() {
        val ui = newSingleThreadContext("ui")
        val linhaUi = runBlocking(ui) { Thread.currentThread() }
        val r = runBlocking(ui) { HttpFalso({ Resposta(200, "[]") }).pedir("u") { Thread.currentThread() } }
        assertNotEquals(linhaUi, (r as Resultado.Ok).valor)
        ui.close()
    }

    @Test fun cancelamentoNaoViraFalha() {
        val resultado = runCatching {
            runBlocking { HttpFalso({ Resposta(200, "[]") }).pedir("u") { throw kotlinx.coroutines.CancellationException("saiu do ecrã") } }
        }
        assertTrue(resultado.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun agenteTemContactoESite() {
        assertEquals("ParagemCerta/0.4.0 (contact@pixellusitano.com; https://pixellusitano.com)", userAgent("0.4.0"))
    }

    @Test fun dadosEstranhosNaoRebentam() {
        val r = runBlocking { HttpFalso({ Resposta(200, "x") }).pedir("u") { java.time.Instant.parse(it) } }
        assertEquals(Resultado.Falha(200, limite = false), r)
    }

    @Test fun pedirComEnviaOPedidoEConverte() {
        val http = HttpFalso({ Resposta(200, "abc") })
        val r = runBlocking { http.pedirCom("POST", "u", "{}", mapOf("X" to "y")) { it.uppercase() } }
        assertEquals(Resultado.Ok("ABC"), r)
        assertEquals(PedidoEnviado("POST", "u", "{}", mapOf("X" to "y")), http.enviados.single())
    }

    @Test fun pedirComTrataOsErrosComoOPedir() {
        fun com(resposta: () -> Resposta) = runBlocking { HttpFalso(resposta).pedirCom("POST", "u", null, emptyMap()) { it } }
        assertEquals(Resultado.Falha(429, limite = true), com { Resposta(429, "") })
        assertEquals(Resultado.Falha(401, limite = false), com { Resposta(401, "") })
        assertEquals(Resultado.Falha(null, limite = false), com { throw java.io.IOException("sem rede") })
    }

    @Test fun httpSemEnviarSoFazGet() {
        val soGet = object : Http {
            override suspend fun get(url: String) = Resposta(200, "")
        }
        val resultado = runCatching { runBlocking { soGet.enviar("POST", "u", null) } }
        assertTrue(resultado.exceptionOrNull() is UnsupportedOperationException)
    }

    /** Servidor HTTP local do JDK que guarda o que recebeu e responde 200 "ok". */
    private fun comServidorLocal(teste: (url: String, recebido: () -> Recebido) -> Unit) {
        val guardado = AtomicReference<Recebido>()
        val servidor = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        servidor.createContext("/") { t ->
            guardado.set(Recebido(t.requestMethod, t.requestBody.readBytes().toString(Charsets.UTF_8), t.requestHeaders.toMap()))
            val resposta = "ok".toByteArray()
            t.sendResponseHeaders(200, resposta.size.toLong())
            t.responseBody.use { it.write(resposta) }
        }
        servidor.start()
        try {
            teste("http://127.0.0.1:${servidor.address.port}/api/x") { guardado.get() }
        } finally {
            servidor.stop(0)
        }
    }

    private class Recebido(val metodo: String, val corpo: String, cabecalhos: Map<String, List<String>>) {
        // Os nomes dos cabeçalhos chegam com a capitalização que o servidor do JDK lhes dá: compara-se em minúsculas.
        val cabecalhos = cabecalhos.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
    }

    @Test fun enviarMandaCorpoECabecalhos() = comServidorLocal { url, recebido ->
        val http = OkHttpHttp(userAgent("0.4.0"))
        val r = runBlocking { http.enviar("POST", url, """{"a":"ç"}""", mapOf("Authorization" to "Bearer tok-123")) }
        assertEquals(Resposta(200, "ok"), r)
        val p = recebido()
        assertEquals("POST", p.metodo)
        assertEquals("""{"a":"ç"}""", p.corpo)
        assertEquals("Bearer tok-123", p.cabecalhos["authorization"])
        assertEquals("application/json; charset=utf-8", p.cabecalhos["content-type"])
        assertEquals(userAgent("0.4.0"), p.cabecalhos["user-agent"])
    }

    @Test fun enviarPostSemCorpoMandaCorpoVazio() = comServidorLocal { url, recebido ->
        val r = runBlocking { OkHttpHttp(userAgent("0.4.0")).enviar("POST", url, null) }
        assertEquals(200, r.codigo)
        assertEquals("POST", recebido().metodo)
        assertEquals("", recebido().corpo)
    }

    @Test fun enviarDeleteSemCorpo() = comServidorLocal { url, recebido ->
        val r = runBlocking { OkHttpHttp(userAgent("0.4.0")).enviar("DELETE", url, null, mapOf("Authorization" to "Bearer tok-123")) }
        assertEquals(200, r.codigo)
        assertEquals("DELETE", recebido().metodo)
        assertEquals("Bearer tok-123", recebido().cabecalhos["authorization"])
    }
}
