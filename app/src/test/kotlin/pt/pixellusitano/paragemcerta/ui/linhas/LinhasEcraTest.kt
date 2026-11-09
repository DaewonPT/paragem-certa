package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import pt.pixellusitano.paragemcerta.core.linhas.LinhaCatalogo
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.CatalogoLinhas
import pt.pixellusitano.paragemcerta.dados.EstadoCatalogo
import pt.pixellusitano.paragemcerta.dados.HttpComEtag
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

private const val A_CARREGAR = "A carregar as linhas…"
private const val SEM_CATALOGO = "As linhas dos outros operadores ainda não chegaram. Abre a app com rede para as carregar."

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class LinhasEcraTest {
    @get:Rule val regra = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    // A fixture tem a 15E da Carris (elétrico), a Linha de Cascais da CP (sem nome curto) e a Azul do Metro.
    private val catalogo = lerCatalogo(fixture("linhas.json"))
    private val l1009 = Linha(Fonte.CM, "1009", "1009", "Lisboa (Sete Rios) - Setúbal", "#C61D23", "#FFFFFF", Modo.AUTOCARRO)
    private val l3708 = Linha(Fonte.CM, "3708", "3708", "Cais do Sodré - Cacilhas", "#C61D23", "#FFFFFF", Modo.AUTOCARRO)
    private val cm = listOf(l3708, l1009)
    private val refCm1009 = RefLinha(Fonte.CM, "1009")
    private val refAz = RefLinha(Fonte.TRANSITOUS, "pt-Metro-Lisboa:Az")
    private val bds = mutableListOf<BaseDados>()

    @After fun fechar() = bds.forEach { it.close() }

    private fun nomes(grupos: List<GrupoLinhas>) = grupos.flatMap { g -> g.operadores.flatMap { (_, ls) -> ls.map { it.nome } } }

    @Composable
    private fun Ecra(
        grupos: List<GrupoLinhas>, estado: EstadoCatalogo = EstadoCatalogo.Pronto(catalogo), filtro: String = "", modo: Modo? = null,
        aoFiltrar: (String) -> Unit = {}, aoEscolherModo: (Modo?) -> Unit = {}, aoAbrirLinha: (RefLinha) -> Unit = {},
    ) = LinhasConteudo(
        grupos, filtro, aoFiltrar, modo, aoEscolherModo, estado, aoAbrirLinha, modifier = Modifier.width(360.dp),
    )

    @Test fun agrupaPorMeioEOperador() {
        val grupos = agruparLinhas(cm, catalogo, "", null)
        // Metro, Comboio, Elétrico, Barco, Autocarro, Outro: só os meios que têm linhas.
        assertEquals(
            listOf(
                Modo.METRO to listOf("Metro de Lisboa" to listOf("Az")),
                Modo.COMBOIO to listOf("CP" to listOf("Cais do Sodré - Cascais")),
                Modo.ELETRICO to listOf("Carris" to listOf("15E")),
                Modo.AUTOCARRO to listOf("Carris Metropolitana" to listOf("1009", "3708")),
            ),
            grupos.map { g -> g.modo to g.operadores.map { (operador, linhas) -> operador to linhas.map { it.nome } } },
        )
    }

    @Test fun osNumerosOrdenamSeComoNumeros() {
        val mais = listOf(
            Linha(Fonte.CM, "99", "99", "", null, null, Modo.AUTOCARRO),
            Linha(Fonte.CM, "1009", "1009", "", null, null, Modo.AUTOCARRO),
            Linha(Fonte.CM, "15", "15", "", null, null, Modo.AUTOCARRO),
        )
        // "15" < "99" < "1009" e não pela ordem das letras; a 15E da Carris é de outro operador.
        assertEquals(listOf("15", "99", "1009"), nomes(agruparLinhas(mais, null, "", Modo.AUTOCARRO)))
    }

    @Test fun procuraPorNomeCurtoELongo() {
        assertEquals(listOf("15E"), nomes(agruparLinhas(cm, catalogo, "15", null)))
        assertEquals(listOf("1009"), nomes(agruparLinhas(cm, catalogo, "1009", null)))
        // Pelo nome longo, sem acentos nem maiúsculas: "Pç. Figueira - Algés", "Azul", "Cais do Sodré - Cascais".
        assertEquals(listOf("15E"), nomes(agruparLinhas(cm, catalogo, "ALGES", null)))
        assertEquals(listOf("Az"), nomes(agruparLinhas(cm, catalogo, "azul", null)))
        assertEquals(listOf("Cais do Sodré - Cascais", "3708"), nomes(agruparLinhas(cm, catalogo, "sodre", null)))
        assertTrue(agruparLinhas(cm, catalogo, "zzz", null).isEmpty())
    }

    @Test fun filtroPorMeio() {
        assertEquals(listOf(Modo.METRO), agruparLinhas(cm, catalogo, "", Modo.METRO).map { it.modo })
        assertEquals(listOf("1009", "3708"), nomes(agruparLinhas(cm, catalogo, "", Modo.AUTOCARRO)))
        assertTrue(agruparLinhas(cm, catalogo, "", Modo.BARCO).isEmpty())
        // O meio e a procura juntam-se.
        assertTrue(agruparLinhas(cm, catalogo, "15", Modo.METRO).isEmpty())
    }

    @Test fun mesmoNomeEmDoisOperadoresSaoLinhasDiferentes() {
        val refOutra = RefLinha(Fonte.TRANSITOUS, "pt-Barreiro:1009")
        val outra = LinhaCatalogo(refOutra, "pt-Barreiro", "1009", "Barreiro - Lavradio", Modo.AUTOCARRO, null, null, setOf("pt-Barreiro_1009_0"), emptyList())
        val dois = catalogo.copy(operadores = catalogo.operadores + ("pt-Barreiro" to "TCB Barreiro"), linhas = catalogo.linhas + outra)
        val grupos = agruparLinhas(listOf(l1009), dois, "1009", null)
        assertEquals(
            listOf("Carris Metropolitana" to listOf(refCm1009), "TCB Barreiro" to listOf(refOutra)),
            grupos.single().operadores.map { (operador, linhas) -> operador to linhas.map { it.ref } },
        )

        val abertas = mutableListOf<RefLinha>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(grupos, EstadoCatalogo.Pronto(dois), aoAbrirLinha = { abertas += it }) }
        }
        val nos = regra.onAllNodesWithText("1009")
        nos[0].performClick()
        nos[1].performClick()
        assertEquals(listOf(refCm1009, refOutra), abertas)
    }

    @Test fun semCatalogoMostraACm() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, null, "", null), EstadoCatalogo.Falhou) } }
        regra.onNodeWithText("1009").assertIsDisplayed()
        regra.onNodeWithText("3708").assertIsDisplayed()
        regra.onNodeWithText(SEM_CATALOGO).assertIsDisplayed()
        regra.onNodeWithText(A_CARREGAR).assertDoesNotExist()
        regra.onNodeWithText("Az").assertDoesNotExist()
    }

    @Test fun carregarFalharEProntoNaoSeConfundem() {
        var estado: EstadoCatalogo by mutableStateOf(EstadoCatalogo.ACarregar)
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, null, "", null), estado) } }
        regra.onNodeWithText(A_CARREGAR).assertIsDisplayed()
        regra.onNodeWithText(SEM_CATALOGO).assertDoesNotExist()
        // A CM já se vê enquanto o resto carrega.
        regra.onNodeWithText("1009").assertIsDisplayed()

        estado = EstadoCatalogo.Falhou
        regra.waitForIdle()
        regra.onNodeWithText(SEM_CATALOGO).assertIsDisplayed()
        regra.onNodeWithText(A_CARREGAR).assertDoesNotExist()

        estado = EstadoCatalogo.Pronto(catalogo)
        regra.waitForIdle()
        regra.onNodeWithText(SEM_CATALOGO).assertDoesNotExist()
        regra.onNodeWithText(A_CARREGAR).assertDoesNotExist()
    }

    /** Telemóvel novo: sem catálogo e sem linhas da CM no Room. O que chega (catálogo e rede) preenche o separador sozinho. */
    @Test @Config(qualifiers = "w360dp-h1400dp")
    fun primeiraAberturaMostraACarregarEDepoisAsLinhas() {
        val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        bds += bd
        val http = HttpPorUrl(
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
        )
        val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
        val linhas = CatalogoLinhas(pasta.root, HttpComEtag({ Resposta(200, fixture("linhas.json"), "\"v1\"") }), "https://exemplo.pt")
        val abertas = mutableListOf<RefLinha>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { LinhasEcra(repositorio, linhas, aoAbrirLinha = { abertas += it }) } }

        regra.onNodeWithText(A_CARREGAR).assertIsDisplayed()
        regra.onNodeWithText("1001").assertDoesNotExist()
        regra.onNodeWithText("Az").assertDoesNotExist()

        runBlocking { linhas.aoAbrir() }
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Az").fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText(A_CARREGAR).assertDoesNotExist()
        regra.onNodeWithText("15E").assertIsDisplayed()

        // A rede da CM chega depois: as linhas entram pelo Flow do Room, sem ninguém pedir à tabela.
        runBlocking { repositorio.atualizarRede() }
        regra.waitUntil(5_000) { regra.onAllNodesWithText("1001").fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText("1001").performClick()
        assertEquals(listOf(RefLinha(Fonte.CM, "1001")), abertas)
    }

    @Test fun semResultadosDizOQueAconteceu() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, catalogo, "zzz", null), filtro = "zzz") } }
        regra.onNodeWithText("Nenhuma linha com esse nome.").assertIsDisplayed()
    }

    @Test fun tocarNumaLinhaAbreOEcraDaLinha() {
        val abertas = mutableListOf<RefLinha>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, catalogo, "", null), aoAbrirLinha = { abertas += it }) }
        }
        regra.onNodeWithText("Az").performClick()
        regra.onNodeWithText("1009").performClick()
        assertEquals(listOf(refAz, refCm1009), abertas)
    }

    @Test fun caixaEBotoesDeMeioChamamOsFiltros() {
        val pedidos = mutableListOf<String>()
        var modo: Modo? by mutableStateOf(Modo.METRO)
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Ecra(
                    agruparLinhas(cm, catalogo, "", modo), modo = modo,
                    aoFiltrar = { pedidos += "texto=$it" }, aoEscolherModo = { pedidos += "meio=$it"; modo = it },
                )
            }
        }
        regra.onNodeWithText("Procurar uma linha").assertIsDisplayed()
        regra.onNode(hasSetTextAction()).performTextInput("15")
        regra.onNode(hasText("Metro") and isSelectable()).assertIsSelected()
        regra.onNodeWithText("Todos").assertIsNotSelected()
        regra.onNode(hasText("Comboio") and isSelectable()).performClick()
        regra.onNodeWithText("Todos").performClick()
        assertEquals(listOf("texto=15", "meio=COMBOIO", "meio=null"), pedidos)
        regra.onNodeWithText("Todos").assertIsSelected()
    }

    @Test fun caixaComTextoAindaDizParaQueServe() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, catalogo, "15", null), filtro = "15") } }
        // Com só um placeholder, o TalkBack perde o nome da caixa assim que se escreve.
        regra.onNode(hasSetTextAction() and hasText("Procurar uma linha")).assertExists()
    }

    @Test fun linhas_claro() = captureRoboImage("src/test/screenshots/linhas_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, catalogo, "", null)) }
    }

    @Test fun linhas_escuro() = captureRoboImage("src/test/screenshots/linhas_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Ecra(agruparLinhas(cm, catalogo, "", null)) }
    }

    @Test fun linhas_letra200() = captureRoboImage("src/test/screenshots/linhas_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(agruparLinhas(cm, catalogo, "", null)) }
        }
    }
}
