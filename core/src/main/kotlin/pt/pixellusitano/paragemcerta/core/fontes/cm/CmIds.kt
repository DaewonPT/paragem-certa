package pt.pixellusitano.paragemcerta.core.fontes.cm

import pt.pixellusitano.paragemcerta.core.fontes.transitous.PREFIXO_CM_TRANSITOUS
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade

private val PREFIXOS = Regex("""^(\[[^\]]*\])+""")

/** A API da Carris Metropolitana prefixa alguns ids com o código do operador: "[YA15B]3708" → "3708". */
fun cmIdLimpo(bruto: String): String = bruto.replace(PREFIXOS, "")

/** "[LA77N]1625_0" → "1625". */
fun cmLinhaDaRota(routeId: String): String = cmIdLimpo(routeId).substringBefore('_')

/** O sentido de um percurso da CM, pelo fim do id: "3708_0_1" → 0, "3708_0_2" → 1 (o direction_id do percurso); outro formato → null. */
fun sentidoDoPadraoCm(padrao: String): Int? = when (cmIdLimpo(padrao).substringAfterLast('_', "")) {
    "1" -> 0
    "2" -> 1
    else -> null
}

/** Paragem do Transitous → id da API da Carris Metropolitana: "pt-Carris-Metropolitana_320047" → "020047"; outro operador → null. */
fun cmParagemDeTransitous(id: String): String? {
    val t = id.removePrefix(PREFIXO_CM_TRANSITOUS).takeIf { it != id && it.isNotEmpty() } ?: return null
    return if (t[0] == '3') "0" + t.substring(1) else t
}

private val VIAGEM_TRANSITOUS = Regex("""^\d{8}_\d\d:\d\d_$PREFIXO_CM_TRANSITOUS""")

/** Viagem do Transitous → trip_id da Carris Metropolitana: "20261101_10:35_pt-Carris-Metropolitana_3708_0_1|1|0525" → "3708_0_1|1|0525". */
fun cmViagemDeTransitous(id: String): String? = VIAGEM_TRANSITOUS.find(id)?.let { id.substring(it.range.last + 1) }

fun gravidadeDe(effect: String?, severityLevel: String?): Gravidade = when (severityLevel) {
    "SEVERE" -> Gravidade.GRAVE
    "WARNING" -> Gravidade.AVISO
    "INFO" -> Gravidade.INFO
    else -> when (effect) {
        "NO_SERVICE" -> Gravidade.GRAVE
        "DETOUR", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS" -> Gravidade.AVISO
        else -> Gravidade.INFO
    }
}
