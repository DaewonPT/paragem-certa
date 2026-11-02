package pt.pixellusitano.paragemcerta.ui.sobre

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test fun termosEPoliticaDePrivacidade_temLigacao() {
        val abertos = mutableListOf<String>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { SobreEcra(versao = "0.1.0", aoAbrir = { abertos += it }) } }
        regra.onNodeWithText("Termos").performScrollTo().assertHasClickAction().performClick()
        regra.onNodeWithText("Política de privacidade").performScrollTo().assertHasClickAction().performClick()
        assertEquals(listOf("https://paragemcerta.pixellusitano.com/termos", "https://paragemcerta.pixellusitano.com/privacidade"), abertos)
    }

    @Test fun fonteTransitous_temLigacao() {
        var aberto: String? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { SobreEcra(versao = "0.1.0", aoAbrir = { aberto = it }) } }
        regra.onNodeWithText("Transitous — horários e rotas").assertHasClickAction().performClick()
        assertEquals("https://transitous.org/sources", aberto)
    }

    @Test fun fonteGeoApi_temLigacao() {
        var aberto: String? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { SobreEcra(versao = "0.1.0", aoAbrir = { aberto = it }) } }
        regra.onNodeWithText("Código postal via GEO API PT (geoapi.pt) · dados CTT/INE").assertHasClickAction().performClick()
        assertEquals("https://geoapi.pt", aberto)
    }
}
