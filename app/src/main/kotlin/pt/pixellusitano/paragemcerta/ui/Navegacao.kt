package pt.pixellusitano.paragemcerta.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.atualizar.NovaVersao
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import pt.pixellusitano.paragemcerta.ui.eu.EuEcra
import pt.pixellusitano.paragemcerta.ui.inicio.InicioEcra
import pt.pixellusitano.paragemcerta.ui.inicio.Painel
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemEcra
import pt.pixellusitano.paragemcerta.ui.pesquisa.PesquisaEcra
import pt.pixellusitano.paragemcerta.ui.sobre.SobreEcra

@Serializable data class Inicio(val linha: String? = null)
@Serializable data object Pesquisa
@Serializable data object Eu
@Serializable data object Sobre
@Serializable data class Paragem(val chave: String)

/** Ecrãs com a barra de baixo. "Rotas" (fase 2) e "Linhas" (fase 4) entram quando existirem. */
@Composable
fun Navegacao(ligacoes: Ligacoes) {
    val nav = rememberNavController()
    val contexto = LocalContext.current
    val atual by nav.currentBackStackEntryAsState()
    val destino = atual?.destination
    val naInicio = destino?.hasRoute(Inicio::class) == true
    val noEu = destino?.hasRoute(Eu::class) == true
    val repositorio = ligacoes.repositorio

    fun separador(rota: Any) = nav.navigate(rota) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    AvisoAtualizacao()

    Scaffold(
        bottomBar = {
            if (naInicio || noEu) {
                NavigationBar {
                    NavigationBarItem(selected = naInicio, onClick = { separador(Inicio()) },
                        icon = { Text("🗺") }, label = { Text(stringResource(R.string.nav_inicio)) })
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
                        aoLimparLinha = { nav.navigate(Inicio()) { popUpTo<Inicio> { inclusive = true } } },
                        painel = { posicao ->
                            Painel(repositorio, posicao, aoAbrirParagem = { nav.navigate(Paragem(it.chave)) }, aoPesquisar = { nav.navigate(Pesquisa) })
                        },
                    )
                }
            }
            composable<Pesquisa> {
                PesquisaEcra(
                    repositorio = repositorio,
                    aoAbrirParagem = { nav.navigate(Paragem(it.ref.chave)) },
                    aoAbrirLinha = { nav.navigate(Inicio(linha = it.id)) { popUpTo<Inicio> { inclusive = true } } },
                    aoVoltar = { nav.popBackStack() },
                )
            }
            composable<Paragem> { entrada ->
                val ref = RefParagem.daChave(entrada.toRoute<Paragem>().chave)
                if (ref == null) nav.popBackStack() else ParagemEcra(ref, repositorio, aoVoltar = { nav.popBackStack() })
            }
            composable<Eu> {
                Box(Modifier.padding(bottom = margens.calculateBottomPadding())) {
                    EuEcra(
                        repositorio = repositorio,
                        preferencias = ligacoes.preferencias,
                        aoAbrirFavorita = { nav.navigate(Paragem(it.ref.chave)) },
                        aoAbrirSobre = { nav.navigate(Sobre) },
                    )
                }
            }
            composable<Sobre> {
                SobreEcra(versao = BuildConfig.VERSION_NAME, aoAbrir = { url ->
                    runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                })
            }
        }
    }
}

/** Na variante pessoal mostra, no máximo uma vez por dia, que há uma versão nova no GitHub. */
@Composable
private fun AvisoAtualizacao() {
    val contexto = LocalContext.current
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    var nova by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<NovaVersao?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { nova = runCatching { Atualizacoes.verificar(contexto) }.getOrNull() }
    val n = nova ?: return
    val falhou = stringResource(R.string.atualizacao_falhou)
    AlertDialog(
        onDismissRequest = { nova = null },
        title = { Text(stringResource(R.string.atualizacao_titulo, n.tag.removePrefix("v"))) },
        text = { Text(n.notas) },
        confirmButton = {
            TextButton(onClick = {
                nova = null
                escopo.launch {
                    runCatching { Atualizacoes.instalar(contexto, n) }
                        .onFailure { Toast.makeText(contexto, falhou, Toast.LENGTH_LONG).show() }
                }
            }) { Text(stringResource(R.string.atualizacao_atualizar)) }
        },
        dismissButton = { TextButton(onClick = { nova = null }) { Text(stringResource(R.string.atualizacao_agora_nao)) } },
    )
}
