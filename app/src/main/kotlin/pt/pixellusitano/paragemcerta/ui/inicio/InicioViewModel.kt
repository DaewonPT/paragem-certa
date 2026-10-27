package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo

/** Zona visível do mapa. */
data class Zona(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double, val zoom: Double)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InicioViewModel(
    private val buscarParagens: suspend (minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) -> List<Paragem>,
    veiculosAoVivo: () -> Flow<List<Veiculo>>,
) : ViewModel() {
    private val zona = MutableStateFlow<Zona?>(null)
    private val _paragens = MutableStateFlow<List<Paragem>>(emptyList())
    val paragens: StateFlow<List<Paragem>> = _paragens.asStateFlow()

    /** Só corre enquanto o mapa está visível (alguém a recolher), e pára 5 s depois de deixar de estar. */
    val veiculos: StateFlow<List<Veiculo>> =
        veiculosAoVivo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _veiculoEscolhido = MutableStateFlow<Veiculo?>(null)
    val veiculoEscolhido: StateFlow<Veiculo?> = _veiculoEscolhido.asStateFlow()

    init {
        zona.filterNotNull()
            .debounce(ESPERA_CAMARA_MS)
            .filter { it.zoom >= ZOOM_MINIMO_PARAGENS }
            .mapLatest { buscarParagens(it.minLat, it.minLon, it.maxLat, it.maxLon) }
            .onEach { _paragens.value = it }
            .launchIn(viewModelScope)
    }

    fun aoMoverCamara(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, zoom: Double) {
        zona.value = Zona(minLat, minLon, maxLat, maxLon, zoom)
        if (zoom < ZOOM_MINIMO_PARAGENS) _paragens.value = emptyList()
    }

    fun escolherVeiculo(v: Veiculo?) {
        _veiculoEscolhido.value = v
    }

    companion object {
        const val ZOOM_MINIMO_PARAGENS = 15.0
        const val ESPERA_CAMARA_MS = 500L
    }
}
