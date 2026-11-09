package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import pt.pixellusitano.paragemcerta.ui.tema.iconeLocal
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import androidx.compose.material3.Icon
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.ehCodigoPostal
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.ehLugarDeCodigoPostal
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.ViagemRecente
import pt.pixellusitano.paragemcerta.ui.comum.CampoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.FaixaAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.FatiaAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista
import pt.pixellusitano.paragemcerta.ui.comum.TituloSecao

/** Os lugares das viagens recentes para escolher: sem "A minha posição", sem nomes repetidos, no máximo 8. */
internal fun lugaresRecentes(recentes: List<ViagemRecente>): List<Lugar> =
    recentes.flatMap { listOf(it.para, it.de) }.filter { it.nome != MINHA_POSICAO }.distinctBy { it.nome }.take(8)

/**
 * Guarda um lugar nos favoritos e avisa: Casa e Trabalho com esse nome (só há uma de cada, a nova substitui a anterior), os outros
 * com o nome do lugar. Guarda no escopo de quem a chama: um ecrã que fecha logo a seguir cancelaria o que ele próprio lançasse.
 */
@Composable
internal fun rememberGuardarLocal(repositorio: Repositorio): (Lugar, tipo: String) -> Unit {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val casa = stringResource(R.string.locais_casa)
    val trabalho = stringResource(R.string.locais_trabalho)
    val guardado = stringResource(R.string.local_guardado)
    return { l, tipo ->
        val nome = when (tipo) {
            "CASA" -> casa
            "TRABALHO" -> trabalho
            else -> l.nome
        }
        escopo.launch { repositorio.guardarLocal(nome, l, tipo) }
        Toast.makeText(contexto, guardado, Toast.LENGTH_SHORT).show()
    }
}

private fun simboloLugar(t: TipoLugar): ImageVector = when (t) {
    TipoLugar.PARAGEM -> Icones.Linhas
    TipoLugar.POSICAO -> Icones.Posicao
    else -> Icones.Lugar
}

/** Escolha da origem ou do destino: locais guardados, recentes, "A minha posição" e pesquisa a partir de 2 letras.
 *  aoEscolher(null) = "A minha posição". */
@Composable
fun EscolherLugarEcra(
    titulo: String,
    comPosicao: Boolean,
    locais: List<LocalGuardado>,
    recentes: List<Lugar>,
    pesquisar: suspend (String) -> List<Lugar>?,
    aoEscolher: (Lugar?) -> Unit,
    aoVoltar: () -> Unit,
    aoGuardar: (Lugar, tipo: String) -> Unit = { _, _ -> },
) {
    var texto by rememberSaveable { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Lugar>?>(null) }
    var semRede by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    // Cada letra nova cancela a espera anterior: só se pesquisa quando se pára de escrever 300 ms.
    LaunchedEffect(texto) {
        resultados = null
        semRede = false
        val t = texto.trim()
        if (t.length < 2) return@LaunchedEffect
        delay(300)
        val r = pesquisar(t)
        resultados = r
        semRede = r == null
    }
    val cores = MaterialTheme.colorScheme
    val minhaPosicao = stringResource(R.string.rotas_minha_posicao)
    // Lugar nulo = "A minha posição".
    val principais = buildList {
        if (comPosicao) add(Triple(Icones.Posicao, minhaPosicao, null as Lugar?))
        locais.forEach { add(Triple(iconeLocal(it.tipo), it.nome, it.lugar as Lugar?)) }
    }

    Column(Modifier.fillMaxSize().background(cores.background)) {
        FaixaAzulejo {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = aoVoltar, Modifier.size(48.dp)) { Icon(Icones.Voltar, stringResource(R.string.voltar), tint = Color.White) }
                CampoAzulejo(texto, { texto = it }, titulo, Modifier.weight(1f).focusRequester(foco), dica = stringResource(R.string.escolher_procura))
            }
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            if (texto.trim().length < 2) {
                itemsIndexed(principais) { i, (icone, nome, lugar) -> Opcao(i, principais.size, icone, nome) { aoEscolher(lugar) } }
                if (recentes.isNotEmpty()) {
                    item { TituloSecao(stringResource(R.string.escolher_recentes), Modifier.padding(horizontal = 16.dp)) }
                    itemsIndexed(recentes) { i, l -> Opcao(i, recentes.size, Icones.Relogio, l.nome) { aoEscolher(l) } }
                }
            } else {
                val r = resultados
                if (semRede) {
                    item { Text(stringResource(R.string.escolher_sem_rede), color = cores.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                } else if (r != null && r.isEmpty()) {
                    val mensagem = if (ehCodigoPostal(texto)) R.string.pesquisa_cp_nao_encontrado else R.string.escolher_sem_resultados
                    item { Text(stringResource(mensagem), color = cores.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }
                val lista = r.orEmpty()
                itemsIndexed(lista) { i, l -> OpcaoGuardavel(i, lista.size, l, aoEscolher = { aoEscolher(l) }, aoGuardar = { tipo -> aoGuardar(l, tipo) }) }
            }
        }
    }
}

@Composable
private fun Opcao(indice: Int, total: Int, icone: ImageVector, nome: String, aoTocar: () -> Unit) {
    FatiaAzulejo(indice, total) {
        ItemLista(onClick = aoTocar) {
            Icon(icone, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(nome, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Resultado da pesquisa com "⋯" para o guardar; as mesmas três ações ficam como ações do TalkBack na linha. */
@Composable
private fun OpcaoGuardavel(indice: Int, total: Int, l: Lugar, aoEscolher: () -> Unit, aoGuardar: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val acoes = listOf(
        "CASA" to stringResource(R.string.guardar_como_casa),
        "TRABALHO" to stringResource(R.string.guardar_como_trabalho),
        "OUTRO" to stringResource(R.string.guardar_como_local),
    )
    val maisOpcoes = stringResource(R.string.mais_opcoes)
    FatiaAzulejo(indice, total) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                // As ações vão no próprio ItemLista: é o nó clicável que o TalkBack foca, não esta linha.
                ItemLista(
                    onClick = aoEscolher,
                    modifier = Modifier.semantics {
                        customActions = acoes.map { (tipo, texto) -> CustomAccessibilityAction(texto) { aoGuardar(tipo); true } }
                    },
                ) {
                    Icon(simboloLugar(l.tipo), contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(l.nome, color = MaterialTheme.colorScheme.onBackground)
                        l.zona?.let {
                            Text(
                                it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ehLugarDeCodigoPostal(l)) {
                            Text(
                                stringResource(R.string.fonte_geoapi), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, Modifier.size(48.dp)) { Icon(Icones.Mais, maisOpcoes) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    acoes.forEach { (tipo, texto) ->
                        DropdownMenuItem(text = { Text(texto) }, onClick = { menu = false; aoGuardar(tipo) })
                    }
                }
            }
        }
    }
}
