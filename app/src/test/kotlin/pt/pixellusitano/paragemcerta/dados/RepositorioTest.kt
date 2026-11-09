package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.IOException
import java.time.Instant

/** Http falso por URL: cada pedido usa a próxima resposta da fila do primeiro prefixo que coincide. */
class HttpPorUrl(vararg filas: Pair<String, List<() -> Resposta>>) : Http {
    private val filas = filas.map { it.first to it.second.toMutableList() }
    val pedidos = mutableListOf<String>()
    override suspend fun get(url: String): Resposta {
        pedidos += url
        val fila = filas.first { url.contains(it.first) }.second
        return (if (fila.size > 1) fila.removeAt(0) else fila.first())()
    }
}

fun fixture(nome: String): String =
    requireNotNull(HttpPorUrl::class.java.getResource("/fixtures/$nome")) { "fixture $nome em falta" }.readText()

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositorioTest {
    private val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
    private val manha = Instant.parse("2026-10-26T05:00:00Z")
    private val terminal = RefParagem(Fonte.CM, "060419")

    private fun repo(http: Http) = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { manha })

    @After fun fechar() = bd.close()

    @Test fun cacheSemRede() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }, { throw IOException("sem rede") }))
        repo(http).chegadas(terminal).test {
            val primeira = awaitItem()
            assertEquals(3, primeira.chegadas.size)
            assertFalse(primeira.semLigacao)
            val segunda = awaitItem()
            assertEquals(primeira.chegadas, segunda.chegadas)
            assertTrue(segunda.semLigacao)
            assertEquals(primeira.atualizadoEm, segunda.atualizadoEm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun chegadasAgoraFazUmaSoConsultaDaFonteDaParagem() = runTest {
        val http = HttpPorUrl(
            "arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
            "stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes.json")) }),
        )
        val cm = repo(http).chegadasAgora(terminal) as Resultado.Ok
        assertEquals(3, cm.valor.size)
        assertEquals(1, http.pedidos.size)
        assertTrue(http.pedidos.single().contains("arrivals/by_stop/060419"))
        val outra = repo(http).chegadasAgora(RefParagem(Fonte.TRANSITOUS, "pt-Carris_3803")) as Resultado.Ok
        assertEquals(listOf("201"), outra.valor.map { it.linhaNome })
        assertTrue(http.pedidos.last().contains("stoptimes"))
    }

    @Test fun chegadasAgoraDevolveAFalhaSemRepetir() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(503, "") }))
        val r = repo(http).chegadasAgora(terminal)
        assertEquals(Resultado.Falha(503, limite = true), r)
        assertEquals(1, http.pedidos.size)
    }

    @Test fun limiteDePedidosEsperaAntesDeRepetir() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(429, "") }))
        repo(http).chegadas(terminal).test {
            assertTrue(awaitItem().tempoRealIndisponivel)
            advanceTimeBy(19_000)
            assertEquals(1, http.pedidos.size)
            advanceTimeBy(2_000)
            awaitItem()
            assertEquals(2, http.pedidos.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun paragemSemChegadas() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, "[]") }))
        repo(http).chegadas(terminal).test {
            val e = awaitItem()
            assertTrue(e.chegadas.isEmpty())
            assertFalse(e.semLigacao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun pesquisaSemAcentosNemMaiusculas() = runTest {
        val http = HttpPorUrl(
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
            "geocode" to listOf({ Resposta(200, "[]") }),
        )
        val r = repo(http)
        assertTrue(r.atualizarRede())
        assertTrue(r.pesquisar("sodre").any { it.nome == "Cais do Sodré (Terminal)" })
        assertEquals("#C61D23", r.linhaCm("3708")?.cor)
    }

    @Test fun paragemDoCatalogoQueFaltaFicaGuardada() = runTest {
        // Uma estação da CP que o telemóvel nunca viu (o Transitous só chega à base por paragensNaZona e pesquisar).
        val cascais = Paragem(RefParagem(Fonte.TRANSITOUS, "pt-CP_94-30007"), "Cascais", 38.6979, -9.4215, null)
        val r = repo(semRede)
        assertNull(r.paragem(cascais.ref))
        r.guardarParagemSeFalta(cascais)
        assertEquals(cascais, r.paragem(cascais.ref))
    }

    @Test fun paragemQueJaEstaGuardadaNaoEPisada() = runTest {
        // A da CM leva as linhas que lá passam: guardar por cima apagava-as.
        val guardada = ParagemEntidade(
            terminal.chave, "CM", terminal.id, "Cais do Sodré (Terminal)", normalizar("Cais do Sodré (Terminal)"), 38.7058, -9.1448, null, "3708,1009",
        )
        bd.paragens().guardar(listOf(guardada))
        repo(semRede).guardarParagemSeFalta(Paragem(terminal, "Cais do Sodré", 0.0, 0.0, true))
        assertEquals(guardada, bd.paragens().porChave(terminal.chave))
    }

    @Test fun mostraOGuardadoAntesDeAPrimeiraRespostaChegar() = runTest {
        val rapido = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))
        repo(rapido).chegadas(terminal).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        val lento = object : Http {
            override suspend fun get(url: String): Resposta { kotlinx.coroutines.delay(30_000); return Resposta(200, "[]") }
        }
        repo(lento).chegadas(terminal).test {
            val primeira = awaitItem()
            assertEquals(3, primeira.chegadas.size)
            assertFalse(primeira.semLigacao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val semRede = object : Http {
        override suspend fun get(url: String): Resposta { kotlinx.coroutines.delay(30_000); return Resposta(200, "[]") }
    }

    @Test fun chegadasGuardadasGuardamOSentidoEOPadrao() = runTest {
        repo(HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))).chegadas(terminal).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        repo(semRede).chegadas(terminal).test {
            val guardadas = awaitItem().chegadas
            assertEquals(listOf(0, 1, 0), guardadas.map { it.sentido })
            assertEquals(listOf("3708_0_1", "3708_0_2", "3708_0_1"), guardadas.map { it.padrao })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun guardadasEstragadasNaoPartem() = runTest {
        bd.chegadas().guardar(ChegadasGuardadasEntidade(terminal.chave, "{isto não é json", manha.epochSecond))
        assertNull(repo(semRede).chegadasGuardadas(terminal, manha))
        // Nem o ecrã da paragem, que lê as mesmas: fica sem nada guardado até a rede responder.
        repo(HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))).chegadas(terminal).test {
            assertEquals(3, awaitItem().chegadas.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun chegadasGuardadasSoTrazemAsQueAindaVem() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))
        repo(http).atualizarGuardadas(terminal, manha)
        val (lista, em) = repo(semRede).chegadasGuardadas(terminal, manha)!!
        assertEquals(3, lista.size)
        assertEquals(manha, em)
        assertTrue(repo(semRede).chegadasGuardadas(terminal, manha.plusSeconds(86_400))!!.first.isEmpty())
    }

    @Test fun nadaGuardadoDaNull() = runTest { assertNull(repo(semRede).chegadasGuardadas(terminal, manha)) }

    @Test fun atualizarGuardadasSoGuardaOQueVeio() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }, { Resposta(500, "") }))
        assertEquals(3, (repo(http).atualizarGuardadas(terminal, manha) as Resultado.Ok).valor.size)
        val depois = manha.plusSeconds(10)
        assertEquals(Resultado.Falha(500, limite = false), repo(http).atualizarGuardadas(terminal, depois))
        assertEquals("a falha não toca no que estava guardado", manha, repo(semRede).chegadasGuardadas(terminal, manha)!!.second)
    }

    @Test fun chegadasGuardadasAntesDoSentidoAindaLeemSe() = runTest {
        // O que a 0.3 guardou não tem "sentido" nem "padrao".
        val antiga = """[{"fonte":"CM","linhaId":"3708","linhaNome":"3708","destino":"Lisboa (C. Sodré)","programada":1792992300,"prevista":null,""" +
            """"idViagem":null,"idVeiculo":null,"modo":"AUTOCARRO","cor":null,"acessivel":null}]"""
        bd.chegadas().guardar(ChegadasGuardadasEntidade(terminal.chave, antiga, manha.epochSecond))
        repo(semRede).chegadas(terminal).test {
            val c = awaitItem().chegadas.single()
            assertEquals("Lisboa (C. Sodré)", c.destino)
            assertNull(c.sentido)
            assertNull(c.padrao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun planearDaOsAcentosQueABaseEAPesquisaConhecem() = runTest {
        // A CP escreve "Cais do Sodre" e "Alcantara -  Mar"; a base conhece o Cais do Sodré (a 100 m) e a pessoa escolheu o destino pela pesquisa.
        bd.paragens().guardar(
            listOf(
                ParagemEntidade(
                    "TRANSITOUS:pt-Transtejo-Soflusa_5", "TRANSITOUS", "pt-Transtejo-Soflusa_5", "Cais do Sodré", normalizar("Cais do Sodré"),
                    38.705, -9.1455, null,
                ),
            ),
        )
        val http = HttpPorUrl("plan" to listOf({ Resposta(200, fixture("transitous-plan-cascais.json")) }))
        val de = Lugar("A minha posição", 38.7062, -9.1442, TipoLugar.POSICAO)
        val para = Lugar("Alcântara - Mar", 38.7024, -9.1741, TipoLugar.PARAGEM)
        val rotas = (repo(http).planear(de, para, manha, false, Filtros()) as Resultado.Ok).valor.itinerarios
        val comboio = rotas[0].etapas.first { !it.aPe }
        assertEquals("Cais do Sodré", comboio.de.nome) // da base
        assertEquals(listOf("Cais do Sodré", "Santos", "Alcântara - Mar", "Belem", "Algés"), comboio.paragens.map { it.nome })
        // "Santos" e "Belem" não têm correspondência (e o "Algés" da rota seguinte corrige o "Alges"): ficam como vêm.
    }

    @Test fun planearSemNadaConhecidoDeixaOsNomesComoVem() = runTest {
        val http = HttpPorUrl("plan" to listOf({ Resposta(200, fixture("transitous-plan-cascais.json")) }))
        val de = Lugar("A minha posição", 38.7062, -9.1442, TipoLugar.POSICAO)
        val para = Lugar("Casa", 38.7022, -9.2292, TipoLugar.LOCAL)
        val rotas = (repo(http).planear(de, para, manha, false, Filtros()) as Resultado.Ok).valor.itinerarios
        assertEquals(listOf("Cais do Sodre", "Santos", "Alcantara - Mar", "Belem", "Algés"), rotas[0].etapas.first { !it.aPe }.paragens.map { it.nome })
    }

    @Test fun planearDaOsCursoresDasPaginas() = runTest {
        val http = HttpPorUrl("plan" to listOf({ Resposta(200, fixture("transitous-plan.json")) }))
        val de = Lugar("A minha posição", 38.7062, -9.1442, TipoLugar.POSICAO)
        val para = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)
        val plano = (repo(http).planear(de, para, manha, false, Filtros(), cursor = "LATER|1793503740") as Resultado.Ok).valor
        assertEquals("EARLIER|1793498820", plano.anterior)
        assertEquals("LATER|1793503740", plano.seguinte)
        assertTrue(http.pedidos.single().contains("pageCursor=LATER%7C1793503740"))
    }

    /** Uma etapa em veículo que sai da paragem [id] (null = a fonte não o dá) às [partida]. */
    private fun etapaEmVeiculo(id: String?, linha: String, destino: String?, partida: Instant) = Etapa(
        aPe = false, modo = Modo.AUTOCARRO, linhaNome = linha, linhaId = null, destino = destino,
        de = Lugar("Origem", 38.70, -9.14, TipoLugar.PARAGEM), para = Lugar("Destino", 38.75, -9.10, TipoLugar.PARAGEM),
        partida = partida, chegada = partida.plusSeconds(900), partidaProgramada = partida, aoVivo = false,
        paragensIntermedias = 3, desenho = emptyList(), cor = null,
        paragens = listOfNotNull(id?.let { ParagemEtapa(it, "Origem", 38.70, -9.14, partida) }),
    )

    @Test fun proximasPartidasDaCmUsamACm() = runTest {
        val http = HttpPorUrl(
            "arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
            "stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes.json")) }),
        )
        // A paragem do Transitous "pt-Carris-Metropolitana_360419" é a 060419 da Carris Metropolitana.
        val etapa = etapaEmVeiculo("pt-Carris-Metropolitana_360419", "3708", "Lisboa (C. Sodré)", Instant.parse("2026-10-26T05:25:00Z"))
        val partidas = repo(http).proximasPartidas(etapa)!!
        // A CM não separa por sentido (uma das três vai para a Costa da Caparica); ordem pela hora prevista ou, sem ela, a planeada.
        assertEquals(listOf(1792992540L, 1792992600L, 1792995900L), partidas.map { (it.prevista ?: it.programada).epochSecond })
        assertTrue(partidas.all { it.fonte == Fonte.CM && it.linhaNome == "3708" })
        assertTrue(http.pedidos.single().contains("arrivals/by_stop/060419"))
    }

    @Test fun proximasPartidasDeOutroOperadorUsamOTransitous() = runTest {
        val http = HttpPorUrl(
            "arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
            "stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes.json")) }),
        )
        val repositorio = repo(http)
        val etapa = etapaEmVeiculo("pt-Carris_3803", "201", "Cais Sodré", Instant.parse("2026-10-27T01:54:00Z"))
        val partidas = repositorio.proximasPartidas(etapa)!!
        assertEquals(listOf("201"), partidas.map { it.linhaNome })
        assertTrue(partidas.all { it.fonte == Fonte.TRANSITOUS })
        assertTrue(http.pedidos.single().contains("stoptimes?stopId=pt-Carris_3803"))
        // Outra linha na mesma paragem: pedido feito, mas nenhuma partida serve (lista vazia, não nulo).
        assertEquals(emptyList<Chegada>(), repositorio.proximasPartidas(etapa.copy(linhaNome = "999")))
    }

    @Test fun paragemRepetidaPedeUmaVez() = runTest {
        val http = HttpPorUrl("stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes.json")) }))
        val repositorio = repo(http)
        val etapa = etapaEmVeiculo("pt-Carris_3803", "201", "Cais Sodré", Instant.parse("2026-10-27T01:54:00Z"))
        // Três rotas a sair da mesma paragem (linha e hora diferentes): as chegadas pedem-se uma vez.
        repositorio.proximasPartidas(etapa, manha)
        repositorio.proximasPartidas(etapa.copy(linhaNome = "999"), manha.plusSeconds(10))
        repositorio.proximasPartidas(etapa.copy(partidaProgramada = etapa.partidaProgramada.plusSeconds(3600)), manha.plusSeconds(19))
        assertEquals(1, http.pedidos.size)
        // Passados 20 s já não servem: volta a perguntar.
        repositorio.proximasPartidas(etapa, manha.plusSeconds(21))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun semIdDeParagemDaNulo() = runTest {
        val http = HttpPorUrl("" to listOf({ Resposta(200, "[]") }))
        val repositorio = repo(http)
        assertNull(repositorio.proximasPartidas(etapaEmVeiculo(null, "201", null, manha)))
        assertNull(repositorio.proximasPartidas(etapaEmVeiculo(null, "201", null, manha).copy(paragens = listOf(ParagemEtapa(null, "Origem", 38.70, -9.14, manha)))))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun falhaDaFonteDaNulo() = runTest {
        val http = HttpPorUrl("stoptimes" to listOf({ Resposta(503, "") }))
        val etapa = etapaEmVeiculo("pt-Carris_3803", "201", "Cais Sodré", manha)
        assertNull(repo(http).proximasPartidas(etapa))
    }

    @Test fun paragemSemServicoNaoFicaIndisponivel() = runTest {
        val r = repo(HttpPorUrl("arrivals" to listOf({ Resposta(404, "[]") })))
        r.chegadas(terminal).test {
            val e = awaitItem()
            assertEquals(emptyList<Chegada>(), e.chegadas)
            assertFalse(e.tempoRealIndisponivel); assertFalse(e.semLigacao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cacheSoComChegadasPassadasNaoMostraAHoraAntiga() = runTest {
        repo(HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))).atualizarGuardadas(terminal, manha)
        val falha = HttpPorUrl("arrivals" to listOf({ Resposta(503, "") }))
        val depois = Repositorio(bd, CarrisMetropolitana(falha), Transitous(falha), GeoApi(falha), relogio = { manha.plusSeconds(86_400) })
        depois.chegadas(terminal).test {
            val e = awaitItem()
            assertTrue(e.tempoRealIndisponivel); assertNull(e.atualizadoEm); assertTrue(e.chegadas.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cmNaZonaDeixaDeForaAsParagensSemLinhas() = runTest {
        bd.paragens().guardar(listOf(
            ParagemEntidade("CM:1", "CM", "1", "Com linhas", normalizar("Com linhas"), 38.70, -9.14, null, "3708"),
            ParagemEntidade("CM:2", "CM", "2", "Sem linhas", normalizar("Sem linhas"), 38.70, -9.14, null, ""),
        ))
        assertEquals(listOf("CM:1"), bd.paragens().cmNaZona(38.69, -9.15, 38.71, -9.13).map { it.chave })
    }
}
