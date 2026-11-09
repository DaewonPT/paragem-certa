package pt.pixellusitano.paragemcerta.ui.comunidade

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.comunidade.prepararFoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FotoRemota
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.ui.comum.BotaoSecundario
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

// Dentro de cacheDir, a única pasta que o FileProvider serve (res/xml/caminhos_fotos.xml).
private const val CAPTURA = "fotos/captura.jpg"

// As fotos que a app envia já têm no máximo isto; uma maior (de outro cliente) lê-se reduzida em vez de ocupar a memória toda.
private const val MAX_LADO_PX = 1280

// Os erros que dizem que ESTA foto não serve: não se repete, escolhe-se outra.
private val RECUSADAS = setOf("foto_grande", "foto_com_metadados", "pedido_invalido")

/**
 * O endereço que a câmara recebe para escrever a captura (o FileProvider das fotos). Gancho para testes: o FileProvider da androidx
 * compara caminhos com '/', por isso não corre num PC com Windows.
 */
internal object Captura {
    var uri: (Context, File) -> Uri = { c, f -> FileProvider.getUriForFile(c, "${c.packageName}.fotos", f) }
}

/**
 * Lê uma foto do servidor com o tamanho limitado: reduz por potências de 2 até o lado maior caber em 1280 px (uma foto de
 * 12 MP lida inteira ocupa ~50 MB). null se não for uma imagem. Corre fora da linha do ecrã.
 */
internal fun decodificarFoto(bytes: ByteArray): Bitmap? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
    var amostra = 1
    while (maxOf(limites.outWidth, limites.outHeight) / amostra > MAX_LADO_PX) amostra *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra })
}

/** O que dizer quando o envio de uma foto falha: as respostas próprias das fotos e, para o resto, as de toda a comunidade (a pausa primeiro). */
@StringRes
internal fun erroDaFoto(f: Resultado.Falha): Int = when (f.erro) {
    "foto_grande" -> R.string.foto_grande
    "foto_com_metadados" -> R.string.foto_com_metadados
    "pedido_invalido" -> R.string.foto_estragada
    "muitos_pedidos" -> R.string.foto_limite
    else -> erroDaComunidade(f)
}

/**
 * A parte das fotos do ecrã da paragem [chave]: uma só foto, a da comunidade ou a de [rua] (a que o ficheiro do Panoramax tem
 * para a paragem, já sem a que a pessoa ocultou), a mais recente das duas (ver [escolherFoto]).
 * A de rua não depende de nenhum interruptor nem da leitura da paragem. A da comunidade, "Foto em revisão" e o botão "Enviar foto"
 * (só sem nenhuma foto) precisam das fotos e da comunidade ligadas e de a leitura da paragem chegar (sem ela não se sabe se já
 * há foto). Os dados da comunidade são os de [relatos], lidos num só pedido. [aoAbrirPagina] recebe a página da foto de rua e
 * [aoOcultarRua] oculta-a. Um 403 `desligado` ao enviar (a config da app é a antiga) esconde a parte da comunidade até a config mudar.
 */
@Composable
fun FotosDaParagem(
    chave: String,
    relatos: RelatosDaParagem,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    carregar: suspend (String) -> ByteArray?,
    aoAbrirConta: () -> Unit,
    rua: FotoDeRua? = null,
    aoAbrirPagina: (String) -> Unit = {},
    aoOcultarRua: () -> Unit = {},
) {
    val contexto = LocalContext.current
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    var desligada by remember(config) { mutableStateOf(false) }
    val publicar = rememberPublicar(comunidade, aoAbrirConta, R.string.foto_entrar_texto)
    var folha by rememberSaveable { mutableStateOf(false) }
    val fotosDaComunidade = config.interruptores.fotos && !desligada
    val comunidadeLida = fotosDaComunidade && config.interruptores.comunidade && relatos.carregado
    val daComunidade = if (comunidadeLida) relatos.fotos.firstOrNull() else null
    val escolhida = escolherFoto(rua, daComunidade)
    when (escolhida) {
        is FotoEscolhida.DaRua -> key(escolhida.foto.id) { FotoDeRuaNaParagem(escolhida.foto, carregar, aoAbrirPagina, aoOcultar = aoOcultarRua) }
        is FotoEscolhida.DaComunidade -> key(escolhida.foto.id) {
            val f = escolhida.foto
            FotoDaParagem(f, carregar, aoOcultar = { relatos.aoOcultarFoto(f) }, aoDenunciar = { relatos.aoDenunciarFoto(f) })
        }
        null -> Unit
    }
    if (comunidadeLida && daComunidade == null) {
        if (relatos.fotoEmRevisao) {
            Text(
                stringResource(R.string.foto_em_revisao), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else if (escolhida == null) {
            BotaoSecundario({ folha = true }, Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.foto_enviar))
            }
        }
    }
    if (folha && fotosDaComunidade) {
        FolhaEnviarFoto(chave, publicar, aoEnviar = { jpeg ->
            comunidade.enviarFoto(chave, jpeg).also { r ->
                val erro = (r as? Resultado.Falha)?.erro
                when {
                    // A foto chegou (ou já lá estava): a paragem passa a mostrar "Foto em revisão".
                    r is Resultado.Ok || erro == "ja_tem_foto" -> relatos.recarregar()
                    erro == "desligado" -> {
                        desligada = true
                        folha = false
                        avisar(contexto, R.string.foto_desligada)
                    }
                }
            }
        }, aoFechar = { folha = false })
    }
}

private enum class Fase { LIVRE, A_PREPARAR, A_ENVIAR }

/**
 * A folha "Enviar foto": escolher no seletor do Android ou tirar com a câmara, preparar (1280 px, sem metadados, ver [prepararFoto])
 * e enviar. Não pede nenhuma permissão: o seletor dá só a foto escolhida e a câmara escreve num ficheiro que a app lhe indica, que
 * se apaga depois de preparado. A foto preparada fica aqui até enviar bem, para "Tentar outra vez" mandar os mesmos bytes. O envio
 * passa por [publicar] (conta e termos); [paragem] identifica a paragem a que a foto pertence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaEnviarFoto(paragem: String, publicar: Publicar, aoEnviar: suspend (ByteArray) -> Resultado<String>, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var jpeg by remember(paragem) { mutableStateOf<ByteArray?>(null) }
    var fase by remember(paragem) { mutableStateOf(Fase.LIVRE) }
    var aviso by remember(paragem) { mutableStateOf<Int?>(null) }
    var falhou by remember(paragem) { mutableStateOf(false) }
    val captura = File(contexto.cacheDir, CAPTURA)

    fun preparar(abrir: () -> InputStream, depois: () -> Unit = {}) {
        fase = Fase.A_PREPARAR
        aviso = null
        escopo.launch {
            // Fora da linha do ecrã: ler e reduzir uma foto de 12 MP leva centenas de milissegundos.
            val pronto = try {
                withContext(Dispatchers.IO) { prepararFoto(abrir) }
            } finally {
                depois()
            }
            fase = Fase.LIVRE
            jpeg = pronto
            falhou = false
            if (pronto == null) aviso = R.string.foto_estragada
        }
    }

    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) preparar({ contexto.contentResolver.openInputStream(uri) ?: throw FileNotFoundException() })
    }
    val tirar = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        if (tirou) preparar({ captura.inputStream() }, depois = { captura.delete() }) else captura.delete()
    }

    val mensagem = when {
        fase == Fase.A_PREPARAR -> R.string.foto_a_preparar
        fase == Fase.A_ENVIAR -> R.string.foto_a_enviar
        aviso != null -> aviso
        jpeg != null -> R.string.foto_pronta
        else -> null
    }
    BasicAlertDialog(onDismissRequest = aoFechar) {
        FolhaEnviarFotoConteudo(
            pronta = jpeg != null, ocupado = fase != Fase.LIVRE, falhou = falhou, mensagem = mensagem, erro = fase == Fase.LIVRE && aviso != null,
            aoEscolher = {
                try {
                    escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: ActivityNotFoundException) {
                    avisar(contexto, R.string.foto_sem_galeria)
                }
            },
            aoTirar = {
                captura.parentFile?.mkdirs()
                captura.delete() // o que ficou de uma captura que não chegou ao fim
                try {
                    tirar.launch(Captura.uri(contexto, captura))
                } catch (e: ActivityNotFoundException) {
                    avisar(contexto, R.string.foto_sem_camera)
                }
            },
            aoEnviar = {
                val bytes = jpeg ?: return@FolhaEnviarFotoConteudo
                aviso = null
                // "A enviar" vale só enquanto o pedido corre: se a resposta for um diálogo (entrar, termos), a folha volta a ficar livre.
                publicar.correr({
                    fase = Fase.A_ENVIAR
                    try {
                        aoEnviar(bytes)
                    } finally {
                        fase = Fase.LIVRE
                    }
                }) { r ->
                    when {
                        r is Resultado.Ok -> {
                            avisar(contexto, R.string.foto_enviada)
                            aoFechar()
                        }
                        // A foto chegou mas a resposta perdeu-se, ou já havia outra: a paragem passa a mostrar "Foto em revisão".
                        r is Resultado.Falha && r.erro == "ja_tem_foto" -> {
                            avisar(contexto, R.string.foto_ja_tem)
                            aoFechar()
                        }
                        r is Resultado.Falha -> {
                            aviso = erroDaFoto(r)
                            // Uma foto recusada não se repete (tem de se escolher outra); com os outros erros "Tentar outra vez" manda a mesma.
                            if (r.erro in RECUSADAS) jpeg = null else falhou = true
                        }
                    }
                }
            },
            aoFechar = aoFechar,
        )
    }
}

/** O cartão da folha, à parte da janela (as capturas de ecrã não apanham janelas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolhaEnviarFotoConteudo(
    pronta: Boolean,
    ocupado: Boolean,
    falhou: Boolean,
    @StringRes mensagem: Int?,
    erro: Boolean,
    aoEscolher: () -> Unit,
    aoTirar: () -> Unit,
    aoEnviar: () -> Unit,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier, shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).padding(24.dp)) {
            Text(stringResource(R.string.foto_folha_titulo), style = MaterialTheme.typography.headlineSmall)
            // Com letra grande o texto rola e os botões de baixo ficam sempre à vista.
            Column(Modifier.weight(1f, fill = false).padding(top = 16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.foto_regras))
                Text(
                    stringResource(R.string.foto_licenca), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
                if (mensagem != null) {
                    Text(
                        stringResource(mensagem), color = if (erro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoSecundario(aoEscolher, ativo = !ocupado) { Text(stringResource(R.string.foto_escolher)) }
                    BotaoSecundario(aoTirar, ativo = !ocupado) { Text(stringResource(R.string.foto_tirar)) }
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
                Button(onClick = aoEnviar, enabled = pronta && !ocupado) {
                    Text(stringResource(if (falhou) R.string.foto_tentar_outra_vez else R.string.foto_botao_enviar))
                }
            }
        }
    }
}

internal sealed interface Carga {
    data object ACarregar : Carga
    data object Falhou : Carga
    class Pronta(val imagem: ImageBitmap) : Carga
}

/** Lê e decodifica a imagem de [url] fora da linha do ecrã, com o tamanho limitado ([decodificarFoto]). */
@Composable
internal fun carregarImagem(url: String, carregar: suspend (String) -> ByteArray?): Carga {
    val carga by produceState<Carga>(Carga.ACarregar, url) {
        val imagem = withContext(Dispatchers.IO) { carregar(url)?.let(::decodificarFoto) }
        value = if (imagem == null) Carga.Falhou else Carga.Pronta(imagem.asImageBitmap())
    }
    return carga
}

/**
 * A foto aprovada de uma paragem, com o crédito, e no menu "Ocultar esta foto" e "Denunciar" (os que forem dados; sem nenhum, sem
 * menu). [carregar] devolve os bytes do endereço da foto (null se não der).
 */
@Composable
fun FotoDaParagem(foto: FotoRemota, carregar: suspend (String) -> ByteArray?, aoOcultar: (() -> Unit)?, aoDenunciar: (() -> Unit)?) {
    val carga = carregarImagem(foto.url, carregar)
    FotoDaParagemConteudo((carga as? Carga.Pronta)?.imagem, falhou = carga == Carga.Falhou, aoOcultar = aoOcultar, aoDenunciar = aoDenunciar)
}

/** A foto e o crédito, sem pedidos (as capturas de ecrã compõem-no com uma imagem já feita). */
@Composable
internal fun FotoDaParagemConteudo(imagem: ImageBitmap?, falhou: Boolean, aoOcultar: (() -> Unit)?, aoDenunciar: (() -> Unit)?) {
    Column(Modifier.padding(top = 16.dp).fillMaxWidth()) {
        ImagemDaFoto(imagem, falhou, stringResource(R.string.foto_da_paragem), Modifier.clip(MaterialTheme.shapes.large))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.foto_credito), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            MenuDaFoto(aoOcultar, aoDenunciar)
        }
    }
}

/** A imagem inteira (sem recortar) ou, enquanto não a há, o que se passa com ela. */
@Composable
internal fun ImagemDaFoto(imagem: ImageBitmap?, falhou: Boolean, descricao: String, modifier: Modifier = Modifier) {
    if (imagem != null) {
        Image(imagem, descricao, modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Text(
            stringResource(if (falhou) R.string.foto_nao_carregou else R.string.a_carregar),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

/** O "⋯" da foto com as opções dadas; sem nenhuma, não há botão. */
@Composable
internal fun MenuDaFoto(aoOcultar: (() -> Unit)?, aoDenunciar: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    val opcoes = stringResource(R.string.foto_mais_opcoes)
    if (aoOcultar == null && aoDenunciar == null) return
    Box {
        IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = opcoes }) { Icon(Icones.Mais, null) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            aoOcultar?.let { ocultar -> DropdownMenuItem(text = { Text(stringResource(R.string.foto_ocultar)) }, onClick = { menu = false; ocultar() }) }
            aoDenunciar?.let { denunciar -> DropdownMenuItem(text = { Text(stringResource(R.string.relato_denunciar)) }, onClick = { menu = false; denunciar() }) }
        }
    }
}
