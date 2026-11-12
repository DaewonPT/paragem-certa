package pt.pixellusitano.paragemcerta.atualizar

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkInfo
import pt.pixellusitano.paragemcerta.BuildConfig
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

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

    // Ao abrir a app: procura no máximo de 6 em 6 horas, só com Wi-Fi (salvo se a pessoa deixou os dados móveis).
    private val agora = Instant.parse("2026-11-02T10:00:00Z")

    private fun ligadoA(tipo: Int) = shadowOf(contexto.getSystemService(ConnectivityManager::class.java)).setActiveNetworkInfo(
        ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, tipo, 0, true, NetworkInfo.State.CONNECTED),
    )

    @Test fun procuraAoAbrirComWifi() {
        ligadoA(ConnectivityManager.TYPE_WIFI)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora))
    }

    @Test fun nuncaProcuraDuasVezesEmSeisHoras() {
        ligadoA(ConnectivityManager.TYPE_WIFI)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora))
        assertFalse(Atualizacoes.reservarProcura(contexto, agora + Duration.ofMinutes(359)))
        assertTrue(Atualizacoes.reservarProcura(contexto, agora + Duration.ofHours(6)))
    }

    // Saíres a meio da transferência cancela-a: a abertura seguinte procura outra vez, sem esperar 6 horas.
    @Test fun aProcuraCortadaALiberaParaAAberturaSeguinte() {
        ligadoA(ConnectivityManager.TYPE_WIFI)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora))
        Atualizacoes.liberarProcura(contexto)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora + Duration.ofMinutes(1)))
    }

    // Com uma versão já descarregada à espera da saída (houve uma viagem) não se procura nem descarrega outra vez.
    @Test fun comUmaVersaoJaDescarregadaAAberturaNaoProcura() = runTest {
        ligadoA(ConnectivityManager.TYPE_WIFI)
        Atualizacoes.descarregada = futura
        Atualizacoes.aoAbrir(contexto, agora)
        assertTrue("a tentativa não se gasta", Atualizacoes.reservarProcura(contexto, agora))
    }

    @Test fun comOpcaoDesligadaNaoProcura() {
        ligadoA(ConnectivityManager.TYPE_WIFI)
        Atualizacoes.definirAtualizarSozinha(contexto, false)
        assertFalse(Atualizacoes.reservarProcura(contexto, agora))
    }

    @Test fun comDadosMoveisSoProcuraSeAPessoaDeixou() {
        ligadoA(ConnectivityManager.TYPE_MOBILE)
        assertFalse(Atualizacoes.reservarProcura(contexto, agora))
        Atualizacoes.definirUsarDadosMoveis(contexto, true)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora))
    }

    // Sem rede não se gasta a tentativa: a abertura seguinte procura.
    @Test fun semRedeNaoProcuraNemGastaATentativa() {
        Atualizacoes.definirUsarDadosMoveis(contexto, true)
        shadowOf(contexto.getSystemService(ConnectivityManager::class.java)).setActiveNetworkInfo(null)
        assertFalse(Atualizacoes.reservarProcura(contexto, agora))
        ligadoA(ConnectivityManager.TYPE_WIFI)
        assertTrue(Atualizacoes.reservarProcura(contexto, agora))
    }

    // Ao sair da app: instala a versão que ficou descarregada, mas nunca durante uma viagem ou um aviso de chegada.
    private val sessoes get() = contexto.packageManager.packageInstaller.allSessions

    private fun comApkDescarregado() {
        File(contexto.cacheDir, "atualizacao.apk").writeBytes(ByteArray(16))
        Atualizacoes.descarregada = futura
    }

    @After fun esquecerADescarregada() {
        Atualizacoes.descarregada = null
    }

    @Test fun aSaidaInstalaAVersaoDescarregada() {
        comApkDescarregado()
        assertEquals(Decisao.INSTALAR, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertEquals(1, sessoes.size)
        assertNull(Atualizacoes.descarregada)
        assertFalse(File(contexto.cacheDir, "atualizacao.apk").exists()) // o instalador já tem a sua cópia
    }

    @Test fun comUmaViagemEmCursoAFicaDescarregadaParaAProximaSaida() {
        comApkDescarregado()
        assertEquals(Decisao.ESPERAR, Atualizacoes.instalarAoSair(contexto, emViagem = true, comAviso = false))
        assertTrue(sessoes.isEmpty())
        assertEquals(futura, Atualizacoes.descarregada)
        assertEquals(Decisao.INSTALAR, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertEquals(1, sessoes.size)
    }

    @Test fun comUmAvisoDeChegadaAtivoNaoInstala() {
        comApkDescarregado()
        assertEquals(Decisao.ESPERAR, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = true))
        assertTrue(sessoes.isEmpty())
        assertEquals(futura, Atualizacoes.descarregada)
    }

    @Test fun duasSaidasSeguidasInstalamUmaSoVez() {
        comApkDescarregado()
        Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false)
        assertEquals(Decisao.NADA, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertEquals(1, sessoes.size)
    }

    @Test fun semNadaDescarregadoASaidaNaoFazNada() {
        assertEquals(Decisao.NADA, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertTrue(sessoes.isEmpty())
    }

    @Test fun desligadaDepoisDeDescarregarNaoInstala() {
        comApkDescarregado()
        Atualizacoes.definirAtualizarSozinha(contexto, false)
        assertEquals(Decisao.NADA, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertTrue(sessoes.isEmpty())
        assertNull(Atualizacoes.descarregada)
    }

    @Test fun versaoJaPendenteDeConfirmacaoNaoSeInstalaOutraVez() {
        comApkDescarregado()
        Atualizacoes.guardarPendente(contexto, futura)
        assertEquals(Decisao.NADA, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertTrue(sessoes.isEmpty())
    }

    // O "Atualizar" do aviso descarrega para outro ficheiro: não apaga nem mistura a versão que espera pela saída.
    @Test fun oAtualizarDoAvisoNaoApagaAVersaoQueEsperaPelaSaida() = comApkNoServidor { url, _ ->
        comApkDescarregado()
        runTest { Atualizacoes.instalar(contexto, NovaVersao("v98.0.0", url, "")) }
        assertTrue(File(contexto.cacheDir, "atualizacao.apk").exists())
        assertEquals(Decisao.INSTALAR, Atualizacoes.instalarAoSair(contexto, emViagem = false, comAviso = false))
        assertEquals(2, sessoes.size)
    }

    // Dois toques seguidos em "Atualizar" (o aviso volta depois de rodar o ecrã): o segundo espera, e os dois instalam.
    @Test fun doisToquesEmAtualizarDescarregamUmDeCadaVez() {
        val soltar = CountDownLatch(1)
        comApkNoServidor(segurar = soltar) { url, pedidos ->
            val nova = NovaVersao("v98.0.0", url, "")
            runBlocking {
                val toques = List(2) { async(Dispatchers.IO) { Atualizacoes.instalar(contexto, nova) } }
                withTimeout(5_000) { while (pedidos.get() == 0) delay(10) }
                delay(500) // tempo de sobra para o segundo descarregamento começar, se não esperasse
                assertEquals(1, pedidos.get())
                soltar.countDown()
                toques.awaitAll()
            }
            assertEquals(2, pedidos.get())
            assertEquals(2, sessoes.size)
        }
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
