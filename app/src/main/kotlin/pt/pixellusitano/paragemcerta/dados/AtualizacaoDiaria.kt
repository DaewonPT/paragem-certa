package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import java.time.Duration
import java.time.Instant

/**
 * Paragens e linhas da Carris Metropolitana: renovam-se ao abrir a app, se as guardadas tiverem mais de 24 h. Corre enquanto a
 * app está aberta (quem chama cancela ao sair); com ela fechada não corre nada.
 */
object AtualizacaoDiaria {
    private const val PREFS = "rede"
    private const val ULTIMA = "ultima_atualizacao"
    private val IDADE_MAXIMA = Duration.ofHours(24)

    /** Devolve se atualizou. Sem rede (ou se falhar a meio) não gasta o dia: a abertura seguinte tenta outra vez. */
    suspend fun aoAbrir(contexto: Context, repositorio: Repositorio, agora: Instant = Instant.now()): Boolean {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultima = Instant.ofEpochSecond(prefs.getLong(ULTIMA, 0))
        // Sem paragens na base (primeira utilização) atualiza sempre.
        if (Duration.between(ultima, agora) < IDADE_MAXIMA && !repositorio.redeEmFalta()) return false
        if (!repositorio.atualizarRede()) return false
        prefs.edit().putLong(ULTIMA, agora.epochSecond).apply()
        return true
    }
}
