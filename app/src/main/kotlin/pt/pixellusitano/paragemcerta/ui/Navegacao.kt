package pt.pixellusitano.paragemcerta.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.atualizar.NovaVersao
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.EcraInicial
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.Ligacoes
import pt.pixellusitano.paragemcerta.ui.bussola.BussolaEcra
import pt.pixellusitano.paragemcerta.ui.comunidade.FotoNaBussola
import pt.pixellusitano.paragemcerta.ui.comunidade.fotoDa
import pt.pixellusitano.paragemcerta.ui.inicio.InicioEcra
import pt.pixellusitano.paragemcerta.ui.inicio.Painel
import pt.pixellusitano.paragemcerta.ui.linhas.FaixaAoAbrir
import pt.pixellusitano.paragemcerta.ui.linhas.LinhaEcra
import pt.pixellusitano.paragemcerta.ui.linhas.LinhaPerturbacoes
import pt.pixellusitano.paragemcerta.ui.linhas.LinhasEcra
import pt.pixellusitano.paragemcerta.ui.linhas.PerturbacoesEcra
import pt.pixellusitano.paragemcerta.ui.linhas.minhasLinhas
import pt.pixellusitano.paragemcerta.ui.menu.AjudaEcra
import pt.pixellusitano.paragemcerta.ui.menu.ContaEcra
import pt.pixellusitano.paragemcerta.ui.menu.DefinicoesEcra
import pt.pixellusitano.paragemcerta.ui.menu.FavoritosEcra
import pt.pixellusitano.paragemcerta.ui.menu.MenuEcra
import pt.pixellusitano.paragemcerta.ui.menu.ReclamarEcra
import pt.pixellusitano.paragemcerta.ui.menu.intentOpiniao
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemEcra
import pt.pixellusitano.paragemcerta.ui.pesquisa.PesquisaEcra
import pt.pixellusitano.paragemcerta.ui.rotas.RotasEcra
import pt.pixellusitano.paragemcerta.ui.rotas.lugarDaRota
import pt.pixellusitano.paragemcerta.ui.rotas.paraRota
import pt.pixellusitano.paragemcerta.ui.sobre.SobreEcra
import pt.pixellusitano.paragemcerta.ui.viagem.ViagemEcra
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem

@Serializable data object Inicio
@Serializable data object Pesquisa
@Serializable data object Menu
@Serializable data object Favoritos
@Serializable data object Definicoes
@Serializable data object Ajuda
@Serializable data object Reclamar
@Serializable data object ContaRota
@Serializable data object Sobre
@Serializable data object Viagem
@Serializable data class Paragem(val chave: String)

/** "Encontrar a paragem": o nome e a posição do sítio para onde a seta aponta; [chave] é a da paragem (`RefParagem.chave`), para a foto, quando se sabe. */
@Serializable data class Bussola(val nome: String, val lat: Double, val lon: Double, val chave: String? = null)

/** origem e destino codificados como "nome|lat|lon" (ver paraRota); origem null = "A minha posição". */
@Serializable data class Rotas(val origem: String? = null, val destino: String? = null)

/** O separador Linhas: a lista das linhas por meio e operador. */
@Serializable data object Linhas

/**
 * O ecrã de uma linha: [chave] é a `RefLinha.chave`; [sentido] e [paragem] (uma `RefParagem.chave`) abrem-no já nesse sentido
 * e nessa paragem (as linhas favoritas). Não se chama "Linha": colide com `core.modelo.Linha`.
 */
@Serializable data class LinhaRota(val chave: String, val sentido: Int? = null, val paragem: String? = null)

/** O ecrã "Perturbações": as das tuas linhas primeiro, depois todas por operador. */
@Serializable data object PerturbacoesRota

/** Uma linha favorita abre o ecrã da linha no sentido e na paragem habitual dela. */
fun LinhaFavorita.rotaDaLinha() = LinhaRota(ref.chave, sentido, paragem.chave)

/** O resultado "linha" da pesquisa abre o ecrã da linha por cima dos resultados: "voltar" regressa a eles. */
fun NavController.abrirLinhaPesquisada(l: Linha) = navigate(LinhaRota(RefLinha(l.fonte, l.id).chave))

/** Onde a app abre para cada escolha das Definições; FAVORITOS é o Início, com o painel aberto pelo InicioEcra. */
fun destinoInicial(e: EcraInicial): Any = when (e) {
    EcraInicial.ROTAS -> Rotas()
    EcraInicial.LINHAS -> Linhas
    else -> Inicio
}

/** Troca de separador na barra de baixo: guarda o que estava aberto no separador que se deixa e repõe o do separador escolhido. */
fun NavController.irParaSeparador(rota: Any) = navigate(rota) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    // O Início é o destino de partida: o Android guarda debaixo dele o que estava por cima (as rotas abertas pela
    // pesquisa) e repô-lo trazia as rotas de volta a cada toque. O Início abre-se sempre limpo.
    restoreState = rota !is Inicio
}

/** Um widget foi tocado: abre a paragem por cima do que está aberto; uma chave que não se lê não faz nada. */
fun NavController.abrirParagemDoWidget(chave: String) {
    val ref = RefParagem.daChave(chave) ?: return
    navigate(Paragem(ref.chave)) { launchSingleTop = true }
}

/** [chave] é a do widget tocado; avisa-se sempre (aoAbrir), mesmo sem abrir nada, para tocar outra vez na mesma paragem a abrir de novo. */
@Composable
internal fun AbrirParagemDoWidget(nav: NavController, chave: String?, aoAbrir: () -> Unit) {
    LaunchedEffect(chave) {
        if (chave != null) {
            nav.abrirParagemDoWidget(chave)
            aoAbrir()
        }
    }
}

/** A Casa ou o Trabalho tocados no widget: [destino] ("nome|lat|lon") abre as Rotas até lá, como o cartão do Início. */
@Composable
internal fun AbrirRotaDoWidget(nav: NavController, destino: String?, aoAbrir: () -> Unit) {
    LaunchedEffect(destino) {
        if (destino != null) {
            nav.navigate(Rotas(destino = destino))
            aoAbrir()
        }
    }
}

/** O ecrã inicial escolhido nas Definições, ou o Início quando a app abre por fora (a notificação da viagem ou um widget). */
fun ecraDeArranque(escolhido: EcraInicial, abrirViagem: Boolean, abrirParagem: String?, abrirRota: String?, definirLocal: String?): EcraInicial =
    if (abrirViagem || abrirParagem != null || abrirRota != null || definirLocal != null) EcraInicial.MAPA else escolhido

/**
 * Ecrãs com a barra de baixo (Início, Rotas, Linhas e Menu). abrirViagem: a notificação da viagem foi tocada, por isso
 * abre-se o ecrã da viagem e avisa-se (aoAbrirViagem) para o pedido não se repetir. abrirParagem: o mesmo para um widget
 * (a chave da paragem dele); abrirRota e definirLocal, para o widget "Casa e Trabalho" (o destino das Rotas, ou o tipo de morada
 * a escolher no Início).
 */
@Composable
fun Navegacao(
    ligacoes: Ligacoes, abrirViagem: Boolean = false, aoAbrirViagem: () -> Unit = {},
    abrirParagem: String? = null, aoAbrirParagem: () -> Unit = {},
    abrirRota: String? = null, aoAbrirRota: () -> Unit = {},
    definirLocal: String? = null, aoDefinirLocalAberto: () -> Unit = {},
) {
    val nav = rememberNavController()
    val contexto = LocalContext.current
    LaunchedEffect(abrirViagem) {
        if (abrirViagem) {
            nav.navigate(Viagem) { launchSingleTop = true }
            aoAbrirViagem()
        }
    }
    AbrirParagemDoWidget(nav, abrirParagem, aoAbrirParagem)
    AbrirRotaDoWidget(nav, abrirRota, aoAbrirRota)
    // A morada escolhe-se dentro do Início: vai-se para lá, e é o InicioEcra que a abre e avisa.
    LaunchedEffect(definirLocal) {
        if (definirLocal != null) nav.irParaSeparador(Inicio)
    }
    val atual by nav.currentBackStackEntryAsState()
    val destino = atual?.destination
    val naInicio = destino?.hasRoute(Inicio::class) == true
    val noMenu = destino?.hasRoute(Menu::class) == true
    val nasRotas = destino?.hasRoute(Rotas::class) == true
    val nasLinhas = destino?.hasRoute(Linhas::class) == true
    val repositorio = ligacoes.repositorio
    val config by ligacoes.configuracoes.atual.collectAsStateWithLifecycle()

    fun separador(rota: Any) = nav.irParaSeparador(rota)
    fun abrirUrl(url: String) {
        runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // O ecrã inicial lê-se uma vez por arranque (rememberSaveable: rodar o ecrã não o repete) e nunca quando a app abre pela
    // notificação da viagem nem por um widget (o separador escolhido ficava por cima da paragem). O Início é sempre o destino
    // de partida: ROTAS segue para o separador Rotas, FAVORITOS abre o painel.
    var ecraInicialAplicado by rememberSaveable { mutableStateOf(false) }
    var abrirFavoritos by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ecraInicialAplicado) return@LaunchedEffect
        val ecra = ecraDeArranque(ligacoes.preferencias.ecraInicial.first(), abrirViagem, abrirParagem, abrirRota, definirLocal)
        ecraInicialAplicado = true
        destinoInicial(ecra).let { if (it !is Inicio) separador(it) }
        abrirFavoritos = ecra == EcraInicial.FAVORITOS
    }

    // Uma atualização descarregada que o sistema ainda quer confirmar mostra-se sempre, tenha ou não "Atualizar sozinha" ligado.
    // Sem ela: com "Atualizar sozinha" ligado a app descarrega ao abrir e instala quando saíres; só quem o desligou recebe o aviso para confirmar.
    val pendente = remember { Atualizacoes.pendente(contexto) }
    // Instalar mata o processo :trip e com ele o aviso da paragem: com uma viagem por acabar o aviso espera pela abertura seguinte
    // (e a verificação diária não se gasta). Começa escondido até se ler os ficheiros da viagem.
    val semViagem by produceState(false) { value = withContext(Dispatchers.IO) { !FicheirosViagem.porAcabar(contexto) } }
    // Os diálogos de abertura à vista: a faixa das perturbações espera que fechem.
    val dialogos = remember { mutableIntStateOf(0) }
    if (semViagem) {
        if (pendente != null) AvisoAtualizacao(pendente, dialogos)
        else if (!remember { Atualizacoes.atualizarSozinha(contexto) }) AvisoAtualizacao(null, dialogos)
    }
    AvisoNovidades(dialogos)
    // Não empilha a pergunta das notificações sobre outro aviso: fica para a abertura seguinte.
    if (pendente == null && remember { Atualizacoes.novidadesPorMostrar(contexto) } == null) AvisoNotificacoes(dialogos)
    val comBarra = naInicio || nasRotas || nasLinhas || noMenu
    // Sobre as faixas azuis (o mapa do Início e os cabeçalhos azulejados) os ícones da barra de estado ficam claros.
    BarraDeEstadoDaNavegacao(
        nasRotas = nasRotas,
        comFaixa = naInicio || nasLinhas || noMenu || listOf(Pesquisa::class, Sobre::class, Favoritos::class, Definicoes::class, Ajuda::class, Reclamar::class, ContaRota::class, PerturbacoesRota::class, Paragem::class, LinhaRota::class, Bussola::class)
            .any { destino?.hasRoute(it) == true },
    )

    Scaffold(
        bottomBar = {
            // Por cima da barra, em todos os separadores (o ecrã inicial pode ser qualquer um deles).
            Column {
                FaixaAoAbrir(
                    ligacoes.perturbacoes, { minhasLinhas(repositorio) }, ligacoes.preferencias,
                    escondida = !comBarra || dialogos.intValue > 0, aoAbrir = { nav.navigate(PerturbacoesRota) },
                )
                if (comBarra) BarraDeBaixo(
                    atual = when {
                        naInicio -> Separador.INICIO
                        nasRotas -> Separador.ROTAS
                        nasLinhas -> Separador.LINHAS
                        else -> Separador.MENU
                    },
                    aoEscolher = {
                        when (it) {
                            Separador.INICIO -> separador(Inicio)
                            Separador.ROTAS -> separador(Rotas())
                            Separador.LINHAS -> separador(Linhas)
                            Separador.MENU -> separador(Menu)
                        }
                    },
                )
            }
        },
    ) { margens ->
        NavHost(nav, startDestination = Inicio, modifier = Modifier.fillMaxSize()) {
            composable<Inicio> {
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    InicioEcra(
                        repositorio = repositorio,
                        comunidade = ligacoes.comunidade, configuracoes = ligacoes.configuracoes, preferencias = ligacoes.preferencias,
                        estacoesGira = ligacoes.estacoesGira,
                        aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                        aoPesquisar = { nav.navigate(Pesquisa) },
                        aoIrPara = { nav.navigate(it) },
                        aoAbrirViagem = { nav.navigate(Viagem) },
                        abrirFavoritos = abrirFavoritos,
                        definirLocal = definirLocal,
                        aoDefinirLocalAberto = aoDefinirLocalAberto,
                        aoAbrirConta = if (config.interruptores.conta) ({ nav.navigate(ContaRota) }) else null,
                        painel = { posicao, aoDefinirLocal ->
                            Painel(
                                repositorio, posicao,
                                aoAbrirParagem = { nav.navigate(Paragem(it.chave)) },
                                aoPesquisar = { nav.navigate(Pesquisa) },
                                catalogo = ligacoes.catalogoLinhas,
                                aoAbrirRotas = { nav.navigate(it) },
                                aoAbrirLinha = { nav.navigate(it.rotaDaLinha()) },
                                aoDefinirLocal = aoDefinirLocal,
                                aoSugerir = ligacoes.casaTrabalho::aoSugerir,
                            )
                        },
                    )
                }
            }
            composable<Pesquisa> {
                PesquisaEcra(
                    repositorio = repositorio,
                    aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                    aoAbrirLinha = nav::abrirLinhaPesquisada,
                    // Também sai da pesquisa: "voltar" das rotas regressa ao mapa.
                    aoIrPara = { nav.navigate(Rotas(destino = it.paraRota())) { popUpTo<Inicio>() } },
                    aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Paragem> { entrada ->
                val ref = RefParagem.daChave(entrada.toRoute<Paragem>().chave)
                if (ref == null) nav.popBackStack() else ParagemEcra(
                    ref, repositorio, ligacoes.comunidade, ligacoes.configuracoes, ligacoes.preferencias,
                    carregarFoto = { ligacoes.http.obterBytes(it) },
                    aoEncontrar = { nav.navigate(Bussola(it.nome, it.lat, it.lon, ref.chave)) },
                    aoAbrirConta = { nav.navigate(ContaRota) },
                    aoVoltar = { nav.popBackStack() },
                    fotoDeRua = ligacoes.fotosDeRua.fotoDa(ref.chave),
                    aoAbrirPagina = ::abrirUrl,
                )
            }
            composable<Rotas> { entrada ->
                val r = entrada.toRoute<Rotas>()
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    RotasEcra(
                        repositorio = repositorio,
                        preferencias = ligacoes.preferencias,
                        comunidade = ligacoes.comunidade,
                        origem = r.origem?.let(::lugarDaRota),
                        destino = r.destino?.let(::lugarDaRota),
                        aoAbrirViagem = { nav.navigate(Viagem) },
                        comunidadeLigada = config.interruptores.comunidade,
                    )
                }
            }
            composable<Linhas> {
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    LinhasEcra(
                        repositorio, ligacoes.catalogoLinhas, aoAbrirLinha = { nav.navigate(LinhaRota(it.chave)) },
                        perturbacoes = { LinhaPerturbacoes(ligacoes.perturbacoes, aoAbrir = { nav.navigate(PerturbacoesRota) }) },
                    )
                }
            }
            composable<PerturbacoesRota> {
                PerturbacoesEcra(ligacoes.perturbacoes, { minhasLinhas(repositorio) }, aoVoltar = { nav.popBackStack() })
            }
            composable<LinhaRota> { entrada ->
                val r = entrada.toRoute<LinhaRota>()
                val ref = RefLinha.daChave(r.chave)
                if (ref == null) nav.popBackStack() else LinhaEcra(
                    ref, r.sentido, r.paragem, ligacoes.detalheDasLinhas, repositorio, ligacoes.comunidade, ligacoes.configuracoes,
                    ligacoes.preferencias,
                    aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                    aoAbrirUrl = ::abrirUrl,
                    aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Menu> {
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    MenuEcra(
                        aoAbrirFavoritos = { nav.navigate(Favoritos) },
                        aoAbrirDefinicoes = { nav.navigate(Definicoes) },
                        aoAbrirAjuda = { nav.navigate(Ajuda) },
                        aoAbrirReclamar = { nav.navigate(Reclamar) },
                        aoEnviarOpiniao = { runCatching { contexto.startActivity(intentOpiniao(BuildConfig.VERSION_NAME)) } },
                        aoAbrirSobre = { nav.navigate(Sobre) },
                        modifier = Modifier.fillMaxSize(),
                        aoAbrirConta = if (config.interruptores.conta) ({ nav.navigate(ContaRota) }) else null,
                    )
                }
            }
            composable<Favoritos> {
                FavoritosEcra(
                    repositorio, ligacoes.catalogoLinhas,
                    aoAbrirFavorita = { nav.navigate(Paragem(it.ref.chave)) },
                    aoAbrirLinha = { nav.navigate(it.rotaDaLinha()) },
                    aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Definicoes> {
                DefinicoesEcra(ligacoes.preferencias, ligacoes.conta, aoVoltar = { nav.popBackStack() }, comunidadeLigada = config.interruptores.comunidade)
            }
            composable<Ajuda> { AjudaEcra(aoVoltar = { nav.popBackStack() }, modifier = Modifier.fillMaxSize()) }
            composable<Reclamar> {
                ReclamarEcra(aoAbrir = ::abrirUrl, aoVoltar = { nav.popBackStack() }, modifier = Modifier.fillMaxSize(), canais = config.canaisReclamacao)
            }
            composable<ContaRota> {
                ContaEcra(
                    ligacoes.conta, repositorio, BuildConfig.GOOGLE_WEB_CLIENT_ID,
                    aoAbrir = ::abrirUrl, aoVoltar = { nav.popBackStack() }, modifier = Modifier.fillMaxSize(),
                )
            }
            composable<Viagem> {
                ViagemEcra(
                    aoVoltar = { nav.popBackStack() }, aoEncontrar = { lugar, chave -> nav.navigate(Bussola(lugar.nome, lugar.lat, lugar.lon, chave)) },
                    comunidade = ligacoes.comunidade, comunidadeLigada = config.interruptores.comunidade,
                    aoAbrirConta = { nav.navigate(ContaRota) },
                )
            }
            composable<Bussola> { entrada ->
                val b = entrada.toRoute<Bussola>()
                val rua = ligacoes.fotosDeRua.fotoDa(b.chave)
                BussolaEcra(
                    b.nome, b.lat, b.lon, aoVoltar = { nav.popBackStack() },
                    foto = { FotoNaBussola(rua, ligacoes.preferencias, carregar = { ligacoes.http.obterBytes(it) }, aoAbrirPagina = ::abrirUrl) },
                )
            }
            composable<Sobre> {
                SobreEcra(
                    versao = BuildConfig.VERSION_NAME, aoAbrir = ::abrirUrl, aoVoltar = { nav.popBackStack() }, carrisAoVivo = config.interruptores.carrisAoVivo,
                    urlDoacoes = BuildConfig.URL_DOACOES.ifBlank { null },
                )
            }
        }
    }
}

/** Enquanto um diálogo de abertura está à vista, conta-o em [abertos]. */
@Composable
private fun ContarAberto(abertos: MutableIntState) {
    DisposableEffect(Unit) {
        abertos.intValue++
        onDispose { abertos.intValue-- }
    }
}

/** Depois de uma atualização, mostra uma só vez o que mudou. */
@Composable
private fun AvisoNovidades(abertos: MutableIntState) {
    val contexto = LocalContext.current
    var novidades by remember { mutableStateOf(Atualizacoes.novidadesPorMostrar(contexto)) }
    val n = novidades ?: return
    ContarAberto(abertos)
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
private fun AvisoNotificacoes(abertos: MutableIntState) {
    val contexto = LocalContext.current
    var aberto by remember { mutableStateOf(Atualizacoes.pedirNotificacoes(contexto)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (!aberto) return
    ContarAberto(abertos)
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
private fun AvisoAtualizacao(pendente: NovaVersao?, abertos: MutableIntState) {
    val contexto = LocalContext.current
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    var nova by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(pendente) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (pendente == null) nova = runCatching { Atualizacoes.verificar(contexto) }.getOrNull()
    }
    val n = nova ?: return
    ContarAberto(abertos)
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
