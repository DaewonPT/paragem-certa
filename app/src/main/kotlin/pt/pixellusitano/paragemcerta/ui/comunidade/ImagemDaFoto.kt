package pt.pixellusitano.paragemcerta.ui.comunidade

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.tema.Icones

// As miniaturas de rua já são mais pequenas; uma maior lê-se reduzida em vez de ocupar a memória toda.
private const val MAX_LADO_PX = 1280

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
