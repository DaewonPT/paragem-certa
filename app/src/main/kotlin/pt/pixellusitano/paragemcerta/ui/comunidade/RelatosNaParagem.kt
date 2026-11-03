package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Preferencias

/** Os relatos de uma paragem (sem os que a pessoa ocultou) e o que ela pode fazer com cada um; ver [rememberRelatosDaParagem]. */
class RelatosDaParagem internal constructor(
    val lista: List<RelatoRemoto>,
    val aoVotar: (RelatoRemoto, aindaEsta: Boolean) -> Unit,
    val aoDenunciar: (RelatoRemoto) -> Unit,
    val aoOcultar: (RelatoRemoto) -> Unit,
)

/**
 * Carrega os relatos da paragem [chave] ao abrir e depois de cada voto. Votar pede o mesmo que relatar (a menos de 300 m ou a
 * bordo da linha do relato, medido no telemóvel); denunciar pede conta e mostra o diálogo. Um 403 `desligado` esconde a secção
 * e os pedidos só recomeçam quando a config mudar. [paragem] pode ser null enquanto carrega: sem ela não se vota. Os diálogos
 * (entrar, termos, denunciar) desenham-se aqui.
 */
@Composable
fun rememberRelatosDaParagem(
    chave: String,
    paragem: Paragem?,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
    aoAbrirConta: () -> Unit,
): RelatosDaParagem {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    val ocultos by preferencias.ocultos.collectAsStateWithLifecycle(emptySet())
    val publicarVoto = rememberPublicar(comunidade, aoAbrirConta, R.string.relato_votar_entrar_texto)
    val publicarDenuncia = rememberPublicar(comunidade, aoAbrirConta, R.string.denunciar_entrar_texto)
    val verificarPerto = rememberVerificarPerto(paragem)
    var relatos by remember(chave) { mutableStateOf(emptyList<RelatoRemoto>()) }
    var recarregar by remember { mutableIntStateOf(0) }
    // O relato e o email da conta que se está a denunciar (lido ao abrir, para o texto não mudar a meio).
    var denunciado by remember { mutableStateOf<Pair<RelatoRemoto, String>?>(null) }
    var pedirConta by remember { mutableStateOf(false) }

    // A config é uma chave: um "desligado" esconde a secção até ela mudar. Uma falha sem resposta deixa o que lá estava.
    LaunchedEffect(chave, config, recarregar) {
        when (val r = comunidade.daParagem(chave)) {
            is Resultado.Ok -> relatos = r.valor.relatos
            is Resultado.Falha -> if (r.erro == "desligado") relatos = emptyList()
        }
    }

    // Um voto ou uma denúncia que falha: desligado esconde a secção; um relato que já acabou sai da lista; o resto só se diz.
    fun falhou(f: Resultado.Falha) {
        if (f.erro == "desligado") {
            relatos = emptyList()
            denunciado = null
            return
        }
        avisar(contexto, erroDoRelato(f))
        if (f.erro == "nao_existe") {
            denunciado = null
            recarregar++
        }
    }

    denunciado?.let { (r, email) ->
        DialogoDenunciar(
            email,
            aoEnviar = { motivo, nome, explicacao ->
                publicarDenuncia.correr({ comunidade.denunciar("relato:${r.id}", motivo, nome, explicacao) }) { res ->
                    // Se falhar o diálogo fica, com o que a pessoa escreveu, para tentar outra vez.
                    when (res) {
                        is Resultado.Ok -> {
                            denunciado = null
                            avisar(contexto, R.string.denunciar_enviada)
                        }
                        is Resultado.Falha -> falhou(res)
                    }
                }
            },
            aoFechar = { denunciado = null },
        )
    }
    if (pedirConta) {
        DialogoEntrar(
            aoEntrar = {
                pedirConta = false
                aoAbrirConta()
            },
            aoFechar = { pedirConta = false },
            texto = R.string.denunciar_entrar_texto,
        )
    }

    return RelatosDaParagem(
        lista = relatos.filter { "relato:${it.id}" !in ocultos },
        aoVotar = { r, aindaEsta ->
            verificarPerto(r.linha.ifEmpty { null }) {
                publicarVoto.correr({ comunidade.votar(r.id, aindaEsta) }) { res ->
                    when (res) {
                        is Resultado.Ok -> {
                            avisar(contexto, R.string.relato_voto_enviado)
                            recarregar++ // as contagens (e se o relato acabou) são as do servidor
                        }
                        is Resultado.Falha -> falhou(res)
                    }
                }
            }
        },
        aoDenunciar = { r ->
            val email = comunidade.emailDaConta
            if (email == null) pedirConta = true else denunciado = r to email
        },
        aoOcultar = { r -> escopo.launch { preferencias.ocultar("relato:${r.id}") } },
    )
}
