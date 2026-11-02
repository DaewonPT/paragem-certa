package pt.pixellusitano.paragemcerta.core.rotas

import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import java.time.Duration
import java.time.Instant

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

private val LIMITE_CAMINHADA_LONGA: Duration = Duration.ofMinutes(12)

/** Cada conjunto sai pela ordem de mostrar (a do enum): o buildSet mantém a ordem de inserção. */
fun etiquetas(lista: List<Itinerario>): List<Set<Etiqueta>> {
    if (lista.isEmpty()) return emptyList()
    val maisRapida = lista.minOf { it.duracao }
    val aPeDaMaisRapida = lista.first { it.duracao == maisRapida }.tempoAPe
    val menosAPe = lista.minOf { it.tempoAPe }
    val primeiraChegada = lista.minOf { it.chegada }
    return lista.map { it ->
        buildSet {
            if (it.duracao == maisRapida) add(Etiqueta.MAIS_RAPIDA)
            // Com uma só rota não há outra que chegue depois.
            if (lista.size > 1 && it.chegada == primeiraChegada) add(Etiqueta.CHEGADA_MAIS_CEDO)
            if (it.tempoAPe == menosAPe && menosAPe < aPeDaMaisRapida) add(Etiqueta.MENOS_A_PE)
            if (it.transbordos == 0 && it.etapas.any { e -> !e.aPe }) add(Etiqueta.DIRETA)
            if (it.tempoAPe >= LIMITE_CAMINHADA_LONGA) add(Etiqueta.CAMINHADA_LONGA)
            folgaMinima(it)?.let { f -> if (f < FOLGA_MINIMA) add(Etiqueta.TRANSBORDO_APERTADO) }
        }
    }
}

/** Estável: o que empata na [ordem] fica pela chegada e, se ainda empata, como veio. */
fun ordenar(lista: List<Itinerario>, ordem: Ordem): List<Itinerario> {
    val por = when (ordem) {
        Ordem.MAIS_RAPIDA -> compareBy<Itinerario> { it.duracao }
        Ordem.CHEGADA_MAIS_CEDO -> compareBy<Itinerario> { it.chegada }
        Ordem.MENOS_A_PE -> compareBy<Itinerario> { it.tempoAPe }
        Ordem.MENOS_TRANSBORDOS -> compareBy<Itinerario> { it.transbordos }
    }
    return lista.sortedWith(por.thenBy { it.chegada })
}

/**
 * As [n] próximas partidas da linha da [etapa] (em veículo) a partir das [chegadas] da paragem dela.
 * Só as que ainda não passaram e não são mais de 60 s antes da partida planeada (a que o utilizador viu no cartão).
 * O Transitous dá o destino de cada partida e a CM não: só as do Transitous se separam por sentido, e só se a etapa o tem.
 */
fun proximasPartidas(chegadas: List<Chegada>, etapa: Etapa, agora: Instant, n: Int = 3): List<Chegada> {
    val desde = etapa.partidaProgramada.minusSeconds(60)
    return chegadas
        .filter { c ->
            c.linhaNome.equals(etapa.linhaNome, ignoreCase = true) &&
                (c.fonte != Fonte.TRANSITOUS || etapa.destino.isNullOrBlank() || c.destino.equals(etapa.destino, ignoreCase = true)) &&
                aindaPorChegar(c, agora) && !c.programada.isBefore(desde)
        }
        .sortedBy { it.prevista ?: it.programada }
        .take(n)
}
