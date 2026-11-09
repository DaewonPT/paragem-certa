package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.ui.inicio.BlocoLinha
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp")
class FavoritosEcraTest {
    @get:Rule val regra = createComposeRule()

    private val cais = RefParagem(Fonte.CM, "060419")
    private val f3708 = LinhaFavorita(RefLinha(Fonte.CM, "3708"), 1, cais, "3708", "Costa da Caparica (Terminal)", 0)
    private val fAz = LinhaFavorita(RefLinha(Fonte.TRANSITOUS, "pt-Metro-Lisboa:Az"), 0, RefParagem(Fonte.TRANSITOUS, "pt-Metro-Lisboa_CM"), "Az", "Santa Apolónia", 1)
    private val f999 = LinhaFavorita(RefLinha(Fonte.TRANSITOUS, "pt-Carris:999"), 0, RefParagem(Fonte.TRANSITOUS, "pt-Carris_3803"), "999", "Algés", 2)
    private val b3708 = BlocoLinha(f3708, "Cais do Sodré (Terminal)", null, existe = true)
    private val bAz = BlocoLinha(fAz, "Colégio Militar/Luz", null, existe = null)
    private val b999 = BlocoLinha(f999, "Cais Sodré", null, existe = false)
    private val paragens = listOf(Favorita(cais, "Cais do Sodré (Terminal)", 0, 38.7, -9.14))

    @Composable
    private fun Ecra(
        linhas: List<BlocoLinha>, favoritas: List<Favorita> = paragens, aoAbrirLinha: (LinhaFavorita) -> Unit = {},
        aoSubirLinha: (LinhaFavorita) -> Unit = {}, aoRemoverLinha: (LinhaFavorita) -> Unit = {},
    ) = FavoritosConteudo(
        favoritas = favoritas, locais = emptyList(), aoAbrirFavorita = {}, aoSubirFavorita = {}, aoRemoverFavorita = {},
        aoRenomearLocal = { _, _ -> }, aoSubirLocal = {}, aoApagarLocal = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
        linhas = linhas, aoAbrirLinha = aoAbrirLinha, aoSubirLinha = aoSubirLinha, aoRemoverLinha = aoRemoverLinha,
    )

    @Test fun menuFavoritosTemLinhas() {
        val abertas = mutableListOf<LinhaFavorita>()
        val subidas = mutableListOf<LinhaFavorita>()
        val removidas = mutableListOf<LinhaFavorita>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(listOf(b3708, bAz), aoAbrirLinha = { abertas += it }, aoSubirLinha = { subidas += it }, aoRemoverLinha = { removidas += it }) }
        }
        regra.onNodeWithText("Linhas").assertIsDisplayed()
        regra.onNodeWithText("3708 → Costa da Caparica (Terminal) em Cais do Sodré (Terminal)").assertIsDisplayed()
        regra.onNodeWithText("Az → Santa Apolónia em Colégio Militar/Luz").assertIsDisplayed()

        regra.onNodeWithText("Az → Santa Apolónia em Colégio Militar/Luz").performClick()
        regra.onNodeWithText("Subir").performClick() // só a segunda tem "Subir"; as paragens têm só uma e a primeira não sobe
        regra.onAllNodesWithText("Remover")[1].performClick() // [0] é a paragem, [1] a 3708, [2] a Az
        regra.onAllNodesWithText("Remover")[2].performClick()
        assertEquals(listOf(fAz), abertas)
        assertEquals(listOf(fAz), subidas)
        assertEquals(listOf(f3708, fAz), removidas)
    }

    @Test fun linhaComOCatalogoACarregarMantemTodasAsAcoes() {
        // Telemóvel novo: o nome guardado fica, com "A carregar…", e continua a abrir, a subir e a remover.
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(listOf(b3708, bAz)) } }
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodesWithText("Esta linha já não existe.").assertCountEquals(0)
        regra.onNodeWithText("Subir").assertIsDisplayed()
    }

    @Test fun linhaQueJaNaoExisteSoDeixaRemover() {
        val abertas = mutableListOf<LinhaFavorita>()
        val subidas = mutableListOf<LinhaFavorita>()
        val removidas = mutableListOf<LinhaFavorita>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Ecra(listOf(b3708, b999), aoAbrirLinha = { abertas += it }, aoSubirLinha = { subidas += it }, aoRemoverLinha = { removidas += it })
            }
        }
        regra.onNodeWithText("Esta linha já não existe.").assertIsDisplayed()
        regra.onNodeWithText("999 → Algés em Cais Sodré").performClick()
        regra.onAllNodesWithText("Subir").assertCountEquals(0)
        regra.onAllNodesWithText("Remover")[2].performClick()
        assertTrue(abertas.isEmpty())
        assertTrue(subidas.isEmpty())
        assertEquals(listOf(f999), removidas)
    }

    @Test fun semLinhasNaoHaSeccaoNemMudaOResto() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(emptyList()) } }
        regra.onAllNodesWithText("Linhas").assertCountEquals(0)
        regra.onNodeWithText("Cais do Sodré (Terminal)").assertIsDisplayed()
    }

    @Test fun soComLinhasNaoDizQueNaoTemFavoritas() {
        val aviso = "Ainda não tens favoritas. Abre uma paragem ou uma linha e toca na estrela."
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(listOf(b3708), favoritas = emptyList()) } }
        regra.onAllNodesWithText(aviso).assertCountEquals(0)
    }

    @Test fun semParagensNemLinhasDizOndeGuardar() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(emptyList(), favoritas = emptyList()) } }
        regra.onNodeWithText("Ainda não tens favoritas. Abre uma paragem ou uma linha e toca na estrela.").assertIsDisplayed()
    }

    @Test fun favoritos_com_linhas_claro() = captureRoboImage("src/test/screenshots/favoritos_com_linhas_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(listOf(b3708, bAz, b999)) }
    }
}
