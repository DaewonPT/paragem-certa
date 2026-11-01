package pt.pixellusitano.paragemcerta.viagem

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
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
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.dados.fixture
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FicheirosViagemTest {
    private val c = RuntimeEnvironment.getApplication()
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]
    private val plano = PlanoViagem(rota, som = true, vibrar = false)
    private val estado = Viagem(rota).inicio(rota.partida).copy(avisados = setOf(Evento.PREPARA_TE), silenciado = true)
    private val pasta get() = File(c.filesDir, "viagem")

    @Test fun planoEEstadoLidosIguais() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, estado)
        assertEquals(plano, FicheirosViagem.lerPlano(c))
        assertEquals(estado, FicheirosViagem.lerEstado(c, rota.partida))
    }

    @Test fun gravarOutraVezSubstitui() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, estado)
        val depois = estado.copy(etapa = 1, silenciado = false)
        FicheirosViagem.gravarEstado(c, depois)
        assertEquals(depois, FicheirosViagem.lerEstado(c, rota.partida))
        assertEquals(setOf("trip-plan.json", "trip-state.json"), pasta.list()!!.toSet())
    }

    @Test fun lerNaoApagaOQueOOutroProcessoEstaAEscrever() {
        // A escrita a meio do :trip (o ".new" do AtomicFile de antes, o temporário de agora) não é da app.
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, estado)
        val aMeio = listOf(File(pasta, "trip-state.json.new"), File(pasta, "trip-state.json123.tmp"))
        aMeio.forEach { it.writeText("{") }
        assertEquals(estado, FicheirosViagem.lerEstado(c, rota.partida))
        assertEquals(plano, FicheirosViagem.lerPlano(c))
        aMeio.forEach { assertTrue(it.name, it.exists()) }
    }

    @Test fun semFicheirosNaoHaNada() {
        assertNull(FicheirosViagem.lerPlano(c))
        assertNull(FicheirosViagem.lerEstado(c))
    }

    @Test fun estadoVelhoApagaSeAoLer() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, estado)
        assertNotNull(FicheirosViagem.lerEstado(c, rota.chegada.plus(Duration.ofMinutes(29))))
        assertNull(FicheirosViagem.lerEstado(c, rota.chegada.plus(Duration.ofMinutes(31))))
        assertNull(FicheirosViagem.lerPlano(c))
        assertFalse(File(pasta, "trip-state.json").exists())
    }

    @Test fun estadoSemPlanoNaoServeDeNada() {
        FicheirosViagem.gravarEstado(c, estado)
        assertNull(FicheirosViagem.lerEstado(c, rota.partida))
        assertFalse(File(pasta, "trip-state.json").exists())
    }

    @Test fun ficheiroEstragadoNaoRebenta() {
        FicheirosViagem.gravarPlano(c, plano)
        File(pasta, "trip-state.json").writeText("{ isto não é json")
        File(pasta, "trip-plan.json").writeText("")
        assertNull(FicheirosViagem.lerEstado(c, rota.partida))
        assertNull(FicheirosViagem.lerPlano(c))
    }

    @Test fun apagarDeixaOGpsParaOAutorCopiar() {
        FicheirosViagem.gravarPlano(c, plano)
        FicheirosViagem.gravarEstado(c, estado)
        File(pasta, "gps.csv").writeText("0,38.7,-9.1,8")
        FicheirosViagem.apagar(c)
        assertNull(FicheirosViagem.lerPlano(c))
        assertNull(FicheirosViagem.lerEstado(c, rota.partida))
        assertTrue(File(pasta, "gps.csv").exists())
    }

    @Test fun emCursoQuandoHaProcessoTrip() {
        val gestor = c.getSystemService(ActivityManager::class.java)
        fun processo(nome: String) = RunningAppProcessInfo(nome, 1, emptyArray())
        shadowOf(gestor).setProcesses(listOf(processo(c.packageName)))
        assertFalse(FicheirosViagem.emCurso(c))
        shadowOf(gestor).setProcesses(listOf(processo(c.packageName), processo("${c.packageName}:trip")))
        assertTrue(FicheirosViagem.emCurso(c))
    }
}
