package pt.pixellusitano.paragemcerta.core

import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta

fun fixture(nome: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$nome")) { "fixture $nome em falta" }.readText()

data class PedidoEnviado(val metodo: String, val url: String, val corpo: String?, val cabecalhos: Map<String, String>)

class PedidoEmBytes(val metodo: String, val url: String, val corpo: ByteArray, val tipo: String, val cabecalhos: Map<String, String>)

/** Http falso: devolve as respostas pela ordem e regista os URL pedidos (e, em [enviados] e [enviadosEmBytes], os pedidos com corpo). */
class HttpFalso(private vararg val respostas: () -> Resposta) : Http {
    val pedidos = mutableListOf<String>()
    val enviados = mutableListOf<PedidoEnviado>()
    val enviadosEmBytes = mutableListOf<PedidoEmBytes>()
    override suspend fun get(url: String): Resposta {
        pedidos += url
        return respostas[minOf(pedidos.size - 1, respostas.size - 1)]()
    }

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        enviados += PedidoEnviado(metodo, url, corpo, cabecalhos)
        return get(url)
    }

    override suspend fun enviarBytes(metodo: String, url: String, corpo: ByteArray, tipo: String, cabecalhos: Map<String, String>): Resposta {
        enviadosEmBytes += PedidoEmBytes(metodo, url, corpo, tipo, cabecalhos)
        return get(url)
    }
}
