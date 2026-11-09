package pt.pixellusitano.paragemcerta.ui.comunidade

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinhaValida
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.ui.comum.BotaoSecundario
import pt.pixellusitano.paragemcerta.ui.tema.iconeRelato

@StringRes
internal fun nomeDoTipo(t: TipoRelato) = when (t) {
    TipoRelato.ACIDENTE -> R.string.relato_acidente
    TipoRelato.DESVIO -> R.string.relato_desvio
    TipoRelato.EVENTO -> R.string.relato_evento
    TipoRelato.NAO_PASSOU -> R.string.relato_nao_passou
    TipoRelato.VAI_CHEIO -> R.string.relato_vai_cheio
    TipoRelato.ELEVADOR -> R.string.relato_elevador
    TipoRelato.PROBLEMA_PARAGEM -> R.string.relato_problema_paragem
}

private fun relatosDesligados(r: Resultado<*>) = r is Resultado.Falha && r.erro == "desligado"

private fun avisarRelato(c: Context, r: Resultado<*>) = avisar(c, when {
    r !is Resultado.Falha -> R.string.relatar_enviado
    relatosDesligados(r) -> R.string.relatar_desligado
    else -> erroDaComunidade(r)
})

/** Uma linha cuja chave o servidor recusa dava sempre 400: não se oferece. */
internal fun linhaQueSeRelata(nome: String) = chaveLinhaValida(chaveLinha(nome))

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
    Surface(
        modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            val escolhido = tipo
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (escolhido != null) Icon(iconeRelato(escolhido), null, Modifier.padding(end = 12.dp))
                Text(
                    if (escolhido == null) stringResource(R.string.relatar) else stringResource(nomeDoTipo(escolhido)),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            // Com letra grande os tipos rolam e o "Cancelar" fica sempre à vista.
            Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                if (escolhido == null) {
                    TipoRelato.entries.forEach { t ->
                        // Sem linhas à vista não há em que linha relatar: ficam só os da paragem.
                        val pode = !t.deLinha || linhaFixa != null || linhas.isNotEmpty()
                        BotaoSecundario(
                            aoTocar = {
                                when {
                                    !t.deLinha -> aoEscolher(t, null)
                                    linhaFixa != null -> aoEscolher(t, linhaFixa)
                                    else -> tipo = t
                                }
                            },
                            ativo = pode,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Icon(iconeRelato(t), null, Modifier.padding(end = 8.dp))
                            Text(stringResource(nomeDoTipo(t)), Modifier.weight(1f))
                        }
                    }
                } else {
                    Text(stringResource(R.string.relatar_que_linha), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linhas.forEach { l -> SuggestionChip(onClick = { aoEscolher(escolhido, l) }, label = { Text(l, fontWeight = FontWeight.Black) }) }
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
    // Um 403 `desligado` (a config da app é a antiga) esconde o botão até a config mudar.
    var desligada by remember(config) { mutableStateOf(false) }
    if (!config.interruptores.comunidade || desligada) return
    val contexto = LocalContext.current
    val publicar = rememberPublicar(comunidade, aoAbrirConta)
    val verificarPerto = rememberVerificarPerto(paragem)
    var folha by rememberSaveable { mutableStateOf(false) }

    fun enviar(p: Paragem, tipo: TipoRelato, linha: String?) =
        publicar.correr({ comunidade.relatar(tipo, p.ref.chave, linha?.let(::chaveLinha)) }) {
            avisarRelato(contexto, it)
            if (relatosDesligados(it)) desligada = true
        }

    // Precisa das coordenadas da paragem: enquanto ela não carrega, não se relata.
    BotaoSecundario({ folha = true }, Modifier.padding(top = 16.dp).fillMaxWidth(), ativo = paragem != null) {
        Text(stringResource(R.string.relatar))
    }
    if (folha) {
        FolhaRelatar(linhas.filter(::linhaQueSeRelata), null, aoEscolher = { t, l ->
            folha = false
            paragem?.let { p -> verificarPerto(l) { enviar(p, t, l) } }
        }, aoFechar = { folha = false })
    }
}

/**
 * O "Relatar" do ecrã da viagem (a folha abre com [aberta]): a bordo, o relato fica na última paragem por onde a viagem
 * passou e na linha da etapa, sem pedir a posição. Fica sempre composto, para os diálogos da conta e dos termos não
 * desaparecerem com a folha.
 */
@Composable
fun RelatarNaViagem(comunidade: Comunidade, linha: String, aberta: Boolean, aoAbrirConta: () -> Unit, aoFechar: () -> Unit, aoDesligar: () -> Unit = {}) {
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
            publicar.correr({ comunidade.relatar(tipo, paragem, chaveLinha(nome)) }) {
                avisarRelato(contexto, it)
                if (relatosDesligados(it)) aoDesligar()
            }
        }
    }, aoFechar = aoFechar)
}
