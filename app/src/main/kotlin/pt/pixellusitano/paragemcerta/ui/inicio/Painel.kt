package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.regras.favoritaPorOmissao
import pt.pixellusitano.paragemcerta.core.regras.maisProximas
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.ViagemRecente
import pt.pixellusitano.paragemcerta.ui.Rotas
import pt.pixellusitano.paragemcerta.ui.rotas.MINHA_POSICAO
import pt.pixellusitano.paragemcerta.ui.rotas.horaLisboa
import pt.pixellusitano.paragemcerta.ui.rotas.paraRota
import pt.pixellusitano.paragemcerta.ui.rotas.simboloLocal
import pt.pixellusitano.paragemcerta.ui.rotas.simboloModo
import pt.pixellusitano.paragemcerta.ui.rotas.textoDuracao
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.FaixaEstado
import pt.pixellusitano.paragemcerta.ui.comum.LinhaChegada
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import java.time.Duration
import java.time.Instant

/** Uma paragem no painel, com as suas chegadas (estado null enquanto carrega). */
data class BlocoParagem(val ref: RefParagem, val nome: String, val estado: EstadoChegadas?, val maximo: Int)

private val VALIDADE_AUTOMATICO: Duration = Duration.ofMinutes(10)
private val VALIDADE_AO_TOQUE: Duration = Duration.ofMinutes(15)

/** Um itinerário guardado ainda serve se tem menos de [validade] e foi planeado a 200 m ou menos da posição de agora. */
internal fun guardadoValido(
    em: Instant, origem: Pair<Double, Double>, agora: Instant, posicao: Pair<Double, Double>, validade: Duration = VALIDADE_AUTOMATICO,
): Boolean =
    Duration.between(em, agora) < validade &&
        distanciaMetros(origem.first, origem.second, posicao.first, posicao.second) <= 200

/**
 * Onde um sítio favorito está em relação a ti, e por isso o que o cartão faz: PERTO (a 500 m ou menos, vai-se a pé), AUTOMATICO
 * (Casa e Trabalho: a sugestão calcula-se sozinha), AO_TOQUE (os outros sítios: só ao tocar, para não gastar pedidos ao Transitous)
 * e SEM_POSICAO (sem localização, tocar abre as rotas, que pedem a origem).
 */
enum class EstadoLocal { PERTO, AUTOMATICO, AO_TOQUE, SEM_POSICAO }

fun estadoDoLocal(local: LocalGuardado, posicao: Pair<Double, Double>?): EstadoLocal {
    val (lat, lon) = posicao ?: return EstadoLocal.SEM_POSICAO
    return when {
        distanciaMetros(lat, lon, local.lugar.lat, local.lugar.lon) <= 500 -> EstadoLocal.PERTO
        local.tipo == "CASA" || local.tipo == "TRABALHO" -> EstadoLocal.AUTOMATICO
        else -> EstadoLocal.AO_TOQUE
    }
}

/** Um sítio favorito no painel, com a sugestão do momento (quando já há) e se a está a calcular. */
data class BlocoLocal(val local: LocalGuardado, val estado: EstadoLocal, val itinerario: Itinerario? = null, val aCalcular: Boolean = false)

/** O primeiro toque num sítio "ao toque" calcula a sugestão; o seguinte (e qualquer toque nos outros estados) abre as rotas. */
internal fun toqueCalcula(estado: EstadoLocal, itinerario: Itinerario?, aCalcular: Boolean): Boolean =
    estado == EstadoLocal.AO_TOQUE && itinerario == null && !aCalcular

/**
 * Um sítio favorito: o nome e, conforme o [estado], a sugestão do momento (linha da primeira etapa em veículo, duração e hora de saída),
 * "Estás perto deste local" ou um convite a tocar. Tocar no cartão e em "Ver mais opções" são alvos separados.
 */
@Composable
fun CartaoLocal(
    local: LocalGuardado, estado: EstadoLocal, itinerario: Itinerario?, aCalcular: Boolean, aoTocar: () -> Unit, aoVerMais: () -> Unit,
) {
    val sugestao = itinerario.takeIf { estado != EstadoLocal.PERTO }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = aoTocar).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(simboloLocal(local.tipo) + " " + local.nome, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val corDetalhe = MaterialTheme.colorScheme.onSurfaceVariant
            when {
                estado == EstadoLocal.PERTO -> Text(stringResource(R.string.painel_perto_local), style = MaterialTheme.typography.bodyMedium, color = corDetalhe)
                sugestao != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val e = sugestao.etapas.firstOrNull { !it.aPe }
                    if (e == null) Text("🚶") else SeloLinha(e.linhaNome ?: simboloModo(e.modo), corDaLinha(e.cor, e.modo))
                    Text(
                        stringResource(R.string.painel_duracao_saida, textoDuracao(sugestao.duracao), sugestao.partida.horaLisboa()),
                        style = MaterialTheme.typography.bodyMedium, color = corDetalhe,
                    )
                }
                aCalcular -> Text(stringResource(R.string.painel_a_calcular), style = MaterialTheme.typography.bodyMedium, color = corDetalhe)
                else -> Text(stringResource(R.string.painel_toca_para_ir), style = MaterialTheme.typography.bodyMedium, color = corDetalhe)
            }
        }
        if (sugestao != null) TextButton(onClick = aoVerMais, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.painel_ver_mais_opcoes))
        }
    }
}

@Composable
private fun CartaoAcao(simbolo: String, titulo: String, detalhe: String, aoAbrir: () -> Unit) {
    OutlinedCard(onClick = aoAbrir, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(simbolo, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Conteúdo puro do painel (testado com capturas). */
@Composable
fun PainelConteudo(
    favoritas: List<BlocoParagem>,
    pertoDeTi: List<BlocoParagem>?,
    linhas: Map<String, Linha>,
    agora: Instant,
    aoAbrirParagem: (RefParagem) -> Unit,
    aoPesquisar: () -> Unit,
    modifier: Modifier = Modifier,
    locais: List<BlocoLocal> = emptyList(),
    ultima: ViagemRecente? = null,
    aoTocarLocal: (BlocoLocal) -> Unit = {},
    aoVerMaisLocal: (BlocoLocal) -> Unit = {},
    aoRepetir: (ViagemRecente) -> Unit = {},
) {
    // "Favoritos" por esta ordem: Casa, Trabalho, as paragens e os outros sítios (cada grupo pela ordem em que o utilizador o pôs).
    val casaETrabalho = listOf("CASA", "TRABALHO").mapNotNull { t -> locais.firstOrNull { it.local.tipo == t } }
    val outros = locais.filter { it.local.tipo != "CASA" && it.local.tipo != "TRABALHO" }
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        FrisoAzulejo(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        ultima?.let { r ->
            CartaoAcao("↻", stringResource(R.string.painel_repetir), stringResource(R.string.painel_de_para, r.de.nome, r.para.nome)) {
                aoRepetir(r)
            }
        }
        Text(stringResource(R.string.painel_favoritos), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        if (favoritas.isEmpty() && locais.isEmpty()) {
            Text(stringResource(R.string.painel_sem_favoritas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        CartoesLocais(casaETrabalho, aoTocarLocal, aoVerMaisLocal)
        favoritas.forEach { Bloco(it, "★ ", linhas, agora, aoAbrirParagem) }
        CartoesLocais(outros, aoTocarLocal, aoVerMaisLocal)

        Text(stringResource(R.string.painel_perto), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        if (pertoDeTi == null) {
            Text(
                stringResource(R.string.painel_procura),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoPesquisar)
                    .padding(vertical = 12.dp),
            )
        } else {
            pertoDeTi.forEach { Bloco(it, "", linhas, agora, aoAbrirParagem) }
        }
    }
}

@Composable
private fun CartoesLocais(locais: List<BlocoLocal>, aoTocar: (BlocoLocal) -> Unit, aoVerMais: (BlocoLocal) -> Unit) {
    if (locais.isEmpty()) return
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        locais.forEach { b -> CartaoLocal(b.local, b.estado, b.itinerario, b.aCalcular, { aoTocar(b) }, { aoVerMais(b) }) }
    }
}

@Composable
private fun Bloco(b: BlocoParagem, prefixo: String, linhas: Map<String, Linha>, agora: Instant, aoAbrir: (RefParagem) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { aoAbrir(b.ref) }.padding(vertical = 6.dp)) {
        Text(prefixo + b.nome, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        val e = b.estado
        if (e == null) {
            Text(stringResource(R.string.a_carregar), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            FaixaEstado(e, agora, Modifier.padding(vertical = 4.dp))
            // Filtra outra vez com o relógio do ecrã: sem rede, a próxima resposta pode demorar minutos.
            val visiveis = e.chegadas.filter { aindaPorChegar(it, agora) }
            if (visiveis.isEmpty() && !e.semLigacao && !e.tempoRealIndisponivel) {
                Text(stringResource(R.string.sem_chegadas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            }
            visiveis.take(b.maximo).forEach { LinhaChegada(it, linhas[it.linhaId], agora) }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A sugestão do momento guardada para um sítio: quando e de onde foi pedida (null = o pedido falhou). */
private class Sugestao(val em: Instant, val origem: Pair<Double, Double>, val itinerario: Itinerario?)

/**
 * Pede as rotas daqui a [local] e guarda a mais cedo a chegar em [sugestoes], ou reaproveita a guardada se ainda serve ([validade] e 200 m).
 * Um pedido que falhou também fica guardado (itinerário null), mas nunca é reaproveitado: pede-se de novo.
 */
private suspend fun sugerir(
    repositorio: Repositorio, sugestoes: MutableMap<Long, Sugestao>, calculando: MutableSet<Long>,
    local: LocalGuardado, posicao: Pair<Double, Double>, validade: Duration,
): Itinerario? {
    val agora = Instant.now()
    sugestoes[local.id]?.takeIf { it.itinerario != null && guardadoValido(it.em, it.origem, agora, posicao, validade) }
        ?.let { return it.itinerario }
    calculando += local.id
    try {
        val partida = Lugar(MINHA_POSICAO, posicao.first, posicao.second, TipoLugar.POSICAO)
        val i = (repositorio.planear(partida, local.lugar, agora, false, Filtros()) as? Resultado.Ok)?.valor?.itinerarios?.minByOrNull { it.chegada }
        sugestoes[local.id] = Sugestao(agora, posicao, i)
        return i
    } finally {
        calculando -= local.id
    }
}

/**
 * Painel ligado aos dados: Favoritos (Casa, Trabalho, as paragens — a por omissão com 3 chegadas, as outras com 2 — e os outros sítios)
 * e até 5 paragens a 800 m.
 */
@Composable
fun Painel(
    repositorio: Repositorio,
    posicao: Pair<Double, Double>?,
    aoAbrirParagem: (RefParagem) -> Unit,
    aoPesquisar: () -> Unit,
    aoAbrirRotas: (Rotas) -> Unit = {},
) {
    val favoritas by remember { repositorio.favoritas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val locais by remember { repositorio.locais() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentes by remember { repositorio.recentes() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }
    // Memória do ecrã. Casa e Trabalho (AUTOMATICO) só voltam a pedir rotas passados 10 min ou se a posição mudou mais de 200 m
    // (e sempre que o último pedido falhou). Os outros sítios (AO_TOQUE) só pedem ao primeiro toque e guardam 15 min.
    val sugestoes = remember { mutableStateMapOf<Long, Sugestao>() }
    val calculando = remember { mutableStateSetOf<Long>() }
    val escopo = rememberCoroutineScope()
    LaunchedEffect(locais, posicao) {
        val p = posicao ?: return@LaunchedEffect
        for (l in locais) if (estadoDoLocal(l, p) == EstadoLocal.AUTOMATICO) sugerir(repositorio, sugestoes, calculando, l, p, VALIDADE_AUTOMATICO)
    }
    val blocosLocais = locais.map { l ->
        val estado = estadoDoLocal(l, posicao)
        // A sugestão automática mostra-se até ser substituída; a de um toque deixa de se mostrar passados 15 min (ou 200 m).
        val s = sugestoes[l.id]?.takeIf {
            estado == EstadoLocal.AUTOMATICO || (posicao != null && guardadoValido(it.em, it.origem, agora, posicao, VALIDADE_AO_TOQUE))
        }
        BlocoLocal(l, estado, s?.itinerario, aCalcular = l.id in calculando || (estado == EstadoLocal.AUTOMATICO && s == null))
    }
    val abrirRotas = { l: LocalGuardado -> aoAbrirRotas(Rotas(destino = l.lugar.paraRota())) }

    val linhas by produceState(emptyMap<String, Linha>()) {
        value = repositorio.linhasCm()
    }
    val perto by produceState<List<pt.pixellusitano.paragemcerta.core.modelo.Paragem>?>(null, posicao) {
        value = posicao?.let { (lat, lon) ->
            maisProximas(repositorio.paragensNaZona(lat - 0.008, lon - 0.01, lat + 0.008, lon + 0.01), lat, lon)
        }
    }
    val primeira = remember(favoritas, posicao) { favoritaPorOmissao(favoritas, posicao) }
    val ordenadas = listOfNotNull(primeira) + favoritas.filter { it.ref != primeira?.ref }

    PainelConteudo(
        favoritas = ordenadas.map { f ->
            BlocoParagem(f.ref, f.nome, estadoDe(repositorio, f.ref), maximo = if (f.ref == primeira?.ref) 3 else 2)
        },
        pertoDeTi = perto?.map { p -> BlocoParagem(p.ref, p.nome, estadoDe(repositorio, p.ref), maximo = 1) },
        linhas = linhas,
        agora = agora,
        aoAbrirParagem = aoAbrirParagem,
        aoPesquisar = aoPesquisar,
        modifier = Modifier.verticalScroll(rememberScrollState()),
        locais = blocosLocais,
        ultima = recentes.firstOrNull(),
        aoTocarLocal = { b ->
            if (posicao != null && toqueCalcula(b.estado, b.itinerario, b.aCalcular)) {
                // Sem rota (rede em baixo, ou nenhuma) o toque não fica sem resposta: as rotas explicam o que falhou.
                escopo.launch { if (sugerir(repositorio, sugestoes, calculando, b.local, posicao, VALIDADE_AO_TOQUE) == null) abrirRotas(b.local) }
            } else abrirRotas(b.local)
        },
        aoVerMaisLocal = { b -> abrirRotas(b.local) },
        // "A minha posição" não se guarda como origem: volta a ser a posição de agora.
        aoRepetir = { r -> aoAbrirRotas(Rotas(origem = r.de.takeIf { it.nome != MINHA_POSICAO }?.paraRota(), destino = r.para.paraRota())) },
    )
}

@Composable
private fun estadoDe(repositorio: Repositorio, ref: RefParagem): EstadoChegadas? {
    val fluxo = remember(ref) { repositorio.chegadas(ref) }
    // Só consulta enquanto o ecrã está visível (a recolha pára abaixo de STARTED).
    return fluxo.collectAsStateWithLifecycle(initialValue = null).value
}
