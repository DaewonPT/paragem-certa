package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista

private fun simboloLocal(tipo: String) = when (tipo) {
    "CASA" -> "🏠"
    "TRABALHO" -> "💼"
    else -> "⭐"
}

private fun simboloLugar(t: TipoLugar) = when (t) {
    TipoLugar.PARAGEM -> "🚏"
    TipoLugar.POSICAO -> "◎"
    else -> "📍"
}

/** Escolha da origem ou do destino: locais guardados, recentes, "A minha posição" e pesquisa a partir de 2 letras.
 *  aoEscolher(null) = "A minha posição". */
@Composable
fun EscolherLugarEcra(
    titulo: String,
    comPosicao: Boolean,
    locais: List<LocalGuardado>,
    recentes: List<Lugar>,
    pesquisar: suspend (String) -> List<Lugar>,
    aoEscolher: (Lugar?) -> Unit,
    aoVoltar: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Lugar>?>(null) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    // Cada letra nova cancela a espera anterior: só se pesquisa quando se pára de escrever 300 ms.
    LaunchedEffect(texto) {
        resultados = null
        val t = texto.trim()
        if (t.length < 2) return@LaunchedEffect
        delay(300)
        resultados = pesquisar(t)
    }
    val cores = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(cores.background).statusBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = aoVoltar) { Text("←") }
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                label = { Text(titulo) },
                placeholder = { Text(stringResource(R.string.escolher_procura)) },
                modifier = Modifier.weight(1f).focusRequester(foco),
            )
        }
        LazyColumn {
            if (texto.trim().length < 2) {
                if (comPosicao) item { Opcao("◎", stringResource(R.string.rotas_minha_posicao)) { aoEscolher(null) } }
                items(locais) { l -> Opcao(simboloLocal(l.tipo), l.nome) { aoEscolher(l.lugar) } }
                if (recentes.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.escolher_recentes), color = cores.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(recentes) { l -> Opcao("🕘", l.nome) { aoEscolher(l) } }
                }
            } else {
                val r = resultados
                if (r != null && r.isEmpty()) {
                    item { Text(stringResource(R.string.escolher_sem_resultados), color = cores.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }
                items(r.orEmpty()) { l -> Opcao(simboloLugar(l.tipo), l.nome) { aoEscolher(l) } }
            }
        }
    }
}

@Composable
private fun Opcao(simbolo: String, nome: String, aoTocar: () -> Unit) {
    ItemLista(onClick = aoTocar) {
        Text(simbolo, color = MaterialTheme.colorScheme.primary)
        Text(nome, color = MaterialTheme.colorScheme.onBackground)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
