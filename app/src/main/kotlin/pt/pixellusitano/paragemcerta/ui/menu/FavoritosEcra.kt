package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.dados.CatalogoLinhas
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.DialogoNome
import pt.pixellusitano.paragemcerta.ui.inicio.BlocoLinha
import pt.pixellusitano.paragemcerta.ui.inicio.rememberBlocosDeLinhas
import pt.pixellusitano.paragemcerta.ui.inicio.tituloDaLinha
import pt.pixellusitano.paragemcerta.ui.rotas.simboloLocal

@Composable
fun FavoritosEcra(
    repositorio: Repositorio,
    catalogo: CatalogoLinhas,
    aoAbrirFavorita: (Favorita) -> Unit,
    aoAbrirLinha: (LinhaFavorita) -> Unit,
    aoVoltar: () -> Unit,
) {
    val escopo = rememberCoroutineScope()
    val favoritas by remember { repositorio.favoritas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val locais by remember { repositorio.locais() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val linhas by remember { repositorio.linhasFavoritas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    FavoritosConteudo(
        favoritas, locais, aoAbrirFavorita,
        aoSubirFavorita = { f ->
            val i = favoritas.indexOf(f)
            if (i > 0) escopo.launch {
                repositorio.reordenarFavoritas(favoritas.toMutableList().apply { add(i - 1, removeAt(i)) })
            }
        },
        aoRemoverFavorita = { f -> escopo.launch { repositorio.removerFavorita(f.ref) } },
        aoRenomearLocal = { l, nome -> escopo.launch { repositorio.renomearLocal(l.id, nome) } },
        aoSubirLocal = { l ->
            val i = locais.indexOf(l)
            if (i > 0) escopo.launch { repositorio.reordenarLocais(locais.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }
        },
        aoApagarLocal = { l -> escopo.launch { repositorio.apagarLocal(l.id) } },
        aoVoltar = aoVoltar,
        modifier = Modifier.fillMaxSize(),
        linhas = rememberBlocosDeLinhas(repositorio, catalogo, linhas),
        aoAbrirLinha = aoAbrirLinha,
        aoSubirLinha = { f ->
            val i = linhas.indexOf(f)
            if (i > 0) escopo.launch {
                repositorio.reordenarLinhasFavoritas(linhas.toMutableList().apply { add(i - 1, removeAt(i)) })
            }
        },
        aoRemoverLinha = { f -> escopo.launch { repositorio.removerLinhaFavorita(f) } },
    )
}

/**
 * As paragens favoritas (subir, remover), as linhas favoritas (abrir, subir, remover) e os sítios guardados (subir, mudar o nome,
 * apagar). Uma linha que já não existe só se pode remover; sem saber se existe (o catálogo ainda carrega) mantém todas as ações.
 */
@Composable
fun FavoritosConteudo(
    favoritas: List<Favorita>,
    locais: List<LocalGuardado>,
    aoAbrirFavorita: (Favorita) -> Unit,
    aoSubirFavorita: (Favorita) -> Unit,
    aoRemoverFavorita: (Favorita) -> Unit,
    aoRenomearLocal: (LocalGuardado, String) -> Unit,
    aoSubirLocal: (LocalGuardado) -> Unit,
    aoApagarLocal: (LocalGuardado) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    linhas: List<BlocoLinha> = emptyList(),
    aoAbrirLinha: (LinhaFavorita) -> Unit = {},
    aoSubirLinha: (LinhaFavorita) -> Unit = {},
    aoRemoverLinha: (LinhaFavorita) -> Unit = {},
) {
    var renomear by remember { mutableStateOf<LocalGuardado?>(null) }
    val cores = MaterialTheme.colorScheme
    Column(modifier.background(cores.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        CabecalhoEcra(stringResource(R.string.menu_favoritos), aoVoltar)

        Subtitulo(stringResource(R.string.favoritos_paragens))
        if (favoritas.isEmpty() && linhas.isEmpty()) Text(stringResource(R.string.eu_sem_favoritas), color = cores.onSurfaceVariant)
        favoritas.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "★ " + f.nome,
                    color = cores.onBackground,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { aoAbrirFavorita(f) }.padding(vertical = 12.dp),
                )
                if (i > 0) TextButton(onClick = { aoSubirFavorita(f) }) { Text("↑ " + stringResource(R.string.eu_subir)) }
                TextButton(onClick = { aoRemoverFavorita(f) }) { Text(stringResource(R.string.eu_remover)) }
            }
            HorizontalDivider(color = cores.outlineVariant)
        }

        if (linhas.isNotEmpty()) Subtitulo(stringResource(R.string.favoritos_linhas), Modifier.padding(top = 24.dp))
        linhas.forEachIndexed { i, b ->
            val existe = b.existe != false
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                val abrir = if (existe) Modifier.clickable(role = Role.Button) { aoAbrirLinha(b.favorita) } else Modifier
                Column(Modifier.weight(1f).then(abrir).padding(vertical = 12.dp)) {
                    Text("★ " + tituloDaLinha(b), color = cores.onBackground)
                    when (b.existe) {
                        false -> Text(stringResource(R.string.linha_ja_nao_existe), color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        null -> Text(stringResource(R.string.a_carregar), color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        true -> Unit
                    }
                }
                if (i > 0 && existe) TextButton(onClick = { aoSubirLinha(b.favorita) }) { Text("↑ " + stringResource(R.string.eu_subir)) }
                TextButton(onClick = { aoRemoverLinha(b.favorita) }) { Text(stringResource(R.string.eu_remover)) }
            }
            HorizontalDivider(color = cores.outlineVariant)
        }

        Subtitulo(stringResource(R.string.favoritos_sitios), Modifier.padding(top = 24.dp))
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
                        if (i > 0) DropdownMenuItem(text = { Text("↑ " + stringResource(R.string.eu_subir)) }, onClick = { menu = false; aoSubirLocal(l) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.locais_mudar_nome)) }, onClick = { menu = false; renomear = l })
                        DropdownMenuItem(text = { Text(stringResource(R.string.locais_apagar)) }, onClick = { menu = false; aoApagarLocal(l) })
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
                aoRenomearLocal(l, nome)
                renomear = null
            },
            aoCancelar = { renomear = null },
        )
    }
}
