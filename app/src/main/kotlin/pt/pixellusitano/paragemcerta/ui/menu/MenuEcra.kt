package pt.pixellusitano.paragemcerta.ui.menu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.sobre.EMAIL_CONTACTO
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.Icones

fun intentOpiniao(versao: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_CONTACTO"))
        .putExtra(Intent.EXTRA_SUBJECT, "Paragem Certa — opinião")
        .putExtra(Intent.EXTRA_TEXT, "Versão da app: $versao\n\n")

@Composable
fun MenuEcra(
    aoAbrirFavoritos: () -> Unit,
    aoAbrirDefinicoes: () -> Unit,
    aoAbrirAjuda: () -> Unit,
    aoAbrirReclamar: () -> Unit,
    aoEnviarOpiniao: () -> Unit,
    aoAbrirSobre: () -> Unit,
    modifier: Modifier = Modifier,
    /** Só vem quando o interruptor `conta` do `config.json` está ligado; sem ele a entrada "Conta" não aparece. */
    aoAbrirConta: (() -> Unit)? = null,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        CabecalhoAzulejo(stringResource(R.string.nav_menu), aoVoltar = null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            CartaoDeItens(
                listOfNotNull(
                    aoAbrirConta?.let { ItemDeMenu(stringResource(R.string.menu_conta), it) },
                    ItemDeMenu(stringResource(R.string.menu_favoritos), aoAbrirFavoritos),
                    ItemDeMenu(stringResource(R.string.eu_definicoes), aoAbrirDefinicoes),
                    ItemDeMenu(stringResource(R.string.menu_ajuda), aoAbrirAjuda),
                    ItemDeMenu(stringResource(R.string.menu_reclamar), aoAbrirReclamar),
                    ItemDeMenu(stringResource(R.string.eu_opiniao), aoEnviarOpiniao),
                    ItemDeMenu(stringResource(R.string.menu_sobre), aoAbrirSobre),
                ),
            )
        }
    }
}

/** Uma linha de [CartaoDeItens]; todas levam a seta, abram outro ecrã ou uma página ou o email fora da app. */
internal class ItemDeMenu(val texto: String, val aoTocar: () -> Unit)

/** As linhas de um menu num só cartão, com divisórias; cada uma é um botão. */
@Composable
internal fun CartaoDeItens(itens: List<ItemDeMenu>, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    CartaoAzulejo(modifier.fillMaxWidth()) {
        itens.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = cores.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = item.aoTocar).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.texto, Modifier.weight(1f), color = cores.onSurface, style = MaterialTheme.typography.titleMedium)
                Icon(Icones.Seguinte, null, tint = cores.onSurfaceVariant)
            }
        }
    }
}
