package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.MotivoDenuncia
import pt.pixellusitano.paragemcerta.ui.comum.BotaoPrincipal

// Os limites do servidor (worker/src/denuncias.ts): o nome até 80 caracteres e sem caracteres de controlo, a explicação até 500.
private const val MAX_NOME = 80
private const val MAX_EXPLICACAO = 500

@StringRes
private fun nomeDoMotivo(m: MotivoDenuncia) = when (m) {
    MotivoDenuncia.FALSO -> R.string.denunciar_falso
    MotivoDenuncia.OFENSIVO -> R.string.denunciar_ofensivo
    MotivoDenuncia.DADOS_PESSOAIS -> R.string.denunciar_dados_pessoais
    MotivoDenuncia.PERIGO -> R.string.denunciar_perigo
    MotivoDenuncia.OUTRO -> R.string.denunciar_outro
}

/**
 * Denunciar um relato (ou, com outro [titulo], uma foto). Não pede nenhum endereço: o recibo e a decisão vão para o email da conta,
 * que é o [email] que se mostra, e o pedido nem o leva (o servidor usa o da sessão). Só se envia com um nome e a declaração de boa-fé marcada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoDenunciar(
    email: String,
    aoEnviar: (MotivoDenuncia, nome: String, explicacao: String) -> Unit,
    aoFechar: () -> Unit,
    @StringRes titulo: Int = R.string.denunciar_titulo,
) {
    // Largura própria em vez da do sistema: com campos de texto, a janela com a largura do sistema nunca fica quieta nos testes do
    // Robolectric. A margem do ecrã põe-se aqui; o cartão tem no máximo 560 dp, como o AlertDialog.
    BasicAlertDialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DenunciarConteudo(email, aoEnviar, aoFechar, Modifier.padding(horizontal = 24.dp), titulo)
    }
}

/** O cartão do diálogo, à parte da janela (as capturas de ecrã não apanham janelas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DenunciarConteudo(
    email: String,
    aoEnviar: (MotivoDenuncia, nome: String, explicacao: String) -> Unit,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes titulo: Int = R.string.denunciar_titulo,
) {
    var motivo by rememberSaveable { mutableStateOf(MotivoDenuncia.FALSO) }
    var nome by rememberSaveable { mutableStateOf("") }
    var explicacao by rememberSaveable { mutableStateOf("") }
    var boaFe by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.headlineSmall)
            // Com letra grande o formulário rola e os botões ficam sempre à vista.
            Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.denunciar_porque), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    MotivoDenuncia.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = motivo == m, role = Role.RadioButton, onClick = { motivo = m }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = motivo == m, onClick = null)
                            Text(stringResource(nomeDoMotivo(m)), Modifier.padding(start = 8.dp))
                        }
                    }
                }
                OutlinedTextField(
                    nome, { nome = it.filter { c -> !c.isISOControl() }.take(MAX_NOME) },
                    Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(R.string.denunciar_nome)) }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    explicacao, { explicacao = it.take(MAX_EXPLICACAO) },
                    Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(R.string.denunciar_explicacao)) }, minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("${explicacao.length}/$MAX_EXPLICACAO") },
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp).toggleable(value = boaFe, role = Role.Checkbox, onValueChange = { boaFe = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = boaFe, onCheckedChange = null)
                    Text(stringResource(R.string.denunciar_boa_fe), Modifier.padding(start = 8.dp))
                }
                Text(
                    stringResource(R.string.denunciar_recibo, email), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Com letra grande os dois botões quebram em vez de um sair do ecrã.
            FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
                BotaoPrincipal({ aoEnviar(motivo, nome.trim(), explicacao.trim()) }, ativo = nome.isNotBlank() && boaFe) {
                    Text(stringResource(R.string.denunciar_enviar))
                }
            }
        }
    }
}
