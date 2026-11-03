package pt.pixellusitano.paragemcerta.ui.comunidade

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes

@StringRes
private fun nomeDoTipo(t: TipoRelato) = when (t) {
    TipoRelato.ACIDENTE -> R.string.relato_acidente
    TipoRelato.DESVIO -> R.string.relato_desvio
    TipoRelato.EVENTO -> R.string.relato_evento
    TipoRelato.NAO_PASSOU -> R.string.relato_nao_passou
    TipoRelato.VAI_CHEIO -> R.string.relato_vai_cheio
    TipoRelato.ELEVADOR -> R.string.relato_elevador
    TipoRelato.PROBLEMA_PARAGEM -> R.string.relato_problema_paragem
}

private fun avisar(c: Context, @StringRes texto: Int) = Toast.makeText(c, texto, Toast.LENGTH_LONG).show()

private fun avisarRelato(c: Context, r: Resultado<*>) = avisar(c, if (r is Resultado.Falha) erroDaComunidade(r) else R.string.relatar_enviado)

/**
 * A folha "Relatar": a grelha dos 7 tipos e, nos de linha, a linha (entre [linhas]). Com [linhaFixa] (a bordo) a linha já
 * está escolhida. Os da paragem saem sempre com a linha null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaRelatar(linhas: List<String>, linhaFixa: String?, aoEscolher: (TipoRelato, String?) -> Unit, aoFechar: () -> Unit) {
    BasicAlertDialog(onDismissRequest = aoFechar) { FolhaRelatarConteudo(linhas, linhaFixa, aoEscolher, aoFechar) }
}

/** O cartão da folha, à parte da janela (as capturas de ecrã não apanham janelas); [tipoInicial] abre já na escolha da linha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolhaRelatarConteudo(
    linhas: List<String>,
    linhaFixa: String?,
    aoEscolher: (TipoRelato, String?) -> Unit,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    tipoInicial: TipoRelato? = null,
) {
    var tipo by rememberSaveable { mutableStateOf(tipoInicial) }
    Surface(modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            val escolhido = tipo
            Text(
                if (escolhido == null) stringResource(R.string.relatar) else stringResource(nomeDoTipo(escolhido)),
                style = MaterialTheme.typography.headlineSmall,
            )
            // Com letra grande os tipos rolam e o "Cancelar" fica sempre à vista.
            Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                if (escolhido == null) {
                    TipoRelato.entries.forEach { t ->
                        // Sem linhas à vista não há em que linha relatar: ficam só os da paragem.
                        val pode = !t.deLinha || linhaFixa != null || linhas.isNotEmpty()
                        OutlinedButton(
                            onClick = {
                                when {
                                    !t.deLinha -> aoEscolher(t, null)
                                    linhaFixa != null -> aoEscolher(t, linhaFixa)
                                    else -> tipo = t
                                }
                            },
                            enabled = pode,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(stringResource(nomeDoTipo(t))) }
                    }
                } else {
                    Text(stringResource(R.string.relatar_que_linha), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linhas.forEach { l -> FilterChip(selected = false, onClick = { aoEscolher(escolhido, l) }, label = { Text(l) }) }
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 16.dp)) {
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
            }
        }
    }
}

/**
 * O "Relatar" do ecrã da paragem: o botão (só com o interruptor `comunidade` ligado), a folha e o envio. Antes de enviar,
 * a pessoa tem de ir a bordo dessa linha ou estar a menos de 300 m da paragem, medido no telemóvel: só vão o tipo, a
 * paragem e a linha. Sem a permissão da localização pede-a como o resto da app (precisa e aproximada).
 */
@Composable
fun RelatarNaParagem(paragem: Paragem?, linhas: List<String>, comunidade: Comunidade, configuracoes: Configuracoes, aoAbrirConta: () -> Unit) {
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    if (!config.interruptores.comunidade) return
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val publicar = rememberPublicar(comunidade, aoAbrirConta)
    var folha by rememberSaveable { mutableStateOf(false) }
    // A escolha à espera da permissão da localização.
    var porPermitir by remember { mutableStateOf<Pair<TipoRelato, String?>?>(null) }
    var explicar by remember { mutableStateOf(false) }

    fun enviar(p: Paragem, tipo: TipoRelato, linha: String?) =
        publicar.correr({ comunidade.relatar(tipo, p.ref.chave, linha?.let(::chaveLinha)) }) { avisarRelato(contexto, it) }

    fun verificar(tipo: TipoRelato, linha: String?) {
        val p = paragem ?: return
        escopo.launch {
            if (linha != null && withContext(Dispatchers.IO) { aBordoDaLinha(contexto, linha) }) return@launch enviar(p, tipo, linha)
            when (proximidade(contexto, p)) {
                Proximidade.PERTO -> enviar(p, tipo, linha)
                Proximidade.LONGE -> avisar(contexto, R.string.relatar_longe)
                Proximidade.IMPRECISA -> avisar(contexto, R.string.relatar_imprecisa)
                Proximidade.SEM_POSICAO -> avisar(contexto, R.string.relatar_sem_posicao)
                Proximidade.SEM_PERMISSAO -> {
                    porPermitir = tipo to linha
                    explicar = true
                }
            }
        }
    }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val escolha = porPermitir
        porPermitir = null
        if (escolha != null) {
            if (r.values.any { it }) verificar(escolha.first, escolha.second) else avisar(contexto, R.string.relatar_sem_localizacao)
        }
    }

    // Precisa das coordenadas da paragem: enquanto ela não carrega, não se relata.
    OutlinedButton(onClick = { folha = true }, enabled = paragem != null, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
        Text(stringResource(R.string.relatar))
    }
    if (folha) {
        FolhaRelatar(linhas, null, aoEscolher = { t, l ->
            folha = false
            verificar(t, l)
        }, aoFechar = { folha = false })
    }
    if (explicar) {
        AlertDialog(
            onDismissRequest = {
                explicar = false
                porPermitir = null
            },
            text = { Text(stringResource(R.string.relatar_localizacao_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    explicar = false
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.loc_aceitar)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explicar = false
                    porPermitir = null
                }) { Text(stringResource(R.string.comunidade_agora_nao)) }
            },
        )
    }
}

/**
 * O "Relatar" do ecrã da viagem (a folha abre com [aberta]): a bordo, o relato fica na última paragem por onde a viagem
 * passou e na linha da etapa, sem pedir a posição. Fica sempre composto, para os diálogos da conta e dos termos não
 * desaparecerem com a folha.
 */
@Composable
fun RelatarNaViagem(comunidade: Comunidade, linha: String, aberta: Boolean, aoAbrirConta: () -> Unit, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val publicar = rememberPublicar(comunidade, aoAbrirConta)
    if (!aberta) return
    FolhaRelatar(listOf(linha), linhaFixa = linha, aoEscolher = { tipo, _ ->
        aoFechar()
        escopo.launch {
            // Lê-se agora: a viagem pode ter passado mais paragens desde que o ecrã abriu.
            val (paragem, nome) = withContext(Dispatchers.IO) { ultimaParagemDaViagem(contexto) }
                ?: return@launch avisar(contexto, R.string.relatar_sem_paragem)
            publicar.correr({ comunidade.relatar(tipo, paragem, chaveLinha(nome)) }) { avisarRelato(contexto, it) }
        }
    }, aoFechar = aoFechar)
}
