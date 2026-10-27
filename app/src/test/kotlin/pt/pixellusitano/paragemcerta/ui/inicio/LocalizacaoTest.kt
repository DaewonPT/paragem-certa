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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
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
}
