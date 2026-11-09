package pt.pixellusitano.paragemcerta.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.pixellusitano.paragemcerta.ui.rotas.BarraDeEstadoDasRotas
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
class BarraDeEstadoTest {
    @get:Rule val regra = createAndroidComposeRule<ComponentActivity>()

    @Test fun soOClaroSemFaixaTemIconesEscuros() {
        assertEquals(true, iconesEscurosNaBarraDeEstado(escuro = false, comFaixa = false))
        assertEquals(false, iconesEscurosNaBarraDeEstado(escuro = false, comFaixa = true))
        assertEquals(false, iconesEscurosNaBarraDeEstado(escuro = true, comFaixa = false))
        assertEquals(false, iconesEscurosNaBarraDeEstado(escuro = true, comFaixa = true))
    }

    private fun iconesClaros(comFaixa: Boolean): Boolean {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { AjustarBarraDeEstado(comFaixa) } }
        regra.waitForIdle()
        val janela = regra.activity.window
        return WindowCompat.getInsetsController(janela, janela.decorView).isAppearanceLightStatusBars
    }

    @Test fun comFaixaOsIconesSaoClaros() = assertEquals(false, iconesClaros(comFaixa = true))

    @Test fun semFaixaNoTemaClaroOsIconesSaoEscuros() = assertEquals(true, iconesClaros(comFaixa = false))

    private fun iconesClarosNasRotas(aEscolher: Boolean, semRota: Boolean, faixaDaNavegacao: Boolean): Boolean {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                BarraDeEstadoDaNavegacao(nasRotas = true, comFaixa = faixaDaNavegacao)
                BarraDeEstadoDasRotas(aEscolher, semRota)
            }
        }
        regra.waitForIdle()
        val janela = regra.activity.window
        return WindowCompat.getInsetsController(janela, janela.decorView).isAppearanceLightStatusBars
    }

    @Test fun nasRotasAListaComFaixaPedeIconesClaros() = assertEquals(false, iconesClarosNasRotas(aEscolher = false, semRota = true, faixaDaNavegacao = false))

    @Test fun nasRotasOEscolherLugarPedeIconesClaros() = assertEquals(false, iconesClarosNasRotas(aEscolher = true, semRota = false, faixaDaNavegacao = false))

    // A Navegacao não decide nas Rotas: mesmo com a faixa pedida lá fora, vale o que o ecrã das Rotas pede.
    @Test fun nasRotasODetalheComOMapaPedeIconesEscuros() = assertEquals(true, iconesClarosNasRotas(aEscolher = false, semRota = false, faixaDaNavegacao = true))
}
