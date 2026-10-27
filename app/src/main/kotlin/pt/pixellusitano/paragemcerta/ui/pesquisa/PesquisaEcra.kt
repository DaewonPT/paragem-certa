package pt.pixellusitano.paragemcerta.ui.pesquisa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.fontes.transitous.operadorDe
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha

@Composable
fun PesquisaEcra(
    repositorio: Repositorio,
    aoAbrirParagem: (Paragem) -> Unit,
    aoAbrirLinha: (Linha) -> Unit,
    aoVoltar: () -> Unit,
) {
    val vm = viewModel { PesquisaViewModel(repositorio::pesquisar, repositorio::linhasCmComecadasPor) }
    val texto by vm.texto.collectAsStateWithLifecycle()
    val resultados by vm.resultados.collectAsStateWithLifecycle()
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = aoVoltar) { Text("←") }
            OutlinedTextField(
                value = texto,
                onValueChange = vm::escrever,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pesquisa_campo)) },
                modifier = Modifier.weight(1f).focusRequester(foco),
            )
        }
        if (texto.trim().length >= 2 && resultados.isEmpty()) {
            Text(stringResource(R.string.pesquisa_sem_resultados), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(resultados) { r ->
                when (r) {
                    is ResultadoPesquisa.DeLinha -> Item(onClick = { aoAbrirLinha(r.l) }) {
                        SeloLinha(r.l.nomeCurto, corDaLinha(r.l.cor, r.l.modo))
                        Text(r.l.nomeLongo, color = MaterialTheme.colorScheme.onBackground)
                    }
                    is ResultadoPesquisa.DeParagem -> Item(onClick = { aoAbrirParagem(r.p) }) {
                        Text("📍", color = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(r.p.nome, color = MaterialTheme.colorScheme.onBackground)
                            Text(subtituloParagem(r.p), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** "Carris Metropolitana · 060419", "CP · 69005": distingue paragens com o mesmo nome. */
internal fun subtituloParagem(p: Paragem): String = when (p.ref.fonte) {
    Fonte.CM -> "Carris Metropolitana · ${p.ref.id}"
    Fonte.TRANSITOUS -> listOfNotNull(operadorDe(p.ref.id), p.ref.id.substringAfterLast('_')).joinToString(" · ")
}

@Composable
private fun Item(onClick: () -> Unit, conteudo: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { conteudo() }
}
