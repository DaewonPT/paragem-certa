package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.CatalogoLinhas
import pt.pixellusitano.paragemcerta.dados.EstadoCatalogo
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.HttpComEtag
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.IOException
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp")
class PainelTest {
    @get:Rule val regra = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    private val agora = Instant.parse("2026-10-27T14:00:00Z")
    private val catalogo = lerCatalogo(fixture("linhas.json"))
    private val terminal = RefParagem(Fonte.CM, "060419")
    private val l3708 = RefLinha(Fonte.CM, "3708")
    private val costa = "Costa da Caparica (Terminal)"
    private val lisboa = "Lisboa (C. Sodré)"
    private val f3708 = LinhaFavorita(l3708, 1, terminal, "3708", costa, 0)
    private val bds = mutableListOf<BaseDados>()

    @After fun fechar() = bds.forEach { it.close() }

    private fun ch(minutos: Long, sentido: Int?, destino: String, linha: String = "3708") = Chegada(
        Fonte.CM, linha, linha, destino, agora.plusSeconds(minutos * 60), null, null, null, Modo.AUTOCARRO, null, null, sentido,
    )

    private fun estado(vararg c: Chegada) = EstadoChegadas(c.toList(), agora, semLigacao = false, tempoRealIndisponivel = false)
    private fun minutos(lista: List<Chegada>) = lista.map { Duration.between(agora, it.programada).toMinutes() }

    // O terminal do Cais do Sodré: a 3708 chega aqui (sentido 0, para Lisboa) e parte daqui (sentido 1, para a Costa da Caparica).
    private val noTerminal = estado(
        ch(3, 0, lisboa), ch(5, 1, costa), ch(12, 0, lisboa), ch(15, 1, costa), ch(30, 1, costa), ch(8, 1, "Fonte da Telha", linha = "3711"),
    )

    private fun repositorio(http: HttpPorUrl): Repositorio {
        val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        bds += bd
        return Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { Instant.parse("2026-10-26T05:00:00Z") })
    }

    private fun catalogoLinhas() = CatalogoLinhas(pasta.root, HttpComEtag({ Resposta(200, fixture("linhas.json"), "\"v1\"") }), "https://exemplo.pt")

    private fun painel(blocos: List<BlocoLinha>, aoAbrirLinha: (LinhaFavorita) -> Unit = {}) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                emptyList(), emptyList(), emptyMap(), agora, {}, {}, Modifier.width(360.dp),
                linhasFavoritas = blocos, aoAbrirLinha = aoAbrirLinha,
            )
        }
    }

    @Test fun blocoDaLinhaFavoritaComDuasChegadas() {
        val b = blocoDaLinha(f3708, noTerminal, existe = true, nomeParagem = "Cais do Sodré (Terminal)")
        assertEquals(f3708, b.favorita)
        assertEquals("Cais do Sodré (Terminal)", b.paragem)
        // Só as do sentido da favorita: a 3711 da mesma paragem fica de fora.
        assertEquals(listOf(5L, 15L, 30L), minutos(b.estado!!.chegadas))
        assertEquals(noTerminal.atualizadoEm, b.estado.atualizadoEm)

        painel(listOf(b))
        regra.onNodeWithText("★ 3708 → $costa em Cais do Sodré (Terminal)").assertIsDisplayed()
        // Três no sentido, mas o bloco mostra duas (a árvore sem juntar: o bloco, que se toca, junta as descrições das chegadas).
        assertEquals(2, regra.onAllNodes(hasContentDescription("Linha 3708 para", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun blocoNoTerminalSoDoSentido() {
        // Parte daqui para a Costa da Caparica (1) e chega aqui vinda de Lisboa (0): cada favorita vê só o seu sentido.
        assertEquals(listOf(5L, 15L, 30L), minutos(blocoDaLinha(f3708, noTerminal, true).estado!!.chegadas))
        val chegadas = blocoDaLinha(f3708.copy(sentido = 0, destino = lisboa), noTerminal, true).estado!!.chegadas
        assertEquals(listOf(3L, 12L), minutos(chegadas))
        assertEquals(setOf(lisboa), chegadas.map { it.destino }.toSet())
    }

    @Test fun semSentidoFiltraPeloDestino() {
        // As chegadas guardadas por uma versão antiga não trazem o sentido.
        val antigas = estado(ch(3, null, lisboa), ch(5, null, costa), ch(9, null, "COSTA DA CAPARICA (TERMINAL)"))
        assertEquals(listOf(5L, 9L), minutos(blocoDaLinha(f3708, antigas, true).estado!!.chegadas))
    }

    @Test fun linhaDoCatalogoSeparaOsSentidosPorDestinoQuandoONumeroNaoServe() {
        // A CP: o Transitous manda directionId 0 em todas as viagens (o gerador inventou os sentidos) e só o destino decide.
        val cp = catalogo.linhas.first { it.operador.startsWith("pt-Comboios") }
        val estacao = cp.sentidos.first { it.sentido == 1 }.paragens.first().ref
        val f = LinhaFavorita(cp.ref, 1, estacao, cp.nome, "Cascais", 0)
        fun comboio(minutos: Long, destino: String, rota: String = cp.rotas.first()) = Chegada(
            Fonte.TRANSITOUS, rota, "Linha de Cascais", destino, agora.plusSeconds(minutos * 60), null, "t$minutos", null,
            Modo.COMBOIO, null, null, 0,
        )
        val e = estado(
            comboio(2, "Cascais"), comboio(4, "Cais do Sodré"), comboio(6, "Oeiras"), comboio(8, "Cascais", rota = "pt-Outro_1"),
        )
        assertEquals(listOf(2L, 6L), minutos(blocoDaLinha(f, e, true, linha = cp).estado!!.chegadas))
    }

    @Test fun mesmaParagemUmSoPedido() {
        val http = HttpPorUrl(
            "arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
        )
        val repo = repositorio(http)
        runBlocking {
            repo.atualizarRede()
            val paragem = repo.paragem(terminal)!!
            // A mesma paragem é favorita e é a habitual de uma linha favorita.
            repo.alternarFavorita(paragem)
            repo.alternarLinhaFavorita(l3708, 1, costa, paragem, "3708")
        }
        http.pedidos.clear()
        val linhas = catalogoLinhas()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Painel(repo, null, {}, {}, catalogo = linhas) } }
        regra.waitUntil(5_000) {
            regra.onAllNodesWithText("★ 3708 → $costa em Cais do Sodré (Terminal)").fetchSemanticsNodes().isNotEmpty() &&
                regra.onAllNodesWithText("★ Cais do Sodré (Terminal)").fetchSemanticsNodes().isNotEmpty()
        }
        regra.waitForIdle()
        assertEquals(http.pedidos.toString(), 1, http.pedidos.count { "arrivals" in it })
    }

    @Test fun favoritaDeLinhaQueDesapareceu() {
        // Catálogo carregado e sem a linha: não existe. O mesmo para a CM, com as linhas dela no Room.
        val sumida = RefLinha(Fonte.TRANSITOUS, "pt-Carris:999")
        assertEquals(false, linhaExiste(sumida, emptyList(), catalogo))
        assertEquals(true, linhaExiste(RefLinha(Fonte.TRANSITOUS, "pt-Carris:15E"), emptyList(), catalogo))
        val cm = listOf(Linha(Fonte.CM, "1001", "1001", "Alcochete - Montijo", null, null, Modo.AUTOCARRO))
        assertEquals(false, linhaExiste(l3708, cm, null))
        assertEquals(true, linhaExiste(RefLinha(Fonte.CM, "1001"), cm, null))

        val abertas = mutableListOf<LinhaFavorita>()
        val f = LinhaFavorita(sumida, 0, RefParagem(Fonte.TRANSITOUS, "pt-Carris_3803"), "999", "Algés", 0)
        painel(listOf(BlocoLinha(f, "Cais Sodré", null, existe = false)), aoAbrirLinha = { abertas += it })
        regra.onNodeWithText("★ 999 → Algés em Cais Sodré").assertIsDisplayed()
        regra.onNodeWithText("Esta linha já não existe.").assertIsDisplayed()
        regra.onAllNodesWithText("A carregar…").assertCountEquals(0)
        // Só se pode tirar (nos Favoritos): no painel tocar nela não abre nada.
        regra.onNodeWithText("Esta linha já não existe.").performClick()
        assertTrue(abertas.isEmpty())
    }

    @Test fun favoritaSemCatalogoNaoDizQueDesapareceu() {
        // Sem catálogo (a carregar ou falhado) ou sem linhas da CM no Room não se sabe: nunca "não existe".
        val az = catalogo.linhas.first { it.nome == "Az" }
        assertNull(linhaExiste(az.ref, emptyList(), null))
        assertNull(linhaExiste(l3708, emptyList(), null))

        val abertas = mutableListOf<LinhaFavorita>()
        val f = LinhaFavorita(az.ref, 0, az.sentidos.first().paragens.first().ref, "Az", "Santa Apolónia", 0)
        painel(listOf(BlocoLinha(f, "Colégio Militar/Luz", null, existe = null)), aoAbrirLinha = { abertas += it })
        regra.onNodeWithText("★ Az → Santa Apolónia em Colégio Militar/Luz").assertIsDisplayed()
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)
        // Mantém as ações: tocar abre a linha.
        regra.onNodeWithText("★ Az → Santa Apolónia em Colégio Militar/Luz").performClick()
        assertEquals(listOf(f), abertas)
    }

    @Test fun telemovelNovoMostraACarregarEDepoisAsChegadasDaLinha() {
        // Ligado de ponta a ponta: o catálogo ainda não chegou, depois chega com a linha.
        val repo = repositorio(HttpPorUrl("stoptimes" to listOf({ throw IOException("sem rede") })))
        val az = catalogo.linhas.first { it.nome == "Az" }
        val colegio = az.sentidos.first { it.sentido == 0 }.paragens.first()
        runBlocking { repo.alternarLinhaFavorita(az.ref, 0, "Santa Apolónia", colegio, "Az") }
        val linhas = catalogoLinhas()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Painel(repo, null, {}, {}, catalogo = linhas) } }

        assertEquals(EstadoCatalogo.ACarregar, linhas.estado.value)
        // A paragem ainda não tem nome (não está no Room nem no catálogo): a linha aparece sem "em …".
        regra.onNodeWithText("★ Az → Santa Apolónia").assertIsDisplayed()
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)

        runBlocking { linhas.aoAbrir() }
        regra.waitUntil(5_000) { regra.onAllNodesWithText("★ Az → Santa Apolónia em Colégio Militar/Luz").fetchSemanticsNodes().isNotEmpty() }
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)
    }

    @Test fun soComLinhasFavoritasNaoMostraOVazio() {
        painel(listOf(blocoDaLinha(f3708, noTerminal, true)))
        regra.onNodeWithText("Toca na ☆ de uma paragem, de uma linha ou de um sítio para o veres aqui").assertDoesNotExist()
        regra.onNodeWithText("★ 3708 → $costa").assertIsDisplayed()
    }
}
