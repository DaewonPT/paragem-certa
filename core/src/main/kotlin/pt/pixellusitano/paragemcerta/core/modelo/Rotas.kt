@file:UseSerializers(InstanteSerializer::class)

package pt.pixellusitano.paragemcerta.core.modelo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant

/** Um Instant como texto ISO-8601, para o itinerário poder ir para o ficheiro da viagem. */
object InstanteSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

enum class TipoLugar { MORADA, LOCAL, PARAGEM, POSICAO }

/** zona = o concelho, só para mostrar (distingue várias ruas com o mesmo nome). */
@Serializable
data class Lugar(val nome: String, val lat: Double, val lon: Double, val tipo: TipoLugar, val zona: String? = null)

/** Uma paragem do percurso de uma etapa em veículo; hora = partida dessa paragem (ou a chegada, na última). */
@Serializable
data class ParagemEtapa(val id: String?, val nome: String, val lat: Double, val lon: Double, val hora: Instant)

/**
 * Uma parte da viagem: a pé (aPe) ou num veículo (modo, linha, destino); distancia em metros, quando a fonte a dá.
 * idViagem e paragens (origem, intermédias e destino) só existem nas etapas em veículo.
 */
@Serializable
data class Etapa(
    val aPe: Boolean, val modo: Modo, val linhaNome: String?, val linhaId: String?, val destino: String?,
    val de: Lugar, val para: Lugar, val partida: Instant, val chegada: Instant, val partidaProgramada: Instant,
    val aoVivo: Boolean, val paragensIntermedias: Int, val desenho: List<Pair<Double, Double>>, val cor: String?,
    val distancia: Double? = null,
    val idViagem: String? = null,
    val paragens: List<ParagemEtapa> = emptyList(),
)

@Serializable
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
