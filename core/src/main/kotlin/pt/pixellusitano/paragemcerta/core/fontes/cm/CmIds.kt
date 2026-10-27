package pt.pixellusitano.paragemcerta.core.fontes.cm

import pt.pixellusitano.paragemcerta.core.modelo.Gravidade

private val PREFIXOS = Regex("""^(\[[^\]]*\])+""")

/** A API da Carris Metropolitana prefixa alguns ids com o código do operador: "[YA15B]3708" → "3708". */
fun cmIdLimpo(bruto: String): String = bruto.replace(PREFIXOS, "")

/** "[LA77N]1625_0" → "1625". */
fun cmLinhaDaRota(routeId: String): String = cmIdLimpo(routeId).substringBefore('_')

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
