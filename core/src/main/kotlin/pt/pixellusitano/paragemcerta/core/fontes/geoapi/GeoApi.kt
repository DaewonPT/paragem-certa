package pt.pixellusitano.paragemcerta.core.fontes.geoapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.fontes.transitous.espacosUnicos
import pt.pixellusitano.paragemcerta.core.fontes.transitous.zonaDe
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir

private val JSON = Json { ignoreUnknownKeys = true }

private val CODIGO_POSTAL = Regex("^\\d{4}[- ]?\\d{3}$")
private val NOME_DE_CODIGO_POSTAL = Regex("^\\d{4}-\\d{3}")
private val MINUSCULAS = setOf("de", "do", "da", "dos", "das", "e")

/** "1400-038", "1400038" ou "1400 038" (sem contar espaços nas pontas): o que se escreve para um código postal completo. */
fun ehCodigoPostal(texto: String): Boolean = CODIGO_POSTAL.matches(texto.trim())

/** "1400 038" → "1400-038", o formato do pedido. Só para texto que passou em [ehCodigoPostal]. */
fun normalizarCodigoPostal(texto: String): String {
    val digitos = texto.filter { it in '0'..'9' }
    return "${digitos.take(4)}-${digitos.drop(4)}"
}

/** lat e lon ficam nulos quando a GeoAPI não tem o ponto (códigos de empresas, por exemplo). */
data class CodigoPostal(
    val cp: String, val rua: String?, val localidade: String, val concelho: String?, val lat: Double?, val lon: Double?,
)

/** O código postal como um sítio para ir; null sem coordenadas. O nome começa pelo código: [ehLugarDeCodigoPostal] reconhece-o por aí. */
fun CodigoPostal.paraLugar(): Lugar? {
    val la = lat ?: return null
    val lo = lon ?: return null
    return Lugar(listOfNotNull(cp, rua).joinToString(" · "), la, lo, TipoLugar.MORADA, zonaDe(localidade, concelho))
}

fun ehLugarDeCodigoPostal(l: Lugar): Boolean = NOME_DE_CODIGO_POSTAL.containsMatchIn(l.nome)

// As chaves da GeoAPI levam acentos ("Artéria"); as coordenadas vêm como [latitude, longitude].
@Serializable private class CodigoPostalJson(
    @SerialName("CP") val cp: String,
    @SerialName("Concelho") val concelho: String? = null,
    @SerialName("Localidade") val localidade: String = "",
    val partes: List<ParteJson> = emptyList(),
    val ruas: List<String> = emptyList(),
    val centroide: List<Double>? = null,
    val centroDeMassa: List<Double>? = null,
)

@Serializable private class ParteJson(@SerialName("Artéria") val arteria: String = "")

private fun List<Double>?.ponto() = this?.takeIf { it.size >= 2 }

/** Os nomes de rua do INE vêm todos em maiúsculas: "DOCA DO BOM SUCESSO" → "Doca do Bom Sucesso". */
private fun nomeDeRua(nome: String): String =
    nome.espacosUnicos().lowercase().split(' ').mapIndexed { i, p ->
        if (i > 0 && p in MINUSCULAS) p else p.replaceFirstChar(Char::uppercaseChar)
    }.joinToString(" ")

object GeoApiParser {
    /** Sem o código na resposta (um erro com código 200) rebenta com SerializationException, que o `pedir` converte em Falha. */
    fun codigoPostal(json: String): CodigoPostal {
        val j = JSON.decodeFromString<CodigoPostalJson>(json)
        val centro = j.centroide.ponto() ?: j.centroDeMassa.ponto()
        // Sem pontos a GeoAPI também não dá "ruas": a artéria dos CTT serve para procurar o sítio no Transitous.
        val rua = j.ruas.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::nomeDeRua)
            ?: j.partes.firstOrNull()?.arteria?.espacosUnicos()?.takeIf { it.isNotEmpty() }
        return CodigoPostal(j.cp, rua, j.localidade, j.concelho, centro?.get(0), centro?.get(1))
    }
}

class GeoApi(private val http: Http, private val base: String = "https://json.geoapi.pt") {
    /** 404 = o código não existe: Ok(null). Rede em baixo, 429 e 5xx são Falha (a pesquisa segue como texto normal). */
    suspend fun codigoPostal(cp: String): Resultado<CodigoPostal?> =
        when (val r = http.pedir("$base/cp/${normalizarCodigoPostal(cp)}", GeoApiParser::codigoPostal)) {
            is Resultado.Ok -> r
            is Resultado.Falha -> if (r.codigo == 404) Resultado.Ok(null) else r
        }
}
