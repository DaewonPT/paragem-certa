package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import pt.pixellusitano.paragemcerta.ui.tema.PadraoAzulejo

/** O topo azulejado dos ecrãs que se abrem a partir do Menu: "←" e título a branco sobre o cobalto (também no escuro). */
@Composable
fun CabecalhoAzulejo(titulo: String, aoVoltar: () -> Unit, modifier: Modifier = Modifier) {
    val baixo = MaterialTheme.shapes.extraLarge
    Box(modifier.fillMaxWidth().clip(baixo.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))).background(Azulejo.Cobalto)) {
        PadraoAzulejo(Modifier.matchParentSize())
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = aoVoltar, Modifier.size(48.dp)) { Icon(Icones.Voltar, stringResource(R.string.voltar), tint = Color.White) }
            Text(
                titulo, Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White,
            )
        }
    }
}
