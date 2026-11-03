package pt.pixellusitano.paragemcerta.ui.viagem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.paragensDe
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.comunidade.RelatarNaViagem
import pt.pixellusitano.paragemcerta.ui.inicio.temLocalizacao
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.mapa.Marca
import pt.pixellusitano.paragemcerta.ui.rotas.horaLisboa
import pt.pixellusitano.paragemcerta.ui.rotas.minutosDe
import pt.pixellusitano.paragemcerta.ui.rotas.nomeModo
import pt.pixellusitano.paragemcerta.ui.rotas.percursos
import pt.pixellusitano.paragemcerta.ui.rotas.simboloModo
import pt.pixellusitano.paragemcerta.ui.rotas.textoDistancia
import pt.pixellusitano.paragemcerta.ui.rotas.textoPartilha
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.Notificacoes
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.time.Duration

/**
 * O ecrã da viagem ligado aos ficheiros do serviço. Sem viagem (parou, ou o ficheiro era velho), sai.
 * [mapa] só serve aos testes (o MapLibre não corre no Robolectric): sem ele, o mapa a sério.
 * Com [comunidadeLigada] (o interruptor `comunidade` da config), "Relatar" nas etapas em veículo.
 */
@Composable
fun ViagemEcra(
    aoVoltar: () -> Unit,
    aoEncontrar: (Lugar) -> Unit = {},
    mapa: (@Composable (seguir: Boolean, aoDeixarDeSeguir: () -> Unit) -> Unit)? = null,
    comunidade: Comunidade? = null,
    comunidadeLigada: Boolean = false,
    aoAbrirConta: () -> Unit = {},
) {
    val contexto = LocalContext.current
    val vm = viewModel {
        val app = contexto.applicationContext // não a Activity: o ViewModel vive mais do que ela
        ViagemViewModel(ler = { FicheirosViagem.lerEstado(app)?.let { e -> FicheirosViagem.lerPlano(app)?.let { it to e } } })
    }
    val vista by vm.vista.collectAsStateWithLifecycle()
    var comLocalizacao by remember { mutableStateOf(temLocalizacao(contexto)) }

    // O serviço (outro processo) avisa a cada passo. Ao voltar à app lê-se também: os avisos de enquanto esteve parada perderam-se.
    LifecycleStartEffect(vm) {
        comLocalizacao = temLocalizacao(contexto) // a permissão pode ter sido dada nas definições
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) =
                if (i.getBooleanExtra(FicheirosViagem.EXTRA_SEM_SERVICO, false)) vm.semServico() else vm.atualizar()
        }
        ContextCompat.registerReceiver(contexto, receptor, IntentFilter(FicheirosViagem.ACAO_ESTADO), ContextCompat.RECEIVER_NOT_EXPORTED)
        vm.atualizar()
        onStopOrDispose { contexto.unregisterReceiver(receptor) }
    }

    var relatar by rememberSaveable { mutableStateOf(false) }
    when (val v = vista) {
        is VistaViagem.Ativa -> {
            // A pé não há linha: o "Relatar" é só nas etapas em veículo.
            val linha = v.ecra.itinerario.etapas[v.ecra.atual].linhaNome?.takeIf { comunidadeLigada }
            if (comunidade != null && linha != null) RelatarNaViagem(comunidade, linha, relatar, aoAbrirConta, aoFechar = { relatar = false })
            ViagemConteudo(
                v.ecra,
                aoSilenciar = { pedirAoServico(contexto, Notificacoes.ACAO_SILENCIAR) },
                aoJaSai = { pedirAoServico(contexto, Notificacoes.ACAO_JA_SAI) },
                aoParar = { pedirAoServico(contexto, Notificacoes.ACAO_PARAR) },
                aoPartilhar = {
                    val texto = textoPartilha(v.ecra.itinerario.copy(chegada = v.ecra.chegada), v.ecra.de, v.ecra.para, LISBOA_FUSO)
                    val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                    contexto.startActivity(Intent.createChooser(envio, null))
                },
                aoVoltar = aoVoltar,
                aoEncontrar = aoEncontrar,
                comLocalizacao = comLocalizacao,
                mapa = mapa ?: { seguir, aoDeixarDeSeguir -> MapaDaViagem(v.ecra, seguir, aoDeixarDeSeguir, comLocalizacao) },
                aoRelatar = if (comunidade != null && linha != null) ({ relatar = true }) else null,
            )
        }
        else -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            if (v == VistaViagem.SemViagem) LaunchedEffect(Unit) { aoVoltar() }
        }
    }
}

/** Os botões fazem o mesmo que os da notificação: pedem a ação ao serviço, no processo :trip. */
internal fun pedirAoServico(c: Context, acao: String) {
    c.startService(Intent(c, ServicoViagem::class.java).setAction(acao))
}

/** O mapa da viagem: o percurso todo (a etapa atual em destaque), as paragens dela, o autocarro e o ponto "estás aqui" do MapLibre. */
@Composable
private fun MapaDaViagem(ecra: EcraViagem, seguir: Boolean, aoDeixarDeSeguir: () -> Unit, comLocalizacao: Boolean) {
    val percursos = remember(ecra.itinerario, ecra.atual) { ecra.itinerario.percursos(ecra.atual) }
    val marcas = remember(ecra.itinerario, ecra.atual) { marcasDe(ecra.itinerario.etapas[ecra.atual]) }
    val inicio = percursos[ecra.atual].pontos.first()
    val autocarro = veiculoDaViagem(ecra)
    Mapa(
        paragens = emptyList(), enquadrar = emptyList(), veiculos = listOfNotNull(autocarro),
        coresLinhas = autocarro?.let { mapOf(it.linhaId to percursos[ecra.atual].cor) }.orEmpty(),
        escuro = LocalEscuro.current, centroInicial = LatLng(inicio.first, inicio.second), camaraInicial = null, centrarEm = null,
        aoMoverCamara = { _, _, _, _, _ -> }, aoTocarParagem = {}, aoTocarVeiculo = {},
        modifier = Modifier.fillMaxSize(), percursos = percursos, marcas = marcas,
        mostrarPosicao = comLocalizacao, seguir = seguir, aoDeixarDeSeguir = aoDeixarDeSeguir,
    )
}

/** As paragens da etapa no mapa, com a de saída destacada; a pé, só o fim da etapa (a paragem a apanhar, ou o destino). */
internal fun marcasDe(etapa: Etapa): List<Marca> =
    if (etapa.aPe) listOf(Marca(etapa.para.lat, etapa.para.lon, etapa.para.nome, sai = true))
    else paragensDe(etapa).let { todas -> todas.mapIndexed { k, p -> Marca(p.lat, p.lon, p.nome, sai = k == todas.lastIndex) } }

/** O autocarro da etapa a bordo, tal e qual o motor o leu (o mesmo Veiculo e o mesmo ícone do mapa do Início); null se não há leitura. */
internal fun veiculoDaViagem(ecra: EcraViagem): Veiculo? {
    val e = ecra.estado
    if (e.fase != Fase.A_BORDO) return null
    val linha = ecra.itinerario.etapas[ecra.atual].linhaNome ?: return null
    return Veiculo("viagem", linha, e.veiculoLat ?: return null, e.veiculoLon ?: return null, null, e.ultimoVeiculo ?: return null)
}

/** A altura do painel com o mapa por trás: o suficiente para o título da etapa e as primeiras paragens; o resto arrasta-se. */
private val PAINEL_VISIVEL = 340.dp
private val MAPA_VISIVEL_COM_PAINEL_ABERTO = 104.dp

/**
 * Conteúdo puro do ecrã da viagem (testado com capturas): o mapa em cima e, por baixo, o painel que se arrasta com o texto.
 * [mapa] desenha o mapa (em teste, um retângulo: o MapLibre não desenha no Robolectric); a câmara segue a pessoa enquanto
 * `seguir`, e [aoDeixarDeSeguir] diz que ela mexeu no mapa. Sem [comLocalizacao] não há o que seguir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViagemConteudo(
    ecra: EcraViagem,
    aoSilenciar: () -> Unit,
    aoJaSai: () -> Unit,
    aoParar: () -> Unit,
    aoPartilhar: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    aoEncontrar: (Lugar) -> Unit = {},
    comLocalizacao: Boolean = false,
    mapa: @Composable (seguir: Boolean, aoDeixarDeSeguir: () -> Unit) -> Unit = { _, _ -> },
    aoRelatar: (() -> Unit)? = null,
) {
    val cores = MaterialTheme.colorScheme
    val estado = ecra.estado
    val pager = rememberPagerState(initialPage = ecra.atual) { ecra.cartoes.size }
    // Passa sozinho para a etapa nova, mas só se estavas a ver a que acabou: quem foi espreitar outra não é arrastado.
    var anterior by remember { mutableIntStateOf(ecra.atual) }
    LaunchedEffect(ecra.atual) {
        if (pager.currentPage == anterior) pager.animateScrollToPage(ecra.atual)
        anterior = ecra.atual
    }
    var seguir by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Aberto por inteiro, deixa à vista a linha de cima do mapa, com o "Voltar".
            val alturaPainel = (maxHeight - MAPA_VISIVEL_COM_PAINEL_ABERTO).coerceAtLeast(PAINEL_VISIVEL)
            BottomSheetScaffold(
                sheetPeekHeight = PAINEL_VISIVEL,
                sheetContainerColor = cores.background,
                sheetContent = {
                    // Altura fixa: o pager e a lista de paragens só se esticam com um limite.
                    Column(Modifier.fillMaxWidth().height(alturaPainel)) {
                        if (estado.terminada) {
                            Text(
                                stringResource(R.string.aviso_chegou, ecra.para), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i -> Cartao(ecra, i, aoEncontrar) }
                        if (!estado.terminada) {
                            HorizontalDivider(color = cores.outlineVariant)
                            Rodape(ecra, aoPartilhar)
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    mapa(seguir && comLocalizacao) { seguir = false }
                    Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp) {
                                TextButton(onClick = aoVoltar) { Text("← " + stringResource(R.string.voltar)) }
                            }
                            if (comLocalizacao && !seguir) Button(onClick = { seguir = true }) { Text(stringResource(R.string.viagem_seguir_me)) }
                        }
                        if (ecra.horario) {
                            Surface(color = cores.secondaryContainer, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp) {
                                Text(
                                    stringResource(R.string.viagem_selo_horario), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Fora do painel, para os botões estarem à vista com ele recolhido.
        Botoes(estado, aoSilenciar, aoJaSai, aoParar, aoRelatar)
    }
}

@Composable
private fun Cartao(ecra: EcraViagem, i: Int, aoEncontrar: (Lugar) -> Unit) {
    val c = ecra.cartoes[i]
    Column(Modifier.fillMaxSize()) {
        Cabecalho(ecra, c, i, aoEncontrar)
        if (c.paragens.isNotEmpty()) Paragens(c)
    }
}

@Composable
private fun Cabecalho(ecra: EcraViagem, c: CartaoEtapa, i: Int, aoEncontrar: (Lugar) -> Unit) {
    val cores = MaterialTheme.colorScheme
    val contexto = LocalContext.current
    val estado = ecra.estado
    val e = c.etapa
    val situacao = stringResource(
        when (c.situacao) {
            Situacao.FEITA -> R.string.viagem_feita
            Situacao.ATUAL -> R.string.viagem_agora
            Situacao.SEGUINTE -> R.string.viagem_a_seguir
        },
    )
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.viagem_etapa_n, i + 1, ecra.cartoes.size) + " · " + situacao,
            color = cores.onSurfaceVariant, style = MaterialTheme.typography.labelLarge,
        )
        if (e.aPe) {
            // A pé, o estado traz os metros que faltam; nas outras etapas, o caminho que a fonte deu (ou a reta).
            val metros = if (c.situacao == Situacao.ATUAL) estado.progresso
            else e.distancia ?: distanciaMetros(e.de.lat, e.de.lon, e.para.lat, e.para.lon)
            Text(
                "🚶 " + stringResource(R.string.viagem_a_pe, e.para.nome, textoDistancia(metros)),
                color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            )
            Text(stringResource(R.string.rotas_a_pe, minutosDe(Duration.between(e.partida, e.chegada)).toInt()), color = cores.onSurfaceVariant)
            // A última etapa a pé acaba no destino, não numa paragem.
            if (c.situacao == Situacao.ATUAL && i < ecra.cartoes.lastIndex) {
                OutlinedButton(onClick = { aoEncontrar(e.para) }) { Text(stringResource(R.string.bussola_encontrar)) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeloLinha(e.linhaNome ?: simboloModo(e.modo), corDaLinha(e.cor, e.modo))
                Text(
                    listOfNotNull(nomeModo(e.modo), e.linhaNome).joinToString(" ") + (e.destino?.let { " → $it" } ?: ""),
                    color = cores.onSurface, fontWeight = FontWeight.Bold,
                )
            }
            val agora = c.situacao == Situacao.ATUAL
            val titulo = when {
                agora && estado.fase == Fase.A_BORDO -> c.faltam.coerceAtLeast(1).let { pluralStringResource(R.plurals.viagem_faltam, it, it, e.para.nome) }
                agora && estado.fase == Fase.ESPERA -> Notificacoes.textoEspera(contexto, e, estado, LISBOA_FUSO)
                else -> null
            }
            titulo?.let { Text(it, color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            // À espera e nas etapas por fazer ainda não "faltam": diz-se quantas são e onde se sai.
            if (titulo == null || estado.fase == Fase.ESPERA) {
                val n = c.paragens.lastIndex
                Text(
                    pluralStringResource(R.plurals.detalhe_paragens, n, n) + " · " + stringResource(R.string.detalhe_sai_em, e.para.nome),
                    color = cores.onSurfaceVariant,
                )
            }
        }
    }
}

/** A lista das paragens da etapa; na atual, abre (e acompanha) "estás aqui", a menos que a pessoa esteja a deslizar. */
@Composable
private fun Paragens(c: CartaoEtapa) {
    val lista = rememberLazyListState()
    val aqui = c.paragens.indexOfFirst { it.aqui }
    LaunchedEffect(aqui) { if (aqui > 0 && !lista.isScrollInProgress) lista.animateScrollToItem(aqui - 1) }
    LazyColumn(Modifier.fillMaxSize(), lista) { items(c.paragens) { LinhaParagem(it) } }
}

@Composable
private fun LinhaParagem(p: ParagemVista) {
    val cores = MaterialTheme.colorScheme
    val cor = if (p.passada) cores.onSurfaceVariant else cores.onSurface
    val marcador = when {
        p.aqui -> cores.primary
        p.sai -> cores.secondary
        p.passada -> cores.outlineVariant
        else -> cores.onSurfaceVariant
    }
    val etiquetas = listOfNotNull(
        if (p.aqui) stringResource(R.string.viagem_estas_aqui) else null,
        if (p.sai) stringResource(R.string.viagem_sais_aqui) else null,
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Largura mínima, não fixa: com letra grande a hora cresce em vez de partir em duas linhas.
        Text(p.hora.horaLisboa(), Modifier.widthIn(min = 56.dp), color = cor, fontWeight = FontWeight.ExtraBold, softWrap = false)
        Box(Modifier.size(12.dp).background(marcador, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(p.nome, color = cor, fontWeight = if (p.aqui || p.sai) FontWeight.Bold else FontWeight.Normal)
            if (etiquetas.isNotEmpty()) {
                Text(
                    etiquetas.joinToString(" · "), color = if (p.aqui) cores.primary else cores.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** A chegada prevista e "Partilhar chegada", no fundo do painel (só com ele aberto). */
@Composable
private fun Rodape(ecra: EcraViagem, aoPartilhar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.viagem_chegada_prevista, ecra.chegada.horaLisboa()),
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
        )
        Button(onClick = aoPartilhar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.viagem_partilhar)) }
    }
}

/** Silenciar, Já saí, Parar e (com a comunidade, numa etapa em veículo) Relatar, numa barra fixa por baixo do painel: à vista com ele recolhido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Botoes(estado: EstadoViagem, aoSilenciar: () -> Unit, aoJaSai: () -> Unit, aoParar: () -> Unit, aoRelatar: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Em linhas que quebram: com letra a 200 % os três botões não cabem lado a lado.
        FlowRow(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!estado.terminada) {
                OutlinedButton(onClick = aoSilenciar, enabled = !estado.silenciado) {
                    Text(stringResource(if (estado.silenciado) R.string.viagem_silenciado else R.string.viagem_silenciar))
                }
                // Só se sai de um veículo: a pé não há de que sair e à espera "Já saí" saltava o autocarro que ainda vais apanhar.
                OutlinedButton(onClick = aoJaSai, enabled = estado.fase == Fase.A_BORDO) { Text(stringResource(R.string.viagem_ja_sai)) }
                aoRelatar?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.relatar)) } }
            }
            OutlinedButton(onClick = aoParar) { Text(stringResource(if (estado.terminada) R.string.viagem_concluir else R.string.viagem_parar)) }
        }
    }
}
