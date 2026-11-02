package pt.pixellusitano.paragemcerta.ui.pesquisa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.Grupo
import pt.pixellusitano.paragemcerta.core.regras.agruparPorEstacao

sealed interface ResultadoPesquisa {
    data class DeLinha(val l: Linha) : ResultadoPesquisa
    data class DeLugar(val l: Lugar) : ResultadoPesquisa
    data class DeEstacao(val g: Grupo<Paragem>) : ResultadoPesquisa
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PesquisaViewModel(
    private val pesquisarParagens: suspend (String) -> List<Paragem>,
    private val linhasComecadasPor: suspend (String) -> List<Linha>,
    private val pesquisarLugares: suspend (String) -> List<Lugar>? = { emptyList() },
) : ViewModel() {
    private val _texto = MutableStateFlow("")
    val texto: StateFlow<String> = _texto.asStateFlow()

    private val _resultados = MutableStateFlow<List<ResultadoPesquisa>>(emptyList())
    val resultados: StateFlow<List<ResultadoPesquisa>> = _resultados.asStateFlow()

    /** Os sítios e moradas falharam (sem rede) para o texto atual; as linhas e as paragens aparecem na mesma. */
    private val _semRede = MutableStateFlow(false)
    val semRede: StateFlow<Boolean> = _semRede.asStateFlow()

    /** Há um texto a pesquisar (a espera de 300 ms e os pedidos): o ecrã não diz "sem resultados" enquanto isto durar. */
    private val _aProcurar = MutableStateFlow(false)
    val aProcurar: StateFlow<Boolean> = _aProcurar.asStateFlow()

    init {
        _texto.debounce(ESPERA_MS)
            .mapLatest { t ->
                val limpo = t.trim()
                val (lista, semRede) = if (limpo.length < MINIMO) emptyList<ResultadoPesquisa>() to false
                else coroutineScope {
                    val lugares = async { pesquisarLugares(limpo) }
                    val paragens = async { pesquisarParagens(limpo) }
                    // Linhas pelo número, depois sítios e moradas (as paragens vêm abaixo, juntas por estação).
                    val lista = linhasComecadasPor(limpo).map { ResultadoPesquisa.DeLinha(it) } +
                        lugares.await().orEmpty().filter { it.tipo != TipoLugar.PARAGEM }.map { ResultadoPesquisa.DeLugar(it) } +
                        agruparPorEstacao(paragens.await(), { it.nome }, { it.lat }, { it.lon }).map { ResultadoPesquisa.DeEstacao(it) }
                    lista to (lugares.await() == null)
                }
                Triple(t, lista, semRede)
            }
            .onEach { (t, lista, semRede) ->
                _resultados.value = lista
                _semRede.value = semRede
                // Se já escreveste mais, estes resultados são de um texto antigo e a pesquisa nova ainda vem a caminho.
                if (t == _texto.value) _aProcurar.value = false
            }
            .launchIn(viewModelScope)
    }

    fun escrever(t: String) {
        if (t == _texto.value) return
        _aProcurar.value = t.trim().length >= MINIMO
        _texto.value = t
    }

    private companion object {
        const val ESPERA_MS = 300L
        const val MINIMO = 2
    }
}
