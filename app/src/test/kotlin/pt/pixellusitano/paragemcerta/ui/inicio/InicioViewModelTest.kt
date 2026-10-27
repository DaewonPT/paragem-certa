package pt.pixellusitano.paragemcerta.ui.inicio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModelTest {
    private val despachante = StandardTestDispatcher()
    private var pedidos = 0
    private val cais = Paragem(RefParagem(Fonte.CM, "062000"), "Cais do Sodré", 38.70533, -9.14394, false)

    private fun vm() = InicioViewModel(
        buscarParagens = { _, _, _, _ -> pedidos++; listOf(cais) },
        veiculosAoVivo = { emptyFlow() },
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
}
