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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.Grupo

@OptIn(ExperimentalCoroutinesApi::class)
class PesquisaViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val pedidos = mutableListOf<String>()
    private val linha3708 = Linha(Fonte.CM, "3708", "3708", "Costa da Caparica - Lisboa", "#C61D23", "#FFFFFF", Modo.AUTOCARRO)
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 38.7, -9.14, false)

    private val champalimaud = Lugar("Fundação Champalimaud", 38.6935, -9.2238, TipoLugar.LOCAL)

    private fun vm(
        paragens: List<Paragem> = listOf(cais),
        lugares: List<Lugar>? = emptyList(),
    ) = PesquisaViewModel(
        pesquisarParagens = { pedidos += it; paragens },
        linhasComecadasPor = { if ("3708".startsWith(it)) listOf(linha3708) else emptyList() },
        pesquisarLugares = { lugares },
    )

    private fun alges(cais: String, lat: Double) =
        Paragem(RefParagem(Fonte.CM, "0604$cais"), "Algés (Estação) $cais", lat, -9.2297, false)

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    @Test fun linhasPrimeiroDepoisParagens() = runTest(despachante) {
        val vm = vm()
        vm.escrever("37")
        advanceUntilIdle()
        assertEquals(
            listOf(ResultadoPesquisa.DeLinha(linha3708), ResultadoPesquisa.DeEstacao(Grupo(cais.nome, cais.lat, cais.lon, listOf(cais)))),
            vm.resultados.value,
        )
    }

    @Test fun sitioAntesDasParagens() = runTest(despachante) {
        val vm = vm(lugares = listOf(champalimaud))
        vm.escrever("fund")
        advanceUntilIdle()
        assertEquals(
            listOf(ResultadoPesquisa.DeLugar(champalimaud), ResultadoPesquisa.DeEstacao(Grupo(cais.nome, cais.lat, cais.lon, listOf(cais)))),
            vm.resultados.value,
        )
        assertFalse(vm.semRede.value)
    }

    @Test fun paragensDosLugaresNaoRepetem() = runTest(despachante) {
        val estacao = Lugar("Cais do Sodré (Terminal)", 38.7, -9.14, TipoLugar.PARAGEM)
        val vm = vm(lugares = listOf(estacao, champalimaud))
        vm.escrever("cais")
        advanceUntilIdle()
        assertEquals(listOf(ResultadoPesquisa.DeLugar(champalimaud)), vm.resultados.value.filterIsInstance<ResultadoPesquisa.DeLugar>())
    }

    @Test fun estacaoJuntaOsCais() = runTest(despachante) {
        val vm = vm(paragens = listOf(alges("P1", 38.7005), alges("P2", 38.7006), alges("P12", 38.7007)))
        vm.escrever("algés")
        advanceUntilIdle()
        val estacoes = vm.resultados.value.filterIsInstance<ResultadoPesquisa.DeEstacao>()
        assertEquals(1, estacoes.size)
        assertEquals("Algés (Estação)", estacoes.single().g.nome)
        assertEquals(3, estacoes.single().g.itens.size)
    }

    @Test fun semRedeMantemLinhas() = runTest(despachante) {
        val vm = vm(lugares = null)
        vm.escrever("37")
        advanceUntilIdle()
        assertTrue(ResultadoPesquisa.DeLinha(linha3708) in vm.resultados.value)
        assertTrue(vm.semRede.value)
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
