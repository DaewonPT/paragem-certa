package pt.pixellusitano.paragemcerta.atualizar

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Os 16 bytes do APK falso: todos diferentes, para se ver um pedaço fora do sítio. */
internal val APK_FALSO = ByteArray(16) { it.toByte() }

/**
 * Servidor HTTP local do JDK que serve o [APK_FALSO]; o `pedidos` dado ao [teste] conta os descarregamentos já começados.
 * Com [segurar], manda metade e espera que o teste o largue (5 s no máximo) antes de mandar o resto.
 * Responde a `Range: bytes=N-` com 206 e o resto, ou 416 se não falta nada, como o GitHub; com [ignorarRange] responde sempre 200.
 * Com [cortar], um pedido sem `Range` recebe só metade e a ligação fecha-se.
 */
internal fun comApkNoServidor(
    segurar: CountDownLatch? = null,
    ignorarRange: Boolean = false,
    cortar: Boolean = false,
    teste: (url: String, pedidos: AtomicInteger) -> Unit,
) {
    val pedidos = AtomicInteger()
    val servidor = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    // Uma linha por pedido: um pedido retido não pode impedir outro de chegar.
    val linhas = Executors.newCachedThreadPool()
    servidor.executor = linhas
    servidor.createContext("/") { t ->
        val desde = t.requestHeaders.getFirst("Range")?.takeUnless { ignorarRange }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
        val total = APK_FALSO.size
        when {
            desde == null -> {
                t.sendResponseHeaders(200, total.toLong())
                t.responseBody.use { corpo ->
                    corpo.write(APK_FALSO, 0, 8)
                    corpo.flush()
                    pedidos.incrementAndGet()
                    if (cortar) throw IOException("ligação cortada a meio")
                    segurar?.await(5, TimeUnit.SECONDS)
                    corpo.write(APK_FALSO, 8, 8)
                }
            }
            desde >= total -> {
                t.responseHeaders.add("Content-Range", "bytes */$total")
                t.sendResponseHeaders(416, -1)
                t.close()
            }
            else -> {
                t.responseHeaders.add("Content-Range", "bytes $desde-${total - 1}/$total")
                t.sendResponseHeaders(206, (total - desde).toLong())
                pedidos.incrementAndGet()
                t.responseBody.use { it.write(APK_FALSO, desde, total - desde) }
            }
        }
    }
    servidor.start()
    try {
        teste("http://127.0.0.1:${servidor.address.port}/a.apk", pedidos)
    } finally {
        servidor.stop(0)
        linhas.shutdownNow()
    }
}
