package pt.pixellusitano.paragemcerta.ui.viagem

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.location.LocationManager
import android.media.AudioManager
import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComecarViagemTest {
    private val c = RuntimeEnvironment.getApplication()

    // Os ficheiros só valem até chegada + 30 min: a viagem tem de ser a de agora.
    private val ex = Exemplo(Instant.now())
    private val plano = ex.plano()

    private fun processos(vararg nomes: String) =
        shadowOf(c.getSystemService(ActivityManager::class.java)).setProcesses(nomes.map { RunningAppProcessInfo(it, 1, emptyArray()) })

    @Test fun leOQueFaltaNoTelemovel() {
        val localizacao = shadowOf(c.getSystemService(LocationManager::class.java))
        val bateria = shadowOf(c.getSystemService(PowerManager::class.java))
        val som = c.getSystemService(AudioManager::class.java)

        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        localizacao.setLocationEnabled(true)
        bateria.setIgnoringBatteryOptimizations(c.packageName, true)
        som.setStreamVolume(AudioManager.STREAM_ALARM, 4, 0)
        assertEquals(Preparacao(localizacaoPrecisa = true, localizacaoLigada = true, notificacoes = true, bateriaLivre = true, volumeAlarme = 4), lerPreparacao(c))

        // Só a aproximada não chega para os avisos.
        shadowOf(c).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        localizacao.setLocationEnabled(false)
        bateria.setIgnoringBatteryOptimizations(c.packageName, false)
        som.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        assertEquals(Preparacao(localizacaoPrecisa = false, localizacaoLigada = false, notificacoes = false, bateriaLivre = false, volumeAlarme = 0), lerPreparacao(c))
    }

    @Test fun comecarGravaOPlanoApagaOEstadoVelhoEArrancaOServico() {
        val velho = ex.aBordo(plano, 1, k = 3).copy(silenciado = true)
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, velho)

        assertTrue(comecarViagem(c, plano.itinerario, som = false, vibrar = true))

        assertEquals(PlanoViagem(plano.itinerario, som = false, vibrar = true), FicheirosViagem.lerPlano(c))
        // O ecrã da viagem abre já: se esperasse pelo estado do serviço (outro processo) não o encontrava e saía.
        val estado = FicheirosViagem.lerEstado(c)
        assertNotNull(estado)
        assertEquals(0, estado!!.etapa)
        assertFalse(estado.silenciado)
        val pedido = shadowOf(c as Application).nextStartedService
        assertEquals(ServicoViagem.ACAO_COMECAR, pedido.action)
        assertEquals(ServicoViagem::class.java.name, pedido.component!!.className)
    }

    @Test fun retomarPedeAoServicoQueRetome() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, ex.aBordo(plano, 1, k = 3))
        assertTrue(retomarViagem(c))
        assertEquals(ServicoViagem.ACAO_RETOMAR, shadowOf(c as Application).nextStartedService.action)
        // Retomar não mexe nos ficheiros: o serviço retoma do estado que lá está.
        assertEquals(ex.aBordo(plano, 1, k = 3), FicheirosViagem.lerEstado(c))
    }

    @Test fun interrompidaSoComEstadoPorAcabarESemServico() {
        processos(c.packageName)
        assertFalse(viagemInterrompida(c)) // nada gravado

        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, ex.aBordo(plano, 1, k = 3))
        assertTrue(viagemInterrompida(c))

        processos(c.packageName, "${c.packageName}:trip")
        assertFalse(viagemInterrompida(c)) // o serviço está a correr
    }

    @Test fun viagemTerminadaNaoEInterrompida() {
        processos(c.packageName)
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, ex.aBordo(plano, 3, k = 1).copy(terminada = true))
        assertFalse(viagemInterrompida(c))
    }

    @Test fun estadoVelhoNaoEInterrompidoEApagaSe() {
        processos(c.packageName)
        val velho = Exemplo(Instant.now().minus(Duration.ofHours(3)))
        val p = velho.plano()
        FicheirosViagem.gravarPlano(c, p)
        FicheirosViagem.gravarEstado(c, velho.aBordo(p, 1, k = 3))
        assertFalse(viagemInterrompida(c))
        assertNull(FicheirosViagem.lerPlano(c))
    }
}
