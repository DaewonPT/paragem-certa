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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import java.time.Duration
import java.time.Instant

/** Uma paragem no painel, com as suas chegadas (estado null enquanto carrega). */
data class BlocoParagem(val ref: RefParagem, val nome: String, val estado: EstadoChegadas?, val maximo: Int)

private val VALIDADE_IR_PARA: Duration = Duration.ofMinutes(10)

/** Um itinerário guardado ainda serve se tem menos de 10 min e foi planeado a 200 m ou menos da posição de agora. */
internal fun guardadoValido(em: Instant, origem: Pair<Double, Double>, agora: Instant, posicao: Pair<Double, Double>): Boolean =
    Duration.between(em, agora) < VALIDADE_IR_PARA &&
        distanciaMetros(origem.first, origem.second, posicao.first, posicao.second) <= 200

/** Casa e trabalho, por esta ordem, só quando estão a mais de 500 m (mais perto, vai-se a pé). Sem posição, nenhum. */
internal fun locaisParaIr(locais: List<LocalGuardado>, posicao: Pair<Double, Double>?): List<LocalGuardado> {
    val (lat, lon) = posicao ?: return emptyList()
    return listOf("CASA", "TRABALHO").mapNotNull { t -> locais.firstOrNull { it.tipo == t } }
        .filter { distanciaMetros(lat, lon, it.lugar.lat, it.lugar.lon) > 500 }
}

/** "Ir para casa": a próxima ligação (linha e hora) e a duração; sem itinerário, um convite a ver as ligações. */
@Composable
fun CartaoIrPara(local: LocalGuardado, itinerario: Itinerario?, aoAbrir: () -> Unit) {
    val titulo = stringResource(if (local.tipo == "CASA") R.string.painel_ir_casa else R.string.painel_ir_trabalho)
    val detalhe = itinerario?.let { i ->
        val e = i.etapas.firstOrNull { !it.aPe }
        if (e == null) "🚶 " + textoDuracao(i.duracao)
        else stringResource(R.string.painel_proxima, e.linhaNome ?: simboloModo(e.modo), e.partida.horaLisboa(), textoDuracao(i.duracao))
    } ?: stringResource(R.string.painel_ver_ligacoes)
    CartaoAcao(simboloLocal(local.tipo), titulo, detalhe, aoAbrir)
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
    irPara: List<Pair<LocalGuardado, Itinerario?>> = emptyList(),
    ultima: ViagemRecente? = null,
    aoIrPara: (LocalGuardado) -> Unit = {},
    aoRepetir: (ViagemRecente) -> Unit = {},
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        FrisoAzulejo(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        if (irPara.isNotEmpty() || ultima != null) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                irPara.forEach { (l, i) -> CartaoIrPara(l, i) { aoIrPara(l) } }
                ultima?.let { r ->
                    CartaoAcao("↻", stringResource(R.string.painel_repetir), stringResource(R.string.painel_de_para, r.de.nome, r.para.nome)) {
                        aoRepetir(r)
                    }
                }
            }
        }
        if (favoritas.isEmpty()) {
            Text(stringResource(R.string.painel_sem_favoritas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        favoritas.forEach { Bloco(it, "★ ", linhas, agora, aoAbrirParagem) }

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

/** Painel ligado aos dados: favoritas (a por omissão com 3 chegadas, as outras com 2) e até 5 paragens a 800 m. */
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
    val paraIr = remember(locais, posicao) { locaisParaIr(locais, posicao) }
    // Memória do ecrã: cada destino só volta a pedir rotas passados 10 min ou se a posição mudou mais de 200 m
    // (e sempre que o último pedido falhou). Guarda-se também a origem usada no pedido.
    val cache = remember { mutableMapOf<Long, Triple<Instant, Pair<Double, Double>, Itinerario?>>() }
    val itinerarios by produceState(emptyMap<Long, Itinerario?>(), paraIr, posicao) {
        val (lat, lon) = posicao ?: return@produceState
        for (l in paraIr) {
            val agora = Instant.now()
            val i = cache[l.id]?.takeIf { guardadoValido(it.first, it.second, agora, lat to lon) }?.third
                ?: (repositorio.planear(Lugar(MINHA_POSICAO, lat, lon, TipoLugar.POSICAO), l.lugar, agora, false, Filtros()) as? Resultado.Ok)
                    ?.valor?.minByOrNull { it.chegada }.also { cache[l.id] = Triple(agora, lat to lon, it) }
            value = value + (l.id to i)
        }
    }
    val linhas by produceState(emptyMap<String, Linha>()) {
        value = repositorio.linhasCm()
    }
    val perto by produceState<List<pt.pixellusitano.paragemcerta.core.modelo.Paragem>?>(null, posicao) {
        value = posicao?.let { (lat, lon) ->
            maisProximas(repositorio.paragensNaZona(lat - 0.008, lon - 0.01, lat + 0.008, lon + 0.01), lat, lon)
        }
    }
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
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
        irPara = paraIr.map { it to itinerarios[it.id] },
        ultima = recentes.firstOrNull(),
        aoIrPara = { l -> aoAbrirRotas(Rotas(destino = l.lugar.paraRota())) },
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
