package pt.pixellusitano.paragemcerta.atualizar

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import pt.pixellusitano.paragemcerta.BuildConfig

@RunWith(RobolectricTestRunner::class)
class AtualizacoesTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val estaVersao = "v" + BuildConfig.VERSION_NAME

    @Test fun porOmissaoAtualizaSozinhaSoComWifi() {
        assertTrue(Atualizacoes.atualizarSozinha(contexto))
        assertFalse(Atualizacoes.usarDadosMoveis(contexto))
    }

    @Test fun definicoesGuardam() {
        Atualizacoes.definirAtualizarSozinha(contexto, false)
        Atualizacoes.definirUsarDadosMoveis(contexto, true)
        assertFalse(Atualizacoes.atualizarSozinha(contexto))
        assertTrue(Atualizacoes.usarDadosMoveis(contexto))
        Atualizacoes.definirAtualizarSozinha(contexto, true)
        Atualizacoes.definirUsarDadosMoveis(contexto, false)
        assertTrue(Atualizacoes.atualizarSozinha(contexto))
        assertFalse(Atualizacoes.usarDadosMoveis(contexto))
    }

    @Test fun semNadaGuardadoNaoHaNovidades() = assertNull(Atualizacoes.novidadesPorMostrar(contexto))

    @Test fun novidadesDaVersaoEmExecucaoSaemUmaVez() {
        Atualizacoes.guardarNovidades(contexto, NovaVersao(estaVersao, "https://exemplo/a.apk", "Rotas mais rápidas."))
        val novidades = Atualizacoes.novidadesPorMostrar(contexto)
        assertEquals(estaVersao, novidades?.tag)
        assertEquals("Rotas mais rápidas.", novidades?.notas)
        Atualizacoes.marcarNovidadesVistas(contexto)
        assertNull(Atualizacoes.novidadesPorMostrar(contexto))
    }

    @Test fun novidadesDeOutraVersaoNaoSaem() {
        Atualizacoes.guardarNovidades(contexto, NovaVersao("v99.0.0", "https://exemplo/a.apk", "Ainda não instalada."))
        assertNull(Atualizacoes.novidadesPorMostrar(contexto))
    }

    private val futura = NovaVersao("v99.0.0", "https://exemplo/a.apk", "Ainda não instalada.")

    @Test fun semNadaGuardadoNaoHaPendente() = assertNull(Atualizacoes.pendente(contexto))

    @Test fun pendenteGuardadaSaiEnquantoForMaisNovaQueAEmExecucao() {
        Atualizacoes.guardarPendente(contexto, futura)
        assertEquals(futura, Atualizacoes.pendente(contexto))
        assertEquals(futura, Atualizacoes.pendente(contexto)) // ler não a gasta: "Agora não" mostra-a na abertura seguinte
    }

    @Test fun pendenteIgualAVersaoEmExecucaoLimpaSe() {
        Atualizacoes.guardarPendente(contexto, NovaVersao(estaVersao, "https://exemplo/a.apk", "Já instalada."))
        assertNull(Atualizacoes.pendente(contexto))
        assertFalse(guardadoComoPendente())
    }

    @Test fun pendenteMaisAntigaQueAEmExecucaoLimpaSe() {
        Atualizacoes.guardarPendente(contexto, NovaVersao("v0.0.1", "https://exemplo/a.apk", "Velha."))
        assertNull(Atualizacoes.pendente(contexto))
        assertFalse(guardadoComoPendente())
    }

    @Test fun limparPendenteApaga() {
        Atualizacoes.guardarPendente(contexto, futura)
        Atualizacoes.limparPendente(contexto)
        assertNull(Atualizacoes.pendente(contexto))
    }

    @Test fun notificacoesPedemSeUmaSoVez() {
        assertTrue(Atualizacoes.pedirNotificacoes(contexto))
        Atualizacoes.marcarNotificacoesPedidas(contexto)
        assertFalse(Atualizacoes.pedirNotificacoes(contexto))
    }

    @Test fun notificacoesNaoSePedemComAtualizarSozinhaDesligado() {
        Atualizacoes.definirAtualizarSozinha(contexto, false)
        assertFalse(Atualizacoes.pedirNotificacoes(contexto))
    }

    @Test fun notificacoesNaoSePedemSeJaForamConcedidas() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Atualizacoes.pedirNotificacoes(contexto))
    }

    @Test fun confirmacaoEmSegundoPlanoFicaPendenteEAvisa() {
        poeAppNoPlano(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
        ResultadoInstalacao().onReceive(contexto, pedidoDeConfirmacao())
        assertEquals(futura, Atualizacoes.pendente(contexto))
        assertEquals(1, shadowOf(contexto.getSystemService(NotificationManager::class.java)).size())
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test fun confirmacaoComAppAFrenteAbreOEcraSemFicarPendente() {
        poeAppNoPlano(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        ResultadoInstalacao().onReceive(contexto, pedidoDeConfirmacao())
        assertNull(Atualizacoes.pendente(contexto))
        assertEquals(0, shadowOf(contexto.getSystemService(NotificationManager::class.java)).size())
        assertEquals(Intent.ACTION_VIEW, shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity?.action)
    }

    private fun guardadoComoPendente() = contexto.getSharedPreferences("atualizacoes", 0).all.keys.any { it.startsWith("pendente") }

    private fun poeAppNoPlano(importancia: Int) {
        val eu = ActivityManager.RunningAppProcessInfo().apply { pid = Process.myPid(); this.importance = importancia }
        shadowOf(contexto.getSystemService(ActivityManager::class.java)).setProcesses(listOf(eu))
    }

    /** O que o instalador devolve quando precisa que o utilizador confirme: o estado e o ecrã a abrir. */
    private fun pedidoDeConfirmacao() = Intent(contexto, ResultadoInstalacao::class.java).comVersao(futura)
        .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
        .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW))
}
