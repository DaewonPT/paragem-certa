package pt.pixellusitano.paragemcerta.core.modelo

import java.time.Duration
import java.time.Instant

enum class TipoLugar { MORADA, LOCAL, PARAGEM, POSICAO }

data class Lugar(val nome: String, val lat: Double, val lon: Double, val tipo: TipoLugar)

/** Uma parte da viagem: a pé (aPe) ou num veículo (modo, linha, destino). */
data class Etapa(
    val aPe: Boolean, val modo: Modo, val linhaNome: String?, val linhaId: String?, val destino: String?,
    val de: Lugar, val para: Lugar, val partida: Instant, val chegada: Instant, val partidaProgramada: Instant,
    val aoVivo: Boolean, val paragensIntermedias: Int, val desenho: List<Pair<Double, Double>>, val cor: String?,
)

data class Itinerario(val etapas: List<Etapa>, val partida: Instant, val chegada: Instant, val transbordos: Int) {
    val duracao: Duration get() = Duration.between(partida, chegada)
    val tempoAPe: Duration get() = etapas.filter { it.aPe }.fold(Duration.ZERO) { a, e -> a + Duration.between(e.partida, e.chegada) }
}

enum class Etiqueta { MAIS_RAPIDA, MENOS_A_PE, DIRETA, TRANSBORDO_APERTADO }

/** modos vazio = todos os meios de transporte. */
data class Filtros(
    val menosAPe: Boolean = false,
    val menosTransbordos: Boolean = false,
    val acessivel: Boolean = false,
    val modos: Set<Modo> = emptySet(),
)
