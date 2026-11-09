package pt.pixellusitano.paragemcerta.widget

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import java.time.Instant

enum class EstadoLigacao { OK, SEM_TRANSPORTES, FALHOU }

/**
 * A última ligação calculada para a Casa ou o Trabalho, só com o que o widget mostra. [cor] em ARGB; [partida] e [em] (quando se
 * calculou) em ms desde 1970. Fora de [EstadoLigacao.OK] só contam o [estado] e o [em].
 */
@Serializable
data class LigacaoGuardada(
    val linha: String?, val cor: Int, val modo: Modo, val soAPe: Boolean, val partida: Long, val duracaoMin: Int, val em: Long,
    val estado: EstadoLigacao,
)

private val JSON = Json { ignoreUnknownKeys = true }

/**
 * A paragem de cada widget e o último ↻, por id do widget; e, para o "Casa e Trabalho", as ligações, a última posição e o último ↻.
 * O ficheiro fica fora das cópias de segurança (tem a posição).
 */
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

    /** [tipo]: "CASA" ou "TRABALHO". */
    fun guardarLigacao(tipo: String, l: LigacaoGuardada) = preferencias.edit().putString("ligacao_$tipo", JSON.encodeToString(l)).apply()

    /** Uma ligação que já não se lê (de outra versão) conta como não haver. */
    fun ligacao(tipo: String): LigacaoGuardada? =
        preferencias.getString("ligacao_$tipo", null)?.let { runCatching { JSON.decodeFromString<LigacaoGuardada>(it) }.getOrNull() }

    fun guardarPosicao(p: PosicaoComHora) = preferencias.edit().putString("posicao", JSON.encodeToString(p)).apply()

    fun posicao(): PosicaoComHora? =
        preferencias.getString("posicao", null)?.let { runCatching { JSON.decodeFromString<PosicaoComHora>(it) }.getOrNull() }

    /** Se as ligações guardadas se calcularam a partir da posição guardada (a atual não veio a tempo). */
    fun marcarDaPosicaoGuardada(sim: Boolean) = preferencias.edit().putBoolean("da_posicao_guardada", sim).apply()

    fun daPosicaoGuardada(): Boolean = preferencias.getBoolean("da_posicao_guardada", false)

    /** Uma só para todos os widgets "Casa e Trabalho": dois widgets postos não podem pedir o dobro. */
    fun ultimaTentativaCasaTrabalho(): Instant? =
        if (preferencias.contains(TENTATIVA_CASA_TRABALHO)) Instant.ofEpochMilli(preferencias.getLong(TENTATIVA_CASA_TRABALHO, 0)) else null

    fun marcarTentativaCasaTrabalho(agora: Instant) = preferencias.edit().putLong(TENTATIVA_CASA_TRABALHO, agora.toEpochMilli()).apply()

    private companion object {
        const val TENTATIVA_CASA_TRABALHO = "tentativa_casa_trabalho"
    }
}
