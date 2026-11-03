package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.emel.EstacaoGira
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class InicioScreenshotTest {
    @get:Rule val regra = createComposeRule()

    // O banner fica por cima do mapa, que o Robolectric não desenha: no lugar dele fica um retângulo.
    @Composable
    private fun Banner(aoRetomar: () -> Unit = {}, aoDescartar: () -> Unit = {}) {
        Box(Modifier.width(360.dp).background(Color(0xFFDDE5F0)).padding(12.dp)) { AvisoViagem(R.string.viagem_interrompida, aoRetomar, aoDescartar = aoDescartar) }
    }

    @Test fun inicio_interrompida() = captureRoboImage("src/test/screenshots/inicio_interrompida.png") {
        TemaParagemCerta(ModoTema.CLARO) { Banner() }
    }

    // O cartão da estação GIRA por cima do mapa (que o Robolectric não desenha); a de referência grava-se no CI.
    @Test fun cartao_gira_claro() = captureRoboImage("src/test/screenshots/cartao_gira_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            Box(Modifier.width(360.dp).background(Color(0xFFDDE5F0)).padding(12.dp)) {
                CartaoGira(EstacaoGira("105", "CC Vasco da Gama", 38.768548, -9.096222), aoAbrirApp = {}, aoFechar = {})
            }
        }
    }

    @Test fun tocarRetomaEDescartarDescarta() {
        val pedidos = mutableListOf<String>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Banner(aoRetomar = { pedidos += "retomar" }, aoDescartar = { pedidos += "descartar" }) }
        }
        regra.onNodeWithText("Viagem interrompida — toca para retomar").performClick()
        regra.onNodeWithText("Descartar").performClick()
        assertEquals(listOf("retomar", "descartar"), pedidos)
    }

    @Test fun viagemEmCursoAbreESemDescartar() {
        var abriu = 0
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { AvisoViagem(R.string.viagem_em_curso, aoAbrir = { abriu++ }) }
        }
        regra.onNodeWithText("Viagem em curso — toca para ver").performClick()
        regra.onNodeWithText("Descartar").assertDoesNotExist()
        assertEquals(1, abriu)
    }

    @Test fun comLetraA200AindaSeVeEToca() {
        regra.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) { TemaParagemCerta(ModoTema.CLARO) { Banner() } }
        }
        regra.onNodeWithText("Viagem interrompida — toca para retomar").assertIsDisplayed()
        regra.onNodeWithText("Descartar").assertIsDisplayed()
    }
}
