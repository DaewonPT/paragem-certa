package pt.pixellusitano.paragemcerta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.atualizar.NovaVersao
import pt.pixellusitano.paragemcerta.atualizar.comApkNoServidor
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
class AvisoAtualizacaoTest {
    @get:Rule val regra = createComposeRule()

    // Rodar o ecrã ou sair com "Voltar" a meio do descarregamento não o corta nem mostra a falha.
    @Test fun sairDoEcraAMeioNaoCortaAAtualizacao() {
        val soltar = CountDownLatch(1)
        comApkNoServidor(segurar = soltar) { url, pedidos ->
            var mostrar by mutableStateOf(true)
            regra.setContent { if (mostrar) AvisoAtualizacao(NovaVersao("v99.0.0", url, ""), remember { mutableIntStateOf(0) }) }
            regra.onNodeWithText("Atualizar").performClick()
            regra.waitUntil(5_000) { pedidos.get() == 1 }
            mostrar = false
            regra.waitForIdle()
            soltar.countDown()
            val instalador = RuntimeEnvironment.getApplication().packageManager.packageInstaller
            regra.waitUntil(5_000) { instalador.allSessions.isNotEmpty() }
            regra.waitForIdle()
            assertNull(ShadowToast.getTextOfLatestToast())
        }
    }
}
