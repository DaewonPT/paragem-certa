package pt.pixellusitano.paragemcerta.ui.pesquisa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

@OptIn(ExperimentalCoroutinesApi::class)
class PesquisaViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val pedidos = mutableListOf<String>()
    private val linha3708 = Linha(Fonte.CM, "3708", "3708", "Costa da Caparica - Lisboa", "#C61D23", "#FFFFFF", Modo.AUTOCARRO)
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 38.7, -9.14, false)

    private fun vm() = PesquisaViewModel(
        pesquisarParagens = { pedidos += it; listOf(cais) },
        linhasComecadasPor = { if ("3708".startsWith(it)) listOf(linha3708) else emptyList() },
    )

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    @Test fun linhasPrimeiroDepoisParagens() = runTest(despachante) {
        val vm = vm()
        vm.escrever("37")
        advanceUntilIdle()
        assertEquals(listOf(ResultadoPesquisa.DeLinha(linha3708), ResultadoPesquisa.DeParagem(cais)), vm.resultados.value)
    }

    @Test fun umCaracterNaoPesquisa() = runTest(despachante) {
        val vm = vm()
        vm.escrever("s")
        advanceUntilIdle()
        assertTrue(pedidos.isEmpty())
        assertTrue(vm.resultados.value.isEmpty())
    }

    @Test fun escreverDepressaFazUmSoPedido() = runTest(despachante) {
        val vm = vm()
        vm.escrever("sod")
        advanceTimeBy(100)
        vm.escrever("sodr")
        advanceUntilIdle()
        assertEquals(listOf("sodr"), pedidos)
    }
}
