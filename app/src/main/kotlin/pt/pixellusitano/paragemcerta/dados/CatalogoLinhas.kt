package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.core.linhas.Catalogo
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.rede.Http
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

/** ACarregar = ainda não há catálogo nenhum (nem guardado); Falhou = não há e o pedido falhou. */
sealed interface EstadoCatalogo {
    data object ACarregar : EstadoCatalogo
    data class Pronto(val c: Catalogo) : EstadoCatalogo
    data object Falhou : EstadoCatalogo
}

/**
 * O `linhas.json` do servidor (as linhas fora da Carris Metropolitana), guardado em [ficheiros] com o ETag. Pede-se ao abrir a
 * app, no máximo de 24 em 24 h; o guardado conta logo como Pronto, por velho que seja: um catálogo antigo é melhor do que nenhum.
 */
class CatalogoLinhas(
    ficheiros: File,
    private val http: Http,
    base: String,
    private val relogio: () -> Instant = Instant::now,
) {
    private val ficheiro = File(ficheiros, "linhas.json")
    // "<segundos do último pedido que respondeu>\n<ETag>": o ETag é o do ficheiro que está ao lado.
    private val marca = File(ficheiros, "linhas.marca")
    private val url = "$base/dados/linhas.json"
    private val _estado = MutableStateFlow<EstadoCatalogo>(EstadoCatalogo.ACarregar)
    val estado: StateFlow<EstadoCatalogo> = _estado
    val atual: Catalogo? get() = (_estado.value as? EstadoCatalogo.Pronto)?.c

    /** Sem rede não gasta o dia (a abertura seguinte tenta outra vez); uma resposta estragada gasta, para não descarregar em cada abertura. */
    suspend fun aoAbrir() {
        // Lido aqui e não ao construir: são centenas de KB para converter, fora da linha do ecrã.
        if (atual == null) lerGuardado()?.let { _estado.value = EstadoCatalogo.Pronto(it) }
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
                    _estado.value = EstadoCatalogo.Pronto(novo)
                }
            }
        }
        if (atual == null) _estado.value = EstadoCatalogo.Falhou
    }

    private suspend fun lerGuardado(): Catalogo? = withContext(Dispatchers.IO) {
        ficheiro.takeIf { it.isFile }?.readText()?.let(::lerOuNada)
    }

    // Um ficheiro estragado é o mesmo que não ter nenhum.
    private fun lerOuNada(texto: String): Catalogo? = try {
        lerCatalogo(texto)
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
