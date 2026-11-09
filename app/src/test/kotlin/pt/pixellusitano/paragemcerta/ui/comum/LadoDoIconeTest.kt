package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LadoDoIconeTest {
    @get:Rule val regra = createComposeRule()

    private fun lado(letra: Float): Dp {
        var lado = 0.dp
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = letra)) { lado = ladoDoIcone(16) }
        }
        regra.waitForIdle()
        return lado
    }

    @Test fun comLetraNormalEIgualAosDp() = assertEquals(16f, lado(1f).value, 0.01f)

    // A escala da letra do Android não é linear, por isso só se pede que cresça bem acima dos 16 dp.
    @Test fun cresceComALetra() = assertTrue(lado(2f).value > 24f)
}
