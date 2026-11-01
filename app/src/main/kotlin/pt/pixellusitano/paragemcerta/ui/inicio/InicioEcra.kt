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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import pt.pixellusitano.paragemcerta.ui.Rotas
import pt.pixellusitano.paragemcerta.ui.comum.DialogoNome
import pt.pixellusitano.paragemcerta.ui.comum.ItemLista
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.ui.viagem.retomarViagem
import pt.pixellusitano.paragemcerta.ui.viagem.viagemInterrompida
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
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
    aoIrPara: (Rotas) -> Unit = {},
    aoAbrirViagem: () -> Unit = {},
) {
    val contexto = LocalContext.current
    val vm = viewModel { InicioViewModel(repositorio::paragensNaZona, repositorio::veiculos, repositorio::lugarEm) }
    val menu by vm.menu.collectAsStateWithLifecycle()
    var guardar by remember { mutableStateOf<Lugar?>(null) }
    val escopo = rememberCoroutineScope()
    val paragens by vm.paragens.collectAsStateWithLifecycle()
    val veiculos by vm.veiculos.collectAsStateWithLifecycle()
    val escolhido by vm.veiculoEscolhido.collectAsStateWithLifecycle()
    val cores by produceState(emptyMap<String, String>()) { value = repositorio.coresDasLinhasCm() }

    var comLocalizacao by remember { mutableStateOf(temLocalizacao(contexto)) }
    var explicar by remember { mutableStateOf(!comLocalizacao && !localizacaoJaPerguntada(contexto)) }
    var posicao by remember { mutableStateOf(ultimaPosicao(contexto)) }
    var centrarAgora by remember { mutableIntStateOf(0) }
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

    // O Android pode ter fechado o serviço da viagem enquanto a app esteve fora: vê-se sempre que se regressa.
    var interrompida by remember { mutableStateOf(false) }
    val naoRetomou = stringResource(R.string.viagem_nao_retomou)
    LifecycleResumeEffect(Unit) {
        val leitura = escopo.launch { interrompida = withContext(Dispatchers.IO) { viagemInterrompida(contexto) } }
        onPauseOrDispose { leitura.cancel() }
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
            aoToqueLongo = vm::toqueLongo,
            mostrarPosicao = comLocalizacao,
            centrarAgora = centrarAgora,
        )
        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarraPesquisa(aoPesquisar)
            if (interrompida) {
                AvisoViagemInterrompida(
                    aoRetomar = {
                        if (retomarViagem(contexto)) aoAbrirViagem() else Toast.makeText(contexto, naoRetomou, Toast.LENGTH_LONG).show()
                    },
                    aoDescartar = {
                        FicheirosViagem.apagar(contexto)
                        interrompida = false
                    },
                )
            }
            linhaId?.let { l ->
                CartaoLinha(repositorio, l, paragensDaLinha, linhaFalhou, posicao, aoLimparLinha)
            }
            escolhido?.let { CartaoVeiculo(it, aoFechar = { vm.escolherVeiculo(null) }) }
            BotaoCentrar(Modifier.align(Alignment.End)) {
                if (comLocalizacao) {
                    centrarAgora++
                    pedirPosicaoAtual(contexto) { posicao = it }
                } else {
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
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
        menu?.let { lugar ->
            MenuPonto(
                lugar,
                aoIrPara = { vm.irParaAqui()?.let(aoIrPara) },
                aoPartirDaqui = { vm.partirDaqui()?.let(aoIrPara) },
                aoGuardar = {
                    guardar = lugar
                    vm.fecharMenu()
                },
                aoFechar = vm::fecharMenu,
            )
        }
        val guardado = stringResource(R.string.local_guardado)
        guardar?.let { lugar ->
            DialogoNome(
                stringResource(R.string.menu_guardar_local),
                inicial = lugar.nome.takeIf { it != InicioViewModel.PONTO_NO_MAPA }.orEmpty(),
                aoConfirmar = { nome ->
                    escopo.launch { repositorio.guardarLocal(nome, lugar, "OUTRO") }
                    Toast.makeText(contexto, guardado, Toast.LENGTH_SHORT).show()
                    guardar = null
                },
                aoCancelar = { guardar = null },
            )
        }
    }
}

/** Menu de um ponto tocado sem largar: o nome e as três ações. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPonto(lugar: Lugar, aoIrPara: () -> Unit, aoPartirDaqui: () -> Unit, aoGuardar: () -> Unit, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            lugar.nome, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(
            "🧭" to R.string.menu_ir_para_aqui to aoIrPara,
            "🚩" to R.string.menu_partir_daqui to aoPartirDaqui,
            "⭐" to R.string.menu_guardar_local to aoGuardar,
        ).forEach { (simboloTexto, acao) ->
            ItemLista(onClick = acao) {
                Text(simboloTexto.first, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(simboloTexto.second), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
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

/** Toca em qualquer ponto para retomar; "Descartar" apaga a viagem. */
@Composable
internal fun AvisoViagemInterrompida(aoRetomar: () -> Unit, aoDescartar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.viagem_interrompida),
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoRetomar).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = aoDescartar) { Text(stringResource(R.string.viagem_descartar)) }
        }
    }
}

@Composable
private fun BotaoCentrar(modifier: Modifier, aoTocar: () -> Unit) {
    val descricao = stringResource(R.string.centrar_posicao)
    Surface(
        modifier.size(48.dp).clickable(role = Role.Button, onClick = aoTocar).semantics { contentDescription = descricao },
        shape = CircleShape, shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // O leitor de ecrã lê a descrição, não o símbolo.
            Text("◎", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
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
