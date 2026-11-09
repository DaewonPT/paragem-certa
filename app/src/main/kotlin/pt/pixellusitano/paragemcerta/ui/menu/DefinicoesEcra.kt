package pt.pixellusitano.paragemcerta.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EcraInicial
import pt.pixellusitano.paragemcerta.dados.EstadoConta
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.TituloSecao
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** [comunidadeLigada]: o interruptor `comunidade` da config; sem ele não há a camada dos relatos. */
@Composable
fun DefinicoesEcra(preferencias: Preferencias, conta: Conta, aoVoltar: () -> Unit, comunidadeLigada: Boolean = false) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val estadoConta by conta.estado.collectAsStateWithLifecycle()
    val camadaRelatos by preferencias.camadaRelatos.collectAsStateWithLifecycle(initialValue = true)
    val camadaAoVivo by preferencias.camadaAoVivo.collectAsStateWithLifecycle(initialValue = true)
    val camadaGira by preferencias.camadaGira.collectAsStateWithLifecycle(initialValue = true)
    val avisosComunidade by preferencias.avisosComunidade.collectAsStateWithLifecycle(initialValue = true)
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
            // A Conta não tem as Preferencias: os relatos e fotos escondidos e as perturbações vistas também são dados deste telemóvel.
            // Sair ou rodar o ecrã a meio não pode deixar metade por apagar (a sincronização antes de sair pode levar 30 s).
            escopo.launch {
                withContext(NonCancellable) {
                    preferencias.esquecerOcultos()
                    preferencias.esquecerVistas()
                    conta.apagarDadosDoTelemovel()
                    Toast.makeText(contexto.applicationContext, apagados, Toast.LENGTH_SHORT).show()
                }
            }
        },
        aoApagarConta = if (estadoConta is EstadoConta.Com) ({ escopo.launch { conta.apagarEAvisar(contexto) } }) else null,
        camadaRelatos = camadaRelatos,
        aoMudarCamadaRelatos = if (comunidadeLigada) ({ escopo.launch { preferencias.definirCamadaRelatos(it) } }) else null,
        camadaAoVivo = camadaAoVivo,
        aoMudarCamadaAoVivo = { escopo.launch { preferencias.definirCamadaAoVivo(it) } },
        camadaGira = camadaGira,
        aoMudarCamadaGira = { escopo.launch { preferencias.definirCamadaGira(it) } },
        avisosComunidade = avisosComunidade,
        // Sempre: as perturbações da CM não precisam da comunidade.
        aoMudarAvisosComunidade = { escopo.launch { preferencias.definirAvisosComunidade(it) } },
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
    /** Só vem quando há conta: a Privacidade mostra "Apagar conta". */
    aoApagarConta: (() -> Unit)? = null,
    /** Se o mapa marca as paragens com relatos confirmados; [aoMudarCamadaRelatos] só vem com a comunidade ligada (sem ele não há essa camada). */
    camadaRelatos: Boolean = true,
    aoMudarCamadaRelatos: ((Boolean) -> Unit)? = null,
    /** As camadas que não dependem do servidor: os autocarros ao vivo (CM e Carris) e as estações GIRA. */
    camadaAoVivo: Boolean = true,
    aoMudarCamadaAoVivo: (Boolean) -> Unit = {},
    camadaGira: Boolean = true,
    aoMudarCamadaGira: (Boolean) -> Unit = {},
    /** Só a variante pessoal tem esta secção (as atualizações da Play Store vêm pela loja). */
    secaoAtualizacoes: @Composable (Modifier) -> Unit = {},
    /** As perturbações e os relatos na viagem e no "Avisa-me"; sem [aoMudarAvisosComunidade] o interruptor não aparece. */
    avisosComunidade: Boolean = true,
    aoMudarAvisosComunidade: ((Boolean) -> Unit)? = null,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        CabecalhoAzulejo(stringResource(R.string.eu_definicoes), aoVoltar)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Definicoes(
                modo, limites, somAviso, vibracaoAviso, aoMudarModo, aoMudarLimites, aoMudarSom, aoMudarVibracao,
                avisosComunidade = avisosComunidade, aoMudarAvisosComunidade = aoMudarAvisosComunidade,
            )
            CamadasDoMapa(
                camadaAoVivo, aoMudarCamadaAoVivo, camadaGira, aoMudarCamadaGira, camadaRelatos, aoMudarCamadaRelatos,
            )
            PreferenciasRotas(filtros, aoMudarFiltros)
            EcraInicialOpcoes(ecraInicial, aoMudarEcraInicial)
            secaoAtualizacoes(Modifier)
            Privacidade(aoApagarDados, Modifier, aoApagarConta)
        }
    }
}

/** O que o mapa desenha por cima das paragens. Desligar uma camada não pede nada nem desenha nada; a dos relatos só aparece com [aoMudarRelatos]. */
@Composable
private fun CamadasDoMapa(
    aoVivo: Boolean, aoMudarAoVivo: (Boolean) -> Unit, gira: Boolean, aoMudarGira: (Boolean) -> Unit,
    relatos: Boolean, aoMudarRelatos: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.eu_camadas_mapa))
        CartaoDeSecao {
            Interruptor(stringResource(R.string.eu_camada_ao_vivo), aoVivo, aoMudarAoVivo)
            Interruptor(stringResource(R.string.eu_camada_gira), gira, aoMudarGira)
            if (aoMudarRelatos != null) Interruptor(stringResource(R.string.eu_camada_relatos), relatos, aoMudarRelatos)
        }
    }
}

/** O ecrã em que a app abre; "Favoritos" é o Início com o painel dos favoritos aberto. */
@Composable
private fun EcraInicialOpcoes(escolhido: EcraInicial, aoEscolher: (EcraInicial) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.pref_ecra_inicial))
        CartaoDeSecao {
            Escolha(
                listOf(
                    EcraInicial.MAPA to stringResource(R.string.pref_ecra_mapa),
                    EcraInicial.ROTAS to stringResource(R.string.pref_ecra_rotas),
                    EcraInicial.LINHAS to stringResource(R.string.pref_ecra_linhas),
                    EcraInicial.FAVORITOS to stringResource(R.string.pref_ecra_favoritos),
                ),
                escolhido, aoEscolher,
            )
        }
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
        CartaoDeSecao {
            Text(stringResource(R.string.eu_atualizacoes_quando), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Interruptor(stringResource(R.string.eu_atualizar_sozinha), sozinha, aoMudarSozinha)
            Interruptor(stringResource(R.string.eu_atualizar_dados_moveis), dadosMoveis, aoMudarDadosMoveis, ativo = sozinha)
        }
    }
}

/** Apagar dados deste telemóvel e, quando há conta ([aoApagarConta] não nulo), apagar a conta; ambos depois de confirmar. */
@Composable
fun Privacidade(aoApagarDados: () -> Unit, modifier: Modifier = Modifier, aoApagarConta: (() -> Unit)? = null) {
    var confirmar by remember { mutableStateOf(false) }
    var confirmarConta by remember { mutableStateOf(false) }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.eu_privacidade))
        CartaoDeSecao {
            Ligacao(stringResource(R.string.eu_apagar_dados), { confirmar = true }, MaterialTheme.colorScheme.error)
            if (aoApagarConta != null) Ligacao(stringResource(R.string.conta_apagar), { confirmarConta = true }, MaterialTheme.colorScheme.error)
        }
    }
    if (confirmarConta && aoApagarConta != null) {
        DialogoApagarConta(
            aoConfirmar = {
                confirmarConta = false
                aoApagarConta()
            },
            aoCancelar = { confirmarConta = false },
        )
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
    /** "Perturbações e relatos das tuas linhas" (na viagem e no "Avisa-me"); sem [aoMudarAvisosComunidade] não aparece. */
    avisosComunidade: Boolean = true,
    aoMudarAvisosComunidade: ((Boolean) -> Unit)? = null,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.eu_modo))
        CartaoDeSecao {
            Escolha(
                listOf(
                    ModoTema.SISTEMA to stringResource(R.string.modo_sistema),
                    ModoTema.CLARO to stringResource(R.string.modo_claro),
                    ModoTema.ESCURO to stringResource(R.string.modo_escuro),
                ),
                modo, aoMudarModo,
            )
        }
        TituloSecao(stringResource(R.string.eu_limites))
        CartaoDeSecao {
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
        }
        TituloSecao(stringResource(R.string.eu_avisos))
        CartaoDeSecao {
            Interruptor(stringResource(R.string.eu_som_aviso), somAviso, aoMudarSom)
            Interruptor(stringResource(R.string.eu_vibracao_aviso), vibracaoAviso, aoMudarVibracao)
            if (aoMudarAvisosComunidade != null) Interruptor(stringResource(R.string.eu_avisos_comunidade), avisosComunidade, aoMudarAvisosComunidade)
        }
    }
}

/** O cartão de uma secção das definições, com a margem do conteúdo. */
@Composable
internal fun CartaoDeSecao(conteudo: @Composable ColumnScope.() -> Unit) {
    CartaoAzulejo(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp), content = conteudo) }
}

@Composable
internal fun Subtitulo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                Text(texto, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
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
            color = if (ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = ligado, onCheckedChange = null, enabled = ativo)
    }
}

@Composable
private fun Ajuste(texto: String, menos: () -> Unit, mais: () -> Unit) {
    val rotuloMenos = stringResource(R.string.menos)
    val rotuloMais = stringResource(R.string.mais)
    val primaria = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texto, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = menos, modifier = Modifier.size(48.dp).semantics { contentDescription = "$rotuloMenos: $texto" }) {
            Icon(Icones.Diminuir, null, tint = primaria)
        }
        IconButton(onClick = mais, modifier = Modifier.size(48.dp).semantics { contentDescription = "$rotuloMais: $texto" }) {
            Icon(Icones.Aumentar, null, tint = primaria)
        }
    }
}
