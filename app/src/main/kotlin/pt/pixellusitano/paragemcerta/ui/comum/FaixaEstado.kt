package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import java.time.Duration
import java.time.Instant

/** "Sem ligação — atualizado há N min" ou "Tempo real indisponível"; nada quando está tudo bem. */
@Composable
fun FaixaEstado(e: EstadoChegadas, agora: Instant, modifier: Modifier = Modifier) {
    val texto = when {
        e.semLigacao && e.atualizadoEm != null ->
            stringResource(R.string.sem_ligacao, Duration.between(e.atualizadoEm, agora).toMinutes().coerceAtLeast(0))
        e.semLigacao -> stringResource(R.string.sem_ligacao_nada)
        e.tempoRealIndisponivel -> stringResource(R.string.tempo_real_indisponivel)
        else -> return
    }
    Text(
        texto,
        color = MaterialTheme.colorScheme.onSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
