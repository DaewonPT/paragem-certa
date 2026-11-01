package pt.pixellusitano.paragemcerta.atualizar

import android.content.Context

/** Na variante da Play Store as atualizações chegam pela loja: aqui não se faz nada. */
object Atualizacoes {
    val disponivel: Boolean = false

    suspend fun verificar(contexto: Context): NovaVersao? = null

    suspend fun instalar(contexto: Context, nova: NovaVersao, semPerguntar: Boolean = false) = Unit

    fun agendar(contexto: Context) = Unit

    fun atualizarSozinha(contexto: Context): Boolean = false

    fun definirAtualizarSozinha(contexto: Context, valor: Boolean) = Unit

    fun usarDadosMoveis(contexto: Context): Boolean = false

    fun definirUsarDadosMoveis(contexto: Context, valor: Boolean) = Unit

    fun novidadesPorMostrar(contexto: Context): NovaVersao? = null

    fun marcarNovidadesVistas(contexto: Context) = Unit

    fun pendente(contexto: Context): NovaVersao? = null

    fun limparPendente(contexto: Context) = Unit

    fun pedirNotificacoes(contexto: Context): Boolean = false

    fun marcarNotificacoesPedidas(contexto: Context) = Unit
}
