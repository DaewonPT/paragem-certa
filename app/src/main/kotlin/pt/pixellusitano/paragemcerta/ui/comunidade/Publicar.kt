package pt.pixellusitano.paragemcerta.ui.comunidade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.ui.sobre.URL_TERMOS

/**
 * Corre as escritas da comunidade (relatar, votar, fotografar, corrigir, denunciar) e trata das duas respostas que pedem
 * alguma coisa à pessoa: sem sessão mostra o [DialogoEntrar]; termos por aceitar mostra o [DialogoTermos] e, aceites, repete
 * a ação uma só vez. Nesses casos [correr] não chama `aoAcabar` (o diálogo é a resposta); nos outros chama-o uma vez.
 * Sem rede nada fica guardado para reenviar: a proximidade era a daquele momento.
 */
class Publicar internal constructor(private val escopo: CoroutineScope, private val comunidade: Comunidade) {
    internal var dialogo by mutableStateOf<Dialogo?>(null)

    internal sealed interface Dialogo {
        data object Entrar : Dialogo
        class Termos(val aoAceitar: () -> Unit) : Dialogo
    }

    fun <T> correr(acao: suspend () -> Resultado<T>, aoAcabar: (Resultado<T>) -> Unit) {
        escopo.launch { tratar(acao(), acao, aoAcabar, termosPorAceitar = true) }
    }

    private fun <T> tratar(r: Resultado<T>, acao: suspend () -> Resultado<T>, aoAcabar: (Resultado<T>) -> Unit, termosPorAceitar: Boolean) {
        val falha = r as? Resultado.Falha
        when {
            // Um 401 do servidor é uma sessão que a Conta acabou de largar (ver Conta.comSessao): para a pessoa é o mesmo que não ter entrado.
            falha?.erro == "sem_sessao" || falha?.codigo == 401 -> dialogo = Dialogo.Entrar
            falha?.erro == "termos_por_aceitar" && termosPorAceitar -> dialogo = Dialogo.Termos {
                escopo.launch {
                    // Depois de aceitar, a ação repete-se uma vez; se o servidor ainda pedir os termos, é uma falha como as outras.
                    when (val aceite = comunidade.aceitarTermos()) {
                        is Resultado.Falha -> tratar(aceite, acao, aoAcabar, termosPorAceitar = false)
                        is Resultado.Ok -> tratar(acao(), acao, aoAcabar, termosPorAceitar = false)
                    }
                }
            }
            else -> aoAcabar(r)
        }
    }
}

/** O [Publicar] deste ecrã, com os seus diálogos; [aoEntrar] abre Menu → Conta e [textoEntrar] diz para que é preciso entrar. */
@Composable
fun rememberPublicar(
    comunidade: Comunidade,
    aoEntrar: () -> Unit,
    @StringRes textoEntrar: Int = R.string.comunidade_entrar_texto,
): Publicar {
    val escopo = rememberCoroutineScope()
    val publicar = remember(comunidade) { Publicar(escopo, comunidade) }
    val fechar = { publicar.dialogo = null }
    when (val d = publicar.dialogo) {
        Publicar.Dialogo.Entrar -> DialogoEntrar(aoEntrar = { fechar(); aoEntrar() }, aoFechar = fechar, texto = textoEntrar)
        is Publicar.Dialogo.Termos -> DialogoTermos(aoAceitar = { fechar(); d.aoAceitar() }, aoFechar = fechar)
        null -> {}
    }
    return publicar
}

@Composable
fun DialogoEntrar(aoEntrar: () -> Unit, aoFechar: () -> Unit, @StringRes texto: Int = R.string.comunidade_entrar_texto) {
    AlertDialog(
        onDismissRequest = aoFechar,
        text = { Text(stringResource(texto)) },
        confirmButton = { TextButton(onClick = aoEntrar) { Text(stringResource(R.string.comunidade_entrar)) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Os termos e as regras da comunidade, aceites antes da primeira publicação (spec 4.7); a ligação abre a página do servidor. */
@Composable
fun DialogoTermos(aoAceitar: () -> Unit, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.termos_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.termos_texto))
                TextButton(
                    onClick = { runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_TERMOS))) } },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.termos_ler)) }
            }
        },
        confirmButton = { TextButton(onClick = aoAceitar) { Text(stringResource(R.string.termos_aceito)) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.comunidade_agora_nao)) } },
    )
}

/** O que dizer quando uma escrita da comunidade falha. */
@StringRes
fun erroDaComunidade(f: Resultado.Falha): Int = when {
    f.emPausa -> R.string.servidor_em_pausa
    f.codigo == null -> R.string.comunidade_sem_ligacao
    f.erro == "muitos_pedidos" -> R.string.comunidade_muitos_pedidos
    f.erro == "devagar" -> R.string.comunidade_devagar
    f.erro == "conta_bloqueada" -> R.string.comunidade_conta_bloqueada
    else -> R.string.comunidade_erro
}

/** Como [erroDaComunidade], mas um relato que já acabou (ou foi apagado) tem a sua frase. */
@StringRes
internal fun erroDoRelato(f: Resultado.Falha): Int = if (f.erro == "nao_existe") R.string.relato_ja_acabou else erroDaComunidade(f)

internal fun avisar(c: Context, @StringRes texto: Int) = Toast.makeText(c, texto, Toast.LENGTH_LONG).show()
