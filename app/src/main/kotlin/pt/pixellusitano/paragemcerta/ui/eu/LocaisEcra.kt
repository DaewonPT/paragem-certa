package pt.pixellusitano.paragemcerta.ui.eu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.DialogoNome
import pt.pixellusitano.paragemcerta.ui.rotas.simboloLocal
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo

@Composable
fun LocaisEcra(repositorio: Repositorio, aoVoltar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    val locais by remember { repositorio.locais() }.collectAsStateWithLifecycle(initialValue = emptyList())
    LocaisConteudo(
        locais,
        aoRenomear = { l, nome -> escopo.launch { repositorio.renomearLocal(l.id, nome) } },
        aoSubir = { l ->
            val i = locais.indexOf(l)
            if (i > 0) escopo.launch { repositorio.reordenarLocais(locais.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }
        },
        aoApagar = { l -> escopo.launch { repositorio.apagarLocal(l.id) } },
        aoVoltar = aoVoltar,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun LocaisConteudo(
    locais: List<LocalGuardado>,
    aoRenomear: (LocalGuardado, String) -> Unit,
    aoSubir: (LocalGuardado) -> Unit,
    aoApagar: (LocalGuardado) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renomear by remember { mutableStateOf<LocalGuardado?>(null) }
    val cores = MaterialTheme.colorScheme
    Column(modifier.background(cores.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = aoVoltar) { Text("←") }
            Text(stringResource(R.string.eu_locais), style = MaterialTheme.typography.titleLarge, color = cores.primary)
        }
        FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (locais.isEmpty()) Text(stringResource(R.string.locais_vazio), color = cores.onSurfaceVariant)
        locais.forEachIndexed { i, l ->
            var menu by remember { mutableStateOf(false) }
            val opcoes = stringResource(R.string.locais_opcoes, l.nome)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${simboloLocal(l.tipo)}  ${l.nome}", Modifier.weight(1f).padding(vertical = 12.dp),
                    color = cores.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = opcoes }) { Text("⋯") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (i > 0) DropdownMenuItem(text = { Text("↑ " + stringResource(R.string.eu_subir)) }, onClick = { menu = false; aoSubir(l) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.locais_mudar_nome)) }, onClick = { menu = false; renomear = l })
                        DropdownMenuItem(text = { Text(stringResource(R.string.locais_apagar)) }, onClick = { menu = false; aoApagar(l) })
                    }
                }
            }
            HorizontalDivider(color = cores.outlineVariant)
        }
    }
    renomear?.let { l ->
        DialogoNome(
            stringResource(R.string.locais_mudar_nome), l.nome,
            aoConfirmar = { nome ->
                aoRenomear(l, nome)
                renomear = null
            },
            aoCancelar = { renomear = null },
        )
    }
}
