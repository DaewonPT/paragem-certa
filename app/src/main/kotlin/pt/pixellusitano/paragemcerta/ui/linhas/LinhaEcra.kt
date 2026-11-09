package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.dados.AoVivo
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.DetalheDasLinhas
import pt.pixellusitano.paragemcerta.dados.DetalheLinha
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.SentidoLinha
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.FaixaEstado
import pt.pixellusitano.paragemcerta.ui.comum.LinhaChegada
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.comunidade.haQuanto
import pt.pixellusitano.paragemcerta.ui.comunidade.nomeDoTipo
import pt.pixellusitano.paragemcerta.ui.inicio.rememberVeiculosDoMapa
import pt.pixellusitano.paragemcerta.ui.inicio.ultimaPosicao
import pt.pixellusitano.paragemcerta.ui.mapa.Marca
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.mapa.Percurso
import pt.pixellusitano.paragemcerta.ui.mapa.daCarris
import pt.pixellusitano.paragemcerta.ui.mapa.veiculosDaLinha
import pt.pixellusitano.paragemcerta.ui.paragem.estiloDaGravidade
import pt.pixellusitano.paragemcerta.ui.paragem.rememberAoVivoCarris
import pt.pixellusitano.paragemcerta.ui.rotas.hex
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.ui.tema.iconeRelato
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Quantas chegadas se mostram na paragem escolhida. */
private const val CHEGADAS_VISIVEIS = 3

/**
 * O que o ecrã da linha mostra. [detalhe] null = ainda não há linha: [semLinha] (o catálogo ou a rede da CM chegaram e ela não está lá),
 * [falhou] (não se conseguiu saber) ou, com os dois a falso, a carregar. [chegadas] são as da [escolhida]; o [horario] só se pede com
 * [horarioAberto] (null = a carregar, ou [horarioFalhou]); [favorita] é a deste [sentido]; [mapaOficial] o endereço da página da rede.
 */
data class EstadoLinha(
    val detalhe: DetalheLinha? = null,
    val semLinha: Boolean = false,
    val sentido: Int = 0,
    val escolhida: Paragem? = null,
    val chegadas: EstadoChegadas? = null,
    val horario: List<LocalTime>? = null,
    val relatos: List<RelatoRemoto> = emptyList(),
    val favorita: LinhaFavorita? = null,
    val mapaOficial: String? = null,
    val horarioAberto: Boolean = false,
    val horarioFalhou: Boolean = false,
    val falhou: Boolean = false,
    val aoVivo: Map<String, AoVivo> = emptyMap(),
)

// "CM" ou o id do operador no Transitous ("pt-Carris"): como o `config.json` identifica o mapa da rede de cada um.
private fun operadorDoMapa(ref: RefLinha) = if (ref.fonte == Fonte.CM) "CM" else ref.id.substringBefore(':')

/** O ecrã de uma linha ligado aos dados. [sentido] e [paragem] (uma `RefParagem.chave`) abrem-no numa linha favorita. */
@Composable
fun LinhaEcra(
    ref: RefLinha,
    sentido: Int?,
    paragem: String?,
    detalhes: DetalheDasLinhas,
    repositorio: Repositorio,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
    aoAbrirParagem: (Paragem) -> Unit,
    aoAbrirUrl: (String) -> Unit,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    val vm = viewModel(key = ref.chave) { LinhaViewModel(ref, detalhes, repositorio, comunidade::daLinha, sentido, paragem, ultimaPosicao(contexto)) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    // Os veículos ao vivo da linha, com a regra da camada do mapa: só a fonte desta linha (a CM ou a Carris) e só com o ecrã à vista.
    val cm = remember(ref) { if (ref.fonte == Fonte.CM) repositorio.veiculos() else emptyFlow<List<Veiculo>>() }
    val carris = remember(ref) { if (ref.daCarris) repositorio.veiculosCarris() else emptyFlow<List<Veiculo>>() }
    val aoVivo = rememberVeiculosDoMapa(cm, carris, preferencias)
    // As próximas da paragem escolhida com o "ao vivo, a N paragens" da Carris, como no ecrã da paragem; recomeça ao escolher outra paragem.
    val aoVivoDasProximas = rememberAoVivoCarris(repositorio, estado.escolhida?.ref?.id, remember(vm) { vm.estado.map { it.chegadas?.chegadas.orEmpty() } })
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }
    val mapaOficial = estado.detalhe?.let { d -> config.mapasOficiais.firstOrNull { it.operador == operadorDoMapa(d.ref) }?.url }
    val escuro = LocalEscuro.current
    LinhaConteudo(
        estado = estado.copy(mapaOficial = mapaOficial, aoVivo = aoVivoDasProximas),
        aoMudarSentido = vm::mudarSentido,
        aoEscolherParagem = vm::escolherParagem,
        aoAbrirParagem = { vm.abrirParagem(it, aoAbrirParagem) },
        aoFavorita = vm::alternarFavorita,
        aoUsarComoHabitual = vm::usarComoHabitual,
        aoVerHorario = vm::verHorario,
        aoAbrirMapaOficial = mapaOficial?.let { url -> { aoAbrirUrl(url) } },
        mapa = { s -> estado.detalhe?.let { MapaDaLinha(it, s, estado.escolhida, detalhes, escuro, vm::escolherParagem, veiculosDaLinha(aoVivo, it)) } },
        agora = agora,
        aoVoltar = aoVoltar,
    )
}

/**
 * O percurso e as paragens do sentido. O percurso carrega à parte das paragens: se falhar (ou demorar) as paragens já estão desenhadas;
 * tocar numa escolhe-a; a escolhida leva o nome; os [veiculos] ao vivo da linha (CM e Carris) desenham-se por cima.
 */
@Composable
private fun MapaDaLinha(
    d: DetalheLinha, s: SentidoLinha, escolhida: Paragem?, detalhes: DetalheDasLinhas, escuro: Boolean, aoTocarParagem: (Paragem) -> Unit,
    veiculos: List<Veiculo>,
) {
    val forma by produceState(emptyList<Pair<Double, Double>>(), d.ref, s.sentido) {
        value = emptyList()
        value = detalhes.forma(d, s.sentido)
    }
    val cor = remember(d.cor, d.modo) { hex(corDaLinha(d.cor, d.modo)) }
    Mapa(
        paragens = s.paragens,
        enquadrar = s.paragens,
        veiculos = veiculos,
        // Os da CM levam a cor da linha; os da Carris, a sua (ver coresDoVeiculo).
        coresLinhas = mapOf(d.ref.id to cor),
        escuro = escuro,
        centroInicial = s.paragens.first().let { LatLng(it.lat, it.lon) },
        camaraInicial = null,
        centrarEm = null,
        aoMoverCamara = { _, _, _, _, _ -> },
        aoTocarParagem = aoTocarParagem,
        aoTocarVeiculo = {},
        modifier = Modifier.fillMaxSize(),
        percursos = listOf(Percurso(forma, cor, aPe = false)),
        marcas = escolhida?.let { listOf(Marca(it.lat, it.lon, it.nome, sai = true)) }.orEmpty(),
    )
}

/**
 * Conteúdo puro do ecrã da linha (testado com capturas). Em cima o mapa do sentido ([mapa]), por baixo a lista: o sentido, os relatos, o
 * mapa oficial da rede e as paragens. Tocar numa paragem escolhe-a e abre-lhe, no sítio, as próximas chegadas e os botões.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinhaConteudo(
    estado: EstadoLinha,
    aoMudarSentido: (Int) -> Unit,
    aoEscolherParagem: (Paragem) -> Unit,
    aoAbrirParagem: (Paragem) -> Unit,
    aoFavorita: () -> Unit,
    aoUsarComoHabitual: () -> Unit,
    aoVerHorario: () -> Unit,
    aoAbrirMapaOficial: (() -> Unit)?,
    mapa: @Composable (SentidoLinha) -> Unit,
    modifier: Modifier = Modifier,
    agora: Instant = Instant.now(),
    aoVoltar: () -> Unit = {},
) {
    val d = estado.detalhe
    val s = d?.sentidos?.firstOrNull { it.sentido == estado.sentido }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CabecalhoDaLinha(estado, aoVoltar, aoFavorita)
        FrisoAzulejo(Modifier.fillMaxWidth())
        if (d == null || s == null) {
            val texto = when {
                estado.semLinha -> R.string.linha_ja_nao_existe
                estado.falhou -> R.string.linha_falhou
                else -> R.string.a_carregar
            }
            Text(stringResource(texto), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { mapa(s) }
        val relatos = estado.relatos.filter { it.expiraEm > agora.toEpochMilli() }
        // O que vem antes das paragens na lista: os sentidos, os relatos (com o título), o mapa oficial e o título das paragens.
        val antesDasParagens = 1 + (if (relatos.isEmpty()) 0 else 1 + relatos.size) + (if (aoAbrirMapaOficial == null) 0 else 1) + 1
        // Abre com a paragem escolhida à vista (a habitual ou a mais perto de ti pode ser a 40.ª); escolher outra, tocando no mapa ou na
        // lista, põe-na no topo com as chegadas por baixo.
        val indiceDaEscolhida = s.paragens.indexOfFirst { it.ref == estado.escolhida?.ref }
        val lista = rememberLazyListState(if (indiceDaEscolhida > 1) antesDasParagens + indiceDaEscolhida - 1 else 0)
        var primeira by remember { mutableStateOf(true) }
        LaunchedEffect(estado.escolhida?.ref) {
            if (!primeira && indiceDaEscolhida >= 0) lista.animateScrollToItem(antesDasParagens + indiceDaEscolhida)
            primeira = false
        }
        val linha = remember(d.ref, d.nome, d.cor, d.modo) { Linha(d.ref.fonte, d.ref.id, d.nome, d.nomeLongo, d.cor, d.corTexto, d.modo) }
        val corDoPonto = corDaLinha(d.cor, d.modo)
        LazyColumn(Modifier.fillMaxWidth().weight(2f), state = lista) {
            item(key = "sentidos") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.sentidos.forEach { o ->
                        FilterChip(
                            selected = o.sentido == s.sentido,
                            onClick = { aoMudarSentido(o.sentido) },
                            label = { Text(stringResource(R.string.linha_sentido, o.destino)) },
                        )
                    }
                }
            }
            if (relatos.isNotEmpty()) {
                item(key = "relatos") { Titulo(R.string.linha_relatos) }
                items(relatos, key = { "relato-${it.id}" }) { RelatoDaLinha(it, agora) }
            }
            if (aoAbrirMapaOficial != null) {
                item(key = "mapa-oficial") {
                    OutlinedButton(onClick = aoAbrirMapaOficial, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.linha_mapa_oficial))
                    }
                }
            }
            item(key = "paragens") { Titulo(R.string.linha_paragens) }
            itemsIndexed(s.paragens, key = { _, p -> p.ref.chave }) { _, p ->
                val escolhida = p.ref == estado.escolhida?.ref
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(escolhida, role = Role.RadioButton) { aoEscolherParagem(p) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(if (escolhida) 14.dp else 10.dp).background(corDoPonto, CircleShape))
                        Text(
                            p.nome, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (escolhida) FontWeight.Bold else null,
                        )
                    }
                    if (escolhida) PainelDaParagem(p, estado, linha, agora, aoAbrirParagem, aoUsarComoHabitual, aoVerHorario)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDaLinha(estado: EstadoLinha, aoVoltar: () -> Unit, aoFavorita: () -> Unit) {
    val d = estado.detalhe
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = aoVoltar) { Text("← " + stringResource(R.string.voltar), color = MaterialTheme.colorScheme.onPrimary) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (d != null) {
                    val descricao = stringResource(if (estado.favorita != null) R.string.linha_favorita else R.string.linha_nao_favorita)
                    IconButton(onClick = aoFavorita, enabled = estado.escolhida != null, modifier = Modifier.semantics { contentDescription = descricao }) {
                        Text(
                            if (estado.favorita != null) "★" else "☆", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
        if (d != null) {
            // Sem nome curto (algumas da CP) o nome da linha é o longo: um selo desse tamanho não cabia.
            val longo = d.nomeLongo.takeIf { it.isNotEmpty() && it != d.nome }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (longo != null) SeloLinha(d.nome, corDaLinha(d.cor, d.modo), d.corTexto?.let { corDaLinha(it, d.modo) })
                Text(longo ?: d.nome, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(d.operador, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun Titulo(texto: Int) {
    Text(
        stringResource(texto), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** O que se faz com a paragem escolhida: as próximas chegadas da linha nela, abri-la, o horário de hoje e, numa favorita, torná-la a habitual. */
@Composable
private fun PainelDaParagem(
    p: Paragem,
    estado: EstadoLinha,
    linha: Linha,
    agora: Instant,
    aoAbrirParagem: (Paragem) -> Unit,
    aoUsarComoHabitual: () -> Unit,
    aoVerHorario: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 38.dp, end = 16.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.linha_proximas, p.nome), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() },
        )
        val e = estado.chegadas
        if (e != null) FaixaEstado(e, agora, Modifier.padding(vertical = 4.dp))
        // Com o relógio do ecrã: uma chegada que já passou sai sem esperar pelo pedido seguinte.
        val visiveis = e?.chegadas?.filter { aindaPorChegar(it, agora) }?.take(CHEGADAS_VISIVEIS)
        when {
            e == null -> Texto(stringResource(R.string.a_carregar))
            visiveis.isNullOrEmpty() && !e.semLigacao && !e.tempoRealIndisponivel -> Texto(stringResource(R.string.sem_chegadas))
            else -> visiveis.orEmpty().forEach { LinhaChegada(it, linha, agora, aoVivo = estado.aoVivo[it.idViagem]) }
        }
        Button(onClick = { aoAbrirParagem(p) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.linha_abrir_paragem))
        }
        if (estado.horarioAberto) HorarioDeHoje(estado)
        else OutlinedButton(onClick = aoVerHorario, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.linha_horario_hoje)) }
        if (estado.favorita != null && estado.favorita.paragem != p.ref) {
            TextButton(onClick = aoUsarComoHabitual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.linha_usar_habitual)) }
        }
    }
}

@Composable
private fun HorarioDeHoje(estado: EstadoLinha) {
    Text(
        stringResource(R.string.linha_horario_hoje), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
    val horas = estado.horario
    when {
        estado.horarioFalhou -> Texto(stringResource(R.string.linha_horario_falhou))
        horas == null -> Texto(stringResource(R.string.a_carregar))
        horas.isEmpty() -> Texto(stringResource(R.string.linha_sem_horario))
        else -> {
            Text(
                stringResource(R.string.linha_primeira_ultima, HORA.format(horas.first()), HORA.format(horas.last())),
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp),
            )
            // Um só texto: quebra sozinho e o leitor de ecrã lê as horas de seguida, em vez de uma paragem por hora.
            Text(horas.joinToString("   ") { HORA.format(it) }, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Texto(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * Um relato da linha: só para ler. Votar e denunciar fazem-se na paragem (aí confere-se que estás perto ou a bordo); ver
 * [pt.pixellusitano.paragemcerta.ui.comunidade.CartaoRelato]. Por confirmar fica discreto, confirmado em destaque.
 */
@Composable
private fun RelatoDaLinha(r: RelatoRemoto, agora: Instant) {
    // Um tipo que esta versão da app não conhece (o servidor é mais novo) não se mostra: não há como lhe dar nome.
    val tipo = TipoRelato.entries.firstOrNull { it.chave == r.tipo } ?: return
    val estilo = estiloDaGravidade(if (r.confirmado) Gravidade.AVISO else Gravidade.INFO, LocalEscuro.current)
    val passageiros = maxOf(r.confirmacoes, 1)
    val quando = haQuanto(Instant.ofEpochMilli(r.criadoEm), agora)
    val detalhe = pluralStringResource(if (r.confirmado) R.plurals.relato_confirmado else R.plurals.relato_por_confirmar, passageiros, passageiros, quando)
    CartaoAzulejo(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().semantics(mergeDescendants = true) {},
        cor = estilo.fundo, contorno = estilo.texto.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(iconeRelato(tipo), null, Modifier.size(18.dp), tint = estilo.texto)
                Text(stringResource(nomeDoTipo(tipo)), color = estilo.texto, style = MaterialTheme.typography.labelLarge)
            }
            Text(detalhe, color = estilo.texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
