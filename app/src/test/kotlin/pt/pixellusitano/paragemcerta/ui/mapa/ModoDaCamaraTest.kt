package pt.pixellusitano.paragemcerta.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

class ModoDaCamaraTest {
    @Test fun bussolaRodaESegue() = assertEquals(RenderMode.COMPASS to CameraMode.TRACKING_COMPASS, modoDaCamara(false, true))
    @Test fun viagemSegueComGps() = assertEquals(RenderMode.GPS to CameraMode.TRACKING_GPS, modoDaCamara(true, false))
    @Test fun semNadaNaoSegue() = assertEquals(RenderMode.NORMAL to CameraMode.NONE, modoDaCamara(false, false))

    @Test fun semParagemCentraNoAutocarro() = assertEquals((38.70 to -9.14) to 15.5, pontoDoAutocarro(38.70 to -9.14, null))
    @Test fun pertoVaiAParagemComZoom16() = assertEquals((38.70225 to -9.14) to 16.0, pontoDoAutocarro(38.70 to -9.14, 38.70225 to -9.14)) // ~250 m
    @Test fun longeEnquadraOsDois() = assertNull(pontoDoAutocarro(38.70 to -9.14, 38.718 to -9.14)) // ~2 km
    @Test fun margensComAFolha() = assertEquals(listOf(96, 96, 96, 152), margensDoAutocarro(2f).toList())
}
