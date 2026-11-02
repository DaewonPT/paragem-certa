package pt.pixellusitano.paragemcerta.core.fontes.servidor

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.IOException

class ServidorTest {
    private val base = "https://servidor.teste"
    private val sessaoJson = """{"token":"tok-123","email":"ana@exemplo.pt"}"""

    private fun servidor(http: HttpFalso) = ServidorParagemCerta(http, base)

    // O corpo compara-se como JSON, não como texto: a ordem das chaves não importa.
    private fun json(texto: String?) = Json.parseToJsonElement(texto!!) as JsonObject

    @Test fun leAConfiguracao() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-config.json")) })
        val c = (servidor(http).config() as Resultado.Ok).valor
        assertEquals("$base/dados/config.json", http.pedidos.single())
        assertEquals(1, c.versao)
        assertFalse(c.interruptores.conta)
        assertFalse(c.interruptores.correcoes)
        assertEquals(70, c.emails.codigosPorDia)
        assertEquals(5, c.canaisReclamacao.size)
        assertEquals(CanalReclamacaoTexto("Carris Metropolitana", "https://carrismetropolitana.pt/contacto"), c.canaisReclamacao[2])
    }

    @Test fun configComCamposNovosNaoParte() = runTest {
        val futura = """{"versao":2,"novidade":{"a":1},"interruptores":{"conta":true,"outroNovo":true},
            "emails":{"codigosPorDia":50,"limiteNovo":3},"canaisReclamacao":[{"nome":"A","url":"https://a.pt","icone":"x"}]}"""
        val c = (servidor(HttpFalso({ Resposta(200, futura) })).config() as Resultado.Ok).valor
        assertEquals(2, c.versao)
        assertTrue(c.interruptores.conta)
        assertFalse(c.interruptores.comunidade) // o que falta fica desligado
        assertEquals(50, c.emails.codigosPorDia)
        assertEquals(listOf(CanalReclamacaoTexto("A", "https://a.pt")), c.canaisReclamacao)
    }

    @Test fun configIlegivelDaFalha() = runTest {
        assertEquals(Resultado.Falha(200, limite = false), servidor(HttpFalso({ Resposta(200, "<html>") })).config())
    }

    @Test fun pedirCodigoMandaOEmail() = runTest {
        val http = HttpFalso({ Resposta(204, "") })
        assertEquals(Resultado.Ok(Unit), servidor(http).pedirCodigo("ana@exemplo.pt"))
        val p = http.enviados.single()
        assertEquals("POST", p.metodo)
        assertEquals("$base/api/auth/email/pedir", p.url)
        assertEquals(buildJsonObject { put("email", "ana@exemplo.pt") }, json(p.corpo))
    }

    @Test fun confirmarCodigoDaSessao() = runTest {
        val http = HttpFalso({ Resposta(200, sessaoJson) })
        val r = servidor(http).confirmarCodigo("ana@exemplo.pt", "123456")
        assertEquals(Resultado.Ok(Sessao("tok-123", "ana@exemplo.pt")), r)
        val p = http.enviados.single()
        assertEquals("$base/api/auth/email/confirmar", p.url)
        assertEquals(buildJsonObject { put("email", "ana@exemplo.pt"); put("codigo", "123456") }, json(p.corpo))
    }

    @Test fun codigoErradoDaFalha401() = runTest {
        val http = HttpFalso({ Resposta(401, """{"erro":"codigo_errado"}""") })
        assertEquals(Resultado.Falha(401, limite = false), servidor(http).confirmarCodigo("ana@exemplo.pt", "000000"))
    }

    @Test fun codigoExpiradoDaFalha410() = runTest {
        val http = HttpFalso({ Resposta(410, """{"erro":"codigo_expirado"}""") })
        assertEquals(Resultado.Falha(410, limite = false), servidor(http).confirmarCodigo("ana@exemplo.pt", "123456"))
    }

    @Test fun muitosPedidosDaLimite() = runTest {
        val http = HttpFalso({ Resposta(429, """{"erro":"muitos_pedidos"}""") })
        assertEquals(Resultado.Falha(429, limite = true), servidor(http).pedirCodigo("ana@exemplo.pt"))
    }

    @Test fun semRedeDaFalhaSemCodigo() = runTest {
        val http = HttpFalso({ throw IOException("sem rede") })
        assertEquals(Resultado.Falha(null, limite = false), servidor(http).pedirCodigo("ana@exemplo.pt"))
    }

    @Test fun nonceDoGoogleEntraNaSessao() = runTest {
        val http = HttpFalso({ Resposta(200, """{"nonce":"n-abc"}""") }, { Resposta(200, sessaoJson) })
        val s = servidor(http)
        assertEquals(Resultado.Ok("n-abc"), s.nonceGoogle())
        assertEquals(Resultado.Ok(Sessao("tok-123", "ana@exemplo.pt")), s.entrarComGoogle("eyJ.token.google"))
        val (pedeNonce, entra) = http.enviados
        assertEquals("POST", pedeNonce.metodo)
        assertEquals("$base/api/auth/google/nonce", pedeNonce.url)
        assertNull(pedeNonce.corpo)
        assertEquals("$base/api/auth/google", entra.url)
        assertEquals(buildJsonObject { put("idToken", "eyJ.token.google") }, json(entra.corpo))
    }

    @Test fun sairEApagarContaUsamOToken() = runTest {
        val http = HttpFalso({ Resposta(204, "") })
        val s = servidor(http)
        assertEquals(Resultado.Ok(Unit), s.sair("tok-123"))
        assertEquals(Resultado.Ok(Unit), s.apagarConta("tok-123"))
        val (sai, apaga) = http.enviados
        assertEquals("POST", sai.metodo)
        assertEquals("$base/api/auth/sair", sai.url)
        assertEquals("DELETE", apaga.metodo)
        assertEquals("$base/api/conta", apaga.url)
        listOf(sai, apaga).forEach {
            assertNull(it.corpo)
            assertEquals(mapOf("Authorization" to "Bearer tok-123"), it.cabecalhos)
        }
    }

    @Test fun sessaoRecusadaDaFalha401() = runTest {
        val http = HttpFalso({ Resposta(401, """{"erro":"sem_sessao"}""") })
        assertEquals(Resultado.Falha(401, limite = false), servidor(http).sincronizar("velho", 0, emptyList()))
    }

    @Test fun sincronizarMandaOTokenEOsDados() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-sincronizar.json")) })
        val minha = FavoritoRemoto("CM:060419", "PARAGEM", "Belém (Mosteiro)", 38.6975, -9.2062, 0, apagado = false)
        val apagada = FavoritoRemoto("9b8a7c6d5e4f4a3b8c2d1e0f9a8b7c6d", "OUTRO", "Ginásio", 38.7, -9.15, 2, apagado = true)

        val r = (servidor(http).sincronizar("tok-123", 7, listOf(minha, apagada)) as Resultado.Ok).valor

        val p = http.enviados.single()
        assertEquals("POST", p.metodo)
        assertEquals("$base/api/sincronizar", p.url)
        assertEquals(mapOf("Authorization" to "Bearer tok-123"), p.cabecalhos)
        val esperado = buildJsonObject {
            put("desde", 7)
            put("alteracoes", buildJsonArray {
                add(buildJsonObject {
                    put("chave", "CM:060419"); put("tipo", "PARAGEM"); put("nome", "Belém (Mosteiro)")
                    put("lat", 38.6975); put("lon", -9.2062); put("ordem", 0); put("apagado", false)
                })
                add(buildJsonObject {
                    put("chave", "9b8a7c6d5e4f4a3b8c2d1e0f9a8b7c6d"); put("tipo", "OUTRO"); put("nome", "Ginásio")
                    put("lat", 38.7); put("lon", -9.15); put("ordem", 2); put("apagado", true)
                })
            })
        }
        assertEquals(esperado, json(p.corpo)) // sem "revisao": isso só vem do servidor

        assertEquals(12L, r.revisao)
        assertEquals(3, r.alteracoes.size)
        assertEquals(FavoritoRemoto("CM:060419", "PARAGEM", "Belém (Mosteiro)", 38.6975, -9.2062, 0, apagado = false, revisao = 9), r.alteracoes[0])
        assertTrue(r.alteracoes[1].apagado)
        assertEquals(12L, r.alteracoes[2].revisao)
    }

    @Test fun sincronizarSemAlteracoesMandaListaVazia() = runTest {
        val http = HttpFalso({ Resposta(200, """{"revisao":0,"alteracoes":[],"campoNovo":true}""") })
        val r = servidor(http).sincronizar("tok-123", 0, emptyList())
        assertEquals(Resultado.Ok(Sincronizacao(0, emptyList())), r)
        assertEquals(buildJsonObject { put("desde", 0); put("alteracoes", buildJsonArray { }) }, json(http.enviados.single().corpo))
    }
}
