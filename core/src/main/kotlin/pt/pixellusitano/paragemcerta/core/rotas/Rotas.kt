package pt.pixellusitano.paragemcerta.core.rotas

import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

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

/**
 * A paragem da Carris Metropolitana onde se entra, nas etapas de autocarro da CM com linha (as únicas com posição ao vivo
 * por trip_id); nas outras, null.
 */
fun Etapa.embarqueCm(): String? =
    if (aPe || linhaNome == null) null else paragens.firstOrNull()?.id?.let(::cmParagemDeTransitous)

private const val TOLERANCIA_ETAPA_S = 90L

/**
 * A chegada do autocarro da [etapa]: da mesma linha, com a hora programada até 90 s da do horário, a mais perto (null se
 * não houver). As [chegadas] são as da paragem de embarque da etapa.
 */
fun chegadaDaEtapa(chegadas: List<Chegada>, etapa: Etapa): Chegada? =
    chegadas
        .filter { it.linhaNome == etapa.linhaNome }
        .map { it to abs(Duration.between(it.programada, etapa.partidaProgramada).seconds) }
        .filter { it.second <= TOLERANCIA_ETAPA_S }
        .minByOrNull { it.second }?.first

private val FOLGA_A_PE: Duration = Duration.ofSeconds(30)

private fun Etapa.mais(d: Duration) =
    copy(partida = partida + d, chegada = chegada + d, paragens = paragens.map { it.copy(hora = it.hora + d) })

/**
 * O [itinerario] com o atraso ao vivo da 1.ª etapa em veículo da CM, tirado das [chegadas] da paragem de embarque. Desloca
 * a ida a pé inicial, a etapa e, até à etapa em veículo seguinte, o resto; a partidaProgramada fica, porque o modo viagem
 * casa o autocarro por ela. null se se perde o transbordo ou já não se chega a tempo à paragem; o mesmo itinerário se não
 * há chegada da etapa com tempo real.
 */
fun corrigirComTempoReal(itinerario: Itinerario, chegadas: List<Chegada>, agora: Instant): Itinerario? {
    val etapas = itinerario.etapas
    val e = etapas.indexOfFirst { !it.aPe }
    if (e < 0 || etapas[e].embarqueCm() == null) return itinerario
    val chegada = chegadaDaEtapa(chegadas, etapas[e]) ?: return itinerario
    val prevista = chegada.prevista ?: return itinerario
    // Com o tempo real da CM já no Transitous (aoVivo), a etapa traz o atraso: somá-lo outra vez contava-o a dobrar.
    val d = if (etapas[e].aoVivo) Duration.ZERO else Duration.between(chegada.programada, prevista)
    val f =(e + 1 until etapas.size).firstOrNull { !etapas[it].aPe }
    val novas = etapas.mapIndexed { i, x ->
        when {
            i == e -> x.mais(d).copy(aoVivo = true)
            i < (f ?: etapas.size) -> x.mais(d)
            else -> x
        }
    }
    if (f != null && novas[f - 1].chegada > etapas[f].partida) return null
    val aPeAntes = etapas.take(e).fold(Duration.ZERO) { a, x -> a + Duration.between(x.partida, x.chegada) }
    if (novas[e].partida < agora + aPeAntes - FOLGA_A_PE) return null
    return itinerario.copy(etapas = novas, partida = itinerario.partida + d, chegada = if (f == null) itinerario.chegada + d else itinerario.chegada)
}
