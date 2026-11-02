package pt.pixellusitano.paragemcerta.ui.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R

data class CanalReclamacao(@StringRes val nome: Int, val url: String)

/** Ficam na app nesta etapa; passam para o `config.json` do servidor na etapa 2. */
val CANAIS_RECLAMACAO = listOf(
    CanalReclamacao(R.string.reclamar_livro, "https://www.livroreclamacoes.pt/Pedido/Reclamacao"),
    CanalReclamacao(R.string.reclamar_carris, "https://www.carris.pt/atendimento/falar-connosco/"),
    CanalReclamacao(R.string.reclamar_cm, "https://carrismetropolitana.pt/contacto"),
    CanalReclamacao(R.string.reclamar_metro, "https://www.metrolisboa.pt/informar/reclamacoes/"),
    CanalReclamacao(R.string.reclamar_cp, "https://www.cp.pt/info/pt/w/reclamacao-cp"),
)

/** aoAbrir recebe um URL https://; quem chama abre-o com Intent.ACTION_VIEW. */
@Composable
fun ReclamarEcra(aoAbrir: (String) -> Unit, aoVoltar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        CabecalhoEcra(stringResource(R.string.menu_reclamar), aoVoltar)
        Text(stringResource(R.string.reclamar_nota), Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        CANAIS_RECLAMACAO.forEach { Ligacao(stringResource(it.nome), { aoAbrir(it.url) }) }
    }
}
