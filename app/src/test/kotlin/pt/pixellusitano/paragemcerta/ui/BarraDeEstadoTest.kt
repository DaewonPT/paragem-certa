package pt.pixellusitano.paragemcerta.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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

    // Lê o estado aqui dentro: só esta função recompõe quando a faixa muda, o ecrã das Rotas fica de fora.
    @Composable private fun PaiDasRotas(faixa: State<Boolean>) = BarraDeEstadoDaNavegacao(nasRotas = true, comFaixa = faixa.value)

    /**
     * O ecrã das Rotas compõe-se antes da Navegacao (que, sem o portão, escreveria por último) e a Navegacao recompõe-se sozinha
     * quando [faixaDaNavegacao] muda, sem o ecrã das Rotas, como em produção. Devolve se os ícones ficam claros no início e depois de cada mudança.
     */
    private fun iconesClarosNasRotas(aEscolher: Boolean, semRota: Boolean): List<Boolean> {
        val faixaDaNavegacao = mutableStateOf(false)
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                BarraDeEstadoDasRotas(aEscolher, semRota)
                PaiDasRotas(faixaDaNavegacao)
            }
        }
        val janela = regra.activity.window
        fun claros() = WindowCompat.getInsetsController(janela, janela.decorView).isAppearanceLightStatusBars
        regra.waitForIdle()
        val inicial = claros()
        faixaDaNavegacao.value = true
        regra.waitForIdle()
        val comFaixa = claros()
        faixaDaNavegacao.value = false
        regra.waitForIdle()
        return listOf(inicial, comFaixa, claros())
    }

    @Test fun nasRotasAListaComFaixaPedeIconesClaros() = assertEquals(List(3) { false }, iconesClarosNasRotas(aEscolher = false, semRota = true))

    @Test fun nasRotasOEscolherLugarPedeIconesClaros() = assertEquals(List(3) { false }, iconesClarosNasRotas(aEscolher = true, semRota = false))

    @Test fun nasRotasODetalheComOMapaPedeIconesEscuros() = assertEquals(List(3) { true }, iconesClarosNasRotas(aEscolher = false, semRota = false))
}
