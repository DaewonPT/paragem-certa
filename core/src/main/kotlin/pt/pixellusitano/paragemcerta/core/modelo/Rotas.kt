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

/** zona = a freguesia e o concelho, só para mostrar (distingue várias ruas com o mesmo nome). */
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

/** A ordem das etiquetas é a de mostrar no cartão. */
enum class Etiqueta { MAIS_RAPIDA, CHEGADA_MAIS_CEDO, MENOS_A_PE, DIRETA, CAMINHADA_LONGA, TRANSBORDO_APERTADO }

/** Como se ordenam as rotas na lista (o Transitous não ordena: faz-se na app). */
enum class Ordem { MAIS_RAPIDA, CHEGADA_MAIS_CEDO, MENOS_A_PE, MENOS_TRANSBORDOS }

/** Ritmo a pé em metros por segundo, para o `pedestrianSpeed` do Transitous. */
enum class Ritmo(val metrosPorSegundo: Double) { LENTO(0.9), NORMAL(1.2), RAPIDO(1.6) }

/**
 * modos vazio = todos os meios de transporte. maxAPeMin = o tempo máximo a pé no início e no fim (5, 10, 15 ou 20 min);
 * "menos a pé" nunca deixa passar dos 10 min.
 */
data class Filtros(
    val menosAPe: Boolean = false,
    val menosTransbordos: Boolean = false,
    val acessivel: Boolean = false,
    val modos: Set<Modo> = emptySet(),
    val maxAPeMin: Int = 15,
    val ritmo: Ritmo = Ritmo.NORMAL,
    val ordem: Ordem = Ordem.MAIS_RAPIDA,
)

/** Uma página de rotas; anterior e seguinte são os cursores de "mais cedo" e "mais tarde" (null = não há). */
data class PlanoRotas(val itinerarios: List<Itinerario>, val anterior: String?, val seguinte: String?)
