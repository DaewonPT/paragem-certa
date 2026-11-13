package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.core.rede.Http
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

/** ACarregar = ainda não há nada (nem guardado); Falhou = não há e o pedido falhou. */
sealed interface EstadoFicheiro<out T> {
    data object ACarregar : EstadoFicheiro<Nothing>
    data class Pronto<out T>(val c: T) : EstadoFicheiro<T>
    data object Falhou : EstadoFicheiro<Nothing>
}

/**
 * Um ficheiro de `/dados/` do servidor (o `linhas.json`, o `fotos-de-rua.json`), guardado em [ficheiros] com o ETag. Pede-se ao abrir a
 * app, no máximo de 24 em 24 h; o guardado conta logo como Pronto, por velho que seja: um ficheiro antigo é melhor do que nenhum.
 * [ler] converte o texto e lança se ele não servir.
 */
class FicheiroDoServidor<T>(
    ficheiros: File,
    nome: String,
    private val http: Http,
    private val url: String,
    private val relogio: () -> Instant = Instant::now,
    private val ler: (String) -> T,
) {
    private val ficheiro = File(ficheiros, nome)
    // "<segundos do último pedido que respondeu>\n<ETag>": o ETag é o do ficheiro que está ao lado.
    private val marca = File(ficheiros, nome.substringBeforeLast('.') + ".marca")
    private val _estado = MutableStateFlow<EstadoFicheiro<T>>(EstadoFicheiro.ACarregar)
    val estado: StateFlow<EstadoFicheiro<T>> = _estado
    val atual: T? get() = (_estado.value as? EstadoFicheiro.Pronto)?.c

    /** Sem rede não gasta o dia (a abertura seguinte tenta outra vez); uma resposta estragada gasta, para não descarregar em cada abertura. */
    suspend fun aoAbrir() {
        // Lido aqui e não ao construir: são centenas de KB para converter, fora da linha do ecrã.
        if (atual == null) lerGuardado()?.let { _estado.value = EstadoFicheiro.Pronto(it) }
        val (pedidoEm, etag) = lerMarca()
        val guardado = atual
        if (guardado != null && Duration.between(pedidoEm, relogio()) < IDADE_MAXIMA) return
        val r = try {
            http.getCom(url, if (guardado != null && etag != null) mapOf("If-None-Match" to etag) else emptyMap())
        } catch (e: IOException) {
            null
        }
        when {
            r == null -> Unit
            r.codigo == 304 && guardado != null -> escreverMarca(etag)
            r.codigo in 200..299 -> {
                val novo = withContext(Dispatchers.Default) { lerOuNada(r.corpo) }
                if (novo == null) {
                    escreverMarca(etag)
                } else {
                    // Primeiro o ficheiro, depois o ETag: a meio, o pior é pedir outra vez o mesmo.
                    withContext(Dispatchers.IO) { escreverDeUmaVez(ficheiro, r.corpo) }
                    escreverMarca(r.etag)
                    _estado.value = EstadoFicheiro.Pronto(novo)
                }
            }
        }
        if (atual == null) _estado.value = EstadoFicheiro.Falhou
    }

    private suspend fun lerGuardado(): T? = withContext(Dispatchers.IO) {
        ficheiro.takeIf { it.isFile }?.readText()?.let(::lerOuNada)
    }

    // Um ficheiro estragado é o mesmo que não ter nenhum.
    private fun lerOuNada(texto: String): T? = try {
        ler(texto)
    } catch (e: RuntimeException) {
        null
    }

    private suspend fun lerMarca(): Pair<Instant, String?> = withContext(Dispatchers.IO) {
        val linhas = marca.takeIf { it.isFile }?.readLines().orEmpty()
        val em = linhas.getOrNull(0)?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: Instant.EPOCH
        em to linhas.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }

    private suspend fun escreverMarca(etag: String?) = withContext(Dispatchers.IO) {
        escreverDeUmaVez(marca, "${relogio().epochSecond}\n${etag.orEmpty()}")
    }

    private companion object {
        val IDADE_MAXIMA: Duration = Duration.ofHours(24)
    }
}

/** Escreve ao lado e troca de uma vez: a meio de uma escrita nunca fica um ficheiro cortado. */
internal fun escreverDeUmaVez(ficheiro: File, texto: String) {
    val novo = File(ficheiro.path + ".novo").apply { writeText(texto) }
    Files.move(novo.toPath(), ficheiro.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
