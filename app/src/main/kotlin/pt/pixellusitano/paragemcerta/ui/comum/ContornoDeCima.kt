package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp

/**
 * O contorno de cima de uma folha que sobe por cima do mapa: a linha de cima e os cantos da [forma] (de 28 dp), por cima do
 * conteúdo e sem ocupar espaço. No escuro a sombra não se vê e é ele que separa a folha do mapa.
 * Limite: abaixo dos cantos os lados da folha não têm contorno; num ecrã mais largo do que a folha, ela separa-se do mapa só por cima.
 */
fun Modifier.contornoDeCima(cor: Color, forma: Shape) = drawWithCache {
    val traco = 1.dp.toPx()
    // Com o dobro da altura, o fundo do contorno cai fora do desenho; o corte no fim dos cantos não deixa os lados a direito soltos.
    val contorno = forma.createOutline(Size(size.width - traco, size.height * 2), layoutDirection, this)
    val fimDosCantos = 28.dp.toPx()
    onDrawWithContent {
        drawContent()
        clipRect(bottom = fimDosCantos) { inset(traco / 2) { drawOutline(contorno, cor, style = Stroke(traco)) } }
    }
}
