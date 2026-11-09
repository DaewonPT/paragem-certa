package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CabecalhoAzulejoTest {
    @get:Rule val regra = createComposeRule()

    @Test fun otituloEUmCabecalhoEOVoltarTemAlvoGrandeEChamaOCallback() {
        var voltou = 0
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { CabecalhoAzulejo("Ajuda", { voltou++ }) } }
        regra.onNodeWithText("Ajuda").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, voltou)
    }

    @Test fun cabecalhoClaro() {
        captureRoboImage("src/test/screenshots/cabecalho_claro.png") {
            TemaParagemCerta(ModoTema.CLARO) { CabecalhoAzulejo("Definições", {}, Modifier.width(360.dp)) }
        }
    }

    @Test fun cabecalhoEscuro() {
        captureRoboImage("src/test/screenshots/cabecalho_escuro.png") {
            TemaParagemCerta(ModoTema.ESCURO) { CabecalhoAzulejo("Definições", {}, Modifier.width(360.dp)) }
        }
    }
}
