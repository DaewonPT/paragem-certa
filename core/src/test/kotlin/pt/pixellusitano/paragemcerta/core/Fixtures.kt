package pt.pixellusitano.paragemcerta.core

import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta

fun fixture(nome: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$nome")) { "fixture $nome em falta" }.readText()

/** Http falso: devolve as respostas pela ordem e regista os URL pedidos. */
class HttpFalso(private vararg val respostas: () -> Resposta) : Http {
    val pedidos = mutableListOf<String>()
    override suspend fun get(url: String): Resposta {
        pedidos += url
        return respostas[minOf(pedidos.size - 1, respostas.size - 1)]()
    }
}
