// A orientação lê-se com o ExifInterface da plataforma de propósito: o androidx.exifinterface seria uma dependência nova.
@file:SuppressLint("ExifInterface")

package pt.pixellusitano.paragemcerta.comunidade

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val LADO_PX = 1280
private const val LADO_MINIMO_PX = 1024
private const val MAX_BYTES = 300 * 1024
private val QUALIDADES = listOf(85, 75, 65, 55)

/**
 * A foto pronta para o servidor: lado maior 1280 px, na posição certa, JPEG até 300 KB e sem metadados; `null` se a
 * imagem está estragada ou não cabe nos 300 KB. Sai sempre de pixéis novos (decodificar e voltar a comprimir), por
 * isso o Exif com o GPS fica para trás; o `gainmap` do Android 14+ tem de ir também, senão o `compress` cola ao JPEG
 * o XMP e uma segunda imagem (Ultra HDR) e o servidor recusa. `abrir` é chamada uma vez por passo e o fluxo fecha-se aqui.
 */
fun prepararFoto(abrir: () -> InputStream): ByteArray? = runCatching { preparar(abrir) }.getOrNull()

private fun preparar(abrir: () -> InputStream): ByteArray? {
    // 1.º passe: só o tamanho e a orientação, sem pixéis.
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    abrir().use { BitmapFactory.decodeStream(it, null, limites) }
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
    // Um Exif ilegível não deita fora a foto: fica como veio.
    val orientacao = runCatching {
        abrir().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    // 2.º passe: nunca a imagem inteira, só uma amostra (potência de 2) cujo lado maior ainda tem pelo menos 1280 px.
    val maior = maxOf(limites.outWidth, limites.outHeight)
    var amostra = 1
    while (maior / (amostra * 2) >= LADO_PX) amostra *= 2
    val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
    val amostrada = abrir().use { BitmapFactory.decodeStream(it, null, opcoes) } ?: return null

    val escala = minOf(1f, LADO_PX.toFloat() / maxOf(amostrada.width, amostrada.height))
    val matriz = Matrix().apply {
        setScale(escala, escala)
        when (orientacao) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }
    val foto = transformar(amostrada, matriz)

    for (qualidade in QUALIDADES) {
        val jpeg = comprimir(foto, qualidade) ?: return null
        if (jpeg.size <= MAX_BYTES) return jpeg
    }
    // Ainda grande a 55: uma última vez, mais pequena.
    val reducao = LADO_MINIMO_PX.toFloat() / maxOf(foto.width, foto.height)
    if (reducao >= 1f) return null
    val pequena = transformar(foto, Matrix().apply { setScale(reducao, reducao) })
    return comprimir(pequena, QUALIDADES.last())?.takeIf { it.size <= MAX_BYTES }
}

/** Liberta já a origem (a amostra pode ter dezenas de MB); a cópia nova é que fica. */
private fun transformar(origem: Bitmap, matriz: Matrix): Bitmap {
    val nova = Bitmap.createBitmap(origem, 0, 0, origem.width, origem.height, matriz, true)
    if (nova !== origem) origem.recycle()
    return nova
}

private fun comprimir(foto: Bitmap, qualidade: Int): ByteArray? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) foto.gainmap = null
    val saida = ByteArrayOutputStream()
    return if (foto.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)) saida.toByteArray() else null
}
