package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
class EscolherLugarTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()

    /** Abre o ecrã, escreve o texto ("rua a") e deixa passar os 300 ms de espera antes da pesquisa. */
    private fun pesquisarCom(texto: String = "rua a", pesquisar: suspend (String) -> List<Lugar>?) {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                EscolherLugarEcra("Para", comPosicao = false, locais = emptyList(), recentes = emptyList(), pesquisar = pesquisar, aoEscolher = {}, aoVoltar = {})
            }
        }
        regra.onNode(hasSetTextAction()).performTextInput(texto)
        regra.mainClock.advanceTimeBy(400)
        regra.waitForIdle()
    }

    @Test fun resultadoTemAsAcoesDeGuardarNoNoDoTalkBack() {
        pesquisarCom { listOf(Lugar("Rua Augusta", 38.71, -9.137, TipoLugar.MORADA)) }
        val no = regra.onNodeWithText("Rua Augusta")
        no.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val esperadas = listOf(R.string.guardar_como_casa, R.string.guardar_como_trabalho, R.string.guardar_como_local).map(contexto::getString)
        assertEquals(esperadas, no.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
    }

    @Test fun resultadoDeCodigoPostalMostraAFonte() {
        pesquisarCom { listOf(Lugar("1400-038 · Doca do Bom Sucesso", 38.69, -9.21, TipoLugar.MORADA, "Lisboa"), Lugar("Rua Augusta", 38.71, -9.137, TipoLugar.MORADA)) }
        // Só o código postal leva a atribuição à GeoAPI.pt.
        regra.onAllNodesWithText(contexto.getString(R.string.fonte_geoapi)).assertCountEquals(1)
    }

    @Test fun codigoPostalInexistenteDizNaoEncontrado() {
        pesquisarCom("1400-099") { emptyList() }
        regra.onNodeWithText(contexto.getString(R.string.pesquisa_cp_nao_encontrado)).assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.escolher_sem_resultados)).assertDoesNotExist()
    }

    @Test fun semRedeDizQueNaoFoiPossivelProcurar() {
        pesquisarCom { null }
        regra.onNodeWithText(contexto.getString(R.string.escolher_sem_rede)).assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.escolher_sem_resultados)).assertDoesNotExist()
    }

    private fun abrirSemTexto() = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            EscolherLugarEcra(
                "Onde é a tua casa?", comPosicao = true, locais = emptyList(),
                recentes = listOf(Lugar("Café Central", 38.71, -9.14, TipoLugar.LOCAL)),
                pesquisar = { null }, aoEscolher = {}, aoVoltar = {},
            )
        }
    }

    @Test fun recentesEUmCabecalho() {
        abrirSemTexto()
        regra.onNodeWithText(contexto.getString(R.string.escolher_recentes)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun rotuloDoCampoFicaDepoisDeEscrever() {
        abrirSemTexto()
        regra.onNode(hasSetTextAction()).performTextInput("rua")
        regra.onNode(hasSetTextAction()).assert(
            SemanticsMatcher("tem o rótulo e o texto") { no ->
                no.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty().contains("Onde é a tua casa?") &&
                    no.config.getOrNull(SemanticsProperties.EditableText)?.text == "rua"
            },
        )
    }

    @Test fun maisOpcoesEncontraSePelaDescricaoCom48dp() {
        pesquisarCom { listOf(Lugar("Rua Augusta", 38.71, -9.137, TipoLugar.MORADA)) }
        regra.onNodeWithContentDescription("Mais opções").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    // Com letra a 200 %: o voltar continua com 48 dp e à vista, e o campo fica à vista e ao lado dele, sem lhe passar por cima.
    @Test fun comLetraA200OVoltarEOCampoContinuamAVistaESemSobrepor() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) {
                    EscolherLugarEcra("Onde é a tua casa?", comPosicao = true, locais = emptyList(), recentes = emptyList(), pesquisar = { null }, aoEscolher = {}, aoVoltar = {})
                }
            }
        }
        val voltar = regra.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val campo = regra.onNode(hasSetTextAction()).assertIsDisplayed()
        val v = voltar.fetchSemanticsNode().boundsInRoot
        val c = campo.fetchSemanticsNode().boundsInRoot
        assertTrue("o campo $c sobrepõe o voltar $v", c.left >= v.right || c.top >= v.bottom)
    }

    @Test fun voltarTem48dp() {
        abrirSemTexto()
        regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
