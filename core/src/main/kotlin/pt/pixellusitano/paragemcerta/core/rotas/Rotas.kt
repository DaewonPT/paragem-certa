package pt.pixellusitano.paragemcerta.core.rotas

import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import java.time.Duration

/** Polilinha codificada (formato público da Google); o Transitous usa precisão 6. */
fun descodificarPolilinha(texto: String, precisao: Int): List<Pair<Double, Double>> {
    val fator = Math.pow(10.0, precisao.toDouble())
    val pontos = mutableListOf<Pair<Double, Double>>()
    var i = 0
    var lat = 0L
    var lon = 0L
    fun proximo(): Long {
        var resultado = 0L
        var deslocamento = 0
        while (true) {
            val b = texto[i++].code - 63
            resultado = resultado or ((b and 0x1f).toLong() shl deslocamento)
            deslocamento += 5
            if (b < 0x20) break
        }
        return if (resultado and 1L != 0L) (resultado shr 1).inv() else resultado shr 1
    }
    while (i < texto.length) {
        lat += proximo()
        lon += proximo()
        pontos += lat / fator to lon / fator
    }
    return pontos
}

private val FOLGA_MINIMA: Duration = Duration.ofMinutes(3)

/** Folga mais curta entre sair de um veículo e partir no seguinte, já descontado o caminho a pé entre os dois. */
private fun folgaMinima(it: Itinerario): Duration? {
    var menor: Duration? = null
    var chegadaAnterior: java.time.Instant? = null
    var aPeEntre = Duration.ZERO
    for (e in it.etapas) {
        if (e.aPe) {
            aPeEntre += Duration.between(e.partida, e.chegada)
            continue
        }
        chegadaAnterior?.let { anterior ->
            val folga = Duration.between(anterior, e.partida) - aPeEntre
            if (menor == null || folga < menor) menor = folga
        }
        chegadaAnterior = e.chegada
        aPeEntre = Duration.ZERO
    }
    return menor
}

fun etiquetas(lista: List<Itinerario>): List<Set<Etiqueta>> {
    if (lista.isEmpty()) return emptyList()
    val maisRapida = lista.minOf { it.duracao }
    val aPeDaMaisRapida = lista.first { it.duracao == maisRapida }.tempoAPe
    val menosAPe = lista.minOf { it.tempoAPe }
    return lista.map { it ->
        buildSet {
            if (it.duracao == maisRapida) add(Etiqueta.MAIS_RAPIDA)
            if (it.tempoAPe == menosAPe && menosAPe < aPeDaMaisRapida) add(Etiqueta.MENOS_A_PE)
            if (it.transbordos == 0 && it.etapas.any { e -> !e.aPe }) add(Etiqueta.DIRETA)
            folgaMinima(it)?.let { f -> if (f < FOLGA_MINIMA) add(Etiqueta.TRANSBORDO_APERTADO) }
        }
    }
}
