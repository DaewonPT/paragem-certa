package pt.pixellusitano.paragemcerta.core.fontes.cm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal class CmChegadaJson(
    @SerialName("line_id") val linha: String,
    val headsign: String = "",
    @SerialName("scheduled_arrival_unix") val programada: Long,
    @SerialName("estimated_arrival_unix") val prevista: Long? = null,
    @SerialName("observed_arrival_unix") val observada: Long? = null,
    @SerialName("trip_id") val viagem: String? = null,
    @SerialName("vehicle_id") val veiculo: String? = null,
)

@Serializable internal class CmParagemJson(
    val id: String,
    @SerialName("long_name") val nome: String? = null,
    val lat: Double,
    val lon: Double,
    @SerialName("wheelchair_boarding") val cadeiraDeRodas: Boolean? = null,
)

@Serializable internal class CmLinhaJson(
    val id: String,
    @SerialName("short_name") val nomeCurto: String? = null,
    @SerialName("long_name") val nomeLongo: String = "",
    val color: String? = null,
    @SerialName("text_color") val corTexto: String? = null,
    @SerialName("pattern_ids") val padroes: List<String> = emptyList(),
)

@Serializable internal class CmVeiculoJson(
    val id: String,
    @SerialName("line_id") val linha: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val bearing: Int? = null,
    val timestamp: Long? = null,
)

@Serializable internal class CmTraducoes(val translation: List<CmTraducao> = emptyList()) {
    fun pt() = (translation.firstOrNull { it.language == "pt" } ?: translation.firstOrNull())?.text.orEmpty()
}

@Serializable internal class CmTraducao(val language: String? = null, val text: String = "")

@Serializable internal class CmPeriodo(val start: Long? = null, val end: Long? = null)

@Serializable internal class CmEntidade(
    @SerialName("route_id") val rota: String? = null,
    @SerialName("stop_id") val paragem: String? = null,
)

@Serializable internal class CmImagem(@SerialName("localized_image") val imagens: List<CmImagemUrl> = emptyList())

@Serializable internal class CmImagemUrl(val url: String? = null)

@Serializable internal class CmAlertaJson(
    @SerialName("active_period") val periodos: List<CmPeriodo> = emptyList(),
    val effect: String? = null,
    @SerialName("severity_level") val gravidade: String? = null,
    @SerialName("header_text") val titulo: CmTraducoes = CmTraducoes(),
    @SerialName("description_text") val texto: CmTraducoes = CmTraducoes(),
    val image: CmImagem? = null,
    @SerialName("informed_entity") val entidades: List<CmEntidade> = emptyList(),
)

@Serializable internal class CmPontoPercurso(
    @SerialName("stop_id") val paragem: String,
    @SerialName("stop_sequence") val ordem: Int = 0,
)

@Serializable internal class CmPadraoJson(val path: List<CmPontoPercurso> = emptyList())
