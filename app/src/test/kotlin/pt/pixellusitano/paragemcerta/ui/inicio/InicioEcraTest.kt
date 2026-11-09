package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
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
import androidx.room.Room
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    private fun abrir(contaLigada: Boolean = false, comPermissao: Boolean = true, escuro: Boolean = false, letra: Float = 1f) {
        val permissoes = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (comPermissao) shadowOf(app).grantPermissions(*permissoes) else shadowOf(app).denyPermissions(*permissoes)
        val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
        val configuracoes = Configuracoes(app, servidor)
        val comunidade = Comunidade(servidor, Conta(app, servidor, repositorio), configuracoes, preferencias)
        val estacoes = EstacoesGira(File(app.cacheDir, "gira-teste"), Gira(http)) { Instant.parse("2026-11-03T09:00:00Z") }
        regra.setContent {
            if (mostrar) DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(letra)) {
                TemaParagemCerta(if (escuro) ModoTema.ESCURO else ModoTema.CLARO) {
                    InicioEcra(
                        repositorio, comunidade, configuracoes, preferencias, estacoes,
                        aoAbrirParagem = {},
                        aoPesquisar = { pesquisou++ },
                        aoAbrirConta = if (contaLigada) ({ abriuConta++ }) else null,
                        mapa = {
                            ligacao = it
                            Box(Modifier.fillMaxSize().background(Color(0xFFDDE5F0)).testTag("mapa"))
                        },
                    )
                }
            }
        }
        regra.waitUntil(5_000) { ligacao != null }
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
