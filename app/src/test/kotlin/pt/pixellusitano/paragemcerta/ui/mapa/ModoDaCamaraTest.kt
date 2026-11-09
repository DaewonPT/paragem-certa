package pt.pixellusitano.paragemcerta.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

class ModoDaCamaraTest {
    @Test fun bussolaRodaESegue() = assertEquals(RenderMode.COMPASS to CameraMode.TRACKING_COMPASS, modoDaCamara(false, true))
    @Test fun viagemSegueComGps() = assertEquals(RenderMode.GPS to CameraMode.TRACKING_GPS, modoDaCamara(true, false))
    @Test fun semNadaNaoSegue() = assertEquals(RenderMode.NORMAL to CameraMode.NONE, modoDaCamara(false, false))
}
