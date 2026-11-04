package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
class LocalizacaoTest {
    @get:Rule val regra = createComposeRule()

    @Test @Config(qualifiers = "w360dp-h480dp")
    fun botoesAcessiveisComLetraGrande() {
        regra.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                TemaParagemCerta(ModoTema.CLARO) { PedidoLocalizacao(aoAceitar = {}, aoRecusar = {}) }
            }
        }
        regra.onNodeWithText("Agora não").performScrollTo().assertIsDisplayed()
    }

    @Test fun pedidoDeLocalizacaoNaoPrometeOQueNaoCumpre() {
        val texto = RuntimeEnvironment.getApplication().getString(R.string.loc_texto)
        assertTrue("a posição vai nos pedidos ao Transitous", "Transitous" in texto)
        assertFalse("a posição sai do telemóvel", "fica no telemóvel" in texto)
    }

    @Test fun pedeAPosicaoAtual() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val gestor = app.getSystemService(LocationManager::class.java)
        shadowOf(gestor).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        var recebida: Pair<Double, Double>? = null
        pedirPosicaoAtual(app) { recebida = it }
        shadowOf(gestor).simulateLocation(Location(LocationManager.GPS_PROVIDER).apply { latitude = 38.7058; longitude = -9.1431 })
        ShadowLooper.idleMainLooper()
        assertEquals(38.7058 to -9.1431, recebida)
    }

    @Test fun pedeAPosicaoComAHoraDaMedicao() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val gestor = app.getSystemService(LocationManager::class.java)
        shadowOf(gestor).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        var recebida: Location? = null
        pedirLocalizacaoAtual(app) { recebida = it }
        shadowOf(gestor).simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply { latitude = 41.15; longitude = -8.61; time = 1_762_000_000_000L },
        )
        ShadowLooper.idleMainLooper()
        assertEquals(1_762_000_000_000L, recebida?.time)
    }

    // "Centrar" depois de um salto grande (o telemóvel esteve sem GPS e agora está noutra cidade): a posição do componente do
    // mapa ainda é a antiga, e a que o pedido traz é a certa.
    private val lisboa = PosicaoComHora(38.7058, -9.1431, hora = 1_000)
    private val porto = PosicaoComHora(41.1496, -8.6110, hora = 5_000)

    @Test fun centrarEscolheAPosicaoMaisNova() {
        assertEquals(porto, maisRecente(lisboa, porto)) // a do componente é a antiga
        assertEquals(porto, maisRecente(porto, lisboa)) // e ao contrário, a do pedido é a antiga
    }

    @Test fun centrarComUmaSoPosicaoUsaEssa() {
        assertEquals(lisboa, maisRecente(lisboa, null))
        assertEquals(lisboa, maisRecente(null, lisboa))
        assertEquals(null, maisRecente(null, null))
    }

    @Test fun umaPosicaoSemHoraPerdeContraUmaComHora() {
        // A última conhecida do telemóvel, antes de haver pedido, não traz hora (0).
        assertEquals(lisboa, maisRecente(lisboa, porto.copy(hora = 0)))
        assertEquals(lisboa, maisRecente(porto.copy(hora = 0), lisboa))
    }

    @Test fun naHoraIgualFicaAPrimeira() {
        assertEquals(lisboa, maisRecente(lisboa, porto.copy(hora = lisboa.hora)))
    }
}
