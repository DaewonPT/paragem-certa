package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo

private val PERGUNTAS = listOf(
    R.string.ajuda_aviso_p to R.string.ajuda_aviso_r,
    R.string.ajuda_bateria_p to R.string.ajuda_bateria_r,
    R.string.ajuda_tempo_real_p to R.string.ajuda_tempo_real_r,
    R.string.ajuda_favoritos_p to R.string.ajuda_favoritos_r,
)

/** Perguntas frequentes; está tudo na app, funciona sem rede. */
@Composable
fun AjudaEcra(aoVoltar: () -> Unit, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    Column(modifier.background(cores.background)) {
        CabecalhoAzulejo(stringResource(R.string.menu_ajuda), aoVoltar)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            PERGUNTAS.forEach { (pergunta, resposta) ->
                Text(
                    stringResource(pergunta), Modifier.padding(top = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, color = cores.onBackground,
                )
                Text(stringResource(resposta), Modifier.padding(top = 4.dp), color = cores.onSurfaceVariant)
            }
        }
    }
}
