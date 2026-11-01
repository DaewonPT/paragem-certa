package pt.pixellusitano.paragemcerta.ui.viagem

import android.Manifest
import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.time.Instant

/** Tocar em "Viagem interrompida": retoma já, ou pede primeiro a localização que o Android 14+ exige ao serviço (o passo do começo). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RetomarViagemTest {
    @get:Rule val regra = createComposeRule()

    private val c = RuntimeEnvironment.getApplication()
    private val ex = Exemplo(Instant.now())
    private val plano = ex.plano()
    private val guardado = ex.aBordo(plano, 1, k = 3)
    private var retomou = 0

    private fun comViagemInterrompida() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, guardado)
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                val retomar = rememberRetomarViagem(aoRetomou = { retomou++ })
                Button(onClick = retomar) { Text("Toca") }
            }
        }
    }

    @Test fun semNenhumaLocalizacaoPedeAntesDeRetomarENadaArranca() {
        shadowOf(c).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        comViagemInterrompida()
        regra.onNodeWithText("Toca").performClick()
        regra.onNodeWithText("Localização necessária").assertExists()
        regra.onNodeWithText("Permitir localização").assertExists()
        assertNull(shadowOf(c as Application).nextStartedService) // o startForeground rebentava e o ecrã da viagem ficava parado
        assertEquals(0, retomou)

        // Recusar deixa tudo como estava: o aviso "Viagem interrompida" continua a ter a viagem para retomar.
        regra.onNodeWithText("Agora não").performClick()
        regra.onNodeWithText("Localização necessária").assertDoesNotExist()
        assertNull(shadowOf(c as Application).nextStartedService)
        assertEquals(0, retomou)
        assertEquals(guardado, FicheirosViagem.lerEstado(c))
    }

    @Test fun comAlgumaLocalizacaoRetomaLogoSemPerguntar() {
        shadowOf(c).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        comViagemInterrompida()
        regra.onNodeWithText("Toca").performClick()
        regra.onNodeWithText("Localização necessária").assertDoesNotExist()
        assertEquals(ServicoViagem.ACAO_RETOMAR, shadowOf(c as Application).nextStartedService.action)
        assertEquals(1, retomou)
    }
}
