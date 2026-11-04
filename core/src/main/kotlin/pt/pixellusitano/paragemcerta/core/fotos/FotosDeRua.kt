package pt.pixellusitano.paragemcerta.core.fotos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Uma foto de rua do Panoramax para uma paragem, como o `fotos-de-rua.json` a traz: [licenca] já escrita para mostrar
 * ("CC BY-SA 4.0") e [data] da captura, AAAA-MM-DD.
 */
@Serializable data class FotoDeRua(val id: String, val miniatura: String, val autor: String, val licenca: String, val data: String)

@Serializable private class FotosDeRuaJson(val versao: Int, val fotos: Map<String, JsonElement> = emptyMap())

private val JSON = Json { ignoreUnknownKeys = true }
private val DATA = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * O `fotos-de-rua.json` gerado no GitHub, por chave de paragem ("CM:060419", "TRANSITOUS:pt-Carris_920"). Lança num ficheiro
 * estragado ou de uma versão que não conhece: o chamador fica com o antigo. Uma entrada estragada, sem autor ou licença, com a
 * data torta ou a miniatura sem https salta-se sem estragar as outras.
 */
fun lerFotosDeRua(texto: String): Map<String, FotoDeRua> {
    val ficheiro = JSON.decodeFromString<FotosDeRuaJson>(texto)
    require(ficheiro.versao == 1) { "versão ${ficheiro.versao} das fotos de rua" }
    return ficheiro.fotos.mapNotNull { (chave, entrada) ->
        val foto = try {
            JSON.decodeFromJsonElement<FotoDeRua>(entrada)
        } catch (_: IllegalArgumentException) {
            return@mapNotNull null
        }
        val boa = foto.id.isNotBlank() && foto.autor.isNotBlank() && foto.licenca.isNotBlank() &&
            foto.miniatura.startsWith("https://") && DATA.matches(foto.data)
        if (boa) chave to foto else null
    }.toMap()
}

/** O ano da captura, para o crédito. */
fun anoDaFoto(f: FotoDeRua): String = f.data.take(4)

/** A página da foto no Panoramax, onde estão o autor e a licença (a CC BY-SA 4.0 pede a ligação à fonte). */
fun paginaDaFoto(f: FotoDeRua): String = "https://api.panoramax.xyz/#focus=pic&pic=${f.id}"

/** A de rua só ganha se for mais recente que a da comunidade; num empate, ou sem data da comunidade, fica a da comunidade. */
fun ruaGanha(rua: FotoDeRua, dataComunidade: String?): Boolean = dataComunidade != null && rua.data > dataComunidade
