package pt.pixellusitano.paragemcerta.ui.sobre

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo

const val URL_REPOSITORIO = "https://github.com/DaewonPT/paragem-certa"
const val EMAIL_CONTACTO = "contact@pixellusitano.com"

private val FONTES = listOf(
    R.string.sobre_fonte_cm to "https://carrismetropolitana.pt/open-data",
    R.string.sobre_fonte_transitous to "https://transitous.org/sources",
    R.string.sobre_fonte_openfreemap to "https://openfreemap.org",
    R.string.sobre_fonte_osm to "https://www.openstreetmap.org/copyright",
)

/** aoAbrir recebe um URL https:// ou mailto:; quem chama abre-o com Intent.ACTION_VIEW. */
@Composable
fun SobreEcra(versao: String, aoAbrir: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.sobre_versao, versao), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Text(stringResource(R.string.sobre_autor), color = MaterialTheme.colorScheme.onBackground)
        Ligacao(R.string.sobre_licenca, URL_REPOSITORIO, aoAbrir)

        Text(stringResource(R.string.sobre_fontes), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        FONTES.forEach { (texto, url) -> Ligacao(texto, url, aoAbrir) }

        Text(stringResource(R.string.sobre_contacto), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        Ligacao(null, "mailto:$EMAIL_CONTACTO", aoAbrir, textoFixo = EMAIL_CONTACTO)
    }
}

@Composable
private fun Ligacao(@StringRes texto: Int?, url: String, aoAbrir: (String) -> Unit, textoFixo: String? = null) {
    Text(
        textoFixo ?: stringResource(texto!!),
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button) { aoAbrir(url) }.padding(vertical = 12.dp),
    )
}
