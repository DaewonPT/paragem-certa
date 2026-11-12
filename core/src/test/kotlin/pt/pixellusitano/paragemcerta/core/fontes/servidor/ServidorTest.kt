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
import pt.pixellusitano.paragemcerta.core.comunidade.Correcao
import pt.pixellusitano.paragemcerta.core.comunidade.MotivoDenuncia
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import java.time.Instant

class ServidorTest {
    private val base = "https://servidor.teste"
    private val sessaoJson = """{"token":"tok-123","email":"ana@exemplo.pt"}"""

    private fun servidor(http: HttpFalso, relogio: () -> Instant = Instant::now) = ServidorParagemCerta(http, base, relogio)

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

    @Test fun configAntigaSemCamposNovosLe() = runTest {
        // A fixture é de um servidor mais antigo: sem `mapasOficiais` nem os interruptores `carrisAoVivo` e `metro`.
        val c = (servidor(HttpFalso({ Resposta(200, fixture("servidor-config.json")) })).config() as Resultado.Ok).valor
        assertEquals(emptyList<MapaOficial>(), c.mapasOficiais)
        assertFalse(c.interruptores.carrisAoVivo)
        assertFalse(c.interruptores.metro)
    }

    @Test fun configNovaLeOsMapasEOsInterruptores() = runTest {
        val nova = """{"versao":1,"interruptores":{"carrisAoVivo":true,"metro":true},"emails":{"codigosPorDia":70},"canaisReclamacao":[],
            "mapasOficiais":[{"operador":"pt-Carris","url":"https://exemplo.pt/mapa","extra":1},{"operador":"CM","url":"https://exemplo.pt/cm"}]}"""
        val c = (servidor(HttpFalso({ Resposta(200, nova) })).config() as Resultado.Ok).valor
        assertTrue(c.interruptores.carrisAoVivo)
        assertTrue(c.interruptores.metro)
        assertFalse(c.interruptores.comunidade)
        assertEquals(listOf(MapaOficial("pt-Carris", "https://exemplo.pt/mapa"), MapaOficial("CM", "https://exemplo.pt/cm")), c.mapasOficiais)
    }

    @Test fun configIlegivelDaFalha() = runTest {
        assertEquals(Resultado.Falha(200, limite = false), servidor(HttpFalso({ Resposta(200, "<html>") })).config())
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
        assertEquals(Resultado.Falha(401, limite = false, erro = "sem_sessao"), servidor(http).sincronizar("velho", 0, emptyList()))
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

    @Test fun relatarMandaSoTipoParagemELinha() = runTest {
        val http = HttpFalso({ Resposta(201, """{"id":"r-1","novo":true,"campoNovo":1}""") })
        assertEquals(Resultado.Ok(RelatoCriado("r-1", true)), servidor(http).relatar("t", TipoRelato.DESVIO, "CM:060419", "736"))
        val p = http.enviados.single()
        assertEquals("POST", p.metodo)
        assertEquals("$base/api/relatos", p.url)
        assertEquals(mapOf("Authorization" to "Bearer t"), p.cabecalhos)
        assertEquals(buildJsonObject { put("tipo", "desvio"); put("paragem", "CM:060419"); put("linha", "736") }, json(p.corpo))
    }

    @Test fun relatarDeParagemNaoMandaLinha() = runTest {
        val http = HttpFalso({ Resposta(200, """{"id":"r-2","novo":false}""") })
        assertEquals(Resultado.Ok(RelatoCriado("r-2", false)), servidor(http).relatar("t", TipoRelato.ELEVADOR, "CM:060419", null))
        assertEquals(buildJsonObject { put("tipo", "elevador"); put("paragem", "CM:060419") }, json(http.enviados.single().corpo))
        // O servidor recusa uma linha num relato de paragem: nunca sai de cá.
        servidor(http).relatar("t", TipoRelato.PROBLEMA_PARAGEM, "CM:060419", "736")
        assertEquals(buildJsonObject { put("tipo", "problema_paragem"); put("paragem", "CM:060419") }, json(http.enviados.last().corpo))
    }

    @Test fun votarMandaORelatoEOVoto() = runTest {
        val http = HttpFalso({ Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""") })
        assertEquals(Resultado.Ok(Voto(2, confirmado = true, ativo = true)), servidor(http).votar("t", "r-1", aindaEsta = false))
        val p = http.enviados.single()
        assertEquals("$base/api/votos", p.url)
        assertEquals(buildJsonObject { put("relato", "r-1"); put("voto", "ja_nao_esta") }, json(p.corpo))
        servidor(http).votar("t", "r-1", aindaEsta = true)
        assertEquals(buildJsonObject { put("relato", "r-1"); put("voto", "ainda_esta") }, json(http.enviados.last().corpo))
    }

    @Test fun corrigirMandaSoOQueOTipoUsa() = runTest {
        val http = HttpFalso({ Resposta(201, """{"id":"c-1"}""") })
        val s = servidor(http)
        assertEquals(Resultado.Ok(Unit), s.corrigir("t", "CM:060419", Correcao.Posicao(38.6975, -9.2062)))
        s.corrigir("t", "CM:060419", Correcao.Nome("Belém (Mosteiro)"))
        s.corrigir("t", "CM:060419", Correcao.JaNaoExiste)
        val (posicao, nome, jaNaoExiste) = http.enviados
        assertEquals("$base/api/correcoes", posicao.url)
        assertEquals(buildJsonObject { put("paragem", "CM:060419"); put("tipo", "posicao"); put("lat", 38.6975); put("lon", -9.2062) }, json(posicao.corpo))
        assertEquals(buildJsonObject { put("paragem", "CM:060419"); put("tipo", "nome"); put("nome", "Belém (Mosteiro)") }, json(nome.corpo))
        assertEquals(buildJsonObject { put("paragem", "CM:060419"); put("tipo", "ja_nao_existe") }, json(jaNaoExiste.corpo))
    }

    @Test fun denunciarMandaABoaFe() = runTest {
        val http = HttpFalso({ Resposta(204, "") })
        assertEquals(Resultado.Ok(Unit), servidor(http).denunciar("t", "relato:r-1", MotivoDenuncia.DADOS_PESSOAIS, "Ana", "Tem o meu número."))
        val p = http.enviados.single()
        assertEquals("$base/api/denuncias", p.url)
        val esperado = buildJsonObject {
            put("conteudo", "relato:r-1"); put("motivo", "dados_pessoais"); put("nome", "Ana"); put("explicacao", "Tem o meu número."); put("boaFe", true)
        }
        assertEquals(esperado, json(p.corpo))
    }

    @Test fun aceitarTermosMandaAVersao() = runTest {
        val http = HttpFalso({ Resposta(204, "") })
        assertEquals(Resultado.Ok(Unit), servidor(http).aceitarTermos("t", "2"))
        val p = http.enviados.single()
        assertEquals("$base/api/termos", p.url)
        assertEquals(buildJsonObject { put("versao", "2") }, json(p.corpo))
        assertEquals(mapOf("Authorization" to "Bearer t"), p.cabecalhos)
    }

    @Test fun leAComunidade() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-comunidade.json")) })
        val d = (servidor(http).comunidade(listOf("CM:060419", "TRANSITOUS:x_(1)")) as Resultado.Ok).valor
        // Um GET: sem corpo, as vírgulas ficam e cada chave vai codificada.
        assertEquals("$base/api/comunidade?paragens=CM%3A060419,TRANSITOUS%3Ax_%281%29", http.enviados.single().url)
        assertEquals("GET", http.enviados.single().metodo)
        assertNull(http.enviados.single().corpo)
        assertEquals(2, d.relatos.size)
        assertEquals(RelatoRemoto("6f1c2a9e-3b7d-4c58-9a10-5d2e8b7f4a11", "desvio", "CM:060419", "736", 1793700000000, 1793710800000, 2, confirmado = true), d.relatos[0])
        assertEquals(listOf(true, false), d.relatos.map { it.confirmado })
        assertEquals("", d.relatos[1].linha)
        assertEquals(CorrecaoRemota("CM:060419", "posicao", 38.6975, -9.2062), d.correcoes.single())
    }

    @Test fun comunidadeVaziaOuComCamposNovosNaoParte() = runTest {
        val vazia = (servidor(HttpFalso({ Resposta(200, "{}") })).comunidade(listOf("CM:1")) as Resultado.Ok).valor
        assertEquals(DadosComunidade(), vazia)
        val futura = """{"relatos":[],"novidade":1,"correcoes":[{"paragem":"CM:1","tipo":"nome","nome":"Casa","extra":true}]}"""
        val d = (servidor(HttpFalso({ Resposta(200, futura) })).comunidade(listOf("CM:1")) as Resultado.Ok).valor
        assertEquals(CorrecaoRemota("CM:1", "nome", nome = "Casa"), d.correcoes.single())
    }

    @Test fun avisosLeOsRelatosDasLinhas() = runTest {
        val corpo = """{"relatos":[{"id":"r-1","tipo":"desvio","paragem":"CM:060419","linha":"15E","criadoEm":1,"expiraEm":2,"confirmacoes":2,"confirmado":true}]}"""
        val http = HttpFalso({ Resposta(200, corpo) })
        val r = (servidor(http).avisos(listOf("736", "15E")) as Resultado.Ok).valor
        assertEquals("$base/api/avisos?linhas=736,15E", http.enviados.single().url)
        assertEquals(listOf(RelatoRemoto("r-1", "desvio", "CM:060419", "15E", 1, 2, 2, confirmado = true)), r.relatos)
    }

    @Test fun avisosLeRelatosEPerturbacoes() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-avisos.json")) })
        val r = (servidor(http).avisos(listOf("736", "VD")) as Resultado.Ok).valor
        assertEquals("$base/api/avisos?linhas=736,VD", http.enviados.single().url)
        assertEquals(listOf(RelatoRemoto("6f1c2a9e-3b7d-4c58-9a10-5d2e8b7f4a11", "desvio", "CM:060419", "736", 1793700000000, 1793710800000, 2, confirmado = true)), r.relatos)
        assertEquals(
            listOf(PerturbacaoRemota("metro:VD:3f2a9c1e", "VD", "Circulação com perturbações. Tempo de espera superior ao normal.")),
            r.perturbacoes,
        )
    }

    @Test fun avisosDeUmServidorAntigoSoComRelatos() = runTest {
        val corpo = """{"relatos":[{"id":"r-1","tipo":"desvio","paragem":"CM:060419","linha":"15E","criadoEm":1,"expiraEm":2,"confirmacoes":2,"confirmado":true}]}"""
        val r = (servidor(HttpFalso({ Resposta(200, corpo) })).avisos(listOf("15E")) as Resultado.Ok).valor
        assertEquals(1, r.relatos.size)
        assertEquals(emptyList<PerturbacaoRemota>(), r.perturbacoes)
        // E o contrário: sem relatos (a comunidade desligada no servidor), só as perturbações.
        val soPerturbacoes = """{"relatos":[],"perturbacoes":[{"id":"metro:AZ:00000000","linha":"AZ","texto":"Circulação interrompida.","novidade":1}]}"""
        val p = (servidor(HttpFalso({ Resposta(200, soPerturbacoes) })).avisos(listOf("AZ")) as Resultado.Ok).valor
        assertEquals(Avisos(perturbacoes = listOf(PerturbacaoRemota("metro:AZ:00000000", "AZ", "Circulação interrompida."))), p)
    }

    @Test fun relatosDasLinhasLeOsConfirmadosEOsPorConfirmar() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-comunidade.json")) })
        val r = (servidor(http).relatosDasLinhas(listOf("736", "15E")) as Resultado.Ok).valor
        assertEquals("$base/api/comunidade?linhas=736,15E", http.enviados.single().url)
        assertEquals("GET", http.enviados.single().metodo)
        assertEquals(listOf(true, false), r.map { it.confirmado })
        assertEquals(
            Resultado.Falha(403, limite = false, erro = "desligado"),
            servidor(HttpFalso({ Resposta(403, """{"erro":"desligado"}""") })).relatosDasLinhas(listOf("736")),
        )
    }

    @Test fun carrisAoVivoLeOsVeiculos() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("servidor-carris.json")) })
        val r = (servidor(http).carrisAoVivo() as Resultado.Ok).valor
        assertEquals("$base/api/rt/carris", http.enviados.single().url)
        assertEquals(1793700000L, r.atualizado)
        assertEquals(VeiculoCarrisRemoto("5247", "103_0", "4502_20261103_103_0_8", 1, 38.7068, -9.1754, 19, "12105", 1793699980), r.veiculos[0])
        assertNull(r.veiculos[1].paragem)
        assertEquals(AoVivoCarris(1, emptyList()), (servidor(HttpFalso({ Resposta(200, """{"atualizado":1,"veiculos":[],"novidade":true}""") })).carrisAoVivo() as Resultado.Ok).valor)
    }

    @Test fun fonteFalhouNaoPausa() = runTest {
        val http = HttpFalso(
            { Resposta(502, """{"erro":"fonte_falhou"}""") }, { Resposta(200, fixture("servidor-carris.json")) }, { Resposta(200, fixture("servidor-avisos.json")) },
        )
        val s = servidor(http)
        // Uma fonte em baixo é uma falha normal: o pedido seguinte sai e o resto do servidor continua a responder.
        val falha = s.carrisAoVivo() as Resultado.Falha
        assertEquals(Resultado.Falha(502, limite = false, erro = "fonte_falhou"), falha)
        assertFalse(falha.emPausa)
        assertTrue(s.carrisAoVivo() is Resultado.Ok)
        assertTrue(s.avisos(listOf("736")) is Resultado.Ok)
        assertEquals(3, http.pedidos.size)
    }

    @Test fun pagina1027PausaOAoVivo() = runTest {
        val agora = Instant.parse("2026-11-03T10:00:00Z")
        val http = HttpFalso({ Resposta(429, "<html><title>Error 1027</title></html>") }, { Resposta(200, fixture("servidor-carris.json")) })
        val s = servidor(http) { agora }
        val r = s.carrisAoVivo() as Resultado.Falha
        assertEquals(Resultado.Falha(429, limite = true, erro = "em_pausa"), r)
        assertTrue(r.emPausa)
        // Em pausa, nenhuma das leituras novas chega ao Http.
        val parado = Resultado.Falha(null, limite = true, erro = "em_pausa")
        assertEquals(parado, s.carrisAoVivo())
        assertEquals(parado, s.relatosDasLinhas(listOf("736")))
        assertEquals(parado, s.avisos(listOf("736")))
        assertEquals(1, http.pedidos.size)
    }

    @Test fun desligadoNumaLeituraVemNaFalha() = runTest {
        val http = HttpFalso({ Resposta(403, """{"erro":"desligado"}""") })
        val s = servidor(http)
        assertEquals(Resultado.Falha(403, limite = false, erro = "desligado"), s.comunidade(listOf("CM:060419")))
        assertEquals(Resultado.Falha(403, limite = false, erro = "desligado"), s.avisos(listOf("736")))
        assertEquals(2, http.pedidos.size) // 403 não é pausa: o segundo pedido chegou
    }

    private val comunidadeVazia = """{"relatos":[],"fotos":[],"emRevisao":[],"correcoes":[]}"""

    @Test fun emPausaAteAMeiaNoiteUtc() = runTest {
        var agora = Instant.parse("2026-11-03T22:00:00Z")
        val http = HttpFalso({ Resposta(503, """{"erro":"em_pausa"}""") }, { Resposta(200, comunidadeVazia) })
        val s = servidor(http) { agora }
        assertEquals(Resultado.Falha(503, limite = true, erro = "em_pausa"), s.comunidade(listOf("CM:060419")))
        assertTrue((s.comunidade(listOf("CM:060419")) as Resultado.Falha).emPausa)
        // Até às 00:00 UTC nada chega ao Http: nem leituras nem escritas.
        agora = Instant.parse("2026-11-03T23:59:59Z")
        val parado = Resultado.Falha(null, limite = true, erro = "em_pausa")
        assertEquals(parado, s.comunidade(listOf("CM:060419")))
        assertEquals(parado, s.avisos(listOf("736")))
        assertEquals(parado, s.relatar("t", TipoRelato.DESVIO, "CM:060419", "736"))
        assertEquals(parado, s.sincronizar("t", 0, emptyList()))
        assertEquals(1, http.pedidos.size)
        // À meia-noite pede outra vez.
        agora = Instant.parse("2026-11-04T00:00:00Z")
        assertEquals(Resultado.Ok(DadosComunidade()), s.comunidade(listOf("CM:060419")))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun pagina1027DaPausa() = runTest {
        val agora = Instant.parse("2026-11-03T10:00:00Z")
        val http = HttpFalso({ Resposta(429, "<html><title>Error 1027</title></html>") }, { Resposta(200, comunidadeVazia) })
        val s = servidor(http) { agora }
        val r = s.comunidade(listOf("CM:060419")) as Resultado.Falha
        assertEquals(Resultado.Falha(429, limite = true, erro = "em_pausa"), r)
        assertTrue(r.emPausa)
        assertEquals(Resultado.Falha(null, limite = true, erro = "em_pausa"), s.avisos(listOf("736")))
        assertEquals(1, http.pedidos.size)
    }

    @Test fun html503NaoPausa() = runTest {
        val http = HttpFalso({ Resposta(503, "<html>Error 1102</html>") }, { Resposta(200, comunidadeVazia) })
        val s = servidor(http)
        assertEquals(Resultado.Falha(503, limite = true), s.comunidade(listOf("CM:060419")))
        assertEquals(Resultado.Ok(DadosComunidade()), s.comunidade(listOf("CM:060419")))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun devagarNaoPausa() = runTest {
        val http = HttpFalso({ Resposta(429, """{"erro":"devagar"}""") }, { Resposta(201, """{"id":"r-1","novo":true}""") })
        val s = servidor(http)
        assertEquals(Resultado.Falha(429, limite = true, erro = "devagar"), s.relatar("t", TipoRelato.DESVIO, "CM:060419", "736"))
        assertEquals(Resultado.Ok(RelatoCriado("r-1", true)), s.relatar("t", TipoRelato.DESVIO, "CM:060419", "736"))
    }

    @Test fun limiteDoWafNasEntradasNaoPausa() = runTest {
        val http = HttpFalso({ Resposta(429, "<html>Too Many Requests</html>") }, { Resposta(200, comunidadeVazia) })
        val s = servidor(http)
        assertEquals(Resultado.Falha(429, limite = true), s.nonceGoogle())
        assertEquals(Resultado.Ok(DadosComunidade()), s.comunidade(listOf("CM:060419")))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun pausaNaoTocaNasEntradasNemNaConfiguracao() = runTest {
        var agora = Instant.parse("2026-11-03T22:00:00Z")
        val http = HttpFalso(
            { Resposta(503, """{"erro":"em_pausa"}""") }, { Resposta(200, """{"nonce":"n-1"}""") }, { Resposta(200, fixture("servidor-config.json")) },
            { Resposta(503, """{"erro":"em_pausa"}""") }, { Resposta(200, comunidadeVazia) },
        )
        val s = servidor(http) { agora }
        assertTrue((s.comunidade(listOf("CM:060419")) as Resultado.Falha).emPausa)
        // Em pausa, o pedido do nonce e a configuração continuam a chegar ao servidor.
        assertEquals(Resultado.Ok("n-1"), s.nonceGoogle())
        assertTrue(s.config() is Resultado.Ok)
        assertEquals(3, http.pedidos.size)
        // Passada a meia-noite, um em_pausa na entrada devolve-se tal e qual, mas não começa pausa nenhuma.
        agora = Instant.parse("2026-11-04T00:00:00Z")
        assertTrue((s.nonceGoogle() as Resultado.Falha).emPausa)
        assertTrue(s.comunidade(listOf("CM:060419")) is Resultado.Ok)
        assertEquals(5, http.pedidos.size)
    }

    @Test fun configNaoEntraNaPausa() = runTest {
        // Nem o 429 de uma página da Cloudflare no config.json começa a pausa.
        val http = HttpFalso({ Resposta(429, "<html>1027</html>") }, { Resposta(200, comunidadeVazia) })
        val s = servidor(http)
        assertEquals(Resultado.Falha(429, limite = true), s.config())
        assertEquals(Resultado.Ok(DadosComunidade()), s.comunidade(listOf("CM:060419")))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun fimDaPausaEAProximaMeiaNoiteUtc() {
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), fimDaPausa(Instant.parse("2026-11-10T21:00:00Z")))
        assertEquals(Instant.parse("2026-07-16T00:00:00Z"), fimDaPausa(Instant.parse("2026-07-15T23:30:00Z")))
        assertEquals(Instant.parse("2026-07-16T00:00:00Z"), fimDaPausa(Instant.parse("2026-07-15T00:00:00Z").plusSeconds(1)))
    }
}
