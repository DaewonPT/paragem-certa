package pt.pixellusitano.paragemcerta.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.atualizar.NovaVersao
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.Ligacoes
import pt.pixellusitano.paragemcerta.ui.bussola.BussolaEcra
import pt.pixellusitano.paragemcerta.ui.eu.EuEcra
import pt.pixellusitano.paragemcerta.ui.eu.LocaisEcra
import pt.pixellusitano.paragemcerta.ui.inicio.InicioEcra
import pt.pixellusitano.paragemcerta.ui.inicio.Painel
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemEcra
import pt.pixellusitano.paragemcerta.ui.pesquisa.PesquisaEcra
import pt.pixellusitano.paragemcerta.ui.rotas.RotasEcra
import pt.pixellusitano.paragemcerta.ui.rotas.lugarDaRota
import pt.pixellusitano.paragemcerta.ui.rotas.paraRota
import pt.pixellusitano.paragemcerta.ui.sobre.SobreEcra
import pt.pixellusitano.paragemcerta.ui.viagem.ViagemEcra
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem

@Serializable data class Inicio(val linha: String? = null)
@Serializable data object Pesquisa
@Serializable data object Eu
@Serializable data object Sobre
@Serializable data object Locais
@Serializable data object Viagem
@Serializable data class Paragem(val chave: String)

/** "Encontrar a paragem": o nome e a posição do sítio para onde a seta aponta. */
@Serializable data class Bussola(val nome: String, val lat: Double, val lon: Double)

/** origem e destino codificados como "nome|lat|lon" (ver paraRota); origem null = "A minha posição". */
@Serializable data class Rotas(val origem: String? = null, val destino: String? = null)

/** Troca de separador na barra de baixo: guarda o que estava aberto no separador que se deixa e repõe o do separador escolhido. */
fun NavController.irParaSeparador(rota: Any) = navigate(rota) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    // O Início é o destino de partida: o Android guarda debaixo dele o que estava por cima (as rotas abertas pela
    // pesquisa) e repô-lo trazia as rotas de volta a cada toque. O Início abre-se sempre limpo.
    restoreState = rota !is Inicio
}

/**
 * Ecrãs com a barra de baixo. "Linhas" (fase 4) entra quando existir. abrirViagem: a notificação da viagem foi tocada, por isso
 * abre-se o ecrã da viagem e avisa-se (aoAbrirViagem) para o pedido não se repetir.
 */
@Composable
fun Navegacao(ligacoes: Ligacoes, abrirViagem: Boolean = false, aoAbrirViagem: () -> Unit = {}) {
    val nav = rememberNavController()
    val contexto = LocalContext.current
    LaunchedEffect(abrirViagem) {
        if (abrirViagem) {
            nav.navigate(Viagem) { launchSingleTop = true }
            aoAbrirViagem()
        }
    }
    val atual by nav.currentBackStackEntryAsState()
    val destino = atual?.destination
    val naInicio = destino?.hasRoute(Inicio::class) == true
    val noEu = destino?.hasRoute(Eu::class) == true
    val nasRotas = destino?.hasRoute(Rotas::class) == true
    val repositorio = ligacoes.repositorio

    fun separador(rota: Any) = nav.irParaSeparador(rota)

    // Uma atualização descarregada que o sistema ainda quer confirmar mostra-se sempre, tenha ou não "Atualizar sozinha" ligado.
    // Sem ela: com "Atualizar sozinha" ligado a app trata disto em segundo plano; só quem o desligou recebe o aviso para confirmar.
    val pendente = remember { Atualizacoes.pendente(contexto) }
    // Instalar mata o processo :trip e com ele o aviso da paragem: com uma viagem por acabar o aviso espera pela abertura seguinte
    // (e a verificação diária não se gasta). Começa escondido até se ler os ficheiros da viagem.
    val semViagem by produceState(false) { value = withContext(Dispatchers.IO) { !FicheirosViagem.porAcabar(contexto) } }
    if (semViagem) {
        if (pendente != null) AvisoAtualizacao(pendente)
        else if (!remember { Atualizacoes.atualizarSozinha(contexto) }) AvisoAtualizacao(null)
    }
    AvisoNovidades()
    // Não empilha a pergunta das notificações sobre outro aviso: fica para a abertura seguinte.
    if (pendente == null && remember { Atualizacoes.novidadesPorMostrar(contexto) } == null) AvisoNotificacoes()

    Scaffold(
        bottomBar = {
            if (naInicio || nasRotas || noEu) {
                NavigationBar {
                    NavigationBarItem(selected = naInicio, onClick = { separador(Inicio()) },
                        icon = { Text("🗺") }, label = { Text(stringResource(R.string.nav_inicio)) })
                    NavigationBarItem(selected = nasRotas, onClick = { separador(Rotas()) },
                        icon = { Text("🧭") }, label = { Text(stringResource(R.string.nav_rotas)) })
                    NavigationBarItem(selected = noEu, onClick = { separador(Eu) },
                        icon = { Text("★") }, label = { Text(stringResource(R.string.nav_eu)) })
                }
            }
        },
    ) { margens ->
        NavHost(nav, startDestination = Inicio(), modifier = Modifier.fillMaxSize()) {
            composable<Inicio> { entrada ->
                val linha = entrada.toRoute<Inicio>().linha
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    InicioEcra(
                        repositorio = repositorio,
                        aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                        aoPesquisar = { nav.navigate(Pesquisa) },
                        linhaId = linha,
                        aoLimparLinha = { nav.popBackStack() },
                        aoIrPara = { nav.navigate(it) },
                        aoAbrirViagem = { nav.navigate(Viagem) },
                        painel = { posicao ->
                            Painel(
                                repositorio, posicao,
                                aoAbrirParagem = { nav.navigate(Paragem(it.chave)) },
                                aoPesquisar = { nav.navigate(Pesquisa) },
                                aoAbrirRotas = { nav.navigate(it) },
                            )
                        },
                    )
                }
            }
            composable<Pesquisa> {
                PesquisaEcra(
                    repositorio = repositorio,
                    aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                    // Fica [Início, Início(linha)]: "voltar" regressa ao mapa normal em vez de fechar a app.
                    aoAbrirLinha = { nav.navigate(Inicio(linha = it.id)) { popUpTo<Inicio>() } },
                    // Também sai da pesquisa: "voltar" das rotas regressa ao mapa.
                    aoIrPara = { nav.navigate(Rotas(destino = it.paraRota())) { popUpTo<Inicio>() } },
                    aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Paragem> { entrada ->
                val ref = RefParagem.daChave(entrada.toRoute<Paragem>().chave)
                if (ref == null) nav.popBackStack() else ParagemEcra(
                    ref, repositorio, aoEncontrar = { nav.navigate(Bussola(it.nome, it.lat, it.lon)) }, aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Rotas> { entrada ->
                val r = entrada.toRoute<Rotas>()
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    RotasEcra(
                        repositorio = repositorio,
                        preferencias = ligacoes.preferencias,
                        origem = r.origem?.let(::lugarDaRota),
                        destino = r.destino?.let(::lugarDaRota),
                        aoAbrirViagem = { nav.navigate(Viagem) },
                    )
                }
            }
            composable<Eu> {
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    EuEcra(
                        repositorio = repositorio,
                        preferencias = ligacoes.preferencias,
                        aoAbrirFavorita = { nav.navigate(Paragem(it.ref.chave)) },
                        aoAbrirSobre = { nav.navigate(Sobre) },
                        aoAbrirLocais = { nav.navigate(Locais) },
                    )
                }
            }
            composable<Locais> { LocaisEcra(repositorio, aoVoltar = { nav.popBackStack() }) }
            composable<Viagem> {
                ViagemEcra(aoVoltar = { nav.popBackStack() }, aoEncontrar = { nav.navigate(Bussola(it.nome, it.lat, it.lon)) })
            }
            composable<Bussola> { entrada ->
                val b = entrada.toRoute<Bussola>()
                BussolaEcra(b.nome, b.lat, b.lon, aoVoltar = { nav.popBackStack() })
            }
            composable<Sobre> {
                SobreEcra(versao = BuildConfig.VERSION_NAME, aoAbrir = { url ->
                    runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                })
            }
        }
    }
}

/** Depois de uma atualização, mostra uma só vez o que mudou. */
@Composable
private fun AvisoNovidades() {
    val contexto = LocalContext.current
    var novidades by remember { mutableStateOf(Atualizacoes.novidadesPorMostrar(contexto)) }
    val n = novidades ?: return
    val fechar = {
        Atualizacoes.marcarNovidadesVistas(contexto)
        novidades = null
    }
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(stringResource(R.string.atualizacao_novidades_titulo, n.tag.removePrefix("v"))) },
        text = { Text(n.notas.trim(), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = fechar) { Text(stringResource(R.string.ok)) } },
    )
}

/** Pergunta, uma só vez, se a app pode mostrar notificações (Android 13+), para avisar quando uma atualização precisa de um toque. */
@Composable
private fun AvisoNotificacoes() {
    val contexto = LocalContext.current
    var aberto by remember { mutableStateOf(Atualizacoes.pedirNotificacoes(contexto)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (!aberto) return
    val fechar = {
        Atualizacoes.marcarNotificacoesPedidas(contexto)
        aberto = false
    }
    AlertDialog(
        onDismissRequest = fechar,
        text = { Text(stringResource(R.string.atualizacao_notificacoes)) },
        confirmButton = {
            TextButton(onClick = {
                fechar()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.atualizacao_continuar)) }
        },
        dismissButton = { TextButton(onClick = fechar) { Text(stringResource(R.string.atualizacao_agora_nao)) } },
    )
}

/**
 * Aviso manual de versão nova. [pendente] é uma versão já descarregada que o sistema ainda quer confirmar: mostra-se logo,
 * sem verificar nada. Sem ela, para quem desligou "Atualizar sozinha", verifica no máximo uma vez por dia no GitHub.
 */
@Composable
private fun AvisoAtualizacao(pendente: NovaVersao?) {
    val contexto = LocalContext.current
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    var nova by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(pendente) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (pendente == null) nova = runCatching { Atualizacoes.verificar(contexto) }.getOrNull()
    }
    val n = nova ?: return
    val falhou = stringResource(R.string.atualizacao_falhou)
    AlertDialog(
        // "Agora não": a pendente fica guardada e volta a aparecer na abertura seguinte.
        onDismissRequest = { nova = null },
        title = { Text(stringResource(R.string.atualizacao_titulo, n.tag.removePrefix("v"))) },
        // Notas longas rolam em vez de ficarem cortadas (o aviso não tem altura para tudo).
        text = { Text(n.notas.trim(), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = {
                nova = null
                escopo.launch {
                    runCatching { Atualizacoes.instalar(contexto, n) }
                        .onSuccess { Atualizacoes.limparPendente(contexto) }
                        .onFailure { Toast.makeText(contexto, falhou, Toast.LENGTH_LONG).show() }
                }
            }) { Text(stringResource(R.string.atualizacao_atualizar)) }
        },
        dismissButton = { TextButton(onClick = { nova = null }) { Text(stringResource(R.string.atualizacao_agora_nao)) } },
    )
}
