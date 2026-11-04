package pt.pixellusitano.paragemcerta.widget

import android.content.Context
import java.time.Instant

/** A paragem de cada widget e o último ↻, por id do widget. */
class DadosWidget(contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun escolher(id: Int, chave: String, nome: String) = preferencias.edit().putString("chave_$id", chave).putString("nome_$id", nome).apply()

    /** (chave da paragem, nome). */
    fun escolha(id: Int): Pair<String, String>? {
        val chave = preferencias.getString("chave_$id", null) ?: return null
        return chave to (preferencias.getString("nome_$id", null) ?: return null)
    }

    fun esquecer(id: Int) = preferencias.edit().remove("chave_$id").remove("nome_$id").remove("tentativa_$id").apply()

    fun esquecerTudo() = preferencias.edit().clear().apply()

    fun ultimaTentativa(id: Int): Instant? =
        if (preferencias.contains("tentativa_$id")) Instant.ofEpochMilli(preferencias.getLong("tentativa_$id", 0)) else null

    fun marcarTentativa(id: Int, agora: Instant) = preferencias.edit().putLong("tentativa_$id", agora.toEpochMilli()).apply()
}
