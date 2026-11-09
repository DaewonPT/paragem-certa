package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
