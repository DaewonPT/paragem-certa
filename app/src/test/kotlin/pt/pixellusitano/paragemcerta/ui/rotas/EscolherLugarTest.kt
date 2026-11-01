package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
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

    /** Abre o ecrã, escreve "rua a" e deixa passar os 300 ms de espera antes da pesquisa. */
    private fun pesquisarCom(pesquisar: suspend (String) -> List<Lugar>?) {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                EscolherLugarEcra("Para", comPosicao = false, locais = emptyList(), recentes = emptyList(), pesquisar = pesquisar, aoEscolher = {}, aoVoltar = {})
            }
        }
        regra.onNode(hasSetTextAction()).performTextInput("rua a")
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

    @Test fun semRedeDizQueNaoFoiPossivelProcurar() {
        pesquisarCom { null }
        regra.onNodeWithText(contexto.getString(R.string.escolher_sem_rede)).assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.escolher_sem_resultados)).assertDoesNotExist()
    }
}
