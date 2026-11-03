package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.annotation.StringRes
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
import pt.pixellusitano.paragemcerta.core.fontes.servidor.CorrecaoRemota
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FotoRemota
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Preferencias

/**
 * Os relatos de uma paragem (sem os que a pessoa ocultou) e o que ela pode fazer com cada um; ver [rememberRelatosDaParagem].
 * A foto vem na mesma leitura (um só pedido por ecrã): [fotos] são as aprovadas, sem as ocultadas; [fotoEmRevisao] diz que há
 * uma à espera do autor; [correcoes] são as aprovadas (ver [aplicarCorrecoes]); [carregado] que a leitura já chegou (sem ela não
 * se sabe se já há foto); [recarregar] volta a ler.
 */
class RelatosDaParagem internal constructor(
    val lista: List<RelatoRemoto>,
    val aoVotar: (RelatoRemoto, aindaEsta: Boolean) -> Unit,
    val aoDenunciar: (RelatoRemoto) -> Unit,
    val aoOcultar: (RelatoRemoto) -> Unit,
    val fotos: List<FotoRemota>,
    val fotoEmRevisao: Boolean,
    val correcoes: List<CorrecaoRemota>,
    val carregado: Boolean,
    val aoDenunciarFoto: (FotoRemota) -> Unit,
    val aoOcultarFoto: (FotoRemota) -> Unit,
    val recarregar: () -> Unit,
)

/** O que se está a denunciar: o id do servidor ("relato:<id>" ou "foto:<id>"), o título do diálogo e a frase para quando já não existe. */
private class Denuncia(val conteudo: String, @StringRes val titulo: Int, @StringRes val jaNaoExiste: Int, val email: String)

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
    var fotos by remember(chave) { mutableStateOf(emptyList<FotoRemota>()) }
    var fotoEmRevisao by remember(chave) { mutableStateOf(false) }
    var correcoes by remember(chave) { mutableStateOf(emptyList<CorrecaoRemota>()) }
    var carregado by remember(chave) { mutableStateOf(false) }
    var recarregar by remember { mutableIntStateOf(0) }
    // O que se está a denunciar e o email da conta (lido ao abrir, para o texto não mudar a meio).
    var denunciado by remember { mutableStateOf<Denuncia?>(null) }
    var pedirConta by remember { mutableStateOf(false) }

    fun esconderTudo() {
        relatos = emptyList()
        fotos = emptyList()
        fotoEmRevisao = false
        correcoes = emptyList()
        carregado = false
    }

    // A config é uma chave: um "desligado" esconde a secção até ela mudar. Uma falha sem resposta deixa o que lá estava.
    LaunchedEffect(chave, config, recarregar) {
        when (val r = comunidade.daParagem(chave)) {
            is Resultado.Ok -> {
                relatos = r.valor.relatos
                fotos = r.valor.fotos
                fotoEmRevisao = chave in r.valor.emRevisao
                correcoes = r.valor.correcoes
                carregado = true
            }
            is Resultado.Falha -> if (r.erro == "desligado") esconderTudo()
        }
    }

    // Um voto ou uma denúncia que falha: desligado esconde a secção; um relato (ou foto) que já acabou sai da lista; o resto só se diz.
    fun falhou(f: Resultado.Falha, @StringRes jaNaoExiste: Int = R.string.relato_ja_acabou) {
        if (f.erro == "desligado") {
            esconderTudo()
            denunciado = null
            return
        }
        avisar(contexto, if (f.erro == "nao_existe") jaNaoExiste else erroDaComunidade(f))
        if (f.erro == "nao_existe") {
            denunciado = null
            recarregar++
        }
    }

    fun denunciar(conteudo: String, @StringRes titulo: Int, @StringRes jaNaoExiste: Int) {
        val email = comunidade.emailDaConta
        if (email == null) pedirConta = true else denunciado = Denuncia(conteudo, titulo, jaNaoExiste, email)
    }

    denunciado?.let { d ->
        DialogoDenunciar(
            d.email,
            aoEnviar = { motivo, nome, explicacao ->
                publicarDenuncia.correr({ comunidade.denunciar(d.conteudo, motivo, nome, explicacao) }) { res ->
                    // Se falhar o diálogo fica, com o que a pessoa escreveu, para tentar outra vez.
                    when (res) {
                        is Resultado.Ok -> {
                            denunciado = null
                            avisar(contexto, R.string.denunciar_enviada)
                        }
                        is Resultado.Falha -> falhou(res, d.jaNaoExiste)
                    }
                }
            },
            aoFechar = { denunciado = null },
            titulo = d.titulo,
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
        aoDenunciar = { r -> denunciar("relato:${r.id}", R.string.denunciar_titulo, R.string.relato_ja_acabou) },
        aoOcultar = { r -> escopo.launch { preferencias.ocultar("relato:${r.id}") } },
        fotos = fotos.filter { "foto:${it.id}" !in ocultos },
        fotoEmRevisao = fotoEmRevisao,
        correcoes = correcoes,
        carregado = carregado,
        aoDenunciarFoto = { f -> denunciar("foto:${f.id}", R.string.denunciar_foto_titulo, R.string.denunciar_foto_ja_nao_existe) },
        aoOcultarFoto = { f -> escopo.launch { preferencias.ocultar("foto:${f.id}") } },
        recarregar = { recarregar++ },
    )
}
