package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Configuracao
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File

private val JSON = Json { ignoreUnknownKeys = true }

/** O `config.json` do servidor: começa com o último que chegou ou, na primeira vez (e sem rede), com o que vem dentro da app. */
class Configuracoes(contexto: Context, private val servidor: ServidorParagemCerta) {
    private val guardada = File(contexto.filesDir, "config.json")
    private val _atual = MutableStateFlow(
        ler(guardada.takeIf { it.isFile }?.readText()) ?: JSON.decodeFromString(contexto.assets.open("config.json").use { it.readBytes().decodeToString() }),
    )
    val atual: StateFlow<Configuracao> = _atual

    /** Pede a nova e guarda-a; sem rede ou com uma resposta estranha fica a anterior. */
    suspend fun aoAbrir() {
        val nova = (servidor.config() as? Resultado.Ok)?.valor ?: return
        withContext(Dispatchers.IO) { guardada.writeText(JSON.encodeToString(nova)) }
        _atual.value = nova
    }

    // Um ficheiro estragado (disco cheio a meio da escrita) é o mesmo que não ter nenhum.
    private fun ler(texto: String?): Configuracao? = texto?.let { runCatching { JSON.decodeFromString<Configuracao>(it) }.getOrNull() }
}
