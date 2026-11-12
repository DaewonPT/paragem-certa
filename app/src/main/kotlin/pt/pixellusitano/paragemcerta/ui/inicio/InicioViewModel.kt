package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.ui.Rotas
import pt.pixellusitano.paragemcerta.ui.rotas.paraRota

/** Zona visível do mapa. */
data class Zona(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double, val zoom: Double)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InicioViewModel(
    private val buscarParagens: suspend (minLat: Double, minLon: Double, maxLat: Double, maxLon: Double) -> List<Paragem>,
    veiculosAoVivo: () -> Flow<List<Veiculo>>,
    private val nomearLugar: suspend (lat: Double, lon: Double) -> Lugar? = { _, _ -> null },
    carrisAoVivo: () -> Flow<List<Veiculo>> = { emptyFlow() },
) : ViewModel() {
    private val zona = MutableStateFlow<Zona?>(null)
    private val _paragens = MutableStateFlow<List<Paragem>>(emptyList())
    val paragens: StateFlow<List<Paragem>> = _paragens.asStateFlow()

    /** Só corre enquanto o mapa está visível (alguém a recolher), e pára 5 s depois de deixar de estar. */
    val veiculos: StateFlow<List<Veiculo>> =
        veiculosAoVivo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Os da Carris (de minuto a minuto): a mesma regra, só correm com o mapa à vista. */
    val veiculosCarris: StateFlow<List<Veiculo>> =
        carrisAoVivo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Última zona vista: o mapa volta a ela quando se regressa ao Início (o MapView é recriado). */
    var camara: Zona? = null
        private set

    fun aoMoverCamara(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, zoom: Double) {
        zona.value = Zona(minLat, minLon, maxLat, maxLon, zoom)
        camara = zona.value
        if (zoom < ZOOM_MINIMO_PARAGENS) _paragens.value = emptyList()
    }

    fun escolherVeiculo(v: Veiculo?) {
        _veiculoEscolhido.value = v
    }

    /** Ponto tocado sem largar no mapa; null = menu fechado. */
    private val _menu = MutableStateFlow<Lugar?>(null)
    val menu: StateFlow<Lugar?> = _menu.asStateFlow()
    private var nomear: Job? = null

    /** Abre logo o menu como "Ponto no mapa"; se a morada chegar em menos de 2 s, troca o nome (as coordenadas são as do toque). */
    fun toqueLongo(lat: Double, lon: Double) {
        val ponto = Lugar(PONTO_NO_MAPA, lat, lon, TipoLugar.LOCAL)
        _menu.value = ponto
        nomear?.cancel()
        nomear = viewModelScope.launch {
            val nome = withTimeoutOrNull(ESPERA_NOME_MS) { nomearLugar(lat, lon) }?.nome ?: return@launch
            if (_menu.value == ponto) _menu.value = ponto.copy(nome = nome)
        }
    }

    fun fecharMenu() {
        nomear?.cancel()
        _menu.value = null
    }

    fun irParaAqui(): Rotas? = _menu.value?.let { Rotas(destino = it.paraRota()) }.also { fecharMenu() }

    fun partirDaqui(): Rotas? = _menu.value?.let { Rotas(origem = it.paraRota()) }.also { fecharMenu() }

    companion object {
        const val ZOOM_MINIMO_PARAGENS = 15.0
        const val ESPERA_CAMARA_MS = 500L
        const val ESPERA_NOME_MS = 2_000L
        const val PONTO_NO_MAPA = "Ponto no mapa"
    }
}
