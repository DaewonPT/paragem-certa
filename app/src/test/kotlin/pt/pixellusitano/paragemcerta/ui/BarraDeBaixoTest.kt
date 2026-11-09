package pt.pixellusitano.paragemcerta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class BarraDeBaixoTest {
    @get:Rule val regra = createComposeRule()

    @Test fun quatroRotulosEOAtivoMarcado() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { BarraDeBaixo(Separador.LINHAS, {}) } }
        regra.onNodeWithText("Linhas").assertIsSelected()
        listOf("Início", "Rotas", "Menu").forEach { regra.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test fun tocarEscolheOSeparador() {
        val escolhidos = mutableListOf<Separador>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { BarraDeBaixo(Separador.INICIO, { escolhidos += it }) } }
        regra.onNodeWithText("Rotas").performClick()
        assertEquals(listOf(Separador.ROTAS), escolhidos)
    }

    @Test fun alvosComPeloMenos48dp() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { BarraDeBaixo(Separador.INICIO, {}) } }
        listOf("Início", "Rotas", "Linhas", "Menu").forEach { regra.onNodeWithText(it).assertHeightIsAtLeast(48.dp) }
    }

    @Composable private fun Barra(modo: ModoTema, letra: Float = 1f) = TemaParagemCerta(modo) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = letra)) {
            BarraDeBaixo(Separador.INICIO, {})
        }
    }

    @Test fun barraClaro() = captureRoboImage("src/test/screenshots/barra_claro.png") { Barra(ModoTema.CLARO) }
    @Test fun barraEscuro() = captureRoboImage("src/test/screenshots/barra_escuro.png") { Barra(ModoTema.ESCURO) }
    @Test fun barraLetra200() = captureRoboImage("src/test/screenshots/barra_letra200.png") { Barra(ModoTema.CLARO, 2f) }
}
