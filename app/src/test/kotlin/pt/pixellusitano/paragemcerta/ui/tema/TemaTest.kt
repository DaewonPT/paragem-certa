package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemaTest {
    @get:Rule val regra = createComposeRule()

    private fun ler(modo: ModoTema): Pair<ColorScheme, Shapes> {
        lateinit var resultado: Pair<ColorScheme, Shapes>
        regra.setContent {
            TemaParagemCerta(modo) { Ler { resultado = it } }
        }
        regra.waitForIdle()
        return resultado
    }

    @Composable
    private fun Ler(aoLer: (Pair<ColorScheme, Shapes>) -> Unit) {
        aoLer(MaterialTheme.colorScheme to MaterialTheme.shapes)
    }

    @Test fun claro() {
        val (c, f) = ler(ModoTema.CLARO)
        assertEquals(Azulejo.Marfim, c.background)
        assertEquals(Azulejo.Branco, c.surface)
        assertEquals(Azulejo.Contorno, c.outlineVariant)
        assertEquals(Azulejo.Branco, c.surfaceContainer)
        assertEquals(Azulejo.Branco, c.surfaceContainerHigh)
        assertEquals(Azulejo.Branco, c.surfaceVariant)
        assertEquals(Color(0xFFDCE6F7), c.secondaryContainer)
        assertEquals(Azulejo.Cinza, c.outline)
        assertEquals(Azulejo.Vermelho, c.error)
        assertEquals(Azulejo.Branco, c.onError)
        assertEquals(Color(0xFFDCE6F7), c.primaryContainer)
        assertEquals(Azulejo.Tinta, c.onPrimaryContainer)
        assertEquals(RoundedCornerShape(14.dp), f.medium)
        assertEquals(RoundedCornerShape(20.dp), f.large)
    }

    @Test fun escuro() {
        val (c, _) = ler(ModoTema.ESCURO)
        assertEquals(Azulejo.NoiteSuperficie, c.surfaceContainer)
        assertEquals(Azulejo.ContornoNoite, c.outlineVariant)
        assertEquals(Azulejo.LinhaEscura, c.secondaryContainer)
        assertEquals(Color(0xFF33467A), c.outlineVariant)
        assertEquals(Azulejo.CinzaNoite, c.outline)
        assertEquals(Azulejo.VermelhoNoite, c.error)
        assertEquals(Azulejo.Noite, c.onError)
        assertEquals(Azulejo.LinhaEscura, c.primaryContainer)
        assertEquals(Azulejo.TextoNoite, c.onPrimaryContainer)
    }
}
