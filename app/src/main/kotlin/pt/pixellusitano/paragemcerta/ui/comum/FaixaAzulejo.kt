package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.PadraoAzulejo

/** A faixa azul de azulejos no topo dos ecrãs: cobalto nos dois temas, cantos de baixo redondos, conteúdo por baixo da barra de estado. */
@Composable
fun FaixaAzulejo(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    val baixo = MaterialTheme.shapes.extraLarge
    Box(modifier.fillMaxWidth().clip(baixo.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))).background(Azulejo.Cobalto)) {
        PadraoAzulejo(Modifier.matchParentSize())
        Column(Modifier.statusBarsPadding(), content = conteudo)
    }
}
