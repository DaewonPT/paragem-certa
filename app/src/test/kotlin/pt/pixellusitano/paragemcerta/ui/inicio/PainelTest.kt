package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.espresso.Espresso
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
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
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.EstadoFicheiro
import pt.pixellusitano.paragemcerta.dados.FicheiroDoServidor
import pt.pixellusitano.paragemcerta.dados.HttpComEtag
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.rotas.MINHA_POSICAO
import pt.pixellusitano.paragemcerta.ui.rotas.rememberGuardarLocal
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

    private fun repositorio(http: Http): Repositorio {
        val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        bds += bd
        return Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { Instant.parse("2026-10-26T05:00:00Z") })
    }

    private fun catalogoLinhas() = FicheiroDoServidor(pasta.root, "linhas.json", HttpComEtag({ Resposta(200, fixture("linhas.json"), "\"v1\"") }), "https://exemplo.pt/dados/linhas.json", ler = ::lerCatalogo)

    private fun painel(blocos: List<BlocoLinha>, aoAbrirLinha: (LinhaFavorita) -> Unit = {}) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                emptyList(), emptyList(), emptyMap(), agora, {}, {}, Modifier.width(360.dp),
                linhasFavoritas = blocos, aoAbrirLinha = aoAbrirLinha,
            )
        }
    }

    @Test fun titulosDasSecoesSaoCabecalhos() {
        painel(emptyList())
        regra.onNodeWithText("Favoritos").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        regra.onNodeWithText("Perto de ti").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun blocoDaLinhaFavoritaComDuasChegadas() {
        val b = blocoDaLinha(f3708, noTerminal, existe = true, nomeParagem = "Cais do Sodré (Terminal)")
        assertEquals(f3708, b.favorita)
        assertEquals("Cais do Sodré (Terminal)", b.paragem)
        // Só as do sentido da favorita: a 3711 da mesma paragem fica de fora.
        assertEquals(listOf(5L, 15L, 30L), minutos(b.estado!!.chegadas))
        assertEquals(noTerminal.atualizadoEm, b.estado.atualizadoEm)

        painel(listOf(b))
        regra.onNodeWithText("3708 → $costa em Cais do Sodré (Terminal)").assertIsDisplayed()
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
            regra.onAllNodesWithText("3708 → $costa em Cais do Sodré (Terminal)").fetchSemanticsNodes().isNotEmpty() &&
                regra.onAllNodesWithText("Cais do Sodré (Terminal)").fetchSemanticsNodes().isNotEmpty()
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
        regra.onNodeWithText("999 → Algés em Cais Sodré").assertIsDisplayed()
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
        regra.onNodeWithText("Az → Santa Apolónia em Colégio Militar/Luz").assertIsDisplayed()
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)
        // Mantém as ações: tocar abre a linha.
        regra.onNodeWithText("Az → Santa Apolónia em Colégio Militar/Luz").performClick()
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

        assertEquals(EstadoFicheiro.ACarregar, linhas.estado.value)
        // A paragem ainda não tem nome (não está no Room nem no catálogo): a linha aparece sem "em …".
        regra.onNodeWithText("Az → Santa Apolónia").assertIsDisplayed()
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)

        runBlocking { linhas.aoAbrir() }
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Az → Santa Apolónia em Colégio Militar/Luz").fetchSemanticsNodes().isNotEmpty() }
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)
    }

    @Test fun soComLinhasFavoritasNaoMostraOVazio() {
        painel(listOf(blocoDaLinha(f3708, noTerminal, true)))
        regra.onNodeWithText("Toca na estrela de uma paragem, de uma linha ou de um sítio para o veres aqui").assertDoesNotExist()
        regra.onNodeWithText("3708 → $costa").assertIsDisplayed()
    }

    private val convite = "Toca para escolher a morada"
    private val dica = "Toca na estrela de uma paragem, de uma linha ou de um sítio para o veres aqui"

    /** O cartão que convida a escolher a morada da Casa ou do Trabalho ([nome]). */
    private fun convite(nome: String) = hasText(nome) and hasText(convite)

    private fun painelComLocais(locais: List<BlocoLocal>, aoDefinirLocal: (String) -> Unit) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                emptyList(), emptyList(), emptyMap(), agora, {}, {}, Modifier.width(360.dp),
                locais = locais, aoDefinirLocal = aoDefinirLocal,
            )
        }
    }

    @Test fun semCasaNemTrabalhoConvidaAEscolherAMorada() {
        val pedidos = mutableListOf<String>()
        painelComLocais(emptyList()) { pedidos += it }
        val casa = regra.onNode(convite("Casa"))
        val trabalho = regra.onNode(convite("Trabalho"))
        // Um só botão por cartão, do tamanho dos outros, que o TalkBack lê "Casa, Toca para escolher a morada" (sem o símbolo).
        casa.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertHeightIsAtLeast(48.dp)
        assertEquals(listOf("Casa", convite), casa.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        // Lado a lado numa só fila (a Casa primeiro) e baixos: no painel fechado não empurram as paragens para fora.
        val c = casa.getBoundsInRoot()
        val t = trabalho.getBoundsInRoot()
        assertEquals(c.top, t.top)
        assertTrue(c.right <= t.left)
        assertTrue("${c.height} ${t.height}", c.height <= 72.dp && t.height <= 72.dp)
        // Sem mais nada guardado, a dica de como guardar continua.
        regra.onNodeWithText(dica).assertIsDisplayed()

        casa.performClick()
        trabalho.performClick()
        assertEquals(listOf("CASA", "TRABALHO"), pedidos)
    }

    @Test fun comLetraGrandeOsConvitesFicamUmPorBaixoDoOutro() {
        // Lado a lado, a 200% o nome ficava "Trab…": com letra grande cada convite tem a largura toda.
        regra.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) {
                    PainelConteudo(emptyList(), emptyList(), emptyMap(), agora, {}, {}, Modifier.width(360.dp), locais = emptyList())
                }
            }
        }
        val c = regra.onNode(convite("Casa")).getBoundsInRoot()
        val t = regra.onNode(convite("Trabalho")).getBoundsInRoot()
        assertTrue("${c.bottom} ${t.top}", c.bottom <= t.top)
        assertEquals(c.width, t.width)
    }

    @Test fun soComCasaMostraACasaEConvidaParaOTrabalho() {
        val pedidos = mutableListOf<String>()
        val casa = LocalGuardado(1, "Casa", Lugar("Casa", 38.72, -9.14, TipoLugar.LOCAL), "CASA", 0)
        painelComLocais(listOf(BlocoLocal(casa, EstadoLocal.SEM_POSICAO))) { pedidos += it }
        regra.onNodeWithText("Casa").assertIsDisplayed() // o cartão de sempre, com a sugestão quando há posição
        regra.onAllNodesWithText(convite).assertCountEquals(1)
        regra.onNodeWithText(dica).assertDoesNotExist()
        // Sozinho na fila, o convite ocupa a largura toda, como o cartão da Casa por cima.
        val trabalho = regra.onNode(convite("Trabalho")).getBoundsInRoot()
        assertEquals(regra.onNodeWithText("Casa").getBoundsInRoot().width, trabalho.width)
        assertTrue("${trabalho.height}", trabalho.height <= 72.dp)

        regra.onNode(convite("Trabalho")).performClick()
        assertEquals(listOf("TRABALHO"), pedidos)
    }

    @Test fun antesDeLerOsLocaisNaoConvidaNemDaADica() {
        // Nos primeiros instantes ainda não se sabe se há Casa: um toque no convite substituiria a que já está guardada.
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                PainelConteudo(emptyList(), emptyList(), emptyMap(), agora, {}, {}, Modifier.width(360.dp), locaisCarregados = false)
            }
        }
        regra.onAllNodesWithText(convite).assertCountEquals(0)
        regra.onNodeWithText(dica).assertDoesNotExist()
    }

    /** O painel e a escolha da morada ligados como no Início: o convite abre a escolha no ecrã todo e quem guarda é o ecrã de baixo. */
    private fun painelComEscolha(repo: Repositorio) {
        val linhas = catalogoLinhas()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                var definir by remember { mutableStateOf<String?>(null) }
                val guardar = rememberGuardarLocal(repo)
                val tipo = definir
                if (tipo == null) Painel(repo, null, {}, {}, catalogo = linhas, aoDefinirLocal = { definir = it })
                else DefinirLocalEcra(repo, tipo, guardar, aoFechar = { definir = null })
            }
        }
    }

    @Test fun escolherACasaGuardaUmaSoCasaNoSitioEscolhido() {
        val repo = repositorio(HttpPorUrl())
        val cafe = Lugar("Café Central", 38.7101, -9.1402, TipoLugar.LOCAL)
        runBlocking { repo.guardarLocal("Café Central", cafe, "OUTRO") }
        painelComEscolha(repo)

        regra.onNode(convite("Casa")).performClick()
        regra.onNodeWithText("Onde é a tua casa?").assertIsDisplayed()
        // Os sítios guardados estão na lista: escolhe-se o café.
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Café Central").fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText("Café Central").performClick()

        // De volta ao painel, com a Casa no lugar do convite.
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Casa").fetchSemanticsNodes().isNotEmpty() }
        regra.onAllNodes(convite("Casa")).assertCountEquals(0)
        assertEquals("Guardado nos favoritos", ShadowToast.getTextOfLatestToast())
        val guardados = runBlocking { repo.locais().first() }
        val casas = guardados.filter { it.tipo == "CASA" }
        assertEquals(1, casas.size)
        assertEquals("Casa", casas[0].nome)
        assertEquals(cafe.lat, casas[0].lugar.lat, 1e-9)
        assertEquals(cafe.lon, casas[0].lugar.lon, 1e-9)
        assertEquals(2, guardados.size) // o café continua lá
    }

    @Test fun guardarPeloMenuComoOQueSeEstaAEscolherFechaAEscolha() {
        val repo = repositorio(HttpPorUrl("geocode" to listOf({ Resposta(200, "[]") })))
        runBlocking { repo.guardarLocal("Café Central", Lugar("Café Central", 38.7101, -9.1402, TipoLugar.LOCAL), "OUTRO") }
        painelComEscolha(repo)
        regra.onNode(convite("Casa")).performClick()
        regra.onNodeWithText("Onde é a tua casa?").performTextInput("Café")
        fun guardarComo(texto: String) {
            regra.waitUntil(5_000) { regra.onAllNodesWithContentDescription("Mais opções").fetchSemanticsNodes().isNotEmpty() }
            regra.onNodeWithContentDescription("Mais opções").performClick()
            regra.onNodeWithText(texto).performClick()
        }

        // Guardar como trabalho não é o que se está a escolher: a escolha da casa continua aberta.
        guardarComo("Guardar como trabalho")
        regra.waitForIdle()
        regra.onNodeWithText("Onde é a tua casa?").assertIsDisplayed()
        // Guardar como casa é escolher: volta ao painel.
        guardarComo("Guardar como casa")
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Casa").fetchSemanticsNodes().isNotEmpty() }
        regra.onAllNodesWithText("Onde é a tua casa?").assertCountEquals(0)
        assertEquals(setOf("CASA", "TRABALHO", "OUTRO"), runBlocking { repo.locais().first() }.map { it.tipo }.toSet())
    }

    @Test fun voltarDaEscolhaNaoGuardaNemSaiDaApp() {
        val repo = repositorio(HttpPorUrl())
        painelComEscolha(repo)

        regra.onNode(convite("Trabalho")).performClick()
        regra.onNodeWithText("Onde é o teu trabalho?").assertIsDisplayed()
        Espresso.pressBack() // o voltar do sistema; falha se fechar a app
        regra.waitForIdle()
        regra.onNode(convite("Trabalho")).assertIsDisplayed()

        // O ← do ecrã também volta sem guardar.
        regra.onNode(convite("Casa")).performClick()
        regra.onNodeWithText("Onde é a tua casa?").assertIsDisplayed()
        regra.onNodeWithContentDescription("Voltar").performClick()
        regra.onNode(convite("Casa")).assertIsDisplayed()
        assertTrue(runBlocking { repo.locais().first() }.isEmpty())
    }

    // Do Saldanha: a Casa e o café ficam a mais de 500 m, por isso a sugestão pede rotas.
    private val saldanha = 38.7369 to -9.1450
    private val sugeridas = mutableListOf<Triple<LocalGuardado, Itinerario, Pair<Double, Double>>>()

    private fun painelComSugestoes(repo: Repositorio) {
        val linhas = catalogoLinhas()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Painel(repo, saldanha, {}, {}, catalogo = linhas, aoSugerir = { l, i, de -> sugeridas += Triple(l, i, de) })
            }
        }
    }

    // Sem paragens perto: a única coisa pedida à rede é a rota.
    private fun repositorioComRotas() = repositorio(
        HttpPorUrl("plan" to listOf({ Resposta(200, fixture("transitous-plan.json")) }), "map/stops" to listOf({ Resposta(200, "[]") })),
    )

    @Test fun sugestaoDaCasaVaiParaOWidget() {
        val repo = repositorioComRotas()
        runBlocking { repo.guardarLocal("Casa", Lugar("Casa", 38.7058, -9.1431, TipoLugar.MORADA), "CASA") }
        painelComSugestoes(repo)
        regra.waitUntil(5_000) { sugeridas.isNotEmpty() }
        regra.waitForIdle()

        val casa = runBlocking { repo.locais().first() }.single()
        val de = Lugar(MINHA_POSICAO, saldanha.first, saldanha.second, TipoLugar.POSICAO)
        val maisCedo = (runBlocking { repo.planear(de, casa.lugar, agora, false, Filtros()) } as Resultado.Ok).valor.itinerarios.minBy { it.chegada }
        assertEquals(1, sugeridas.size)
        assertEquals(casa, sugeridas[0].first)
        assertEquals(maisCedo, sugeridas[0].second)
        assertEquals(saldanha, sugeridas[0].third)
    }

    @Test fun widgetQueFalhaNaoDeitaAbaixoOPainel() {
        val repo = repositorioComRotas()
        runBlocking {
            repo.guardarLocal("Casa", Lugar("Casa", 38.7058, -9.1431, TipoLugar.MORADA), "CASA")
            repo.guardarLocal("Trabalho", Lugar("Trabalho", 38.7101, -9.1402, TipoLugar.MORADA), "TRABALHO")
        }
        val linhas = catalogoLinhas()
        var tentativas = 0
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Painel(repo, saldanha, {}, {}, catalogo = linhas, aoSugerir = { _, _, _ -> tentativas++; throw IllegalStateException("o widget rebentou") })
            }
        }
        // As duas sugestões aparecem: a falha do widget na Casa não impede a do Trabalho nem o cartão.
        regra.waitUntil(5_000) { regra.onAllNodes(hasText("sai às", substring = true)).fetchSemanticsNodes().size == 2 }
        regra.waitForIdle()
        assertEquals(2, tentativas)
    }

    @Test fun widgetLentoNaoAtrasaOTrabalho() {
        val repo = repositorioComRotas()
        runBlocking {
            repo.guardarLocal("Casa", Lugar("Casa", 38.7058, -9.1431, TipoLugar.MORADA), "CASA")
            repo.guardarLocal("Trabalho", Lugar("Trabalho", 38.7101, -9.1402, TipoLugar.MORADA), "TRABALHO")
        }
        val linhas = catalogoLinhas()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Painel(repo, saldanha, {}, {}, catalogo = linhas, aoSugerir = { _, _, _ -> awaitCancellation() })
            }
        }
        regra.waitUntil(5_000) { regra.onAllNodes(hasText("sai às", substring = true)).fetchSemanticsNodes().size == 2 }
    }

    @Test fun sugestaoDeOutroSitioNaoVaiParaOWidget() {
        val repo = repositorioComRotas()
        runBlocking { repo.guardarLocal("Café Central", Lugar("Café Central", 38.7101, -9.1402, TipoLugar.LOCAL), "OUTRO") }
        painelComSugestoes(repo)
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Café Central").fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText("Café Central").performScrollTo().performClick()
        // O toque calcula a sugestão (a hora de saída aparece no cartão), mas o widget só quer a Casa e o Trabalho.
        regra.waitUntil(5_000) { regra.onAllNodes(hasText("sai às", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        regra.waitForIdle()
        assertTrue(sugeridas.isEmpty())
    }

    // O Início sai e volta (outro separador, uma paragem aberta) e a posição muda com um GPS novo.
    private var mostrar by mutableStateOf(true)
    private var aqui by mutableStateOf(saldanha)

    private fun painelQueSaiEVolta(repo: Repositorio) {
        val linhas = catalogoLinhas()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                if (mostrar) Painel(repo, aqui, {}, {}, catalogo = linhas, aoSugerir = { l, i, de -> sugeridas += Triple(l, i, de) })
            }
        }
    }

    private fun httpComRotas() = HttpPorUrl("plan" to listOf({ Resposta(200, fixture("transitous-plan.json")) }), "map/stops" to listOf({ Resposta(200, "[]") }))
    private fun planos(http: HttpPorUrl) = http.pedidos.count { "plan" in it }
    private fun comCasa(repo: Repositorio) = repo.also {
        runBlocking { it.guardarLocal("Casa", Lugar("Casa", 38.7058, -9.1431, TipoLugar.MORADA), "CASA") }
    }

    /** A resposta do plan só chega quando o [portao] abrir (o pedido conta logo, como um que já saiu). */
    private class HttpComPortao(val http: HttpPorUrl, val portao: CompletableDeferred<Unit>) : Http {
        override suspend fun get(url: String): Resposta = http.get(url).also { if ("plan" in url) portao.await() }
    }

    private val saiAs = hasText("sai às", substring = true)

    @Test fun voltarAoInicioNaoPedeARotaOutraVez() {
        val http = httpComRotas()
        painelQueSaiEVolta(comCasa(repositorio(http)))
        regra.waitUntil(5_000) { regra.onAllNodes(saiAs).fetchSemanticsNodes().isNotEmpty() }

        mostrar = false
        regra.waitForIdle()
        mostrar = true
        regra.waitForIdle()
        regra.onNode(saiAs).assertIsDisplayed()
        assertEquals(http.pedidos.toString(), 1, planos(http))
    }

    @Test fun voltarAoInicioNaoPedeAsChegadasOutraVez() {
        val http = HttpPorUrl(
            "arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
        )
        val repo = repositorio(http)
        runBlocking {
            repo.atualizarRede()
            repo.alternarFavorita(repo.paragem(terminal)!!)
        }
        http.pedidos.clear()
        val linhas = catalogoLinhas()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { if (mostrar) Painel(repo, null, {}, {}, catalogo = linhas) } }
        // O relógio do ecrã é o verdadeiro e a fixture é de 26/10: chegado o estado, já passaram todas.
        val semChegadas = { regra.onAllNodesWithText("Não há chegadas nas próximas 2 horas").fetchSemanticsNodes().isNotEmpty() }
        // Espera pelo estado (e não pelo pedido), para as chegadas já estarem guardadas quando o Início sai.
        regra.waitUntil(5_000, semChegadas)

        mostrar = false
        regra.waitForIdle()
        mostrar = true
        regra.waitUntil(5_000, semChegadas)
        regra.waitForIdle()
        assertEquals(http.pedidos.toString(), 1, http.pedidos.count { "arrivals" in it })
    }

    /** Com o plan da Casa a meio, o GPS dá uma posição [dLat] graus a norte do Saldanha; depois o plan responde. */
    private fun moverAMeio(dLat: Double): HttpPorUrl {
        val http = httpComRotas()
        val portao = CompletableDeferred<Unit>()
        painelQueSaiEVolta(comCasa(repositorio(HttpComPortao(http, portao))))
        regra.waitUntil(5_000) { planos(http) == 1 }
        aqui = saldanha.first + dLat to saldanha.second
        regra.waitForIdle()
        portao.complete(Unit)
        regra.waitUntil(5_000) { sugeridas.isNotEmpty() }
        regra.waitForIdle()
        return http
    }

    @Test fun posicaoNovaPertoNaoPedeOutraVez() {
        val http = moverAMeio(0.00045) // ~50 m
        assertEquals(http.pedidos.toString(), 1, planos(http))
        assertEquals(1, sugeridas.size)
    }

    @Test fun posicaoAMaisDe200mPedeOutraVez() {
        val http = moverAMeio(0.0027) // ~300 m
        assertEquals(http.pedidos.toString(), 2, planos(http))
        // Só a rota de onde se está agora chega ao widget: a de 300 m atrás já não serve.
        assertEquals(listOf(saldanha.first + 0.0027 to saldanha.second), sugeridas.map { it.third })
    }

    @Test fun sairAMeioGuardaASugestao() {
        val http = httpComRotas()
        val portao = CompletableDeferred<Unit>()
        painelQueSaiEVolta(comCasa(repositorio(HttpComPortao(http, portao))))
        regra.waitUntil(5_000) { planos(http) == 1 }

        mostrar = false
        regra.waitForIdle()
        portao.complete(Unit)
        regra.waitUntil(5_000) { sugeridas.size == 1 }
        mostrar = true
        regra.waitForIdle()
        regra.onNode(saiAs).assertIsDisplayed()
        assertEquals(http.pedidos.toString(), 1, planos(http))
    }
}
