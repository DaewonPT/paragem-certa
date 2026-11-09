package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.size
import pt.pixellusitano.paragemcerta.ui.tema.iconeModo
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.perturbacoes.FontePerturbacao
import pt.pixellusitano.paragemcerta.core.perturbacoes.Perturbacao
import pt.pixellusitano.paragemcerta.core.perturbacoes.dasLinhas
import pt.pixellusitano.paragemcerta.core.perturbacoes.novas
import pt.pixellusitano.paragemcerta.dados.FontePerturbacoes
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DIA = DateTimeFormatter.ofPattern("dd/MM")

private fun operadorDe(p: Perturbacao) = when (p.fonte) {
    FontePerturbacao.CM -> "Carris Metropolitana"
    FontePerturbacao.METRO -> "Metro de Lisboa"
}

private fun modoDe(p: Perturbacao) = when (p.fonte) {
    FontePerturbacao.CM -> Modo.AUTOCARRO
    FontePerturbacao.METRO -> Modo.METRO
}

/** As tuas linhas (chaveLinha): as favoritas e as que passam nas paragens favoritas. */
suspend fun minhasLinhas(r: Repositorio): Set<String> =
    r.linhasFavoritas().first().mapTo(HashSet()) { chaveLinha(it.nome) } + r.linhasDasParagensFavoritas()

/** "2 perturbações novas nas tuas linhas": tocar abre a lista ([aoAbrir]); × fecha. */
@Composable
fun FaixaPerturbacoes(n: Int, aoAbrir: () -> Unit, aoFechar: () -> Unit, modifier: Modifier = Modifier) {
    val fechar = stringResource(R.string.fechar)
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.faixa_perturbacoes, n, n),
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoAbrir).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = aoFechar, modifier = Modifier.semantics { contentDescription = fechar }) {
                // O leitor de ecrã lê "Fechar", não o símbolo.
                Text("×", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * A faixa ao abrir a app: as perturbações novas (não vistas) das tuas linhas ([minhas]), lidas uma vez por abertura. Com
 * [escondida] (fora dos separadores, ou com um diálogo de abertura à vista) não se vê, mas continua composta para não pedir
 * outra vez. Tocar abre o ecrã das perturbações; tocar ou × marca-as como vistas.
 */
@Composable
fun FaixaAoAbrir(
    fonte: FontePerturbacoes, minhas: suspend () -> Set<String>, preferencias: Preferencias, escondida: Boolean, aoAbrir: () -> Unit,
    agora: () -> Instant = Instant::now,
) {
    val escopo = rememberCoroutineScope()
    var ids by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) {
        if (ids == null) ids = novas(fonte.dasLinhas(minhas(), agora()), preferencias.perturbacoesVistas.first()).map { it.id }.distinct()
    }
    val lista = ids.orEmpty()
    if (escondida || lista.isEmpty()) return
    fun vistas() {
        ids = emptyList()
        escopo.launch { preferencias.marcarVistas(lista.toSet()) }
    }
    FaixaPerturbacoes(lista.size, aoAbrir = { vistas(); aoAbrir() }, aoFechar = ::vistas)
}

/** A linha "Perturbações (N)" do separador Linhas: as ativas, pedidas quando o separador abre; tocar abre o ecrã delas. Se a CM falha, não há linha. */
@Composable
fun LinhaPerturbacoes(fonte: FontePerturbacoes, aoAbrir: () -> Unit, agora: () -> Instant = Instant::now) {
    val n by produceState<Int?>(null) { value = fonte.todas(agora())?.size }
    val total = n ?: return
    ItemLista(onClick = aoAbrir) {
        Text(stringResource(R.string.linhas_perturbacoes, total), color = MaterialTheme.colorScheme.onBackground)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** O ecrã "Perturbações": pede todas e separa as das tuas linhas ([minhas]). */
@Composable
fun PerturbacoesEcra(fonte: FontePerturbacoes, minhas: suspend () -> Set<String>, aoVoltar: () -> Unit, agora: () -> Instant = Instant::now) {
    var falhou by remember { mutableStateOf(false) }
    val listas by produceState<Pair<List<Perturbacao>, List<Perturbacao>>?>(null) {
        val todas = fonte.todas(agora())
        if (todas == null) {
            falhou = true
            return@produceState
        }
        val dasMinhas = dasLinhas(todas, minhas())
        value = dasMinhas to todas.filter { it !in dasMinhas }
    }
    var filtro by rememberSaveable { mutableStateOf<Modo?>(null) }
    PerturbacoesConteudo(
        listas?.first.orEmpty(), listas?.second.orEmpty(), filtro, { filtro = it }, aoVoltar, Modifier.fillMaxSize(),
        aCarregar = listas == null && !falhou, falhou = falhou,
    )
}

/**
 * As das tuas linhas ([minhas]) primeiro, depois as [outras] por operador, só do meio [filtro] (null = todos). Só os títulos
 * e a data do fim; o texto aparece ao tocar no título. Sem nenhuma, diz porquê: [aCarregar], [falhou] (a CM não respondeu) ou não há.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerturbacoesConteudo(
    minhas: List<Perturbacao>,
    outras: List<Perturbacao>,
    filtro: Modo?,
    aoFiltrar: (Modo?) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    aCarregar: Boolean = false,
    falhou: Boolean = false,
) {
    val modos = (minhas + outras).map(::modoDe).distinct().sorted()
    val doMeio = { l: List<Perturbacao> -> l.filter { filtro == null || modoDe(it) == filtro } }
    val minhasDoMeio = doMeio(minhas)
    val porOperador = doMeio(outras).groupBy(::operadorDe).toList().sortedBy { it.first }
    var abertas by rememberSaveable { mutableStateOf(listOf<String>()) }
    @Composable
    fun Item(p: Perturbacao) {
        val aberta = p.id in abertas
        ItemLista(onClick = { abertas = if (aberta) abertas - p.id else abertas + p.id }) {
            Column(Modifier.fillMaxWidth()) {
                Text(p.titulo, color = MaterialTheme.colorScheme.onBackground)
                p.fim?.let {
                    Text(
                        stringResource(R.string.perturbacoes_ate, DIA.format(it.atZone(LISBOA_FUSO))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (aberta) Text(p.texto, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        CabecalhoAzulejo(stringResource(R.string.perturbacoes_titulo), aoVoltar)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "meios") {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filtro == null, { aoFiltrar(null) }, label = { Text(stringResource(R.string.linhas_todos)) })
                    modos.forEach { m ->
                        FilterChip(filtro == m, { aoFiltrar(m) }, leadingIcon = { Icon(iconeModo(m), null, Modifier.size(FilterChipDefaults.IconSize)) }, label = { Text(stringResource(textoDoModo(m))) })
                    }
                }
            }
            if (minhasDoMeio.isNotEmpty()) {
                item(key = "minhas") { Titulo(stringResource(R.string.perturbacoes_minhas)) }
                // Sem chave: dois avisos da CM com o mesmo título e o mesmo início têm o mesmo id.
                items(minhasDoMeio) { Item(it) }
            }
            porOperador.forEach { (operador, lista) ->
                item(key = "operador-$operador") { Titulo(operador) }
                items(lista) { Item(it) }
            }
            if (minhasDoMeio.isEmpty() && porOperador.isEmpty()) {
                item(key = "sem-lista") {
                    Text(
                        stringResource(
                            when {
                                aCarregar -> R.string.a_carregar
                                falhou -> R.string.perturbacoes_falhou
                                else -> R.string.perturbacoes_nenhuma
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).semantics { heading() },
    )
}
