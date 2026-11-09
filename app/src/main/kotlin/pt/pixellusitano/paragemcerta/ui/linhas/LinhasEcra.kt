package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.size
import pt.pixellusitano.paragemcerta.ui.tema.iconeModo
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.linhas.Catalogo
import pt.pixellusitano.paragemcerta.core.linhas.LinhaCatalogo
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.CatalogoLinhas
import pt.pixellusitano.paragemcerta.dados.EstadoCatalogo
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.CampoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.FatiaAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.FaixaAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.SeloModo
import pt.pixellusitano.paragemcerta.ui.comum.TituloSecao
import pt.pixellusitano.paragemcerta.ui.comum.bordaDoFiltro
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import java.time.LocalDate

/** Os meios pela ordem da lista (e dos botões); "Outro" só aparece em "Todos". */
private val ORDEM_MODOS = listOf(Modo.METRO, Modo.COMBOIO, Modo.ELETRICO, Modo.BARCO, Modo.AUTOCARRO, Modo.OUTRO)
private const val OPERADOR_CM = "Carris Metropolitana"
private val NUMERO_NO_INICIO = Regex("^\\d+")

// 15E, 99, 728, 1009: os números por ordem de número (não de letras), depois o resto por letras.
private val ORDEM_DAS_LINHAS = compareBy<LinhaCatalogo>(
    { NUMERO_NO_INICIO.find(it.nome)?.value?.toIntOrNull() ?: Int.MAX_VALUE },
    { it.nome.lowercase() },
)

/** Um meio de transporte e, dentro dele, as linhas de cada operador (o nome do operador para mostrar). */
data class GrupoLinhas(val modo: Modo, val operadores: List<Pair<String, List<LinhaCatalogo>>>)

/**
 * A lista do separador: a CM (do Room) e as do [catalogo] (se já chegou) por meio e operador, só com o [modo] escolhido e as que
 * a procura [filtro] encontra. A chave de cada linha é a [RefLinha]: a 1009 da CM e uma 1009 de outro operador são duas linhas.
 */
fun agruparLinhas(cm: List<Linha>, catalogo: Catalogo?, filtro: String, modo: Modo?): List<GrupoLinhas> {
    val daCm = cm.map {
        LinhaCatalogo(RefLinha(Fonte.CM, it.id), OPERADOR_CM, it.nomeCurto, it.nomeLongo, it.modo, it.cor, it.corTexto, emptySet(), emptyList())
    }
    val operadores = catalogo?.operadores.orEmpty()
    val todas = daCm + catalogo?.linhas.orEmpty()
    // A procura do catálogo (nome curto primeiro, depois o longo, sem acentos) serve às duas fontes de uma vez; a data não conta.
    val escolhidas = if (filtro.isBlank()) todas.sortedWith(ORDEM_DAS_LINHAS)
    else Catalogo(catalogo?.geradoEm ?: LocalDate.ofEpochDay(0), operadores, todas).procurar(filtro)
    return ORDEM_MODOS.filter { modo == null || it == modo }.mapNotNull { m ->
        val doMeio = escolhidas.filter { it.modo == m }
        if (doMeio.isEmpty()) null
        else GrupoLinhas(m, doMeio.groupBy { operadores[it.operador] ?: it.operador }.toList().sortedBy { it.first })
    }
}

/**
 * O separador Linhas. As da CM vêm do Room em fluxo, por isso a lista enche-se quando a rede chega; o resto, do catálogo
 * ([CatalogoLinhas.estado]). A procura e o meio escolhido guardam-se com o separador (rememberSaveable + restoreState).
 */
@Composable
fun LinhasEcra(
    repositorio: Repositorio,
    catalogo: CatalogoLinhas,
    aoAbrirLinha: (RefLinha) -> Unit,
    modifier: Modifier = Modifier,
    perturbacoes: @Composable () -> Unit = {},
) {
    val cm by remember(repositorio) { repositorio.linhasCmFlow() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val estado by catalogo.estado.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf("") }
    var modo by rememberSaveable { mutableStateOf<Modo?>(null) }
    val grupos = remember(cm, estado, filtro, modo) { agruparLinhas(cm, (estado as? EstadoCatalogo.Pronto)?.c, filtro, modo) }
    LinhasConteudo(grupos, filtro, { filtro = it }, modo, { modo = it }, estado, aoAbrirLinha, perturbacoes, modifier)
}

/**
 * A lista. [estado] diz o que falta do catálogo: a carregar, falhou (só se vê a CM e a frase a explicar) ou pronto. [perturbacoes]
 * é o espaço da linha "Perturbações".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinhasConteudo(
    grupos: List<GrupoLinhas>,
    filtro: String,
    aoFiltrar: (String) -> Unit,
    modo: Modo?,
    aoEscolherModo: (Modo?) -> Unit,
    estado: EstadoCatalogo,
    aoAbrirLinha: (RefLinha) -> Unit,
    perturbacoes: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FaixaAzulejo {
            CampoAzulejo(filtro, aoFiltrar, stringResource(R.string.linhas_procurar), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "meios") {
                // Passam à linha seguinte em vez de ficarem escondidos de lado (ecrãs estreitos ou letra grande).
                FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(modo == null, { aoEscolherModo(null) }, label = { Text(stringResource(R.string.linhas_todos)) }, border = bordaDoFiltro(modo == null))
                    ORDEM_MODOS.filter { it != Modo.OUTRO }.forEach { m ->
                        FilterChip(
                            modo == m, { aoEscolherModo(m) }, leadingIcon = { Icon(iconeModo(m), null, Modifier.size(FilterChipDefaults.IconSize)) },
                            label = { Text(stringResource(textoDoModo(m))) }, border = bordaDoFiltro(modo == m),
                        )
                    }
                }
            }
            item(key = "perturbacoes") { perturbacoes() }
            when (estado) {
                EstadoCatalogo.ACarregar -> item(key = "estado") { Aviso(stringResource(R.string.linhas_a_carregar)) }
                EstadoCatalogo.Falhou -> item(key = "estado") { Aviso(stringResource(R.string.linhas_sem_catalogo)) }
                is EstadoCatalogo.Pronto -> if (grupos.isEmpty()) item(key = "estado") { Aviso(stringResource(R.string.linhas_sem_resultados)) }
            }
            grupos.forEach { g ->
                item(key = "meio-${g.modo}") {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 8 dp em cima: o título tem 12 dp em cima e 4 em baixo, e o ícone centra-se na linha do texto.
                        Icon(iconeModo(g.modo), null, Modifier.padding(top = 8.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        TituloSecao(stringResource(textoDoModo(g.modo)))
                    }
                }
                g.operadores.forEach { (operador, linhas) ->
                    // Uma fatia do cartão do operador por linha, para a lista continuar a compor só o que se vê.
                    itemsIndexed(linhas, key = { _, l -> l.ref.chave }) { i, l ->
                        // Sem nome curto (algumas da CP) o nome da linha é o longo: um selo desse tamanho não cabia.
                        val longo = l.nomeLongo.takeIf { it.isNotEmpty() && it != l.nome }
                        val cor = corDaLinha(l.cor, l.modo)
                        FatiaAzulejo(
                            i, linhas.size,
                            Modifier.padding(top = if (i == 0) 4.dp else 0.dp, bottom = if (i == linhas.lastIndex) 4.dp else 0.dp).fillMaxWidth(),
                        ) {
                            if (i == 0) Text(
                                operador, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                            )
                            ItemLista(onClick = { aoAbrirLinha(l.ref) }) {
                                if (longo != null) SeloLinha(l.nome, cor, l.corTexto?.let { corDaLinha(it, l.modo) }) else Box(Modifier.clearAndSetSemantics {}) { SeloModo(l.modo, cor) }
                                Text(longo ?: l.nome, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun textoDoModo(m: Modo) = when (m) {
    Modo.METRO -> R.string.modo_metro
    Modo.COMBOIO -> R.string.modo_comboio
    Modo.ELETRICO -> R.string.modo_eletrico
    Modo.BARCO -> R.string.modo_barco
    Modo.AUTOCARRO -> R.string.modo_autocarro
    Modo.OUTRO -> R.string.modo_outro
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp))
}
