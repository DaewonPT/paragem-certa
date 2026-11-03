package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.fontes.emel.EstacaoGira
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File
import java.time.Duration
import java.time.Instant

@Serializable private class EstacaoGuardada(val id: String, val nome: String, val lat: Double, val lon: Double)

@Serializable private class GiraGuardada(val obtidoEm: Long, val etag: String? = null, val estacoes: List<EstacaoGuardada>)

/**
 * As estações GIRA da EMEL, guardadas em [ficheiros] com o ETag. Pedem-se quando o mapa abre e as guardadas têm mais de 24 h;
 * uma falha (sem rede, ficheiro cortado) fica com as antigas e não gasta o dia.
 */
class EstacoesGira(ficheiros: File, private val gira: Gira, private val relogio: () -> Instant) {
    private val ficheiro = File(ficheiros, "gira.json")
    private val _atual = MutableStateFlow<List<EstacaoGira>>(emptyList())
    val atual: StateFlow<List<EstacaoGira>> = _atual
    private var guardado: GiraGuardada? = null
    private var lido = false

    suspend fun seVelhas() {
        if (!lido) {
            lido = true
            guardado = withContext(Dispatchers.IO) { ler() }?.also { mostrar(it) }
        }
        val antes = guardado
        if (antes != null && Duration.between(Instant.ofEpochSecond(antes.obtidoEm), relogio()) < IDADE_MAXIMA) return
        val leitura = (gira.estacoes(antes?.etag) as? Resultado.Ok)?.valor ?: return
        // Sem estações = 304: as guardadas continuam a valer.
        val estacoes = leitura.estacoes?.map { EstacaoGuardada(it.id, it.nome, it.lat, it.lon) } ?: antes?.estacoes ?: return
        val novo = GiraGuardada(relogio().epochSecond, leitura.etag, estacoes)
        withContext(Dispatchers.IO) { escreverDeUmaVez(ficheiro, JSON.encodeToString(novo)) }
        guardado = novo
        mostrar(novo)
    }

    private fun mostrar(g: GiraGuardada) {
        _atual.value = g.estacoes.map { EstacaoGira(it.id, it.nome, it.lat, it.lon) }
    }

    // Um ficheiro estragado é o mesmo que não ter nenhum.
    private fun ler(): GiraGuardada? =
        ficheiro.takeIf { it.isFile }?.let { runCatching { JSON.decodeFromString<GiraGuardada>(it.readText()) }.getOrNull() }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val IDADE_MAXIMA: Duration = Duration.ofHours(24)
    }
}
