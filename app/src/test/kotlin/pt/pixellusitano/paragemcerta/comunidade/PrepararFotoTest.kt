package pt.pixellusitano.paragemcerta.comunidade

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrepararFotoTest {
    private val max = 300 * 1024

    @Test
    fun reduzOLadoMaiorPara1280() {
        val saida = preparar(jpegDeFoto(4000, 3000))
        assertEquals(1280 to 960, tamanhoDe(saida))
    }

    @Test
    fun respeitaAOrientacao() {
        val saida = preparar(comExif(jpegDeFoto(4000, 3000)))
        assertEquals(960 to 1280, tamanhoDe(saida))
    }

    @Test
    fun tiraOsMetadados() {
        val entrada = comExif(jpegDeFoto(2000, 1500))
        // Sem isto o teste não provava nada: a entrada traz mesmo o segmento que o servidor recusa.
        assertEquals(true, temMetadados(entrada))
        assertTrue(contem(entrada, "Exif"))

        val saida = preparar(entrada)
        assertEquals(false, temMetadados(saida))
        assertFalse(contem(saida, "Exif"))
        assertFalse(contem(saida, "http://ns.adobe.com/xap"))
    }

    @Test
    fun ficaAte300KB() {
        val saida = preparar(jpegDeFoto(4000, 3000))
        assertTrue("tem ${saida.size} bytes", saida.size <= max)
        assertSaidaLimpa(saida)
    }

    @Test
    fun ruidoPuroDaAte300KBOuNulo() {
        val aleatorio = Random(7)
        val entrada = jpegDe(4000, 3000, qualidade = 60) { _, _ -> aleatorio.nextInt() or (0xff shl 24) }
        val saida = prepararFoto { ByteArrayInputStream(entrada) }
        if (saida != null) {
            assertTrue("tem ${saida.size} bytes", saida.size <= max)
            assertSaidaLimpa(saida)
        }
    }

    @Test
    fun imagemEstragadaDaNulo() {
        val boa = jpegDeFoto(800, 600)
        assertNull(prepararFoto { ByteArrayInputStream(ByteArray(0)) })
        assertNull(prepararFoto { ByteArrayInputStream("isto não é uma imagem".toByteArray()) })
        assertNull(prepararFoto { ByteArrayInputStream(boa.copyOf(40)) })
        assertNull(prepararFoto { throw java.io.IOException("sem acesso ao ficheiro") })
    }

    @Test
    fun ajudaDoTesteConfereComOServidor() {
        val limpo = jpegDe(16, 16) { x, y -> 0xff000000.toInt() or (x * 16 shl 8) or y }
        assertEquals(false, temMetadados(limpo))
        assertEquals(true, temMetadados(comExif(limpo)))
        assertEquals(true, temMetadados(comSegmento(limpo, 0xed, "Photoshop 3.0\u0000".toByteArray())))
        assertNull(temMetadados(limpo + 0.toByte()))
        assertNull(temMetadados(limpo + limpo))
        assertNull(temMetadados(limpo.copyOf(limpo.size - 1)))
        assertNull(temMetadados(byteArrayOf(limpo[0], limpo[1], 0xff.toByte()) + limpo.copyOfRange(2, limpo.size)))
    }

    private fun preparar(entrada: ByteArray): ByteArray {
        val saida = prepararFoto { ByteArrayInputStream(entrada) }
        assertNotNull(saida)
        return saida!!
    }

    /** O que o servidor exige à saída (worker/src/fotos.ts): JPEG inteiro, sem APP1 nem APP13, sem nada depois do primeiro EOI. */
    private fun assertSaidaLimpa(saida: ByteArray) {
        assertEquals(listOf(0xff, 0xd8, 0xff), saida.take(3).map { it.toInt() and 0xff })
        assertEquals(listOf(0xff, 0xd9), saida.takeLast(2).map { it.toInt() and 0xff })
        assertEquals(false, temMetadados(saida))
        assertFalse(contem(saida, "Exif"))
    }

    private fun tamanhoDe(jpeg: ByteArray): Pair<Int, Int> {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        return bitmap.width to bitmap.height
    }

    private fun contem(bytes: ByteArray, texto: String): Boolean = String(bytes, Charsets.ISO_8859_1).contains(texto)

    /** Gradiente com muito ruído por cima, como uma foto de uma rua: a 85 passa dos 300 KB e só desce de qualidade até caber. */
    private fun jpegDeFoto(largura: Int, altura: Int): ByteArray {
        val aleatorio = Random(1)
        return jpegDe(largura, altura) { x, y ->
            val ruido = aleatorio.nextInt(-60, 61)
            val r = (x * 255 / largura + ruido).coerceIn(0, 255)
            val g = (y * 255 / altura + ruido).coerceIn(0, 255)
            val b = ((x + y) * 255 / (largura + altura) + ruido).coerceIn(0, 255)
            0xff000000.toInt() or (r shl 16) or (g shl 8) or b
        }
    }

    private fun jpegDe(largura: Int, altura: Int, qualidade: Int = 90, pixel: (Int, Int) -> Int): ByteArray {
        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val linha = IntArray(largura)
        for (y in 0 until altura) {
            for (x in 0 until largura) linha[x] = pixel(x, y)
            bitmap.setPixels(linha, 0, largura, 0, y, largura, 1)
        }
        val saida = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, qualidade, saida))
        bitmap.recycle()
        return saida.toByteArray()
    }

    /** Mete a seguir ao SOI um APP1 "Exif" com orientação 6 (rodar 90°) e uma latitude falsa, como as câmaras. */
    private fun comExif(jpeg: ByteArray): ByteArray {
        val tiff = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put("II".toByteArray()).putShort(42).putInt(8)
        // IFD0 (em 8): orientação = 6 e apontador para o IFD do GPS (em 38).
        tiff.putShort(2)
        tiff.putShort(0x0112).putShort(3).putInt(1).putShort(6).putShort(0)
        tiff.putShort(0x8825.toShort()).putShort(4).putInt(1).putInt(38)
        tiff.putInt(0)
        // IFD do GPS (em 38): "N" e 38° 43' 0" a apontar para os 3 racionais (em 68).
        tiff.putShort(2)
        tiff.putShort(1).putShort(2).putInt(2).put('N'.code.toByte()).put(0).putShort(0)
        tiff.putShort(2).putShort(5).putInt(3).putInt(68)
        tiff.putInt(0)
        for ((numerador, denominador) in listOf(38 to 1, 43 to 1, 0 to 1)) tiff.putInt(numerador).putInt(denominador)
        return comSegmento(jpeg, 0xe1, "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.array())
    }

    private fun comSegmento(jpeg: ByteArray, marcador: Int, conteudo: ByteArray): ByteArray {
        val tamanho = conteudo.size + 2
        val segmento = byteArrayOf(0xff.toByte(), marcador.toByte(), (tamanho shr 8).toByte(), tamanho.toByte()) + conteudo
        return jpeg.copyOf(2) + segmento + jpeg.copyOfRange(2, jpeg.size)
    }

    // Espelho de temMetadados (worker/src/fotos.ts), passo a passo: true com APP1 ou APP13, false se está limpo,
    // null se não é um JPEG inteiro (SOI … EOI com o primeiro EOI no último byte, sem preenchimento FF entre segmentos).
    private fun temMetadados(jpeg: ByteArray): Boolean? {
        fun b(i: Int) = jpeg[i].toInt() and 0xff
        fun semTamanho(m: Int) = m == 0x00 || m == 0x01 || m == 0xff || m == 0xd8 || m in 0xd0..0xd7
        val n = jpeg.size
        if (n < 6 || b(0) != 0xff || b(1) != 0xd8 || b(2) != 0xff || b(n - 2) != 0xff || b(n - 1) != 0xd9) return null

        var pos = 2
        var comImagem = false
        while (pos < n - 1) {
            if (b(pos) != 0xff) return null
            val marcador = b(pos + 1)
            if (marcador == 0xe1 || marcador == 0xed) return true
            if (marcador == 0xd9) return if (comImagem && pos == n - 2) false else null
            if (semTamanho(marcador) || pos + 4 > n) return null

            val tamanho = (b(pos + 2) shl 8) or b(pos + 3)
            pos += 2 + tamanho
            if (tamanho < 2 || pos > n) return null

            if (marcador == 0xda) {
                comImagem = true
                while (pos < n - 1) {
                    if (b(pos) != 0xff || b(pos + 1) == 0xff) {
                        pos++
                        continue
                    }
                    if (b(pos + 1) != 0x00 && b(pos + 1) !in 0xd0..0xd7) break
                    pos += 2
                }
            }
        }
        return null
    }
}
