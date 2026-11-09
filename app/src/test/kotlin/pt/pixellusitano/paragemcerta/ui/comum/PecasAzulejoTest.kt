package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PecasAzulejoTest {
    @get:Rule val regra = createComposeRule()

    private val ehTitulo = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test fun cabecalhoSemVoltarNaoTemBotao() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { CabecalhoAzulejo("Menu", aoVoltar = null) } }
        regra.onNodeWithContentDescription("Voltar").assertDoesNotExist()
        regra.onNodeWithText("Menu").assert(ehTitulo)
    }

    @Composable private fun CabecalhoCompleto() = CabecalhoAzulejo(
        "Linha 15E", {}, subtitulo = "Algés - Praça da Figueira",
        acao = { Box(Modifier.testTag("acao").size(48.dp)) },
        antesDoTitulo = { Box(Modifier.testTag("antes").size(48.dp)) },
    )

    @Test fun cabecalhoComSubtituloEAcao() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { CabecalhoCompleto() } }
        regra.onNodeWithText("Linha 15E").assert(ehTitulo)
        regra.onNodeWithText("Algés - Praça da Figueira").assertExists()
        regra.onNodeWithTag("acao").assertExists()
        regra.onNodeWithTag("antes").assertExists()
        regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun cabecalhoComLetraA200() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) { CabecalhoCompleto() }
            }
        }
        val voltar = regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        regra.onNodeWithTag("acao").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val titulo = regra.onNodeWithText("Linha 15E").assertIsDisplayed()
        assertTrue(titulo.getBoundsInRoot().top >= voltar.getBoundsInRoot().bottom)
    }

    @Test fun campoNoEscuroUsaTinta() {
        regra.setContent { TemaParagemCerta(ModoTema.ESCURO) { CampoAzulejo("Rossio", {}, "Pesquisar") } }
        val nos = regra.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val resultados = mutableListOf<TextLayoutResult>()
        nos.forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados) }
        assertTrue("o campo não expõe o layout do texto", resultados.isNotEmpty())
        assertEquals(Azulejo.Tinta, resultados.first().layoutInput.style.color)
    }

    @Test fun rotuloDoCampoFicaComTexto() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { CampoAzulejo("Rossio", {}, "Pesquisar paragem") } }
        regra.onNode(hasSetTextAction()).assert(
            SemanticsMatcher("tem o rótulo e o texto") { no ->
                val texto = no.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                val editavel = no.config.getOrNull(SemanticsProperties.EditableText)?.text
                texto.contains("Pesquisar paragem") && editavel == "Rossio"
            },
        )
    }

    @Test fun bordaDoFiltroEscolhido() {
        lateinit var escolhido: BorderStroke
        lateinit var solto: BorderStroke
        var primaria = Color.Unspecified
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                escolhido = bordaDoFiltro(true)
                solto = bordaDoFiltro(false)
                primaria = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(1.dp, escolhido.width)
        assertEquals(primaria, (escolhido.brush as SolidColor).value)
        assertNotEquals(primaria, (solto.brush as SolidColor).value)
    }

    @Test fun tituloSecaoEhCabecalho() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { TituloSecao("Preferências") } }
        regra.onNodeWithText("Preferências").assert(ehTitulo)
    }
}
