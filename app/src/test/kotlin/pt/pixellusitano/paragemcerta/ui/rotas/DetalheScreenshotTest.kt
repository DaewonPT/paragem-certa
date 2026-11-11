package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class DetalheScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    // A mesma rota com mais uma etapa de autocarro, esta da Carris Metropolitana (a 3.ª): só ela tem "Onde está o autocarro?".
    private val rotaCm = rota.etapas[1].let { b ->
        val cm = b.copy(linhaNome = "3708", paragens = listOf(ParagemEtapa("pt-Carris-Metropolitana_320047", b.de.nome, b.de.lat, b.de.lon, b.partida)))
        rota.copy(etapas = rota.etapas.take(2) + cm + rota.etapas.drop(2))
    }

    // O MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo, no escuro com o fundo do estilo escuro do OpenFreeMap.
    @Composable
    private fun Detalhe(
        aoComecar: () -> Unit = {}, itinerario: Itinerario = rota, seguida: Int? = null,
        estado: EstadoAutocarro = EstadoAutocarro.A_PROCURAR, aoVerAutocarro: (Int) -> Unit = {},
    ) = DetalheRotaConteudo(
        itinerario, de = "Cais do Sodré", para = "Oriente",
        mapa = { Box(Modifier.fillMaxSize().background(if (LocalEscuro.current) Color(0xFF0C0C0C) else Color(0xFFDDE5F0))) },
        aoVoltar = {}, aoComecar = aoComecar, aoPartilhar = {}, aoAbrirTvde = {}, modifier = Modifier.width(360.dp),
        seguida = seguida, estadoAutocarro = estado, aoVerAutocarro = aoVerAutocarro,
    )

    @Test fun detalhe_claro() = captureRoboImage("src/test/screenshots/detalhe_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Detalhe() }
    }

    @Test fun detalhe_escuro() = captureRoboImage("src/test/screenshots/detalhe_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Detalhe() }
    }

    @Test fun detalhe_letra200() = captureRoboImage("src/test/screenshots/detalhe_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Detalhe() }
        }
    }

    @Test fun comecarViagemEOBotaoPrincipalAntesDePartilharRota() {
        var comecou = 0
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Detalhe(aoComecar = { comecou++ }) } }
        regra.onNodeWithText("Começar viagem").performScrollTo()
        val comecar = regra.onNodeWithText("Começar viagem").fetchSemanticsNode().boundsInRoot.top
        val partilhar = regra.onNodeWithText("Partilhar rota").fetchSemanticsNode().boundsInRoot.top
        assertTrue(comecar < partilhar)
        regra.onNodeWithText("Começar viagem").performClick()
        assertEquals(1, comecou)
    }

    @Test fun comLetraA200OSeloFicaPorCimaENenhumaPalavraPartePeloMeio() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) { Detalhe() }
            }
        }
        val no = regra.onAllNodes(SemanticsMatcher("etapa de transporte") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Autocarro") && "→" in it.text } == true
        }, useUnmergedTree = true).onFirst().fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        no.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        val primeira = resultados.first().let { it.layoutInput.text.text.substring(0, it.getLineEnd(0)) }
        assertTrue("a primeira linha é \"$primeira\"", primeira.startsWith("Autocarro"))
    }

    @Test fun soAEtapaDaCmTemOBotaoEOToqueDaOIndiceDela() {
        var tocada: Int? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Detalhe(itinerario = rotaCm, aoVerAutocarro = { tocada = it }) } }
        regra.onAllNodesWithText("Onde está o autocarro?").assertCountEquals(1)
        val botao = regra.onNodeWithText("Onde está o autocarro?").performScrollTo()
        val alvo = botao.fetchSemanticsNode().touchBoundsInRoot
        val minimo = with(regra.density) { 48.dp.toPx() }
        assertTrue("alvo de ${alvo.width} x ${alvo.height} px", alvo.width >= minimo - 0.5f && alvo.height >= minimo - 0.5f)
        botao.performClick()
        assertEquals(2, tocada)
        // O leitor de ecrã ouve a linha na ação ("Toca duas vezes para ver o 3708 no mapa"), e a ação faz o mesmo que o toque.
        tocada = null
        assertEquals("ver o 3708 no mapa", botao.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        botao.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, tocada)
        // Sem etapa seguida não há texto de estado.
        regra.onAllNodesWithText("A procurar o autocarro…").assertCountEquals(0)
    }

    @Test fun oEstadoDoAutocarroApareceNaEtapaSeguidaEOLeitorDeEcraDiz() {
        var estado by mutableStateOf(EstadoAutocarro.A_PROCURAR)
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Detalhe(itinerario = rotaCm, seguida = 2, estado = estado) } }
        val procurar = regra.onNodeWithText("A procurar o autocarro…").performScrollTo().fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, procurar.config.getOrNull(SemanticsProperties.LiveRegion))
        estado = EstadoAutocarro.SEM_POSICAO
        regra.onNodeWithText("Não há posição ao vivo deste autocarro.").assertExists()
        estado = EstadoAutocarro.NO_MAPA
        regra.onNodeWithText("No mapa, em cima.").assertExists()
    }

    @Test fun comLetraA200OBotaoDoAutocarroNaoPartePalavras() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) { Detalhe(itinerario = rotaCm) }
            }
        }
        val no = regra.onNodeWithText("Onde está o autocarro?", useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        no.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        val r = resultados.first()
        // Cada linha que não é a última acaba num espaço: a quebra é entre palavras.
        for (l in 0 until r.lineCount - 1) {
            assertEquals("a linha $l é \"${r.layoutInput.text.text.substring(r.getLineStart(l), r.getLineEnd(l))}\"", ' ', r.layoutInput.text.text[r.getLineEnd(l) - 1])
        }
    }

    @Test fun detalhe_autocarro() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Detalhe(itinerario = rotaCm, seguida = 2) } }
        regra.onNodeWithText("A procurar o autocarro…").performScrollTo()
        regra.onRoot().captureRoboImage("src/test/screenshots/detalhe_autocarro.png")
    }

    @Test fun detalhe_comecar() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Detalhe() } }
        regra.onNodeWithText("Abrir app de TVDE").performScrollTo()
        regra.onRoot().captureRoboImage("src/test/screenshots/detalhe_comecar.png")
    }
}
