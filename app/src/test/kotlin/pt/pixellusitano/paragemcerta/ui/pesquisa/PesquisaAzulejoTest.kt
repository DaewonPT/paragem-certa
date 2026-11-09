package pt.pixellusitano.paragemcerta.ui.pesquisa

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.Grupo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PesquisaAzulejoTest {
    @get:Rule val regra = createComposeRule()

    private val ehTitulo = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val sitio = Lugar("Rua Augusta", 38.71, -9.137, TipoLugar.MORADA, "Lisboa")
    private val paragem = Paragem(RefParagem(Fonte.CM, "060777"), "Algés (Jardim)", 38.7011, -9.2312, false)
    private val grupo = Grupo(paragem.nome, paragem.lat, paragem.lon, listOf(paragem))

    private fun abrir(texto: String = "algés", letra: Float = 1f) = regra.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = letra)) {
            TemaParagemCerta(ModoTema.CLARO) {
                PesquisaConteudo(
                    texto, listOf(ResultadoPesquisa.DeLugar(sitio), ResultadoPesquisa.DeEstacao(grupo)), false, {}, {}, {}, {}, {},
                )
            }
        }
    }

    @Test fun campoTemORotuloMesmoComTexto() {
        abrir("algés")
        regra.onNode(hasSetTextAction()).assert(
            SemanticsMatcher("tem o rótulo e o texto") { no ->
                val rotulo = no.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                rotulo.contains("Sítio, rua ou código postal") && no.config.getOrNull(SemanticsProperties.EditableText)?.text == "algés"
            },
        )
    }

    @Test fun voltarTem48dpENaFaixa() {
        abrir()
        val voltar = regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val campo = regra.onNode(hasSetTextAction())
        // O voltar e o campo estão na mesma faixa, por cima dos resultados.
        assertTrue(voltar.getBoundsInRoot().top < regra.onNodeWithText("Sítios e moradas").getBoundsInRoot().top)
        assertTrue(campo.getBoundsInRoot().bottom <= regra.onNodeWithText("Sítios e moradas").getBoundsInRoot().top)
        // À esquerda do campo está a faixa cobalto, não o fundo marfim.
        val centro = campo.fetchSemanticsNode().boundsInRoot.center.y.toInt()
        val pixel = regra.onRoot().captureToImage().toPixelMap()[2, centro]
        assertTrue("fora da faixa: $pixel", pixel.red < 0.3f && pixel.blue > 0.5f)
    }

    // Com letra a 200 %: o voltar continua com 48 dp e à vista, e o campo fica à vista, ao lado ou por baixo dele, sem lhe passar por cima.
    @Test fun comLetraA200OVoltarEOCampoContinuamAVistaESemSobrepor() {
        abrir(letra = 2f)
        val voltar = regra.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val campo = regra.onNode(hasSetTextAction()).assertIsDisplayed()
        val v = voltar.fetchSemanticsNode().boundsInRoot
        val c = campo.fetchSemanticsNode().boundsInRoot
        assertTrue("o campo $c sobrepõe o voltar $v", c.left >= v.right || c.top >= v.bottom)
    }

    @Test fun titulosDasSeccoesSaoCabecalhos() {
        abrir()
        regra.onNodeWithText("Sítios e moradas").assert(ehTitulo)
        regra.onNodeWithText("Paragens").assert(ehTitulo)
    }

    @Test fun estrelaContinuaAEncontrarPelaDescricao() {
        abrir()
        regra.onNodeWithContentDescription("Guardar nos favoritos").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
