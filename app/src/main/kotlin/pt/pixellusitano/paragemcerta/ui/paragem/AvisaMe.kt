package pt.pixellusitano.paragemcerta.ui.paragem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

/** Quantos minutos antes se pode pedir o aviso, e o que vem marcado. */
private val MINUTOS = listOf(2, 5, 10, 15)
private const val MINUTOS_OMISSAO = 5

/**
 * O "Avisa-me" de uma paragem: o diálogo e, ao avisar, o pedido das notificações (Android 13+, se faltar) e o arranque do
 * serviço da vigia. Sem a permissão a pessoa é avisada de que os avisos só tocam e vibram, e o serviço arranca na mesma.
 * [linhas] são os nomes das linhas (o que a pessoa vê), por onde a vigia as reconhece.
 */
@Composable
fun AvisaMe(ref: RefParagem, nomeParagem: String, linhas: List<String>, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    var escolha by remember { mutableStateOf<Pair<Set<String>, Int>?>(null) }

    fun arrancar(escolhidas: Set<String>, minutos: Int) {
        try {
            ContextCompat.startForegroundService(contexto, ServicoAvisoChegada.vigiar(contexto, ref, nomeParagem, escolhidas, minutos))
        } catch (_: IllegalStateException) { // o Android recusou o serviço em primeiro plano
            Toast.makeText(contexto, R.string.avisame_falhou, Toast.LENGTH_LONG).show()
        }
        aoFechar()
    }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dada ->
        if (!dada) Toast.makeText(contexto, R.string.avisame_sem_notificacoes, Toast.LENGTH_LONG).show()
        escolha?.let { (l, m) -> arrancar(l, m) }
    }

    DialogoAvisaMe(
        linhas,
        aoAvisar = { escolhidas, minutos ->
            val semPermissao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (semPermissao) {
                escolha = escolhidas to minutos
                pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrancar(escolhidas, minutos)
            }
        },
        aoCancelar = aoFechar,
    )
}

/** O diálogo: as linhas da paragem (todas desmarcadas; é preciso uma para avisar) e quantos minutos antes avisar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoAvisaMe(linhas: List<String>, aoAvisar: (Set<String>, Int) -> Unit, aoCancelar: () -> Unit) {
    BasicAlertDialog(onDismissRequest = aoCancelar) { AvisaMeConteudo(linhas, aoAvisar, aoCancelar) }
}

/** O cartão do diálogo, à parte da janela (as capturas de ecrã não apanham janelas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvisaMeConteudo(linhas: List<String>, aoAvisar: (Set<String>, Int) -> Unit, aoCancelar: () -> Unit, modifier: Modifier = Modifier) {
    var escolhidas by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var minutos by rememberSaveable { mutableIntStateOf(MINUTOS_OMISSAO) }
    Surface(modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            Text(stringResource(R.string.avisame), style = MaterialTheme.typography.headlineSmall)
            // Com muitas linhas, ou letra grande, os controlos rolam e os botões ficam sempre à vista.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.avisame_linhas), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                linhas.forEach { nome ->
                    val marcada = nome in escolhidas
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(marcada, role = Role.Checkbox) { escolhidas = if (it) escolhidas + nome else escolhidas - nome },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(marcada, onCheckedChange = null)
                        Text(nome, Modifier.padding(start = 12.dp))
                    }
                }
                Text(stringResource(R.string.avisame_quando), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                // Em ecrãs estreitos ou com letra grande os minutos passam à linha seguinte, em vez de ficarem escondidos de lado.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MINUTOS.forEach { m ->
                        FilterChip(selected = m == minutos, onClick = { minutos = m }, label = { Text(stringResource(R.string.avisame_minutos, m)) })
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 24.dp)) {
                TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) }
                TextButton(onClick = { aoAvisar(escolhidas, minutos) }, enabled = escolhidas.isNotEmpty()) { Text(stringResource(R.string.avisame_avisar)) }
            }
        }
    }
}
