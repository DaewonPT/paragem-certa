package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.tema.Icones

/**
 * O topo azulejado dos ecrãs: título a branco sobre o cobalto (também no escuro).
 * Sem [subtitulo], [acao] nem [antesDoTitulo] é uma só fila ("←" e título); com algum deles,
 * a fila de cima leva o voltar e a [acao], e por baixo vem o título a toda a largura. [aoVoltar] nulo não mostra o botão.
 */
@Composable
fun CabecalhoAzulejo(
    titulo: String,
    aoVoltar: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acao: (@Composable () -> Unit)? = null,
    antesDoTitulo: (@Composable () -> Unit)? = null,
) {
    val voltar: @Composable () -> Unit = {
        if (aoVoltar != null) {
            IconButton(onClick = aoVoltar, Modifier.size(48.dp)) { Icon(Icones.Voltar, stringResource(R.string.voltar), tint = Color.White) }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
    FaixaAzulejo(modifier) {
        if (subtitulo == null && acao == null && antesDoTitulo == null) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (aoVoltar != null) voltar()
                Text(
                    titulo, Modifier.padding(start = if (aoVoltar == null) 16.dp else 0.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White,
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                voltar()
                acao?.invoke()
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                antesDoTitulo?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(
                        titulo, Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White,
                    )
                    if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}
