package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.robolectric.annotation.Config
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CartaoAzulejoTest {
    @get:Rule val regra = createComposeRule()

    @Test fun clicavel_temUmSoNoBotaoComOTexto() {
        regra.setContent { CartaoAzulejo(aoTocar = {}) { Text("dentro") } }
        regra.onNode(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertExists()
        regra.onAllNodes(hasClickAction() and hasText("dentro")).assertCountEquals(1)
    }

    @Test fun semAoTocar_naoTemNosClicaveis() {
        regra.setContent { CartaoAzulejo { Text("dentro") } }
        regra.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    // Três fatias de um cartão repartido: a primeira só tem os cantos de cima redondos, a do meio nenhuns, a última só os de baixo.
    @Test fun fatiasTemOsCantosCertos() {
        regra.setContent {
            Column(Modifier.background(Color.Black)) {
                (0..2).forEach { i ->
                    CartaoAzulejo(Modifier.testTag("f$i").size(60.dp), forma = formaDaFatia(i, 3), cor = Color.Red, contorno = Color.Red) { Box(Modifier.size(60.dp)) }
                }
            }
        }
        val pixeis = (0..2).map { i ->
            val img = regra.onNodeWithTag("f$i").captureToImage().toPixelMap()
            listOf(img[0, 0], img[0, img.height - 1]).map { it.red > 0.5f } // canto de cima, canto de baixo: cheio ou vazio (a sombra mexe um pouco no vermelho)
        }
        assertEquals(listOf(false, true), pixeis[0])
        assertEquals(listOf(true, true), pixeis[1])
        assertEquals(listOf(true, false), pixeis[2])
    }

    // A junção de duas fatias tem uma só linha fina (a divisória), sem contorno duplo nem sombra a mais.
    @Config(qualifiers = "xxhdpi")
    @Test fun juncaoDeFatias_temUmaSoLinhaFina() {
        regra.setContent {
            Column(Modifier.background(Color.White).testTag("col")) {
                (0..1).forEach { i -> FatiaAzulejo(i, 2) { Box(Modifier.size(60.dp)) } }
            }
        }
        val img = regra.onNodeWithTag("col").captureToImage().toPixelMap()
        val junta = with(regra.density) { 60.dp.roundToPx() }
        val x = with(regra.density) { 16.dp.roundToPx() + 90 } // a meio do cartão (60 dp de largura)
        val linhaFina = with(regra.density) { 1.dp.roundToPx() }
        assertEquals(true, img[x, 0].red < 0.93f) // o topo da primeira
        assertEquals(true, img[x, img.height - 1].red < 0.93f) // o fundo da última
        val escuras = (junta - 8..junta + 8).count { img[x, it].red < 0.93f }
        assertEquals(linhaFina, escuras)
        // Nas laterais há contorno de cada lado em todas as fatias, inclusive na junção.
        assertEquals(true, img[with(regra.density) { 16.dp.roundToPx() }, junta + 10].red < 0.93f)
    }

    @Test fun comCorEContorno_desenhaComEles() {
        regra.setContent {
            Box(Modifier.background(Color.Black)) {
                CartaoAzulejo(Modifier.testTag("cartao").size(40.dp), aoTocar = {}, cor = Color.Red, contorno = Color.Green) { Box(Modifier.size(40.dp)) }
            }
        }
        regra.onNodeWithTag("cartao").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val img = regra.onNodeWithTag("cartao").captureToImage()
        val centro = img.toPixelMap()[img.width / 2, img.height / 2]
        val borda = img.toPixelMap()[img.width / 2, 0]
        assertEquals(Color.Red, centro)
        assertEquals(Color.Green, borda)
    }
}
