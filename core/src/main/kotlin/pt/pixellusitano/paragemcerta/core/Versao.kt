package pt.pixellusitano.paragemcerta.core

private val TAG_VERSAO = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

/** "v1.2.3" → 10203 (major*10000 + minor*100 + patch); null se a tag não for uma versão. */
fun versionCodeFromTag(tag: String): Int? {
    val (major, minor, patch) = TAG_VERSAO.matchEntire(tag.trim())?.destructured ?: return null
    return major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

fun isNewer(currentVersionCode: Int, tag: String): Boolean =
    (versionCodeFromTag(tag) ?: return false) > currentVersionCode
