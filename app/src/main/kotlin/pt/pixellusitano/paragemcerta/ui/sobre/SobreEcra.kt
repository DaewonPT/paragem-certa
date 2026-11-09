package pt.pixellusitano.paragemcerta.ui.sobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.TituloSecao
import pt.pixellusitano.paragemcerta.ui.menu.CartaoDeItens
import pt.pixellusitano.paragemcerta.ui.menu.ItemDeMenu
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo

const val URL_REPOSITORIO = "https://github.com/DaewonPT/paragem-certa"
const val EMAIL_CONTACTO = "contact@pixellusitano.com"
const val URL_TERMOS = "https://paragemcerta.pixellusitano.com/termos"
const val URL_PRIVACIDADE = "https://paragemcerta.pixellusitano.com/privacidade"

// O crédito da Carris só aparece com as posições ao vivo ligadas (carrisAoVivo): sem elas a app não usa nada dela. O do Metro está sempre:
// o catálogo de linhas, com os horários dele, já vem dos seus dados.
private fun fontes(carrisAoVivo: Boolean) = listOfNotNull(
    R.string.sobre_fonte_cm to "https://carrismetropolitana.pt/open-data",
    R.string.sobre_fonte_transitous to "https://transitous.org/sources",
    R.string.sobre_fonte_operadores to "https://transitous.org/sources/",
    R.string.sobre_fonte_metro to "https://www.metrolisboa.pt/dados-abertos/",
    (R.string.sobre_fonte_carris to "https://www.carris.pt").takeIf { carrisAoVivo },
    R.string.sobre_fonte_emel to "https://dados.emel.pt",
    R.string.sobre_fonte_openfreemap to "https://openfreemap.org",
    R.string.sobre_fonte_osm to "https://www.openstreetmap.org/copyright",
    R.string.fonte_geoapi to "https://geoapi.pt",
    R.string.sobre_fonte_panoramax to "https://panoramax.fr",
)

/**
 * aoAbrir recebe um URL https:// ou mailto:; quem chama abre-o com Intent.ACTION_VIEW. [carrisAoVivo]: o interruptor da config.
 * [urlDoacoes]: a página dos donativos, só na versão do GitHub (nula na da Play).
 */
@Composable
fun SobreEcra(versao: String, aoAbrir: (String) -> Unit, aoVoltar: () -> Unit, carrisAoVivo: Boolean = false, urlDoacoes: String? = null) {
    @Composable
    fun ligacoes(itens: List<Pair<String, String>>) = CartaoDeItens(itens.map { (texto, url) -> ItemDeMenu(texto, { aoAbrir(url) }) })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CabecalhoAzulejo(stringResource(R.string.menu_sobre), aoVoltar)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CartaoAzulejo(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.sobre_versao, versao), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    Text(stringResource(R.string.sobre_autor), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            ligacoes(listOfNotNull(stringResource(R.string.sobre_licenca) to URL_REPOSITORIO, urlDoacoes?.let { stringResource(R.string.sobre_apoiar) to it }))
            TituloSecao(stringResource(R.string.sobre_fontes))
            ligacoes(fontes(carrisAoVivo).map { (texto, url) -> stringResource(texto) to url })
            TituloSecao(stringResource(R.string.sobre_contacto))
            ligacoes(listOf(EMAIL_CONTACTO to "mailto:$EMAIL_CONTACTO"))
            ligacoes(listOf(stringResource(R.string.ligacao_termos) to URL_TERMOS, stringResource(R.string.ligacao_privacidade) to URL_PRIVACIDADE))
        }
    }
}
