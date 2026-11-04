package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pt.pixellusitano.paragemcerta.core.linhas.Catalogo
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.rede.Http
import java.io.File
import java.time.Instant

/** ACarregar = ainda não há catálogo nenhum (nem guardado); Falhou = não há e o pedido falhou. */
sealed interface EstadoCatalogo {
    data object ACarregar : EstadoCatalogo
    data class Pronto(val c: Catalogo) : EstadoCatalogo
    data object Falhou : EstadoCatalogo
}

/**
 * O `linhas.json` do servidor (as linhas fora da Carris Metropolitana): um [FicheiroDoServidor] com o catálogo como estado,
 * que é o que os ecrãs das linhas já leem.
 */
class CatalogoLinhas(
    ficheiros: File,
    http: Http,
    base: String,
    relogio: () -> Instant = Instant::now,
) {
    private val _estado = MutableStateFlow<EstadoCatalogo>(EstadoCatalogo.ACarregar)
    val estado: StateFlow<EstadoCatalogo> = _estado
    private val ficheiro = FicheiroDoServidor(
        ficheiros, "linhas.json", http, "$base/dados/linhas.json", relogio, ler = ::lerCatalogo,
        aoMudar = { _estado.value = it.comoCatalogo() },
    )
    val atual: Catalogo? get() = ficheiro.atual

    suspend fun aoAbrir() = ficheiro.aoAbrir()
}

private fun EstadoFicheiro<Catalogo>.comoCatalogo(): EstadoCatalogo = when (this) {
    EstadoFicheiro.ACarregar -> EstadoCatalogo.ACarregar
    is EstadoFicheiro.Pronto -> EstadoCatalogo.Pronto(c)
    EstadoFicheiro.Falhou -> EstadoCatalogo.Falhou
}
