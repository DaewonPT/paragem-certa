package pt.pixellusitano.paragemcerta.ui.inicio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.ui.Rotas
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModelTest {
    private val despachante = StandardTestDispatcher()
    private var pedidos = 0
    private val cais = Paragem(RefParagem(Fonte.CM, "062000"), "Cais do Sodré", 38.70533, -9.14394, false)

    private fun vm(nomear: suspend (Double, Double) -> Lugar? = { _, _ -> null }) = InicioViewModel(
        buscarParagens = { _, _, _, _ -> pedidos++; listOf(cais) },
        veiculosAoVivo = { emptyFlow() },
        nomearLugar = nomear,
    )

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    @Test fun zoomLongeNaoPedeParagens() = runTest(despachante) {
        val vm = vm()
        vm.aoMoverCamara(38.70, -9.15, 38.71, -9.14, zoom = 14.0)
        advanceUntilIdle()
        assertEquals(0, pedidos)
        assertEquals(emptyList<Paragem>(), vm.paragens.value)
    }

    @Test fun pedeUmaVezDepoisDeACamaraParar() = runTest(despachante) {
        val vm = vm()
        vm.aoMoverCamara(38.70, -9.15, 38.71, -9.14, zoom = 15.5)
        advanceTimeBy(100)
        vm.aoMoverCamara(38.701, -9.151, 38.711, -9.141, zoom = 15.5)
        advanceTimeBy(100)
        vm.aoMoverCamara(38.702, -9.152, 38.712, -9.142, zoom = 15.5)
        advanceTimeBy(100)
        assertEquals(0, pedidos)
        advanceUntilIdle()
        assertEquals(1, pedidos)
        assertEquals(listOf(cais), vm.paragens.value)
    }

    @Test fun toqueLongoAbreMenuEIrParaAqui() = runTest(despachante) {
        val vm = vm()
        vm.toqueLongo(38.7, -9.14)
        advanceUntilIdle()
        assertEquals(Lugar("Ponto no mapa", 38.7, -9.14, TipoLugar.LOCAL), vm.menu.value)
        assertEquals(Rotas(destino = "Ponto no mapa|38.7|-9.14"), vm.irParaAqui())
        assertNull(vm.menu.value)
    }

    @Test fun partirDaquiPoeAOrigem() = runTest(despachante) {
        val vm = vm()
        vm.toqueLongo(38.7, -9.14)
        assertEquals(Rotas(origem = "Ponto no mapa|38.7|-9.14"), vm.partirDaqui())
    }

    @Test fun nomeQueChegaDepressaFicaComAsCoordenadasDoToque() = runTest(despachante) {
        val vm = vm { _, _ -> Lugar("Campo Pequeno", 38.742, -9.146, TipoLugar.PARAGEM) }
        vm.toqueLongo(38.7436, -9.1452)
        advanceUntilIdle()
        assertEquals(Lugar("Campo Pequeno", 38.7436, -9.1452, TipoLugar.LOCAL), vm.menu.value)
    }

    @Test fun nomeQueDemoraMaisDe2sNaoChega() = runTest(despachante) {
        val vm = vm { _, _ -> delay(5_000); Lugar("Tarde demais", 0.0, 0.0, TipoLugar.MORADA) }
        vm.toqueLongo(38.7, -9.14)
        advanceUntilIdle()
        assertEquals("Ponto no mapa", vm.menu.value?.nome)
    }

    @Test fun lembraACamaraAoVoltarAoMapa() = runTest(despachante) {
        val vm = vm()
        assertEquals(null, vm.camara)
        vm.aoMoverCamara(38.70, -9.15, 38.72, -9.13, zoom = 16.0)
        assertEquals(Zona(38.70, -9.15, 38.72, -9.13, 16.0), vm.camara)
    }
}
