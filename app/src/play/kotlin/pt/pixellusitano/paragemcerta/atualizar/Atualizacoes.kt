package pt.pixellusitano.paragemcerta.atualizar

import android.content.Context

/** Na variante da Play Store as atualizações chegam pela loja: aqui não se faz nada. */
object Atualizacoes {
    suspend fun verificar(contexto: Context): NovaVersao? = null

    suspend fun instalar(contexto: Context, nova: NovaVersao) = Unit
}
