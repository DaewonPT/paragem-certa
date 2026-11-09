package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstacoesGira
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.bussola.RegistoFalso
import pt.pixellusitano.paragemcerta.ui.mapa.Dono
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class InicioEcraTest {
    @get:Rule val regra = createComposeRule()

    private val app = RuntimeEnvironment.getApplication() as Application
    private val bd = Room.inMemoryDatabaseBuilder(app, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    // Tudo o que o Início pede falha: sem rede, o mapa de teste chega.
    private val http = HttpPorUrl("" to listOf<() -> Resposta>({ throw IOException("sem rede") }))
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private var ligacao: LigacaoMapaInicio? = null
    private var pesquisou = 0
    private var abriuConta = 0
    private var mostrar by mutableStateOf(true)

    @Before fun preparar() {
        File(app.noBackupFilesDir, "conta.json").delete()
        File(app.filesDir, "config.json").delete()
    }

    @After fun fechar() {
        mostrar = false // a composição acaba antes de a base fechar
        regra.waitForIdle()
        bd.close()
    }

    private fun abrir(
        contaLigada: Boolean = false,
        comPermissao: Boolean = true,
        escuro: Boolean = false,
        letra: Float = 1f,
        comBussola: Boolean = false,
        registo: RegistoFalso? = null,
        dono: Dono? = null,
        painelAlto: Boolean = false,
        abrirFavoritos: Boolean = false,
    ) {
        val permissoes = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (comPermissao) shadowOf(app).grantPermissions(*permissoes) else shadowOf(app).denyPermissions(*permissoes)
        if (comBussola) shadowOf(app.getSystemService(SensorManager::class.java)).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
        val configuracoes = Configuracoes(app, servidor)
        val comunidade = Comunidade(servidor, Conta(app, servidor, repositorio), configuracoes, preferencias)
        val estacoes = EstacoesGira(File(app.cacheDir, "gira-teste"), Gira(http)) { Instant.parse("2026-11-03T09:00:00Z") }
        val locais = listOfNotNull<ProvidedValue<*>>(
            registo?.let { r -> LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = r } },
            dono?.let { LocalLifecycleOwner provides it },
        )
        regra.setContent {
            if (mostrar) CompositionLocalProvider(*locais.toTypedArray()) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(letra)) {
                    TemaParagemCerta(if (escuro) ModoTema.ESCURO else ModoTema.CLARO) {
                        InicioEcra(
                            repositorio, comunidade, configuracoes, preferencias, estacoes,
                            aoAbrirParagem = {},
                            aoPesquisar = { pesquisou++ },
                            painel = { _, _ -> if (painelAlto) Box(Modifier.fillMaxWidth().height(2_000.dp)) },
                            abrirFavoritos = abrirFavoritos,
                            aoAbrirConta = if (contaLigada) ({ abriuConta++ }) else null,
                            mapa = {
                                ligacao = it
                                Box(Modifier.fillMaxSize().background(Color(0xFFDDE5F0)).testTag("mapa"))
                            },
                        )
                    }
                }
            }
        }
        regra.waitUntil(5_000) { ligacao != null }
    }

    // ---- o botão da posição e o modo bússola ----

    private val centrar = "Centrar na minha posição"
    private val sair = "Sair do modo bússola"
    private val dica = "Arrasta o mapa ou toca outra vez para sair"
    private fun botao() = regra.onNode(hasContentDescription(centrar) or hasContentDescription(sair))
    private fun estado(texto: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, texto)
    private val semEstado = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun entrarNaBussola() {
        botao().performClick()
        botao().performClick()
        regra.onNodeWithContentDescription(sair).assertExists()
    }

    @Test fun doisToquesEntramNaBussola() {
        abrir(comBussola = true)
        botao().performClick()
        regra.onNodeWithContentDescription(centrar).assert(estado("Centrado; toca outra vez para seguir com a bússola"))
        assertFalse(ligacao!!.bussola)
        botao().performClick()
        regra.onNodeWithContentDescription(sair).assert(semEstado)
        assertTrue(ligacao!!.bussola)
        regra.onNodeWithContentDescription("Voltar ao norte").assertIsDisplayed()
    }

    @Test fun semPermissaoNuncaEntraNaBussola() {
        marcarLocalizacaoPerguntada(app) // sem a explicação por cima do mapa
        val registo = RegistoFalso(dar = false)
        abrir(comPermissao = false, comBussola = true, registo = registo)
        repeat(2) {
            botao().performClick()
            regra.onNodeWithContentDescription(centrar).assert(semEstado)
            assertFalse(ligacao!!.bussola)
        }
        assertEquals(2, registo.pedidas.size) // cada toque pede a permissão
        regra.onNodeWithContentDescription("Voltar ao norte").assertDoesNotExist()
    }

    @Test fun semBussolaSoVoltaACentrar() {
        abrir()
        botao().performClick()
        botao().performClick()
        regra.onNodeWithContentDescription(centrar).assert(estado("Centrado"))
        assertFalse(ligacao!!.bussola)
    }

    @Test fun bussolaPequenaSaiDoModo() {
        abrir(comBussola = true)
        entrarNaBussola()
        regra.onNodeWithContentDescription("Voltar ao norte").performClick()
        regra.onNodeWithContentDescription("Voltar ao norte").assertDoesNotExist()
        regra.onNodeWithContentDescription(centrar).assert(semEstado)
        assertFalse(ligacao!!.bussola)
    }

    @Test fun mapaQueDeixaDeSeguirPoeLivre() {
        abrir(comBussola = true)
        entrarNaBussola()
        regra.runOnIdle { ligacao!!.aoDeixarDeSeguir() }
        regra.onNodeWithContentDescription(centrar).assert(semEstado)
        assertFalse(ligacao!!.bussola)
    }

    @Test fun arrastarPoeLivre() {
        abrir(comBussola = true)
        botao().performClick()
        regra.runOnIdle { ligacao!!.aoArrastar() }
        regra.onNodeWithContentDescription(centrar).assert(semEstado)
        botao().performClick()
        regra.onNodeWithContentDescription(centrar).assert(estado("Centrado; toca outra vez para seguir com a bússola"))
        assertFalse(ligacao!!.bussola)
    }

    @Test fun retirarAPermissaoVoltaALivre() {
        val dono = Dono().also { it.registo.currentState = Lifecycle.State.RESUMED }
        abrir(comBussola = true, dono = dono)
        botao().performClick()
        regra.onNodeWithContentDescription(centrar).assert(estado("Centrado; toca outra vez para seguir com a bússola"))
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        regra.runOnIdle {
            dono.registo.currentState = Lifecycle.State.STARTED // ON_PAUSE: a pessoa foi às definições do telemóvel
            dono.registo.currentState = Lifecycle.State.RESUMED
        }
        regra.onNodeWithContentDescription(centrar).assert(semEstado)
    }

    @Test fun dicaSoNaPrimeiraVez() {
        abrir(comBussola = true)
        entrarNaBussola()
        regra.onNodeWithText(dica).assertIsDisplayed()
        assertTrue(runBlocking { preferencias.dicaBussolaVista.first() })
        // Sair do Início e voltar: o modo recomeça em livre e a dica já não aparece.
        mostrar = false
        regra.waitForIdle()
        mostrar = true
        regra.onNodeWithContentDescription(centrar).assert(semEstado)
        entrarNaBussola()
        regra.onNodeWithText(dica).assertDoesNotExist()
    }

    @Test fun dicaFechaSozinhaAos8s() {
        abrir(comBussola = true)
        entrarNaBussola()
        regra.onNodeWithText(dica).assertIsDisplayed()
        regra.mainClock.autoAdvance = false
        regra.mainClock.advanceTimeBy(7_000)
        regra.onNodeWithText(dica).assertExists()
        regra.mainClock.advanceTimeBy(1_100)
        regra.onNodeWithText(dica).assertDoesNotExist()
        regra.onNodeWithContentDescription(sair).assertExists() // fecha a dica, o modo fica
        regra.mainClock.autoAdvance = true
    }

    @Test fun alvosDoModo() {
        abrir(comBussola = true)
        botao().assertWidthIsAtLeast(52.dp).assertHeightIsAtLeast(52.dp)
        entrarNaBussola()
        botao().assertWidthIsAtLeast(52.dp).assertHeightIsAtLeast(52.dp)
        regra.onNodeWithContentDescription("Voltar ao norte").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun bussolaPequenaADireitaAbaixoDaFaixa() {
        abrir(comBussola = true)
        entrarNaBussola()
        val ecra = regra.onRoot().getUnclippedBoundsInRoot()
        val faixa = regra.onNodeWithTag("faixa").getUnclippedBoundsInRoot()
        val bussola = regra.onNodeWithContentDescription("Voltar ao norte").getUnclippedBoundsInRoot()
        assertEquals((ecra.right - 12.dp).value, bussola.right.value, 1f)
        assertEquals((faixa.bottom + 40.dp).value, bussola.top.value, 1f)
    }

    @Test fun botaoFicaPorCimaDoPainel() {
        abrir(painelAlto = true)
        val ecra = regra.onRoot().getUnclippedBoundsInRoot().bottom
        val fundoBotao = botao().getUnclippedBoundsInRoot().bottom
        assertEquals((ecra - alturaPainelFechado(780.dp) - 12.dp).value, fundoBotao.value, 1f)
    }

    @Test fun botaoSomeComOPainelAberto() {
        abrir(painelAlto = true, abrirFavoritos = true)
        regra.waitForIdle()
        botao().assertDoesNotExist()
    }

    @Test fun inicioBussola() {
        abrir(contaLigada = true, comBussola = true)
        entrarNaBussola()
        regra.onNodeWithText(dica).assertIsDisplayed()
        regra.onRoot().captureRoboImage("src/test/screenshots/inicio_bussola.png")
    }

    @Test fun controlosDaPosicaoSaoBotoes() {
        abrir(comBussola = true)
        val botao = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        regra.onNodeWithContentDescription(centrar).assert(botao)
        entrarNaBussola()
        regra.onNodeWithContentDescription("Voltar ao norte").assert(botao)
    }

    @Test fun pesquisaAbreAPesquisa() {
        abrir()
        regra.onNodeWithText("Para onde vais?").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, pesquisou)
    }

    @Test fun contaSoComOInterruptor() {
        abrir()
        regra.onNodeWithContentDescription("Conta").assertDoesNotExist()
    }

    @Test fun contaComInterruptorAbreAConta() {
        abrir(contaLigada = true)
        regra.onNodeWithContentDescription("Conta").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, abriuConta)
    }

    @Test fun faixaComLetraA200() {
        abrir(contaLigada = true, letra = 2f)
        regra.onNodeWithText("Para onde vais?").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        regra.onNodeWithContentDescription("Conta").assertHeightIsAtLeast(48.dp)
    }

    @Test fun margemDoMapaSegueAFaixa() {
        abrir(contaLigada = true)
        val faixa = regra.onNodeWithTag("faixa").getUnclippedBoundsInRoot().height
        assertEquals((faixa + 8.dp).value, ligacao!!.margemTopo.value, 0.01f)
    }

    @Test fun inicioClaro() {
        abrir(contaLigada = true)
        regra.onRoot().captureRoboImage("src/test/screenshots/inicio_claro.png")
    }

    @Test fun inicioEscuro() {
        abrir(contaLigada = true, escuro = true)
        regra.onRoot().captureRoboImage("src/test/screenshots/inicio_escuro.png")
    }

    @Test fun inicioLetra200() {
        abrir(contaLigada = true, letra = 2f)
        regra.onRoot().captureRoboImage("src/test/screenshots/inicio_letra200.png")
    }
}
