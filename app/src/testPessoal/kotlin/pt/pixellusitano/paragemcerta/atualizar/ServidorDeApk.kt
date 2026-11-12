package pt.pixellusitano.paragemcerta.atualizar

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servidor HTTP local do JDK que serve um APK falso de 16 bytes; o `pedidos` dado ao [teste] conta os descarregamentos já começados.
 * Com [segurar], manda metade e espera que o teste o largue (5 s no máximo) antes de mandar o resto.
 */
internal fun comApkNoServidor(segurar: CountDownLatch? = null, teste: (url: String, pedidos: AtomicInteger) -> Unit) {
    val pedidos = AtomicInteger()
    val servidor = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    // Uma linha por pedido: um pedido retido não pode impedir outro de chegar.
    val linhas = Executors.newCachedThreadPool()
    servidor.executor = linhas
    servidor.createContext("/") { t ->
        t.sendResponseHeaders(200, 16)
        t.responseBody.use { corpo ->
            corpo.write(ByteArray(8))
            corpo.flush()
            pedidos.incrementAndGet()
            segurar?.await(5, TimeUnit.SECONDS)
            corpo.write(ByteArray(8))
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
