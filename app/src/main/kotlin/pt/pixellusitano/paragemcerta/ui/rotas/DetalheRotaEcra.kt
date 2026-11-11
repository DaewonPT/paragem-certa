package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import androidx.compose.material3.Icon
import pt.pixellusitano.paragemcerta.ui.comum.SeloModo
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import pt.pixellusitano.paragemcerta.ui.comum.BotaoPrincipal
import pt.pixellusitano.paragemcerta.ui.comum.BotaoSecundario
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.RotuloAtraso
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.viagem.CM_INTERVALO
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.contornoDeCima
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.comum.ladoDoIcone
import pt.pixellusitano.paragemcerta.ui.inicio.temLocalizacao
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.mapa.Percurso
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.ui.viagem.PreparacaoViagem
import java.time.Duration
import java.time.Instant

/** Sem desenho da fonte, o troço vai em linha reta de uma ponta à outra. */
private fun Etapa.pontos() = desenho.ifEmpty { listOf(de.lat to de.lon, para.lat to para.lon) }

internal fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

/** O percurso de cada etapa no mapa, com a cor da linha. Com [atual] (a etapa da viagem), as outras vão esbatidas. */
internal fun Itinerario.percursos(atual: Int? = null) =
    etapas.mapIndexed { i, e -> Percurso(e.pontos(), hex(corDaLinha(e.cor, e.modo)), e.aPe, esbatido = atual != null && i != atual) }

/** O Transitous chama START e END às pontas dadas em coordenadas; nunca são o nome de uma paragem. */
private val NOMES_DE_COORDENADA = setOf("START", "END")

/**
 * O itinerário com os nomes que a pessoa viu na origem e no destino, onde a fonte pôs START ou END. Um nome verdadeiro
 * (a paragem escolhida) fica: são estes nomes que a viagem diz na notificação, no "Chegaste a ..." e na partilha.
 */
fun Itinerario.comNomes(de: String, para: String): Itinerario = copy(
    etapas = etapas.mapIndexed { i, e ->
        e.copy(
            de = if (i == 0 && e.de.nome in NOMES_DE_COORDENADA) e.de.copy(nome = de) else e.de,
            para = if (i == etapas.lastIndex && e.para.nome in NOMES_DE_COORDENADA) e.para.copy(nome = para) else e.para,
        )
    },
)

/**
 * [seguida]: a etapa cujo autocarro se mostra no mapa, só uma de cada vez (null = nenhuma); [aoSeguir] escolhe outra.
 * [comunidadeLigada]: o interruptor `comunidade` da config, para a viagem saber se pode pedir os relatos ao servidor.
 */
@Composable
fun DetalheRotaEcra(
    it: Itinerario, de: String, para: String, repositorio: Repositorio, preferencias: Preferencias, aoComecou: () -> Unit, aoVoltar: () -> Unit,
    seguida: Int?, aoSeguir: (Int) -> Unit,
    comunidadeLigada: Boolean = false,
) {
    val contexto = LocalContext.current
    val escuro = LocalEscuro.current
    var preparar by rememberSaveable { mutableStateOf(false) }
    val etapaSeguida = seguida?.let(it.etapas::getOrNull)
    val (autocarro, estadoAutocarro) = rememberAutocarro(repositorio, seguida, etapaSeguida)
    var centrar by remember { mutableIntStateOf(0) }
    var centrado by remember(seguida) { mutableStateOf(false) }
    LaunchedEffect(seguida, autocarro != null) {
        if (autocarro != null && !centrado) {
            centrado = true
            centrar++
        }
    }
    DetalheRotaConteudo(
        it, de, para,
        mapa = {
            val percursos = it.percursos()
            val inicio = percursos.first().pontos.first()
            Mapa(
                paragens = emptyList(), enquadrar = emptyList(), veiculos = listOfNotNull(autocarro),
                coresLinhas = if (autocarro == null || etapaSeguida == null) emptyMap()
                else mapOf(autocarro.linhaId to hex(corDaLinha(etapaSeguida.cor, etapaSeguida.modo))),
                escuro = escuro, centroInicial = LatLng(inicio.first, inicio.second), camaraInicial = null, centrarEm = null,
                aoMoverCamara = { _, _, _, _, _ -> }, aoTocarParagem = {}, aoTocarVeiculo = {},
                modifier = Modifier.fillMaxSize(), percursos = percursos, mostrarPosicao = temLocalizacao(contexto),
                centrarNoVeiculo = centrar,
            )
        },
        seguida = seguida,
        estadoAutocarro = estadoAutocarro,
        aoVerAutocarro = { i -> if (seguida == i) centrar++ else aoSeguir(i) },
        aoVoltar = aoVoltar,
        aoComecar = { preparar = true },
        aoPartilhar = {
            val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, textoPartilha(it, de, para, LISBOA_FUSO))
            contexto.startActivity(Intent.createChooser(envio, null))
        },
        aoAbrirTvde = {
            // Sem a app da loja (telemóveis sem Google), abre a loja no navegador.
            runCatching { contexto.startActivity(intentTvde(contexto.packageManager)) }.onFailure {
                runCatching {
                    contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=TVDE&c=apps")))
                }
            }
        },
    )
    if (preparar) {
        PreparacaoViagem(
            remember(it, de, para) { it.comNomes(de, para) }, preferencias,
            aoComecou = {
                preparar = false
                aoComecou()
            },
            aoCancelar = { preparar = false },
            comunidadeLigada = comunidadeLigada,
        )
    }
}

/**
 * O autocarro da [etapa] seguida ([i]) e o que dizer dele. Com o ecrã visível pede as chegadas da paragem de embarque a cada
 * 30 s e recolhe os veículos da CM (15 s); sem etapa, ou fora do ecrã, não pede nada. Numa falha nada muda: fica o último
 * autocarro conhecido. Depois de o ver no mapa segue a viagem dele, mesmo quando passa na paragem e sai das chegadas.
 */
@Composable
private fun rememberAutocarro(repositorio: Repositorio, i: Int?, etapa: Etapa?): Pair<Veiculo?, EstadoAutocarro> {
    val paragem = etapa?.embarqueCm()
    val ciclo = LocalLifecycleOwner.current.lifecycle
    var chegadas by remember(i) { mutableStateOf(emptyList<Chegada>()) }
    var voltas by remember(i) { mutableIntStateOf(0) }
    LaunchedEffect(i) {
        if (paragem == null) return@LaunchedEffect
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                (repositorio.chegadasAgora(RefParagem(Fonte.CM, paragem)) as? Resultado.Ok)?.let { chegadas = it.valor }
                voltas++
                delay(CM_INTERVALO.toMillis())
            }
        }
    }
    val veiculos by remember(paragem != null) { if (paragem != null) repositorio.veiculos() else emptyFlow() }
        .collectAsStateWithLifecycle<List<Veiculo>?>(null) // null = ainda sem resposta
    var guardada by remember(i) { mutableStateOf<String?>(null) }
    val autocarro = remember(etapa, chegadas, veiculos) {
        etapa?.let { autocarroDaEtapa(chegadas, veiculos.orEmpty(), it, Instant.now(), guardada) }
    }
    LaunchedEffect(autocarro?.viagem) { autocarro?.viagem?.let { guardada = it } }
    return autocarro to estadoAutocarro(autocarro, voltas, veiculos != null)
}

@Composable
fun DetalheRotaConteudo(
    it: Itinerario,
    de: String,
    para: String,
    mapa: @Composable () -> Unit,
    aoVoltar: () -> Unit,
    aoComecar: () -> Unit,
    aoPartilhar: () -> Unit,
    aoAbrirTvde: () -> Unit,
    modifier: Modifier = Modifier,
    /** A etapa cujo autocarro se procura (null = nenhuma) e o que já se sabe dele; o toque em "Onde está o autocarro?" dá o índice da etapa. */
    seguida: Int? = null,
    estadoAutocarro: EstadoAutocarro = EstadoAutocarro.A_PROCURAR,
    aoVerAutocarro: (Int) -> Unit = {},
) {
    val cores = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(cores.background)) {
        Box(Modifier.fillMaxWidth().height(ALTURA_DO_MAPA)) {
            mapa()
            Surface(
                shape = CircleShape, shadowElevation = 4.dp, border = BorderStroke(1.dp, cores.outlineVariant),
                modifier = Modifier.statusBarsPadding().padding(8.dp),
            ) {
                IconButton(onClick = aoVoltar, Modifier.size(48.dp)) { Icon(Icones.Voltar, stringResource(R.string.voltar)) }
            }
        }
        // A folha com os dados sobe 28 dp por cima do mapa, de topo redondo e com contorno.
        val forma = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        LazyColumn(
            Modifier.fillMaxSize().padding(top = ALTURA_DO_MAPA - 28.dp)
                .clip(forma)
                .background(cores.background)
                .contornoDeCima(cores.outlineVariant, forma),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$de → $para", Modifier.semantics { heading() },
                        color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${textoDuracao(it.duracao)} · ${it.partida.horaLisboa()} – ${it.chegada.horaLisboa()}",
                        color = cores.onSurfaceVariant,
                    )
                }
            }
            item {
                CartaoAzulejo(Modifier.fillMaxWidth()) {
                    it.etapas.forEachIndexed { i, e ->
                        val daCm = e.embarqueCm() != null
                        LinhaEtapa(
                            e, paraNome = if (i == it.etapas.lastIndex) para else e.para.nome,
                            aoVerAutocarro = { aoVerAutocarro(i) }.takeIf { daCm },
                            estadoAutocarro = estadoAutocarro.takeIf { daCm && i == seguida },
                        )
                        HorizontalDivider(color = cores.outlineVariant)
                    }
                    Hora(it.chegada.horaLisboa(), cores.onSurface) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icones.Lugar, null, Modifier.size(ladoDoIcone(20)), tint = cores.primary)
                            Text(stringResource(R.string.detalhe_chegada, para), color = cores.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoPrincipal(aoComecar, Modifier.fillMaxWidth()) { Text(stringResource(R.string.detalhe_comecar)) }
                    BotaoSecundario(aoPartilhar, Modifier.fillMaxWidth()) { Text(stringResource(R.string.detalhe_partilhar)) }
                    BotaoSecundario(aoAbrirTvde, Modifier.fillMaxWidth()) { Text(stringResource(R.string.detalhe_tvde)) }
                }
            }
        }
    }
}

private val ALTURA_DO_MAPA = 260.dp

/** Hora à esquerda, numa coluna fixa, e o que acontece a essa hora à direita. */
@Composable
private fun Hora(hora: String, cor: Color, conteudo: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Largura mínima, não fixa: com letra grande a hora cresce em vez de partir em duas linhas.
        Text(hora, Modifier.widthIn(min = 56.dp), color = cor, fontWeight = FontWeight.ExtraBold, softWrap = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { conteudo() }
    }
}

/** [aoVerAutocarro]: só nas etapas da CM, com o botão "Onde está o autocarro?"; [estadoAutocarro]: só na etapa seguida. */
@Composable
private fun LinhaEtapa(e: Etapa, paraNome: String, aoVerAutocarro: (() -> Unit)?, estadoAutocarro: EstadoAutocarro?) {
    val cores = MaterialTheme.colorScheme
    val minutos = minutosDe(Duration.between(e.partida, e.chegada)).toInt()
    if (e.aPe) {
        Hora(e.partida.horaLisboa(), cores.onSurfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icones.APe, null, Modifier.size(ladoDoIcone(20)), tint = cores.onSurfaceVariant)
                Text(
                    stringResource(R.string.detalhe_a_pe, minutos) + (e.distancia?.let { " (${textoDistancia(it)})" } ?: ""),
                    color = cores.onSurface,
                )
            }
            Text(stringResource(R.string.detalhe_ate, paraNome), color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val prevista = e.partida.takeIf { e.aoVivo }
        val limites = LocalLimites.current
        val corHora = CoresAtraso.cor(estado(e.partidaProgramada, prevista, limites), LocalEscuro.current)
        val paragens = e.paragensIntermedias + 1 // contando a paragem onde se sai
        Hora(e.partida.horaLisboa(), corHora) {
            val selo = @Composable { e.linhaNome?.let { SeloLinha(it, corDaLinha(e.cor, e.modo)) } ?: SeloModo(e.modo, corDaLinha(e.cor, e.modo)) }
            val nome = @Composable {
                Text(
                    listOfNotNull(nomeModo(e.modo), e.linhaNome).joinToString(" ") + (e.destino?.let { " → $it" } ?: ""),
                    color = cores.onSurface, fontWeight = FontWeight.Bold,
                )
            }
            // Com letra grande o selo vai para cima do nome: ao lado, a palavra "Autocarro" partia a meio.
            if (LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { selo(); nome() }
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { selo(); nome() }
            RotuloAtraso(e.partidaProgramada, prevista, limites, corHora)
            Text(
                listOf(
                    textoDuracao(Duration.between(e.partida, e.chegada)),
                    pluralStringResource(R.plurals.detalhe_paragens, paragens, paragens),
                    stringResource(R.string.detalhe_sai_em, paraNome),
                ).joinToString(" · "),
                color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            aoVerAutocarro?.let {
                BotaoSecundario(it) {
                    // Com letra grande o ícone sai: ao lado dele, o "autocarro?" partia a meio.
                    if (LocalDensity.current.fontScale < 1.5f) Icon(Icones.Lugar, null, Modifier.padding(end = 8.dp).size(ladoDoIcone(18)))
                    Text(stringResource(R.string.detalhe_onde_esta_autocarro))
                }
            }
            estadoAutocarro?.let {
                Text(
                    stringResource(
                        when (it) {
                            EstadoAutocarro.A_PROCURAR -> R.string.detalhe_autocarro_a_procurar
                            EstadoAutocarro.SEM_POSICAO -> R.string.detalhe_autocarro_sem_posicao
                            EstadoAutocarro.NO_MAPA -> R.string.detalhe_autocarro_no_mapa
                        },
                    ),
                    // Educado: o leitor de ecrã diz cada mudança sem cortar o que estiver a ler.
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
