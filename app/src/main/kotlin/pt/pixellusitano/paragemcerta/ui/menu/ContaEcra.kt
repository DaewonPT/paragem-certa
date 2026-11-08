package pt.pixellusitano.paragemcerta.ui.menu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstadoConta
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.pedirTokenGoogle
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.sobre.URL_PRIVACIDADE
import pt.pixellusitano.paragemcerta.ui.sobre.URL_TERMOS
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Quanto se espera pela Google: há telemóveis em que a janela dela nunca aparece e o pedido nunca volta. */
private val ESPERA_GOOGLE = 3.minutes

/** Em que ponto da entrada a pessoa está: a escolher como entrar, a escrever o email ou a escrever o código que recebeu. */
enum class PassoEntrada { ESCOLHA, EMAIL, CODIGO }

/**
 * Menu → Conta, ligado à [conta]. [webClientId] vazio = sem botão do Google. [pedirToken] é o Credential Manager (trocável nos testes),
 * que desiste passada a [espera].
 * Antes de entrar, se há favoritos no telemóvel, pergunta se vão para a conta: podem ser de outra pessoa que usou este telemóvel.
 */
@Composable
fun ContaEcra(
    conta: Conta,
    repositorio: Repositorio,
    webClientId: String,
    aoAbrir: (String) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    pedirToken: suspend (Activity, String, String) -> String? = ::pedirTokenGoogle,
    espera: Duration = ESPERA_GOOGLE,
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val estado by conta.estado.collectAsStateWithLifecycle()
    var passo by rememberSaveable { mutableStateOf(PassoEntrada.ESCOLHA) }
    var mensagem by remember { mutableStateOf<Int?>(null) }
    var aOcupar by remember { mutableStateOf(false) }
    // Uma entrada à espera da resposta a "Juntar os favoritos…?"; recebe a resposta e acaba a entrada.
    var aEspera by remember { mutableStateOf<(suspend (Boolean) -> Unit)?>(null) }

    fun trabalhar(bloco: suspend () -> Unit) {
        if (aOcupar) return
        aOcupar = true
        mensagem = null
        escopo.launch {
            try {
                bloco()
            } finally {
                aOcupar = false
            }
        }
    }

    /** Acaba uma entrada: ou entrou (o estado da conta muda sozinho) ou diz porquê não. */
    fun acabar(r: Resultado<Unit>, erro: (Resultado.Falha) -> Int) {
        when (r) {
            is Resultado.Ok -> passo = PassoEntrada.ESCOLHA
            is Resultado.Falha -> mensagem = erro(r)
        }
    }

    suspend fun entrar(fazer: suspend (Boolean) -> Unit) {
        if (repositorio.chavesNoTelemovel().isEmpty()) fazer(false) else aEspera = fazer
    }

    ContaConteudo(
        estado = estado,
        googleDisponivel = webClientId.isNotBlank(),
        passo = passo,
        mensagem = mensagem?.let { stringResource(it) },
        aOcupar = aOcupar,
        aoEntrarComGoogle = {
            trabalhar {
                when (val nonce = conta.nonceGoogle()) {
                    is Resultado.Falha -> mensagem = erroDoGoogle(nonce)
                    is Resultado.Ok -> {
                        val token = contexto.atividade()?.let { withTimeoutOrNull(espera) { pedirToken(it, webClientId, nonce.valor) } }
                        if (token == null) mensagem = R.string.conta_erro_google
                        else entrar { juntar -> acabar(conta.entrarComGoogle(token, juntar), ::erroDoGoogle) }
                    }
                }
            }
        },
        aoPedirCodigo = { email ->
            trabalhar {
                when (val r = conta.pedirCodigo(email)) {
                    is Resultado.Ok -> passo = PassoEntrada.CODIGO
                    is Resultado.Falha -> mensagem = erroDoPedido(r)
                }
            }
        },
        aoMudarPasso = { passo = it },
        aoConfirmarCodigo = { email, codigo ->
            trabalhar { entrar { juntar -> acabar(conta.confirmarCodigo(email, codigo, juntar), ::erroDoCodigo) } }
        },
        aoSincronizar = {
            trabalhar {
                val r = conta.sincronizar()
                if (r is Resultado.Falha) mensagem = erroDeSincronizar(r)
            }
        },
        aoSair = {
            trabalhar {
                conta.sair()
                passo = PassoEntrada.ESCOLHA
            }
        },
        aoApagarConta = { trabalhar { conta.apagarEAvisar(contexto) } },
        aoAbrir = aoAbrir,
        aoVoltar = aoVoltar,
        modifier = modifier,
    )

    aEspera?.let { terminar ->
        AlertDialog(
            // Fechar a pergunta (atrás, fora da caixa) desiste da entrada: nada foi para a conta e o código pode voltar a usar-se.
            onDismissRequest = { aEspera = null },
            text = { Text(stringResource(R.string.conta_juntar_pergunta)) },
            confirmButton = {
                TextButton(onClick = {
                    aEspera = null
                    trabalhar { terminar(true) }
                }) { Text(stringResource(R.string.conta_juntar)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    aEspera = null
                    trabalhar { terminar(false) }
                }) { Text(stringResource(R.string.conta_nao_juntar)) }
            },
        )
    }
}

/** O ecrã da conta sem estado de rede: [passo] e [mensagem] vêm de fora; o email e o código escritos ficam cá. */
@Composable
fun ContaConteudo(
    estado: EstadoConta,
    googleDisponivel: Boolean,
    passo: PassoEntrada,
    mensagem: String?,
    aOcupar: Boolean,
    aoEntrarComGoogle: () -> Unit,
    aoPedirCodigo: (String) -> Unit,
    aoMudarPasso: (PassoEntrada) -> Unit,
    aoConfirmarCodigo: (String, String) -> Unit,
    aoSincronizar: () -> Unit,
    aoSair: () -> Unit,
    aoApagarConta: () -> Unit,
    aoAbrir: (String) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Muda de passo, começa em branco: um código usado não volta.
    var codigo by rememberSaveable(passo) { mutableStateOf("") }
    var apagar by remember { mutableStateOf(false) }
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        CabecalhoEcra(stringResource(R.string.menu_conta), aoVoltar)
        when (estado) {
            is EstadoConta.Com -> {
                Text(estado.email, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    estado.sincronizadaEm?.let { stringResource(R.string.conta_sincronizado_as, HORA.format(it.atZone(LISBOA_FUSO))) }
                        ?: stringResource(R.string.conta_por_sincronizar),
                    Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = aoSincronizar, enabled = !aOcupar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conta_sincronizar)) }
                Ligacao(stringResource(R.string.conta_sair), aoSair)
                Ligacao(stringResource(R.string.conta_apagar), { apagar = true }, MaterialTheme.colorScheme.error)
            }
            EstadoConta.SemConta -> {
                Text(stringResource(R.string.conta_nota), Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (googleDisponivel && passo != PassoEntrada.CODIGO) {
                    Button(onClick = aoEntrarComGoogle, enabled = !aOcupar, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.conta_google))
                    }
                }
                when (passo) {
                    PassoEntrada.ESCOLHA ->
                        OutlinedButton(onClick = { aoMudarPasso(PassoEntrada.EMAIL) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.conta_entrar_email))
                        }
                    PassoEntrada.EMAIL -> {
                        OutlinedTextField(
                            email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.conta_email)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        Button(
                            onClick = { aoPedirCodigo(email.trim()) }, enabled = !aOcupar && '@' in email,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.conta_enviar_codigo)) }
                    }
                    PassoEntrada.CODIGO -> {
                        Text(stringResource(R.string.conta_codigo_enviado, email.trim()), Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                        OutlinedTextField(
                            codigo, { codigo = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.conta_codigo)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(
                            onClick = { aoConfirmarCodigo(email.trim(), codigo) }, enabled = !aOcupar && codigo.length == 6,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.conta_entrar)) }
                        TextButton(onClick = { aoMudarPasso(PassoEntrada.EMAIL) }) { Text(stringResource(R.string.conta_mudar_email)) }
                    }
                }
            }
        }
        // O leitor de ecrã lê o erro assim que aparece.
        mensagem?.let {
            Text(it, Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
        }
        if (aOcupar) Text(stringResource(R.string.a_carregar), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(top = 24.dp)) {
            Ligacao(stringResource(R.string.ligacao_termos), { aoAbrir(URL_TERMOS) })
            Ligacao(stringResource(R.string.ligacao_privacidade), { aoAbrir(URL_PRIVACIDADE) })
        }
    }
    if (apagar) {
        DialogoApagarConta(
            aoConfirmar = {
                apagar = false
                aoApagarConta()
            },
            aoCancelar = { apagar = false },
        )
    }
}

/** A pergunta antes de apagar a conta; igual no ecrã da conta e nas Definições → Privacidade. */
@Composable
internal fun DialogoApagarConta(aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        text = { Text(stringResource(R.string.conta_apagar_confirmar)) },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(stringResource(R.string.locais_apagar), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Apaga a conta e diz, num aviso curto, se deu; sem rede a conta fica como estava. */
internal suspend fun Conta.apagarEAvisar(contexto: Context) {
    val texto = if (apagarConta() is Resultado.Ok) R.string.conta_apagada else R.string.conta_apagar_falhou
    Toast.makeText(contexto, texto, Toast.LENGTH_LONG).show()
}

private tailrec fun Context.atividade(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.atividade()
    else -> null
}

// Em todas: em_pausa = o servidor passou a quota grátis do dia (spec 5.4), seja qual for o código.
// 400 = email que o servidor não aceita, 429 = mais de 3 códigos por hora, o resto (502 da Resend…) = não saiu.
@StringRes private fun erroDoPedido(f: Resultado.Falha) = if (f.emPausa) R.string.servidor_em_pausa else when (f.codigo) {
    null -> R.string.conta_erro_sem_ligacao
    400 -> R.string.conta_erro_email
    429 -> R.string.conta_erro_muitos
    else -> R.string.conta_erro_envio
}

// 401 = código errado, 410 = expirado ou já gasto as 5 tentativas.
@StringRes private fun erroDoCodigo(f: Resultado.Falha) = if (f.emPausa) R.string.servidor_em_pausa else when (f.codigo) {
    null -> R.string.conta_erro_sem_ligacao
    401 -> R.string.conta_erro_codigo
    410 -> R.string.conta_erro_expirou
    else -> R.string.conta_erro_generico
}

@StringRes private fun erroDoGoogle(f: Resultado.Falha) = if (f.emPausa) R.string.servidor_em_pausa else when (f.codigo) {
    null -> R.string.conta_erro_sem_ligacao
    401 -> R.string.conta_erro_google
    else -> R.string.conta_erro_generico
}

@StringRes private fun erroDeSincronizar(f: Resultado.Falha) = if (f.emPausa) R.string.servidor_em_pausa else when (f.codigo) {
    null -> R.string.conta_erro_sem_ligacao
    401 -> R.string.conta_sessao_terminou
    else -> R.string.conta_erro_sincronizar
}
