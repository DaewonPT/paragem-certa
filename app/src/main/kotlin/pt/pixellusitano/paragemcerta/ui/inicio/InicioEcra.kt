package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.LifecycleResumeEffect
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.ui.comum.FaixaEstado
import pt.pixellusitano.paragemcerta.core.regras.maisProximas
import pt.pixellusitano.paragemcerta.ui.comum.LinhaChegada
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.mapa.LISBOA
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioEcra(
    repositorio: Repositorio,
    aoAbrirParagem: (Paragem) -> Unit,
    aoPesquisar: () -> Unit = {},
    linhaId: String? = null,
    aoLimparLinha: () -> Unit = {},
    painel: @Composable (posicao: Pair<Double, Double>?) -> Unit = {},
) {
    val contexto = LocalContext.current
    val vm = viewModel { InicioViewModel(repositorio::paragensNaZona, repositorio::veiculos) }
    val paragens by vm.paragens.collectAsStateWithLifecycle()
    val veiculos by vm.veiculos.collectAsStateWithLifecycle()
    val escolhido by vm.veiculoEscolhido.collectAsStateWithLifecycle()
    val cores by produceState(emptyMap<String, String>()) { value = repositorio.coresDasLinhasCm() }

    var comLocalizacao by remember { mutableStateOf(temLocalizacao(contexto)) }
    var explicar by remember { mutableStateOf(!comLocalizacao && !localizacaoJaPerguntada(contexto)) }
    var posicao by remember { mutableStateOf(ultimaPosicao(contexto)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        comLocalizacao = r.values.any { it }
    }
    // A permissão pode ter sido dada nas definições do sistema: volta a ver sempre que se regressa à app.
    LifecycleResumeEffect(Unit) {
        comLocalizacao = temLocalizacao(contexto)
        onPauseOrDispose { }
    }
    LaunchedEffect(comLocalizacao) {
        if (comLocalizacao) pedirPosicaoAtual(contexto) { posicao = it }
    }

    var linhaFalhou by remember(linhaId) { mutableStateOf(false) }
    val paragensDaLinha by produceState<List<Paragem>?>(null, linhaId) {
        value = linhaId?.let { l -> repositorio.paragensDaLinha(l).also { if (it == null) linhaFalhou = true } }
    }

    LaunchedEffect(Unit) {
        // Primeira utilização: as paragens da Carris Metropolitana ainda não estão na base local.
        if (repositorio.redeEmFalta()) repositorio.atualizarRede()
    }

    val folha = rememberBottomSheetScaffoldState()
    Box(Modifier.fillMaxSize()) {
    BottomSheetScaffold(
        scaffoldState = folha,
        sheetPeekHeight = 260.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = { painel(posicao) },
    ) { _ ->
    Box(Modifier.fillMaxSize()) {
        Mapa(
            paragens = paragensDaLinha ?: paragens,
            enquadrar = paragensDaLinha.orEmpty(),
            veiculos = veiculos,
            coresLinhas = cores,
            escuro = LocalEscuro.current,
            centroInicial = posicao?.let { LatLng(it.first, it.second) } ?: LISBOA,
            camaraInicial = vm.camara,
            centrarEm = posicao?.let { LatLng(it.first, it.second) },
            aoMoverCamara = vm::aoMoverCamara,
            aoTocarParagem = aoAbrirParagem,
            aoTocarVeiculo = vm::escolherVeiculo,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarraPesquisa(aoPesquisar)
            linhaId?.let { l ->
                CartaoLinha(repositorio, l, paragensDaLinha, linhaFalhou, posicao, aoLimparLinha)
            }
            escolhido?.let { CartaoVeiculo(it, aoFechar = { vm.escolherVeiculo(null) }) }
        }
    }
    }
        // Fora do painel: se ficasse dentro, o painel tapava os botões.
        if (explicar) {
            PedidoLocalizacao(
                aoAceitar = {
                    marcarLocalizacaoPerguntada(contexto)
                    explicar = false
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                aoRecusar = {
                    marcarLocalizacaoPerguntada(contexto)
                    explicar = false
                },
            )
        }
    }
}

@Composable
private fun BarraPesquisa(aoPesquisar: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoPesquisar),
        shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Text("🔍  " + stringResource(R.string.pesquisa_dica), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

/** Linha escolhida na pesquisa: as paragens dela estão no mapa e mostra-se a 1.ª chegada na paragem dela mais próxima. */
@Composable
private fun CartaoLinha(
    repositorio: Repositorio,
    linhaId: String,
    paragens: List<Paragem>?,
    falhou: Boolean,
    posicao: Pair<Double, Double>?,
    aoFechar: () -> Unit,
) {
    val maisProxima = remember(paragens, posicao) {
        val lista = paragens.orEmpty()
        posicao?.let { (lat, lon) -> maisProximas(lista, lat, lon, limite = 1, raioMetros = Double.MAX_VALUE).firstOrNull() }
            ?: lista.firstOrNull()
    }
    val estado by remember(maisProxima) { maisProxima?.let { repositorio.chegadas(it.ref) } ?: kotlinx.coroutines.flow.emptyFlow() }
        .collectAsStateWithLifecycle(initialValue = null)
    val agora = Instant.now()
    val primeira = estado?.chegadas?.firstOrNull { it.linhaId == linhaId && aindaPorChegar(it, agora) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.linha_no_mapa, linhaId), style = MaterialTheme.typography.titleMedium)
                val e = estado
                when {
                    falhou -> Text(stringResource(R.string.linha_falhou), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    paragens == null || (maisProxima != null && e == null) ->
                        Text(stringResource(R.string.a_carregar), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        maisProxima?.let { Text(it.nome, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        e?.let { FaixaEstado(it, agora, Modifier.padding(vertical = 4.dp)) }
                        when {
                            primeira != null -> LinhaChegada(primeira, null, agora)
                            e != null && !e.semLigacao && !e.tempoRealIndisponivel ->
                                Text(stringResource(R.string.sem_chegadas), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.fechar)) }
        }
    }
}

@Composable
private fun CartaoVeiculo(v: Veiculo, aoFechar: () -> Unit) {
    val segundos = Duration.between(v.ultimoSinal, Instant.now()).seconds.coerceAtLeast(0)
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), tonalElevation = 3.dp, shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.veiculo_linha, v.linhaId), style = MaterialTheme.typography.titleMedium)
                v.destino?.let { Text(stringResource(R.string.veiculo_destino, it)) }
                Text(stringResource(R.string.veiculo_sinal, segundos), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.fechar)) }
        }
    }
}

@Composable
internal fun PedidoLocalizacao(aoAceitar: () -> Unit, aoRecusar: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            // Não deixa os toques passarem para o mapa por baixo.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.loc_titulo), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            FrisoAzulejo(Modifier.fillMaxWidth())
            Text(stringResource(R.string.loc_texto), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = aoAceitar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.loc_aceitar)) }
            TextButton(onClick = aoRecusar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.loc_recusar)) }
        }
    }
}
