package pt.pixellusitano.paragemcerta.ui.inicio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SugestoesViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val agora = Instant.parse("2026-11-16T08:00:00Z")
    private val casa = LocalGuardado(1, "Casa", Lugar("Algés", 38.7024, -9.2286, TipoLugar.MORADA), "CASA", 0)
    private val paragem = Lugar("Paragem", 38.71, -9.14, TipoLugar.PARAGEM)

    /** Uma rota só de autocarro da [linha]: sai [sai] min depois de agora e chega [chega] min depois. */
    private fun rota(linha: String, sai: Long, chega: Long): Itinerario {
        val p = agora.plusSeconds(sai * 60)
        val c = agora.plusSeconds(chega * 60)
        return Itinerario(listOf(Etapa(false, Modo.AUTOCARRO, linha, null, "Algés (Estação)", paragem, paragem, p, c, p, false, 3, emptyList(), null)), p, c, 0)
    }

    private val r1103 = rota("1103", 5, 40)
    private val r1502 = rota("1502", 8, 45)
    private fun linha(i: Itinerario) = i.etapas.first { !it.aPe }.linhaNome!!

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    // Duas rotas para Algés: a 1103 chega às 08:40 e a 1502 às 08:45 (sem atraso).
    @Test fun sugestaoMudaDeLinhaQuandoOAtrasoMudaSemNovoPedido() = runTest(despachante) {
        var atraso = mapOf("1103" to 0L, "1502" to 0L) // minutos, por linha
        var pedidos = 0
        val vm = SugestoesViewModel({ _, _, _ -> pedidos++; Resultado.Ok(PlanoRotas(listOf(r1103, r1502), null, null)) },
            { lista, _ -> lista.map { it.copy(chegada = it.chegada.plusSeconds(60 * atraso.getValue(linha(it)))) } }, { agora })
        assertEquals("1103", linha(vm.sugerir(casa, 38.71 to -9.14, Duration.ofMinutes(10)) { _, _, _ -> }!!))
        atraso = mapOf("1103" to 10L, "1502" to 0L)
        vm.recorrigir()
        assertEquals("1502", linha(vm.sugestoes.getValue(casa.id).itinerario!!))
        assertEquals(listOf(r1103, r1502), vm.sugestoes.getValue(casa.id).plano)
        assertEquals(1, pedidos)
    }
}
