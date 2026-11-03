package pt.pixellusitano.paragemcerta.core.rede

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    /** Servidor HTTP local do JDK que guarda o que recebeu e responde [codigo] com [resposta] (por omissão, 200 "ok"). */
    private fun comServidorLocal(codigo: Int = 200, resposta: ByteArray = "ok".toByteArray(), teste: (url: String, recebido: () -> Recebido) -> Unit) {
        val guardado = AtomicReference<Recebido>()
        val servidor = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        servidor.createContext("/") { t ->
            guardado.set(Recebido(t.requestMethod, t.requestBody.readBytes(), t.requestHeaders.toMap()))
            t.sendResponseHeaders(codigo, resposta.size.toLong())
            t.responseBody.use { it.write(resposta) }
        }
        servidor.start()
        try {
            teste("http://127.0.0.1:${servidor.address.port}/api/x") { guardado.get() }
        } finally {
            servidor.stop(0)
        }
    }

    private class Recebido(val metodo: String, val bytes: ByteArray, cabecalhos: Map<String, List<String>>) {
        val corpo get() = bytes.toString(Charsets.UTF_8)

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

    // Um JPEG de mentira: bytes que não são UTF-8 válido, para provar que o corpo não passa por texto.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x7F, 0x80.toByte(), 0xFF.toByte(), 0xD9.toByte())

    @Test fun enviarBytesMandaOCorpoEmBruto() = comServidorLocal { url, recebido ->
        val http = OkHttpHttp(userAgent("0.4.0"))
        val r = runBlocking { http.enviarBytes("POST", url, jpeg, "image/jpeg", mapOf("Authorization" to "Bearer tok-123")) }
        assertEquals(Resposta(200, "ok"), r)
        val p = recebido()
        assertEquals("POST", p.metodo)
        assertEquals("image/jpeg", p.cabecalhos["content-type"])
        assertArrayEquals(jpeg, p.bytes)
        assertEquals("Bearer tok-123", p.cabecalhos["authorization"])
        assertEquals(userAgent("0.4.0"), p.cabecalhos["user-agent"])
    }

    @Test fun obterBytesDaNuloForaDos2xx() {
        val http = OkHttpHttp(userAgent("0.4.0"))
        comServidorLocal(resposta = jpeg) { url, _ -> assertArrayEquals(jpeg, runBlocking { http.obterBytes(url) }) }
        comServidorLocal(codigo = 404, resposta = jpeg) { url, _ -> assertNull(runBlocking { http.obterBytes(url) }) }
        val porta = java.net.ServerSocket(0).use { it.localPort } // ninguém a ouvir: sem rede
        assertNull(runBlocking { http.obterBytes("http://127.0.0.1:$porta/x") })
    }

    @Test fun obterBytesDeUmEnderecoMalFormadoDaNulo() {
        val http = OkHttpHttp(userAgent("0.4.0"))
        // O endereço da foto vem do servidor: um texto que não é um URL não pode rebentar a app.
        listOf("", "isto não é um endereço", "ftp://127.0.0.1/foto.jpg", "http://").forEach { assertNull(it, runBlocking { http.obterBytes(it) }) }
    }

    @Test fun httpSemBytesSoFazGet() {
        val soGet = object : Http {
            override suspend fun get(url: String) = Resposta(200, "")
        }
        assertTrue(runCatching { runBlocking { soGet.enviarBytes("POST", "u", jpeg, "image/jpeg") } }.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(runCatching { runBlocking { soGet.obterBytes("u") } }.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test fun erroDoServidorVemNaFalha() {
        fun com(lerErro: Boolean, resposta: Resposta) =
            runBlocking { HttpFalso({ resposta }).pedirCom("POST", "u", null, emptyMap(), lerErro) { it } }
        assertEquals(Resultado.Falha(429, true, "muitos_pedidos"), com(true, Resposta(429, """{"erro":"muitos_pedidos"}""")))
        // Sem lerErro (os outros servidores) o corpo não se lê; e o que não é o JSON do servidor (uma página HTML, um texto, um erro que não é texto) fica sem erro.
        assertEquals(Resultado.Falha(429, true), com(false, Resposta(429, """{"erro":"muitos_pedidos"}""")))
        assertEquals(Resultado.Falha(429, true), com(true, Resposta(429, "<html>Error 1027</html>")))
        assertEquals(Resultado.Falha(500, false), com(true, Resposta(500, "")))
        assertEquals(Resultado.Falha(400, false), com(true, Resposta(400, "[]")))
        assertEquals(Resultado.Falha(400, false), com(true, Resposta(400, """{"erro":{"a":1}}""")))
        assertTrue(Resultado.Falha(503, true, "em_pausa").emPausa)
        assertFalse(Resultado.Falha(503, true).emPausa)
    }

    @Test fun enviarDeleteSemCorpo() = comServidorLocal { url, recebido ->
        val r = runBlocking { OkHttpHttp(userAgent("0.4.0")).enviar("DELETE", url, null, mapOf("Authorization" to "Bearer tok-123")) }
        assertEquals(200, r.codigo)
        assertEquals("DELETE", recebido().metodo)
        assertEquals("Bearer tok-123", recebido().cabecalhos["authorization"])
    }
}
