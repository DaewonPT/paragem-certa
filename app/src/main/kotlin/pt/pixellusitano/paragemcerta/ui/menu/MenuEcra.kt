package pt.pixellusitano.paragemcerta.ui.menu

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.sobre.EMAIL_CONTACTO
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo

fun intentOpiniao(versao: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_CONTACTO"))
        .putExtra(Intent.EXTRA_SUBJECT, "Paragem Certa — opinião")
        .putExtra(Intent.EXTRA_TEXT, "Versão da app: $versao\n\n")

/** O "←" e o título com o friso, no topo dos ecrãs que se abrem a partir do Menu. */
@Composable
internal fun CabecalhoEcra(titulo: String, aoVoltar: () -> Unit) {
    val voltar = stringResource(R.string.voltar)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = aoVoltar, modifier = Modifier.semantics { contentDescription = voltar }) { Text("←") }
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
    FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
fun MenuEcra(
    aoAbrirFavoritos: () -> Unit,
    aoAbrirDefinicoes: () -> Unit,
    aoAbrirAjuda: () -> Unit,
    aoAbrirReclamar: () -> Unit,
    aoEnviarOpiniao: () -> Unit,
    aoAbrirSobre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        Text(stringResource(R.string.nav_menu), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        ItemMenu(R.string.menu_favoritos, aoAbrirFavoritos)
        ItemMenu(R.string.eu_definicoes, aoAbrirDefinicoes)
        ItemMenu(R.string.menu_ajuda, aoAbrirAjuda)
        ItemMenu(R.string.menu_reclamar, aoAbrirReclamar)
        ItemMenu(R.string.eu_opiniao, aoEnviarOpiniao, comSeta = false) // abre o email, não outro ecrã
        ItemMenu(R.string.menu_sobre, aoAbrirSobre)
    }
}

@Composable
private fun ItemMenu(@StringRes texto: Int, aoTocar: () -> Unit, comSeta: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoTocar).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(texto), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        if (comSeta) Text("›", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}
