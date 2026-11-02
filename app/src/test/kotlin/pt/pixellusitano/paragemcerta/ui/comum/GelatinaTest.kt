package pt.pixellusitano.paragemcerta.ui.comum

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GelatinaTest {
    @get:Rule val regra = createComposeRule()

    private fun assertEscala(t: Float, x: Float, y: Float) {
        val (sx, sy) = escalaGelatina(t)
        assertEquals("scaleX em t=$t", x, sx, 1e-4f)
        assertEquals("scaleY em t=$t", y, sy, 1e-4f)
    }

    @Test fun escalaNosSeisPontos() {
        assertEscala(0f, 1f, 1f)
        assertEscala(0.25f, 1.3f, 0.75f)
        assertEscala(0.45f, 0.82f, 1.2f)
        assertEscala(0.62f, 1.1f, 0.92f)
        assertEscala(0.80f, 0.97f, 1.03f)
        assertEscala(1f, 1f, 1f)
    }

    @Test fun escalaNumPontoIntermedioFicaEntreOsVizinhos() {
        // t = 0,35 é o meio de 0,25 (1,3×0,75) e 0,45 (0,82×1,2).
        assertEscala(0.35f, 1.06f, 0.975f)
    }

    @Test fun escalaForaDoIntervaloFicaNoRepouso() {
        assertEscala(-0.5f, 1f, 1f)
        assertEscala(2f, 1f, 1f)
    }

    @Test fun animacoesLigadasSegueARemocaoDeAnimacoesDoAndroid() {
        val contexto = RuntimeEnvironment.getApplication()
        assertTrue("sem a definição, as animações estão ligadas", animacoesLigadas(contexto))
        Settings.Global.putFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertFalse(animacoesLigadas(contexto))
        Settings.Global.putFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertTrue(animacoesLigadas(contexto))
    }

    // Um ícone de 100×40 dp: o efeito vê-se nos limites que o teste mede (o testTag fica por dentro do graphicsLayer).
    private fun montar(escolhido: Boolean) = mutableStateOf(escolhido).also { estado ->
        regra.mainClock.autoAdvance = false
        regra.setContent { Box(Modifier.size(100.dp, 40.dp).gelatina(estado.value).testTag("icone")) }
    }

    /** Avança fotograma a fotograma (um salto único só daria um fotograma ao efeito) e devolve (largura, altura) em cada um. */
    private fun fotogramas(ms: Int) = List(ms / 16) {
        regra.mainClock.advanceTimeByFrame()
        val caixa = regra.onNodeWithTag("icone").getBoundsInRoot()
        caixa.width.value to caixa.height.value
    }

    private fun assertEmRepouso(f: Pair<Float, Float>) {
        assertEquals(100f, f.first, 0.5f)
        assertEquals(40f, f.second, 0.5f)
    }

    @Test fun esticaEAssentaQuandoPassaAEscolhido() {
        val escolhido = montar(false)
        fotogramas(100).forEach(::assertEmRepouso)
        escolhido.value = true
        val f = fotogramas(800)
        assertTrue("estica na horizontal (pico 1,3): ${f.maxOf { it.first }}", f.maxOf { it.first } > 125f)
        assertTrue("achata na vertical (1,3×0,75): ${f.minOf { it.second }}", f.minOf { it.second } < 32f)
        assertTrue("depois estica na vertical (0,82×1,2): ${f.maxOf { it.second }}", f.maxOf { it.second } > 45f)
        assertEmRepouso(f.last())
    }

    @Test fun jaEscolhidoAoAparecerNaoAnima() {
        montar(true)
        fotogramas(800).forEach(::assertEmRepouso)
    }

    @Test fun semAnimacoesNaoAnima() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val escolhido = montar(false)
        fotogramas(100).forEach(::assertEmRepouso)
        escolhido.value = true
        fotogramas(800).forEach(::assertEmRepouso)
    }

    @Test fun deixarOSeparadorACaminhoAssentaJa() {
        val escolhido = montar(false)
        fotogramas(100).forEach(::assertEmRepouso)
        escolhido.value = true
        fotogramas(130)
        escolhido.value = false
        assertEmRepouso(fotogramas(64).last())
    }
}
