package pt.pixellusitano.paragemcerta.ui.sobre

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class SobreScreenshotTest {
    @get:Rule val regra = createComposeRule()

    @Test fun sobre_claro() = captureRoboImage("src/test/screenshots/sobre_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { SobreEcra(versao = "0.1.0", aoAbrir = {}) }
    }

    @Test fun fonteTransitous_temLigacao() {
        var aberto: String? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { SobreEcra(versao = "0.1.0", aoAbrir = { aberto = it }) } }
        regra.onNodeWithText("Transitous — horários e rotas").assertHasClickAction().performClick()
        assertEquals("https://transitous.org/sources", aberto)
    }
}
