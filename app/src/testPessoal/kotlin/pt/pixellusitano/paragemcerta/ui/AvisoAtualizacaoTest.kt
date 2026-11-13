package pt.pixellusitano.paragemcerta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import java.time.Instant
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
class AvisoAtualizacaoTest {
    @get:Rule val regra = createComposeRule()

    // Rodar o ecrã ou sair com "Voltar" a meio do descarregamento não o corta nem mostra a falha.
    @Test fun sairDoEcraAMeioNaoCortaAAtualizacao() {
        val soltar = CountDownLatch(1)
        // Pedido antes de o descarregamento começar: o Android cria o instalador da primeira vez que é pedido, sem trinco, e o
        // Robolectric guarda as sessões em cada instância; pedido ao mesmo tempo que a app, cada um podia ficar com o seu.
        val instalador = RuntimeEnvironment.getApplication().packageManager.packageInstaller
        comApkNoServidor(segurar = soltar) { url, pedidos ->
            var mostrar by mutableStateOf(true)
            regra.setContent { if (mostrar) AvisoAtualizacao(NovaVersao("v99.0.0", url, ""), remember { mutableIntStateOf(0) }) }
            regra.onNodeWithText("Atualizar").performClick()
            regra.waitUntil(5_000) { pedidos.get() == 1 }
            mostrar = false
            regra.waitForIdle()
            soltar.countDown()
            regra.waitUntil(5_000) { instalador.allSessions.isNotEmpty() }
            regra.waitForIdle()
            assertNull(ShadowToast.getTextOfLatestToast())
        }
    }

    // Rodar o ecrã com o aviso aberto: a verificação do dia já foi gasta, por isso o aviso tem de voltar do estado guardado.
    @Test fun rodarOEcraNaoEscondeOAviso() {
        // O dia já gasto, como logo depois da verificação que trouxe o aviso: a nova composição não vai ao GitHub.
        RuntimeEnvironment.getApplication().getSharedPreferences("atualizacoes", 0).edit()
            .putLong("ultima_verificacao", Instant.now().epochSecond).commit()
        val restauro = StateRestorationTester(regra)
        // Antes de rodar a versão chega pela pendente; depois de rodar a Navegacao chama o aviso sem ela, como no caminho diário.
        var pendente: NovaVersao? = NovaVersao("v99.0.0", "https://exemplo/a.apk", "Notas.")
        restauro.setContent { AvisoAtualizacao(pendente, remember { mutableIntStateOf(0) }) }
        regra.onNodeWithText("Há uma versão nova (99.0.0)").assertExists()
        pendente = null
        restauro.emulateSavedInstanceStateRestore()
        regra.waitForIdle()
        regra.onNodeWithText("Há uma versão nova (99.0.0)").assertExists()
    }

    // "Agora não" fecha a pendente até à abertura seguinte: rodar o ecrã não a volta a mostrar.
    @Test fun agoraNaoContinuaFechadoDepoisDeRodar() {
        val restauro = StateRestorationTester(regra)
        restauro.setContent { AvisoAtualizacao(NovaVersao("v99.0.0", "https://exemplo/a.apk", ""), remember { mutableIntStateOf(0) }) }
        regra.onNodeWithText("Agora não").performClick()
        restauro.emulateSavedInstanceStateRestore()
        regra.onNodeWithText("Agora não").assertDoesNotExist()
    }
}
