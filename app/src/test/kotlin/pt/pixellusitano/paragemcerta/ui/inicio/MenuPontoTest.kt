package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.rotas.rememberGuardarLocal
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp")
class MenuPontoTest {
    @get:Rule val regra = createComposeRule()

    private val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val repo = HttpPorUrl().let { Repositorio(bd, CarrisMetropolitana(it), Transitous(it), GeoApi(it)) }
    private val ponto = Lugar("Rua Augusta 10", 38.7101, -9.1368, TipoLugar.LOCAL)

    @After fun fechar() = bd.close()

    @Test fun guardarDaTresOpcoes() {
        val pedidos = mutableListOf<String>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { MenuPonto(ponto, aoIrPara = {}, aoPartirDaqui = {}, aoGuardar = { pedidos += it }, aoFechar = {}) }
        }
        regra.onNodeWithText("Guardar local").assertDoesNotExist()
        listOf("Guardar como casa", "Guardar como trabalho", "Guardar como local").forEach { texto ->
            // Cada opção é um só botão para o TalkBack, como as outras do menu.
            regra.onNode(hasText(texto)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        }
        assertEquals(listOf("CASA", "TRABALHO", "OUTRO"), pedidos)
    }

    @Test @Config(qualifiers = "w360dp-h560dp")
    fun abreInteiroNumEcraBaixo() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { MenuPonto(ponto, aoIrPara = {}, aoPartirDaqui = {}, aoGuardar = {}, aoFechar = {}) }
        }
        // Aberto a meio, as últimas das cinco opções ficavam por baixo do ecrã.
        regra.onNodeWithText("Guardar como local").assertIsDisplayed()
    }

    @Test fun guardarComoTrabalhoSubstituiOAnterior() {
        runBlocking { repo.guardarLocal("Trabalho", Lugar("Escritório antigo", 38.75, -9.15, TipoLugar.LOCAL), "TRABALHO") }
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                // Como no Início: Casa e Trabalho guardam-se logo, com o nome fixo.
                val guardar = rememberGuardarLocal(repo)
                MenuPonto(ponto, aoIrPara = {}, aoPartirDaqui = {}, aoGuardar = { guardar(ponto, it) }, aoFechar = {})
            }
        }
        regra.onNodeWithText("Guardar como trabalho").performClick()
        regra.waitForIdle()

        val trabalhos = runBlocking { repo.locais().first() }.filter { it.tipo == "TRABALHO" }
        assertEquals(1, trabalhos.size)
        assertEquals("Trabalho", trabalhos[0].nome)
        assertEquals(ponto.lat, trabalhos[0].lugar.lat, 1e-9)
        assertEquals(ponto.lon, trabalhos[0].lugar.lon, 1e-9)
        assertEquals("Guardado nos favoritos", ShadowToast.getTextOfLatestToast())
    }
}
