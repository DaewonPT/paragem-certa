package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A forma da fatia [indice] de [total] de um cartão repartido por itens de uma lista: só a primeira tem os cantos de cima redondos e só a última os de baixo. */
@Composable
fun formaDaFatia(indice: Int, total: Int): CornerBasedShape {
    val grande = MaterialTheme.shapes.large
    val semCima = if (indice == 0) grande else grande.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
    return if (indice == total - 1) semCima else semCima.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
}

/** Cartão com contorno fino, na [forma], na cor da superfície ou em [cor] e [contorno]; com [aoTocar] é um só botão. */
@Composable
fun CartaoAzulejo(
    modifier: Modifier = Modifier,
    aoTocar: (() -> Unit)? = null,
    forma: Shape = MaterialTheme.shapes.large,
    cor: Color = MaterialTheme.colorScheme.surface,
    contorno: Color = MaterialTheme.colorScheme.outlineVariant,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    val borda = BorderStroke(1.dp, contorno)
    if (aoTocar != null) {
        Surface(aoTocar, modifier.semantics { role = Role.Button }, shape = forma, color = cor, border = borda, shadowElevation = 1.dp) {
            Column(content = conteudo)
        }
    } else {
        Surface(modifier, shape = forma, color = cor, border = borda, shadowElevation = 1.dp) { Column(content = conteudo) }
    }
}

/**
 * Uma fatia de um cartão repartido por itens de uma lista preguiçosa: desenha só a sua parte de um cartão (os lados em todas, o topo
 * só na primeira e o fundo só na última), sem sombra, e a partir da segunda tem a divisória por dentro.
 */
@Composable
fun FatiaAzulejo(indice: Int, total: Int, modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    val forma = formaDaFatia(indice, total)
    val contorno = MaterialTheme.colorScheme.outlineVariant
    val raio = MaterialTheme.shapes.large
    Surface(
        modifier.padding(horizontal = 16.dp).drawWithContent {
            drawContent()
            // O contorno do cartão inteiro, com o que sobra para fora desta fatia cortado: só fica a parte que lhe pertence.
            val fora = 2 * raio.topStart.toPx(size, this)
            val cima = if (indice == 0) 0f else -fora
            val baixo = if (indice == total - 1) size.height else size.height + fora
            val largura = 1.dp.toPx()
            clipRect {
                drawPath(
                    Path().apply {
                        addRoundRect(RoundRect(Rect(largura / 2, cima + largura / 2, size.width - largura / 2, baixo - largura / 2), CornerRadius(raio.topStart.toPx(size, this@drawWithContent) - largura / 2)))
                    },
                    contorno, style = Stroke(largura),
                )
            }
        },
        shape = forma, color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            if (indice > 0) HorizontalDivider(color = contorno)
            conteudo()
        }
    }
}
