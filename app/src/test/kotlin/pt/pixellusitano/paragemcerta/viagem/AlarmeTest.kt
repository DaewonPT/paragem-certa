package pt.pixellusitano.paragemcerta.viagem

import android.os.VibrationAttributes
import android.os.Vibrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.core.viagem.Evento

/** O som toca no telemóvel (o Robolectric não o reproduz); aqui vê-se a vibração, que tem a duração de cada aviso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmeTest {
    private val c = RuntimeEnvironment.getApplication()
    private val vibrador get() = shadowOf(c.getSystemService(Vibrator::class.java))

    @Test fun saiAgoraVibraSeisSegundosComOUsoAlarme() {
        Alarme.tocar(c, Evento.SAI_AGORA, som = false, vibrar = true)
        assertTrue(vibrador.isVibrating)
        assertEquals(VibrationAttributes.USAGE_ALARM, (vibrador.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
        assertEquals(6.0, vibrador.pattern.sum() / 1000.0, 1.0)
    }

    @Test fun prepareTeVibraTresSegundos() {
        Alarme.tocar(c, Evento.PREPARA_TE, som = false, vibrar = true)
        assertEquals(3.0, vibrador.pattern.sum() / 1000.0, 1.0)
    }

    @Test fun semVibracaoOuEmEventosSemAlarmeNaoVibra() {
        Alarme.tocar(c, Evento.SAI_AGORA, som = false, vibrar = false)
        Alarme.tocar(c, Evento.ETAPA_SEGUINTE, som = true, vibrar = true)
        Alarme.tocar(c, Evento.CHEGOU, som = true, vibrar = true)
        assertFalse(vibrador.isVibrating)
    }
}
