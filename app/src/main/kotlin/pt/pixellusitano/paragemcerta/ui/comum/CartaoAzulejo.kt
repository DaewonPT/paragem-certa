package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Cartão de superfície com contorno fino; com [aoTocar] é um só botão. */
@Composable
fun CartaoAzulejo(modifier: Modifier = Modifier, aoTocar: (() -> Unit)? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    val cores = MaterialTheme.colorScheme
    val forma = MaterialTheme.shapes.large
    val borda = BorderStroke(1.dp, cores.outlineVariant)
    if (aoTocar != null) {
        Surface(aoTocar, modifier.semantics { role = Role.Button }, shape = forma, color = cores.surface, border = borda, shadowElevation = 1.dp) {
            Column(content = conteudo)
        }
    } else {
        Surface(modifier, shape = forma, color = cores.surface, border = borda, shadowElevation = 1.dp) { Column(content = conteudo) }
    }
}
