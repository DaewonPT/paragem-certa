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
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Paragem

sealed interface ResultadoPesquisa {
    data class DeParagem(val p: Paragem) : ResultadoPesquisa
    data class DeLinha(val l: Linha) : ResultadoPesquisa
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PesquisaViewModel(
    private val pesquisarParagens: suspend (String) -> List<Paragem>,
    private val linhasComecadasPor: suspend (String) -> List<Linha>,
) : ViewModel() {
    private val _texto = MutableStateFlow("")
    val texto: StateFlow<String> = _texto.asStateFlow()

    private val _resultados = MutableStateFlow<List<ResultadoPesquisa>>(emptyList())
    val resultados: StateFlow<List<ResultadoPesquisa>> = _resultados.asStateFlow()

    init {
        _texto.debounce(ESPERA_MS)
            .mapLatest { t ->
                val limpo = t.trim()
                if (limpo.length < MINIMO) emptyList()
                // Linhas da Carris Metropolitana cujo número começa pelo texto, depois paragens (CM e depois Transitous).
                else linhasComecadasPor(limpo).map { ResultadoPesquisa.DeLinha(it) } +
                    pesquisarParagens(limpo).map { ResultadoPesquisa.DeParagem(it) }
            }
            .onEach { _resultados.value = it }
            .launchIn(viewModelScope)
    }

    fun escrever(t: String) {
        _texto.value = t
    }

    private companion object {
        const val ESPERA_MS = 300L
        const val MINIMO = 2
    }
}
