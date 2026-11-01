package pt.pixellusitano.paragemcerta.ui.viagem

import android.Manifest
import android.app.Application
import android.location.LocationManager
import android.media.AudioManager
import android.os.PowerManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.time.Instant

/** DataStore em memória, como o do PreferenciasTest (o de ficheiros não substitui o ficheiro no Windows). */
private class LojaEmMemoria : DataStore<Preferences> {
    private val estado = MutableStateFlow(emptyPreferences())
    override val data = estado
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(estado.value).also { estado.value = it }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreparacaoViagemTest {
    @get:Rule val regra = createComposeRule()

    private val c = RuntimeEnvironment.getApplication()
    private val itinerario = Exemplo(Instant.now()).itinerario()
    private val preferencias = Preferencias(LojaEmMemoria())
    private var comecou = 0
    private var desistiu = 0

    /** O telemóvel como o de quem já deu tudo: nada por perguntar. */
    @Before fun telemovelEmOrdem() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(c.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        shadowOf(c.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(c.packageName, true)
        c.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, 4, 0)
    }

    private fun preparar() = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            PreparacaoViagem(itinerario, preferencias, aoComecou = { comecou++ }, aoCancelar = { desistiu++ })
        }
    }

    // As definições e o arranque correm fora da thread principal: o waitForIdle sozinho não os espera.
    private fun esperarPor(titulo: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(titulo).fetchSemanticsNodes().isNotEmpty() }

    private fun esperarComecar() = regra.waitUntil(5_000) { regra.waitForIdle(); comecou > 0 }

    private fun primeiraViagemFeita() = runBlocking { preferencias.primeiraViagemFeita.first() }

    @Test fun semNadaPorPerguntarComecaLogo() {
        preparar()
        esperarComecar()
        assertEquals(0, desistiu)
        assertNotNull(FicheirosViagem.lerEstado(c))
        assertEquals(ServicoViagem.ACAO_COMECAR, shadowOf(c as Application).nextStartedService.action)
        assertTrue(primeiraViagemFeita())
    }

    @Test fun naPrimeiraViagemExplicaABateriaEEsperaPelaPessoa() {
        shadowOf(c.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(c.packageName, false)
        preparar()
        esperarPor("Poupança de bateria")
        assertEquals(0, comecou)
        assertNull(FicheirosViagem.lerPlano(c)) // nada arranca antes de a pessoa continuar
        regra.onNodeWithText("Continuar").performClick()
        esperarComecar()
        assertNotNull(FicheirosViagem.lerPlano(c))
    }

    @Test fun naSegundaViagemNaoExplicaABateria() {
        runBlocking { preferencias.marcarPrimeiraViagemFeita() }
        shadowOf(c.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(c.packageName, false)
        preparar()
        esperarComecar()
        regra.onNodeWithText("Poupança de bateria").assertDoesNotExist()
    }

    @Test fun localizacaoDesligadaAvisaEDeixaComecarAMesma() {
        runBlocking { preferencias.marcarPrimeiraViagemFeita() }
        shadowOf(c.getSystemService(LocationManager::class.java)).setLocationEnabled(false)
        preparar()
        esperarPor("Localização desligada")
        assertEquals(0, comecou)
        regra.onNodeWithText("Começar à mesma").performClick()
        esperarComecar()
    }

    @Test fun semNenhumaLocalizacaoNoAndroid14NaoComecaEExplica() {
        runBlocking { preferencias.marcarPrimeiraViagemFeita() }
        shadowOf(c).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        preparar()
        esperarPor("Localização precisa")
        regra.onNodeWithText("Começar pelo horário").performClick()
        esperarPor("Localização necessária")
        regra.onNodeWithText("Para acompanhar a viagem com o ecrã desligado, o Android pede a localização, pelo menos aproximada.").assertExists()
        regra.onNodeWithText("Permitir localização").assertExists()
        assertEquals(0, comecou)
        assertNull(FicheirosViagem.lerPlano(c)) // o serviço rebentava ao arrancar: nada é gravado nem pedido
        assertNull(shadowOf(c as Application).nextStartedService)

        regra.onNodeWithText("Agora não").performClick()
        regra.waitUntil(5_000) { regra.waitForIdle(); desistiu > 0 }
        assertEquals(0, comecou)
        assertNull(FicheirosViagem.lerPlano(c))
        assertNull(FicheirosViagem.lerEstado(c))
    }

    @Test fun comAAproximadaComecaPeloHorario() {
        runBlocking { preferencias.marcarPrimeiraViagemFeita() }
        shadowOf(c).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        preparar()
        esperarPor("Localização precisa")
        regra.onNodeWithText("Começar pelo horário").performClick()
        esperarComecar()
        regra.onNodeWithText("Localização necessária").assertDoesNotExist()
        assertEquals(ServicoViagem.ACAO_COMECAR, shadowOf(c as Application).nextStartedService.action)
    }

    @Test fun oPlanoLevaOSomEAVibracaoDasDefinicoes() {
        runBlocking {
            preferencias.definirSomAviso(false)
            preferencias.definirVibracaoAviso(true)
        }
        // Volume do alarme a zero: com o som desligado não faz diferença, por isso não pergunta.
        c.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        preparar()
        esperarComecar()
        val plano = FicheirosViagem.lerPlano(c)!!
        assertFalse(plano.som)
        assertTrue(plano.vibrar)
    }
}
