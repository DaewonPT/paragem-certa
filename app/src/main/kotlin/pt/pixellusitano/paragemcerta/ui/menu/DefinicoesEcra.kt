package pt.pixellusitano.paragemcerta.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.dados.EcraInicial
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

@Composable
fun DefinicoesEcra(repositorio: Repositorio, preferencias: Preferencias, aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val modo by preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
    val limites by preferencias.limites.collectAsStateWithLifecycle(initialValue = Limites())
    val somAviso by preferencias.somAviso.collectAsStateWithLifecycle(initialValue = true)
    val vibracaoAviso by preferencias.vibracaoAviso.collectAsStateWithLifecycle(initialValue = true)
    val filtros by preferencias.filtros.collectAsStateWithLifecycle(initialValue = Filtros())
    val ecraInicial by preferencias.ecraInicial.collectAsStateWithLifecycle(initialValue = EcraInicial.MAPA)
    val apagados = stringResource(R.string.eu_dados_apagados)
    DefinicoesConteudo(
        modo = modo,
        limites = limites,
        somAviso = somAviso,
        vibracaoAviso = vibracaoAviso,
        filtros = filtros,
        ecraInicial = ecraInicial,
        aoMudarModo = { escopo.launch { preferencias.definirModo(it) } },
        aoMudarLimites = { l -> escopo.launch { runCatching { preferencias.definirLimites(l) } } },
        aoMudarSom = { escopo.launch { preferencias.definirSomAviso(it) } },
        aoMudarVibracao = { escopo.launch { preferencias.definirVibracaoAviso(it) } },
        aoMudarFiltros = { escopo.launch { preferencias.definirFiltros(it) } },
        aoMudarEcraInicial = { escopo.launch { preferencias.definirEcraInicial(it) } },
        aoVoltar = aoVoltar,
        modifier = Modifier.fillMaxSize(),
        aoApagarDados = {
            escopo.launch { repositorio.apagarDadosDoTelemovel() }
            Toast.makeText(contexto, apagados, Toast.LENGTH_SHORT).show()
        },
        secaoAtualizacoes = { modificador ->
            if (Atualizacoes.disponivel) {
                var sozinha by remember { mutableStateOf(Atualizacoes.atualizarSozinha(contexto)) }
                var dadosMoveis by remember { mutableStateOf(Atualizacoes.usarDadosMoveis(contexto)) }
                SecaoAtualizacoes(
                    sozinha = sozinha,
                    dadosMoveis = dadosMoveis,
                    aoMudarSozinha = {
                        sozinha = it
                        Atualizacoes.definirAtualizarSozinha(contexto, it)
                    },
                    aoMudarDadosMoveis = {
                        dadosMoveis = it
                        Atualizacoes.definirUsarDadosMoveis(contexto, it)
                    },
                    modifier = modificador,
                )
            }
        },
    )
}

@Composable
fun DefinicoesConteudo(
    modo: ModoTema,
    limites: Limites,
    somAviso: Boolean,
    vibracaoAviso: Boolean,
    filtros: Filtros,
    ecraInicial: EcraInicial,
    aoMudarModo: (ModoTema) -> Unit,
    aoMudarLimites: (Limites) -> Unit,
    aoMudarSom: (Boolean) -> Unit,
    aoMudarVibracao: (Boolean) -> Unit,
    aoMudarFiltros: (Filtros) -> Unit,
    aoMudarEcraInicial: (EcraInicial) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    aoApagarDados: () -> Unit = {},
    /** Só a variante pessoal tem esta secção (as atualizações da Play Store vêm pela loja). */
    secaoAtualizacoes: @Composable (Modifier) -> Unit = {},
) {
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        CabecalhoEcra(stringResource(R.string.eu_definicoes), aoVoltar)
        Definicoes(modo, limites, somAviso, vibracaoAviso, aoMudarModo, aoMudarLimites, aoMudarSom, aoMudarVibracao)
        PreferenciasRotas(filtros, aoMudarFiltros, Modifier.padding(top = 24.dp))
        EcraInicialOpcoes(ecraInicial, aoMudarEcraInicial, Modifier.padding(top = 24.dp))
        secaoAtualizacoes(Modifier.padding(top = 24.dp))
        Privacidade(aoApagarDados, Modifier.padding(top = 24.dp))
    }
}

/** O ecrã em que a app abre; "Favoritos" é o Início com o painel dos favoritos aberto. */
@Composable
private fun EcraInicialOpcoes(escolhido: EcraInicial, aoEscolher: (EcraInicial) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.pref_ecra_inicial))
        Escolha(
            listOf(
                EcraInicial.MAPA to stringResource(R.string.pref_ecra_mapa),
                EcraInicial.ROTAS to stringResource(R.string.pref_ecra_rotas),
                EcraInicial.FAVORITOS to stringResource(R.string.pref_ecra_favoritos),
            ),
            escolhido, aoEscolher,
        )
    }
}

/** Atualizar sozinha (procura ao abrir a app e instala quando saíres) e se pode gastar dados móveis; sem o primeiro, o segundo não conta. */
@Composable
fun SecaoAtualizacoes(
    sozinha: Boolean,
    dadosMoveis: Boolean,
    aoMudarSozinha: (Boolean) -> Unit,
    aoMudarDadosMoveis: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.eu_atualizacoes))
        Text(stringResource(R.string.eu_atualizacoes_quando), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Interruptor(stringResource(R.string.eu_atualizar_sozinha), sozinha, aoMudarSozinha)
        Interruptor(stringResource(R.string.eu_atualizar_dados_moveis), dadosMoveis, aoMudarDadosMoveis, ativo = sozinha)
    }
}

/** Apagar dados deste telemóvel, depois de confirmar. */
@Composable
fun Privacidade(aoApagarDados: () -> Unit, modifier: Modifier = Modifier) {
    var confirmar by remember { mutableStateOf(false) }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.eu_privacidade))
        Ligacao(stringResource(R.string.eu_apagar_dados), { confirmar = true }, MaterialTheme.colorScheme.error)
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            text = { Text(stringResource(R.string.eu_apagar_confirmar)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    aoApagarDados()
                }) { Text(stringResource(R.string.locais_apagar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
internal fun Ligacao(texto: String, aoTocar: () -> Unit, cor: Color = MaterialTheme.colorScheme.primary) {
    Text(
        texto,
        color = cor,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoTocar).padding(vertical = 12.dp),
    )
}

/** Aparência, cores do atraso e avisos da viagem; o título "Definições" é o do ecrã. */
@Composable
fun Definicoes(
    modo: ModoTema,
    limites: Limites,
    somAviso: Boolean,
    vibracaoAviso: Boolean,
    aoMudarModo: (ModoTema) -> Unit,
    aoMudarLimites: (Limites) -> Unit,
    aoMudarSom: (Boolean) -> Unit,
    aoMudarVibracao: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Subtitulo(stringResource(R.string.eu_modo))
        Escolha(
            listOf(
                ModoTema.SISTEMA to stringResource(R.string.modo_sistema),
                ModoTema.CLARO to stringResource(R.string.modo_claro),
                ModoTema.ESCURO to stringResource(R.string.modo_escuro),
            ),
            modo, aoMudarModo,
        )
        Subtitulo(stringResource(R.string.eu_limites), Modifier.padding(top = 16.dp))
        Ajuste(
            stringResource(R.string.eu_limite_atrasado, limites.atrasado),
            menos = { if (limites.atrasado > 1) aoMudarLimites(limites.copy(atrasado = limites.atrasado - 1)) },
            mais = { if (limites.atrasado + 1 < limites.muitoAtrasado) aoMudarLimites(limites.copy(atrasado = limites.atrasado + 1)) },
        )
        Ajuste(
            stringResource(R.string.eu_limite_muito, limites.muitoAtrasado),
            menos = { if (limites.muitoAtrasado - 1 > limites.atrasado) aoMudarLimites(limites.copy(muitoAtrasado = limites.muitoAtrasado - 1)) },
            mais = { if (limites.muitoAtrasado < 30) aoMudarLimites(limites.copy(muitoAtrasado = limites.muitoAtrasado + 1)) },
        )
        Subtitulo(stringResource(R.string.eu_avisos), Modifier.padding(top = 16.dp))
        Interruptor(stringResource(R.string.eu_som_aviso), somAviso, aoMudarSom)
        Interruptor(stringResource(R.string.eu_vibracao_aviso), vibracaoAviso, aoMudarVibracao)
    }
}

/** Título de uma secção das definições, com o friso por baixo. */
@Composable
internal fun TituloSecao(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
internal fun Subtitulo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

/** Uma escolha entre várias, em linhas de 48 dp com o botão de opção. */
@Composable
internal fun <T> Escolha(opcoes: List<Pair<T, String>>, escolhida: T, aoEscolher: (T) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        opcoes.forEach { (valor, texto) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = escolhida == valor, role = Role.RadioButton) { aoEscolher(valor) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = escolhida == valor, onClick = null)
                Text(texto, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun Interruptor(texto: String, ligado: Boolean, aoMudar: (Boolean) -> Unit, ativo: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = ligado, enabled = ativo, role = Role.Switch, onValueChange = aoMudar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto,
            color = if (ativo) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = ligado, onCheckedChange = null, enabled = ativo)
    }
}

@Composable
private fun Ajuste(texto: String, menos: () -> Unit, mais: () -> Unit) {
    val rotuloMenos = stringResource(R.string.menos)
    val rotuloMais = stringResource(R.string.mais)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texto, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        TextButton(onClick = menos, modifier = Modifier.semantics { contentDescription = "$rotuloMenos: $texto" }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = mais, modifier = Modifier.semantics { contentDescription = "$rotuloMais: $texto" }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
