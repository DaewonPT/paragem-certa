package pt.pixellusitano.paragemcerta.ui.pesquisa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.fontes.transitous.operadorDe
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.inicio.ultimaPosicao

@Composable
fun PesquisaEcra(
    repositorio: Repositorio,
    aoAbrirParagem: (Paragem) -> Unit,
    aoAbrirLinha: (Linha) -> Unit,
    aoIrPara: (Lugar) -> Unit,
    aoVoltar: () -> Unit,
) {
    // O contexto da aplicação, não o da atividade: o ViewModel sobrevive a ela.
    val contexto = LocalContext.current.applicationContext
    val vm = viewModel {
        PesquisaViewModel(repositorio::pesquisar, repositorio::linhasCmComecadasPor) { t -> repositorio.pesquisarLugares(t, ultimaPosicao(contexto)) }
    }
    val texto by vm.texto.collectAsStateWithLifecycle()
    val resultados by vm.resultados.collectAsStateWithLifecycle()
    val semRede by vm.semRede.collectAsStateWithLifecycle()
    PesquisaConteudo(texto, resultados, semRede, vm::escrever, aoAbrirLinha, aoIrPara, aoAbrirParagem, aoVoltar)
}

/** "Para onde vais?": linhas, depois sítios e moradas, depois estações e paragens. Tocar num sítio ou estação escolhe o destino. */
@Composable
fun PesquisaConteudo(
    texto: String,
    resultados: List<ResultadoPesquisa>,
    semRede: Boolean,
    aoEscrever: (String) -> Unit,
    aoAbrirLinha: (Linha) -> Unit,
    aoIrPara: (Lugar) -> Unit,
    aoAbrirParagem: (Paragem) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val linhas = resultados.filterIsInstance<ResultadoPesquisa.DeLinha>()
    val sitios = resultados.filterIsInstance<ResultadoPesquisa.DeLugar>()
    val estacoes = resultados.filterIsInstance<ResultadoPesquisa.DeEstacao>()

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = aoVoltar) { Text("←") }
            OutlinedTextField(
                value = texto,
                onValueChange = aoEscrever,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pesquisa_campo)) },
                modifier = Modifier.weight(1f).focusRequester(foco),
            )
        }
        // Sem rede, "experimenta o nome de uma rua" não ajuda: fica só o aviso de ligação, no fim da lista.
        if (texto.trim().length >= 2 && resultados.isEmpty() && !semRede) {
            Text(stringResource(R.string.pesquisa_sem_resultados), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(linhas) { r ->
                ItemLista(onClick = { aoAbrirLinha(r.l) }) {
                    SeloLinha(r.l.nomeCurto, corDaLinha(r.l.cor, r.l.modo))
                    Text(r.l.nomeLongo, color = MaterialTheme.colorScheme.onBackground)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (sitios.isNotEmpty()) {
                item { Cabecalho(stringResource(R.string.pesquisa_sitios)) }
                items(sitios) { r ->
                    ItemLista(onClick = { aoIrPara(r.l) }) {
                        Text("📍", color = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(r.l.nome, color = MaterialTheme.colorScheme.onBackground)
                            r.l.zona?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (estacoes.isNotEmpty()) {
                item { Cabecalho(stringResource(R.string.pesquisa_paragens)) }
                items(estacoes) { r ->
                    val g = r.g
                    val unica = g.itens.singleOrNull()
                    // O botão é irmão da linha, não filho: assim cada um tem o seu alvo (e o seu foco no TalkBack).
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            ItemLista(onClick = { aoIrPara(Lugar(g.nome, g.lat, g.lon, TipoLugar.PARAGEM)) }) {
                                Text("🚏", color = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(g.nome, color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        if (unica != null) subtituloParagem(unica)
                                        else pluralStringResource(R.plurals.pesquisa_n_paragens, g.itens.size, g.itens.size),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        if (unica != null) {
                            val descricao = stringResource(R.string.pesquisa_chegadas_em, unica.nome)
                            TextButton(onClick = { aoAbrirParagem(unica) }, modifier = Modifier.semantics { contentDescription = descricao }) {
                                Text(stringResource(R.string.pesquisa_chegadas))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (semRede) {
                item {
                    Text(stringResource(R.string.pesquisa_sem_rede), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Cabecalho(texto: String) {
    Text(
        texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** "Carris Metropolitana · 060419", "CP · 69005": distingue paragens com o mesmo nome. */
internal fun subtituloParagem(p: Paragem): String = when (p.ref.fonte) {
    Fonte.CM -> "Carris Metropolitana · ${p.ref.id}"
    Fonte.TRANSITOUS -> listOfNotNull(operadorDe(p.ref.id), p.ref.id.substringAfterLast('_')).joinToString(" · ")
}
