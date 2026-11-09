package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** O contorno de um filtro: 1 dp na cor primária quando está escolhido. */
@Composable
fun bordaDoFiltro(escolhido: Boolean, ativo: Boolean = true): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = ativo, selected = escolhido,
        selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.dp,
    )

@Composable
fun BotaoPrincipal(aoTocar: () -> Unit, modifier: Modifier = Modifier, ativo: Boolean = true, conteudo: @Composable RowScope.() -> Unit) {
    Button(aoTocar, modifier, enabled = ativo, shape = MaterialTheme.shapes.medium, content = conteudo)
}

@Composable
fun BotaoSecundario(aoTocar: () -> Unit, modifier: Modifier = Modifier, ativo: Boolean = true, conteudo: @Composable RowScope.() -> Unit) {
    val primaria = MaterialTheme.colorScheme.primary
    OutlinedButton(
        aoTocar, modifier, enabled = ativo, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaria),
        border = if (ativo) BorderStroke(1.dp, primaria) else ButtonDefaults.outlinedButtonBorder(enabled = false),
        content = conteudo,
    )
}
