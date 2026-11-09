package pt.pixellusitano.paragemcerta.ui.paragem

import android.Manifest
import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

/** O Android a responder aos pedidos de permissão do teste: [dar] diz se a pessoa aceita. */
private class RegistoFalso(private val dar: Boolean) : ActivityResultRegistry() {
    val pedidas = mutableListOf<Any?>()
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        pedidas += input
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, dar as O)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AvisaMeTest {
    @get:Rule val regra = createComposeRule()

    private val c = RuntimeEnvironment.getApplication()
    private val sodre = RefParagem(Fonte.CM, "060001")
    private val linhas = listOf("728", "736", "3708")
    private val avisos = mutableListOf<Pair<Set<String>, Int>>()
    private var cancelou = 0
    private var fechou = 0

    // ---- o diálogo ----

    @Composable
    private fun Dialogo() = TemaParagemCerta(ModoTema.CLARO) {
        DialogoAvisaMe(linhas, aoAvisar = { l, m -> avisos += l to m }, aoCancelar = { cancelou++ })
    }

    @Test fun semLinhasNaoDeixaAvisar() {
        regra.setContent { Dialogo() }
        regra.onNodeWithText("Avisar").assertIsNotEnabled().performClick()
        assertTrue(avisos.isEmpty())
        regra.onNodeWithText("736").performClick()
        regra.onNodeWithText("Avisar").assertIsEnabled()
        regra.onNodeWithText("736").performClick() // desmarcada outra vez
        regra.onNodeWithText("Avisar").assertIsNotEnabled()
    }

    @Test fun abreComTodasAsLinhasDesmarcadasECincoMinutos() {
        regra.setContent { Dialogo() }
        linhas.forEach { regra.onNodeWithText(it).assertIsOff() }
        regra.onNodeWithText("5 min").assertIsSelected()
    }

    @Test fun enviaAsLinhasEOsMinutosEscolhidos() {
        regra.setContent { Dialogo() }
        regra.onNodeWithText("728").performClick().assertIsOn()
        regra.onNodeWithText("3708").performClick()
        regra.onNodeWithText("10 min").performClick().assertIsSelected()
        regra.onNodeWithText("5 min").assertIsNotSelected()
        regra.onNodeWithText("Avisar").performClick()
        assertEquals(listOf(setOf("728", "3708") to 10), avisos)
    }

    @Test fun semMexerNosMinutosAvisaAosCinco() {
        regra.setContent { Dialogo() }
        regra.onNodeWithText("736").performClick()
        regra.onNodeWithText("Avisar").performClick()
        assertEquals(listOf(setOf("736") to 5), avisos)
    }

    @Test fun cancelarNaoAvisa() {
        regra.setContent { Dialogo() }
        regra.onNodeWithText("736").performClick()
        regra.onNodeWithText("Cancelar").performClick()
        assertEquals(1, cancelou)
        assertTrue(avisos.isEmpty())
    }

    // O diálogo é uma janela à parte, que as capturas não apanham: capta-se o cartão.
    @Test fun avisa_me() = captureRoboImage("src/test/screenshots/avisa_me.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            AvisaMeConteudo(linhas, aoAvisar = { _, _ -> }, aoCancelar = {}, modifier = Modifier.width(312.dp))
        }
    }

    @Test fun avisa_me_escuro() = captureRoboImage("src/test/screenshots/avisa_me_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) {
            AvisaMeConteudo(linhas, aoAvisar = { _, _ -> }, aoCancelar = {}, modifier = Modifier.width(312.dp))
        }
    }

    // ---- o botão no ecrã da paragem ----

    @Composable
    private fun Ecra(vigiada: Boolean = false, presentes: List<Pair<String, String>> = linhas.map { it to it }, aoAvisaMe: () -> Unit = {}) =
        TemaParagemCerta(ModoTema.CLARO) {
            ParagemConteudo(
                nome = "Cais do Sodré", codigo = "060001", acessivel = null, favorita = false, vigiada = vigiada, estado = null, filtro = null,
                linhasPresentes = presentes, alertas = emptyList(), linhas = emptyMap(), agora = Instant.parse("2026-11-02T10:00:00Z"),
                aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = aoAvisaMe, aoReportar = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
            )
        }

    @Test fun oBotaoFicaAntesDeReportarErroEAbre() {
        var toques = 0
        regra.setContent { Ecra(aoAvisaMe = { toques++ }) }
        regra.onNodeWithText("Reportar erro").performScrollTo()
        regra.onNodeWithText("Avisa-me").performScrollTo()
        val avisa = regra.onNodeWithText("Avisa-me").fetchSemanticsNode().boundsInRoot.top
        val reportar = regra.onNodeWithText("Reportar erro").fetchSemanticsNode().boundsInRoot.top
        assertTrue(avisa < reportar)
        regra.onNodeWithText("Avisa-me").performClick()
        assertEquals(1, toques)
    }

    @Test fun paragemVigiadaDizAVigiarEPara() {
        var toques = 0
        regra.setContent { Ecra(vigiada = true, aoAvisaMe = { toques++ }) }
        regra.onNodeWithText("Avisa-me").assertDoesNotExist()
        regra.onNodeWithText("A vigiar · Parar").performScrollTo().performClick()
        assertEquals(1, toques)
    }

    @Test fun semLinhasNaParagemNaoHaNadaParaVigiar() {
        regra.setContent { Ecra(presentes = emptyList()) }
        regra.onNodeWithText("Avisa-me").performScrollTo().assertIsNotEnabled()
    }

    // ---- avisar: a permissão das notificações e o serviço ----

    private fun avisaMe(registo: RegistoFalso) = regra.setContent {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registo },
        ) {
            TemaParagemCerta(ModoTema.CLARO) { AvisaMe(sodre, "Cais do Sodré", linhas, aoFechar = { fechou++ }) }
        }
    }

    private fun avisarLinhasEMinutos() {
        regra.onNodeWithText("728").performClick()
        regra.onNodeWithText("736").performClick()
        regra.onNodeWithText("15 min").performClick()
        regra.onNodeWithText("Avisar").performClick()
        regra.waitForIdle()
    }

    /** O serviço arrancado é o pedido VIGIAR da paragem, com as linhas e os minutos escolhidos. */
    private fun assertArrancouComOPedido(minutos: Int) {
        val esperado = ServicoAvisoChegada.vigiar(c, sodre, "Cais do Sodré", setOf("728", "736"), minutos)
        val real: Intent = shadowOf(c as Application).nextStartedService
        assertEquals(ServicoAvisoChegada.ACAO_VIGIAR, real.action)
        assertEquals(esperado.component, real.component)
        assertEquals(esperado.extras!!.keySet(), real.extras!!.keySet())
        fun valor(i: Intent, k: String) = i.extras!!.let { @Suppress("DEPRECATION") it.get(k) }.let { (it as? Array<*>)?.toSet() ?: it }
        esperado.extras!!.keySet().forEach { assertEquals(it, valor(esperado, it), valor(real, it)) }
    }

    @Test fun avisarArrancaOServicoSemPedirNada() {
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val registo = RegistoFalso(dar = false)
        avisaMe(registo)
        avisarLinhasEMinutos()
        assertArrancouComOPedido(15)
        assertTrue(registo.pedidas.isEmpty())
        assertNull(ShadowToast.getTextOfLatestToast())
        assertEquals(1, fechou)
    }

    @Test fun semAPermissaoPedeEArrancaDepoisDeConcedida() {
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val registo = RegistoFalso(dar = true)
        avisaMe(registo)
        avisarLinhasEMinutos()
        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), registo.pedidas)
        assertArrancouComOPedido(15)
        assertNull(ShadowToast.getTextOfLatestToast())
        assertEquals(1, fechou)
    }

    @Test fun permissaoRecusadaExplicaEArrancaMesmoAssim() {
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val registo = RegistoFalso(dar = false)
        avisaMe(registo)
        avisarLinhasEMinutos()
        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), registo.pedidas)
        assertEquals("Os avisos vão só tocar e vibrar, sem aparecer no ecrã.", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)
        assertArrancouComOPedido(15)
        assertEquals(1, fechou)
    }
}
