package pt.pixellusitano.paragemcerta.ui.inicio

import pt.pixellusitano.paragemcerta.ui.tema.iconeLocal
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import androidx.compose.material3.Icon
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.PadraoAzulejo
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.core.fontes.emel.EstacaoGira
import pt.pixellusitano.paragemcerta.core.fontes.servidor.DadosComunidade
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.EstacoesGira
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.ui.mapa.daCarris
import pt.pixellusitano.paragemcerta.ui.mapa.paragensDoMapa
import pt.pixellusitano.paragemcerta.ui.mapa.veiculosDoMapa
import pt.pixellusitano.paragemcerta.ui.rotas.EscolherLugarEcra
import pt.pixellusitano.paragemcerta.ui.rotas.lugaresRecentes
import pt.pixellusitano.paragemcerta.ui.rotas.rememberGuardarLocal
import pt.pixellusitano.paragemcerta.ui.viagem.rememberRetomarViagem
import pt.pixellusitano.paragemcerta.ui.viagem.viagemEmCurso
import pt.pixellusitano.paragemcerta.ui.viagem.viagemInterrompida
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import java.time.Duration
import java.time.Instant

/** Os relatos pedem-se 1 s depois de a câmara parar; o Início só entrega a lista de paragens 0,5 s depois (ver [InicioViewModel]). */
internal const val ESPERA_RELATOS_MS = 1_000L - InicioViewModel.ESPERA_CAMARA_MS

/**
 * Os relatos da comunidade das [paragens] à vista, para marcar no mapa os confirmados (ver [paragensDoMapa]). Só se pedem com a
 * `comunidade` ligada na config e a camada "Relatos da comunidade" ligada nas Definições, depois da espera por a câmara parar
 * (cada lista nova recomeça-a) e nunca com a app em segundo plano: ao ir para lá a espera e o pedido por fazer cancelam-se, e ao
 * voltar recomeça-se (a cache de 2 min evita pedir o que já veio). Falhando o pedido, fica o que estava guardado.
 */
@Composable
internal fun rememberRelatosDoMapa(
    paragens: List<Paragem>,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
): DadosComunidade {
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    val camada by preferencias.camadaRelatos.collectAsStateWithLifecycle(initialValue = true)
    val ciclo = LocalLifecycleOwner.current.lifecycle
    var relatos by remember { mutableStateOf(DadosComunidade()) }
    val ligada = config.interruptores.comunidade && camada
    LaunchedEffect(ligada, paragens) {
        if (!ligada || paragens.isEmpty()) {
            relatos = DadosComunidade()
            return@LaunchedEffect
        }
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(ESPERA_RELATOS_MS)
            relatos = comunidade.dasParagens(paragens.map { it.ref.chave })
        }
    }
    return relatos
}

private val SEM_VEICULOS = emptyFlow<List<Veiculo>>()

/**
 * Os veículos ao vivo do mapa: [cm] e [carris] só se recolhem com a camada "Autocarros ao vivo" ligada (desligada, nenhum dos dois
 * pede nada) e com o ecrã à vista (STARTED): com a app em segundo plano não há pedidos. Até as Preferências responderem a camada conta
 * como desligada, para quem a desligou não ter um pedido a cada abertura.
 */
@Composable
internal fun rememberVeiculosDoMapa(cm: Flow<List<Veiculo>>, carris: Flow<List<Veiculo>>, preferencias: Preferencias): List<Veiculo> {
    val aoVivo by preferencias.camadaAoVivo.collectAsStateWithLifecycle(initialValue = false)
    val daCm by (if (aoVivo) cm else SEM_VEICULOS).collectAsStateWithLifecycle(initialValue = emptyList())
    val daCarris by (if (aoVivo) carris else SEM_VEICULOS).collectAsStateWithLifecycle(initialValue = emptyList())
    return veiculosDoMapa(daCm, daCarris, aoVivo)
}

/**
 * As estações GIRA do mapa. Com a camada "Estações GIRA" ligada lê as guardadas e, se têm mais de 24 h, pede à EMEL quando o mapa
 * abre (e a cada regresso à app, que gasta um pedido só se passou o dia); desligada, não pede e não desenha nada. Falhar deixa as
 * antigas (o mapa sem pinos é pior que o mapa com os de há dias).
 */
@Composable
internal fun rememberGiraDoMapa(estacoes: EstacoesGira, preferencias: Preferencias): List<EstacaoGira> {
    val camada by preferencias.camadaGira.collectAsStateWithLifecycle(initialValue = false)
    val lidas by estacoes.atual.collectAsStateWithLifecycle()
    val ciclo = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(camada) {
        if (!camada) return@LaunchedEffect
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                estacoes.seVelhas()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem rede, disco cheio: ficam as antigas e a abertura seguinte tenta outra vez
            }
        }
    }
    return if (camada) lidas else emptyList()
}

internal const val PACOTE_GIRA = "com.vaimoo.emelsharing"

/** Por ordem: a app GIRA (se estiver instalada), a loja e a página da app na loja, no navegador. */
private fun intencoesGira(pm: PackageManager): List<Intent> = listOfNotNull(
    pm.getLaunchIntentForPackage(PACOTE_GIRA),
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACOTE_GIRA")),
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACOTE_GIRA")),
)

/** Abre a primeira que abrir (sem a app da loja, nos telemóveis sem Google, o market:// não abre nada); false se nenhuma abrir. */
internal fun abrirGira(pm: PackageManager, iniciar: (Intent) -> Unit): Boolean = intencoesGira(pm).any { runCatching { iniciar(it) }.isSuccess }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioEcra(
    repositorio: Repositorio,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
    estacoesGira: EstacoesGira,
    aoAbrirParagem: (Paragem) -> Unit,
    aoPesquisar: () -> Unit = {},
    painel: @Composable (posicao: Pair<Double, Double>?, aoDefinirLocal: (tipo: String) -> Unit) -> Unit = { _, _ -> },
    aoIrPara: (Rotas) -> Unit = {},
    aoAbrirViagem: () -> Unit = {},
    abrirFavoritos: Boolean = false,
    aoAbrirConta: (() -> Unit)? = null,
    mapa: (@Composable (LigacaoMapaInicio) -> Unit)? = null,
) {
    val contexto = LocalContext.current
    val vm = viewModel { InicioViewModel(repositorio::paragensNaZona, repositorio::veiculos, repositorio::lugarEm, repositorio::veiculosCarris) }
    val menu by vm.menu.collectAsStateWithLifecycle()
    var guardar by remember { mutableStateOf<Lugar?>(null) }
    // "CASA" ou "TRABALHO" enquanto se escolhe a morada no ecrã todo; null = o mapa.
    var definir by rememberSaveable { mutableStateOf<String?>(null) }
    val guardarLocal = rememberGuardarLocal(repositorio)
    val escopo = rememberCoroutineScope()
    val paragens by vm.paragens.collectAsStateWithLifecycle()
    val veiculos = rememberVeiculosDoMapa(vm.veiculos, vm.veiculosCarris, preferencias)
    val gira = rememberGiraDoMapa(estacoesGira, preferencias)
    var giraEscolhida by remember { mutableStateOf<EstacaoGira?>(null) }
    val escolhido by vm.veiculoEscolhido.collectAsStateWithLifecycle()
    val cores by produceState(emptyMap<String, String>()) { value = repositorio.coresDasLinhasCm() }

    var comLocalizacao by remember { mutableStateOf(temLocalizacao(contexto)) }
    var explicar by remember { mutableStateOf(!comLocalizacao && !localizacaoJaPerguntada(contexto)) }
    var posicao by remember { mutableStateOf(ultimaPosicao(contexto)) }
    var centrarAgora by remember { mutableIntStateOf(0) }
    // A hora da posição pedida: o mapa só centra nela se for mais nova que a do ponto azul (0 = a última conhecida, sem hora).
    var horaPosicao by remember { mutableLongStateOf(0L) }
    val receberPosicao = { l: android.location.Location ->
        posicao = l.latitude to l.longitude
        horaPosicao = l.time
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        comLocalizacao = r.values.any { it }
    }
    // A permissão pode ter sido dada nas definições do sistema: volta a ver sempre que se regressa à app.
    LifecycleResumeEffect(Unit) {
        comLocalizacao = temLocalizacao(contexto)
        onPauseOrDispose { }
    }
    LaunchedEffect(comLocalizacao) {
        if (comLocalizacao) pedirLocalizacaoAtual(contexto, receberPosicao)
    }

    // O Android pode ter fechado o serviço da viagem enquanto a app esteve fora: vê-se sempre que se regressa.
    // Com a viagem a correr, o aviso leva ao ecrã dela (a notificação também).
    var interrompida by remember { mutableStateOf(false) }
    var emCurso by remember { mutableStateOf(false) }
    val retomar = rememberRetomarViagem(aoRetomou = aoAbrirViagem)
    LifecycleResumeEffect(Unit) {
        val leitura = escopo.launch {
            withContext(Dispatchers.IO) { viagemInterrompida(contexto) to viagemEmCurso(contexto) }.let { (parada, ativa) ->
                interrompida = parada
                emCurso = ativa
            }
        }
        onPauseOrDispose { leitura.cancel() }
    }

    LaunchedEffect(Unit) {
        // Primeira utilização: as paragens da Carris Metropolitana ainda não estão na base local.
        if (repositorio.redeEmFalta()) repositorio.atualizarRede()
    }

    // As paragens da zona, como a comunidade as corrigiu e com o triângulo nas de relato confirmado.
    val relatos = rememberRelatosDoMapa(paragens, comunidade, configuracoes, preferencias)
    val desenhadas = remember(paragens, relatos) {
        val pontos = paragensDoMapa(paragens, relatos)
        pontos.map { it.first } to pontos.filter { it.second }.map { it.first.ref.chave }.toSet()
    }

    val folha = rememberBottomSheetScaffoldState()
    // "Favoritos" como ecrã inicial: o painel abre uma só vez (rememberSaveable: voltar de outro ecrã não o reabre por cima do que a pessoa fez).
    var favoritosAbertos by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(abrirFavoritos) {
        if (abrirFavoritos && !favoritosAbertos) {
            favoritosAbertos = true
            folha.bottomSheetState.expand()
        }
    }
    val tipo = definir
    if (tipo != null) {
        DefinirLocalEcra(repositorio, tipo, guardarLocal, aoFechar = { definir = null })
        return
    }
    Box(Modifier.fillMaxSize()) {
    BottomSheetScaffold(
        scaffoldState = folha,
        sheetPeekHeight = 260.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = { painel(posicao) { definir = it } },
    ) { _ ->
    Box(Modifier.fillMaxSize()) {
        var alturaFaixa by remember { mutableIntStateOf(0) }
        val densidade = LocalDensity.current
        // A atribuição e o logótipo do mapa ficam logo abaixo da faixa.
        val ligacao = LigacaoMapaInicio(
            bussola = false, margemTopo = with(densidade) { alturaFaixa.toDp() } + 8.dp,
            aoDeixarDeSeguir = {}, aoArrastar = {}, aoRodar = {},
        )
        (mapa ?: { l ->
            Mapa(
                paragens = desenhadas.first,
                comRelato = desenhadas.second,
                enquadrar = emptyList(),
                veiculos = veiculos,
                coresLinhas = cores,
                escuro = LocalEscuro.current,
                centroInicial = posicao?.let { LatLng(it.first, it.second) } ?: LISBOA,
                camaraInicial = vm.camara,
                centrarEm = posicao?.let { LatLng(it.first, it.second) },
                aoMoverCamara = vm::aoMoverCamara,
                aoTocarParagem = aoAbrirParagem,
                // Um cartão de cada vez: tocar num veículo fecha o da estação e vice-versa.
                aoTocarVeiculo = {
                    giraEscolhida = null
                    vm.escolherVeiculo(it)
                },
                modifier = Modifier.fillMaxSize(),
                aoToqueLongo = vm::toqueLongo,
                mostrarPosicao = comLocalizacao,
                centrarAgora = centrarAgora,
                horaCentrarEm = horaPosicao,
                gira = gira,
                aoTocarGira = {
                    vm.escolherVeiculo(null)
                    giraEscolhida = it
                },
                bussola = l.bussola,
                aoDeixarDeSeguir = l.aoDeixarDeSeguir,
                aoArrastar = l.aoArrastar,
                aoRodar = l.aoRodar,
                margemTopo = l.margemTopo,
            )
        })(ligacao)
        FaixaInicio(aoPesquisar, aoAbrirConta, Modifier.testTag("faixa").onSizeChanged { alturaFaixa = it.height })
        Column(Modifier.padding(top = with(densidade) { alturaFaixa.toDp() }).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (interrompida) {
                AvisoViagem(
                    R.string.viagem_interrompida,
                    aoAbrir = retomar,
                    aoDescartar = {
                        FicheirosViagem.apagar(contexto)
                        interrompida = false
                    },
                )
            }
            if (emCurso) AvisoViagem(R.string.viagem_em_curso, aoAbrir = aoAbrirViagem)
            escolhido?.let { CartaoVeiculo(it, aoFechar = { vm.escolherVeiculo(null) }) }
            giraEscolhida?.let { e ->
                CartaoGira(
                    e,
                    aoAbrirApp = { abrirGira(contexto.packageManager) { contexto.startActivity(it) } },
                    aoFechar = { giraEscolhida = null },
                )
            }
            BotaoCentrar(Modifier.align(Alignment.End)) {
                if (comLocalizacao) {
                    centrarAgora++
                    pedirLocalizacaoAtual(contexto, receberPosicao)
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
                // Casa e Trabalho guardam-se logo, com o nome fixo; um local pede o nome.
                aoGuardar = { t ->
                    if (t == "OUTRO") guardar = lugar else guardarLocal(lugar, t)
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

/** Menu de um ponto tocado sem largar: o nome, ir para lá, partir de lá e guardá-lo como casa, trabalho ou local ([aoGuardar] com o tipo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuPonto(lugar: Lugar, aoIrPara: () -> Unit, aoPartirDaqui: () -> Unit, aoGuardar: (tipo: String) -> Unit, aoFechar: () -> Unit) {
    // Sempre aberto por inteiro: a meio, num ecrã baixo, as últimas das cinco opções ficavam por baixo.
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            lugar.nome, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(
            Icones.Navegacao to R.string.menu_ir_para_aqui to aoIrPara,
            Icones.Partida to R.string.menu_partir_daqui to aoPartirDaqui,
            iconeLocal("CASA") to R.string.guardar_como_casa to { aoGuardar("CASA") },
            iconeLocal("TRABALHO") to R.string.guardar_como_trabalho to { aoGuardar("TRABALHO") },
            iconeLocal("OUTRO") to R.string.guardar_como_local to { aoGuardar("OUTRO") },
        ).forEach { (iconeTexto, acao) ->
            ItemLista(onClick = acao) {
                Icon(iconeTexto.first, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(iconeTexto.second), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
    }
}

/**
 * A morada da Casa ou do Trabalho ([tipo]), no ecrã todo como nas Rotas: um sítio guardado, um recente ou uma pesquisa. O escolhido
 * guarda-se com [guardar] (de quem fica aberto: este ecrã fecha logo) e o ecrã fecha; ← e o voltar do sistema fecham sem guardar.
 */
@Composable
internal fun DefinirLocalEcra(repositorio: Repositorio, tipo: String, guardar: (Lugar, tipo: String) -> Unit, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val locais by remember { repositorio.locais() }.collectAsStateWithLifecycle(emptyList())
    val recentes by remember { repositorio.recentes() }.collectAsStateWithLifecycle(emptyList())
    BackHandler(onBack = aoFechar)
    EscolherLugarEcra(
        titulo = stringResource(if (tipo == "CASA") R.string.definir_casa else R.string.definir_trabalho),
        comPosicao = false,
        locais = locais,
        recentes = lugaresRecentes(recentes),
        pesquisar = { t -> repositorio.pesquisarLugares(t, ultimaPosicao(contexto)) },
        aoEscolher = { l ->
            if (l != null) guardar(l, tipo)
            aoFechar()
        },
        aoVoltar = aoFechar,
        // Guardar pelo "⋯" como o que se está a escolher é escolher; como outra coisa, a escolha continua.
        aoGuardar = { l, t ->
            guardar(l, t)
            if (t == tipo) aoFechar()
        },
    )
}

/** O que o Início dá ao mapa: o modo bússola, a margem de cima (a faixa azul) e os avisos de a pessoa o mexer. */
class LigacaoMapaInicio(
    val bussola: Boolean,
    val margemTopo: Dp,
    val aoDeixarDeSeguir: () -> Unit,
    val aoArrastar: () -> Unit,
    val aoRodar: (Double) -> Unit,
)

/** A faixa azul de azulejos por baixo da barra de estado: o campo da pesquisa e, com a conta ligada, o botão Conta. */
@Composable
private fun FaixaInicio(aoPesquisar: () -> Unit, aoAbrirConta: (() -> Unit)?, modifier: Modifier = Modifier) {
    val topo = MaterialTheme.shapes.extraLarge.let { it.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)) }
    Box(modifier.fillMaxWidth().clip(topo).background(Azulejo.Cobalto)) {
        PadraoAzulejo(Modifier.matchParentSize())
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoPesquisar),
                shape = MaterialTheme.shapes.medium, color = Color.White,
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icones.Pesquisa, contentDescription = null, tint = Azulejo.Cinza)
                    Text(stringResource(R.string.pesquisa_dica), color = Azulejo.Cinza, fontWeight = FontWeight.Bold)
                }
            }
            if (aoAbrirConta != null) {
                IconButton(
                    aoAbrirConta,
                    Modifier.size(48.dp).background(Color.White.copy(alpha = 0.12f), CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                ) { Icon(Icones.Conta, stringResource(R.string.menu_conta), tint = Color.White) }
            }
        }
    }
}

/** O aviso da viagem: toca em qualquer ponto para a abrir; "Descartar" (só na interrompida) apaga-a. */
@Composable
internal fun AvisoViagem(texto: Int, aoAbrir: () -> Unit, modifier: Modifier = Modifier, aoDescartar: (() -> Unit)? = null) {
    Surface(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(texto),
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoAbrir).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (aoDescartar == null) 16.dp else 0.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            aoDescartar?.let { TextButton(onClick = it) { Text(stringResource(R.string.viagem_descartar)) } }
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

@Composable
private fun CartaoVeiculo(v: Veiculo, aoFechar: () -> Unit) {
    val segundos = Duration.between(v.ultimoSinal, Instant.now()).seconds.coerceAtLeast(0)
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp, shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Uma rota que o catálogo não conhece dá um autocarro da Carris sem nome de linha: fica só "Carris · ao vivo".
                if (v.linhaId.isNotEmpty()) Text(stringResource(R.string.veiculo_linha, v.linhaId), style = MaterialTheme.typography.titleMedium)
                if (v.daCarris) {
                    Text(stringResource(R.string.veiculo_carris), style = if (v.linhaId.isEmpty()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                }
                v.destino?.let { Text(stringResource(R.string.veiculo_destino, it)) }
                Text(stringResource(R.string.veiculo_sinal, segundos), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.fechar)) }
        }
    }
}

/** A estação GIRA tocada no mapa: o nome, o atalho para a app GIRA (onde se vê a disponibilidade) e o crédito da EMEL. */
@Composable
fun CartaoGira(e: EstacaoGira, aoAbrirApp: () -> Unit, aoFechar: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp, shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.nome, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = aoAbrirApp) { Text(stringResource(R.string.gira_ver_disponibilidade)) }
                Text(stringResource(R.string.gira_credito), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
