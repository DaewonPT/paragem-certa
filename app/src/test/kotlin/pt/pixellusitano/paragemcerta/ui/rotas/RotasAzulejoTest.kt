package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class RotasAzulejoTest {
    @get:Rule val regra = createComposeRule()

    private val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
    private var origens = 0
    private var destinos = 0

    @Composable
    private fun Rotas() = RotasConteudo(
        pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)),
        estado = EstadoRotas.Prontas(rotas, etiquetas(rotas)), filtros = Filtros(),
        alertas = emptySet(), agora = Instant.parse("2026-11-01T02:20:00Z"),
        aoEscolherOrigem = { origens++ }, aoEscolherDestino = { destinos++ }, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
        aoAbrirRota = {}, aoTentarOutraVez = {}, aoMaisCedo = {}, aoMaisTarde = {}, modifier = Modifier.width(360.dp),
    )

    @Test fun deParaFicamNaFaixaEOsDoisCamposAbremOEscolherLugar() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Rotas() } }
        val de = regra.onNodeWithText("De").fetchSemanticsNode().boundsInRoot
        // À esquerda do campo está a faixa cobalto, não o fundo marfim.
        val pixel = regra.onRoot().captureToImage().toPixelMap()[2, de.center.y.toInt()]
        assertTrue("fora da faixa: $pixel", pixel.red < 0.3f && pixel.blue > 0.5f)
        regra.onNodeWithText("De").performClick()
        regra.onNodeWithText("Para").performClick()
        assertEquals(1, origens)
        assertEquals(1, destinos)
    }

    @Test fun filtroEscolhidoTemABordaDoFiltro() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Rotas() } }
        val img = regra.onNode(hasText("Partir agora") and isSelectable()).captureToImage().toPixelMap()
        val borda = (0..2).map { img[it, img.height / 2] }
        assertTrue("sem borda primária: $borda", borda.any { it.red < 0.3f && it.blue > 0.5f })
    }

    @Test fun duracaoNaoPartaComLetraA200() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { TemaParagemCerta(ModoTema.CLARO) { Rotas() } }
        }
        val nos = regra.onAllNodes(hasText(textoDuracao(rotas[0].duracao)), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("a duração não está no ecrã", nos.isNotEmpty())
        nos.forEach { no ->
            val resultados = mutableListOf<TextLayoutResult>()
            no.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
            assertEquals(1, resultados.single().lineCount)
        }
    }
}
