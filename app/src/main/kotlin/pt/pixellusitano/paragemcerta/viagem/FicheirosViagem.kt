package pt.pixellusitano.paragemcerta.viagem

import android.app.ActivityManager
import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Os dois ficheiros por onde a app e o serviço da viagem (processo :trip) se entendem: o plano, que a app escreve
 * ao começar, e o estado, que o serviço escreve a cada passo. Sobrevivem a qualquer um dos processos morrer.
 */
object FicheirosViagem {
    /** O aviso, só para a nossa app, de que o estado mudou; vai sem dados: quem o recebe volta a ler o ficheiro. */
    const val ACAO_ESTADO = "pt.pixellusitano.paragemcerta.VIAGEM"

    private const val PLANO = "trip-plan.json"
    private const val ESTADO = "trip-state.json"
    private val GUARDA = Duration.ofMinutes(30)
    private val json = Json { ignoreUnknownKeys = true }

    private fun ficheiro(c: Context, nome: String) = File(File(c.filesDir, "viagem"), nome)

    fun gravarPlano(c: Context, p: PlanoViagem) = gravar(ficheiro(c, PLANO), json.encodeToString(p))

    /** Falhar a escrever (disco cheio) não pode deitar abaixo o serviço: a próxima escrita, uns segundos depois, repete. */
    fun gravarEstado(c: Context, e: EstadoViagem) {
        try {
            gravar(ficheiro(c, ESTADO), json.encodeToString(e))
        } catch (_: IOException) {
        }
    }

    fun lerPlano(c: Context): PlanoViagem? = ler(ficheiro(c, PLANO))

    /** Um estado sem plano, ou de mais de chegada + 30 min, não serve para retomar: apaga os dois ficheiros e devolve null. */
    fun lerEstado(c: Context, agora: Instant = Instant.now()): EstadoViagem? {
        val estado = ler<EstadoViagem>(ficheiro(c, ESTADO)) ?: return null
        val plano = lerPlano(c)
        if (plano == null || agora > plano.itinerario.chegada + GUARDA) {
            apagar(c)
            return null
        }
        return estado
    }

    /** Só os dois ficheiros: o gps.csv das versões de depuração fica para o autor o copiar. */
    fun apagar(c: Context) {
        AtomicFile(ficheiro(c, PLANO)).delete()
        AtomicFile(ficheiro(c, ESTADO)).delete()
    }

    /** O serviço está vivo se a app tiver um processo cujo nome acaba em ":trip". */
    fun emCurso(c: Context): Boolean =
        c.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any { it.processName.endsWith(":trip") }

    private fun gravar(f: File, texto: String) {
        f.parentFile?.mkdirs()
        val a = AtomicFile(f)
        val saida = a.startWrite()
        try {
            saida.write(texto.encodeToByteArray())
            a.finishWrite(saida)
        } catch (e: IOException) {
            a.failWrite(saida)
            throw e
        }
    }

    /** Um ficheiro que não existe ou está estragado (processo morto a meio, versão antiga) é o mesmo que não haver nada. */
    private inline fun <reified T> ler(f: File): T? = try {
        json.decodeFromString<T>(AtomicFile(f).readFully().decodeToString())
    } catch (_: Exception) {
        null
    }
}
