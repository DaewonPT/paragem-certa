package pt.pixellusitano.paragemcerta.ui.viagem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem

@OptIn(ExperimentalCoroutinesApi::class)
class ViagemViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val ex = Exemplo()
    private val plano = ex.plano()
    private fun ecra(e: EstadoViagem, p: PlanoViagem = plano) = ecraDaViagem(p, e, ex.viagem(p))

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    @Test fun aBordoMostraAEtapaAtualEOQueFalta() {
        val e = ecra(ex.aBordo(plano, 1, k = 2))
        assertEquals(1, e.atual)
        assertEquals(listOf(Situacao.FEITA, Situacao.ATUAL, Situacao.SEGUINTE, Situacao.SEGUINTE), e.cartoes.map { it.situacao })
        val c = e.cartoes[1]
        assertEquals(3, c.faltam)
        assertEquals(listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente"), c.paragens.map { it.nome })
        assertEquals(listOf(true, true, false, false, false, false), c.paragens.map { it.passada })
        assertEquals(listOf(2), c.paragens.indices.filter { c.paragens[it].aqui }) // estás aqui: a última de que o veículo já saiu
        assertEquals(listOf(5), c.paragens.indices.filter { c.paragens[it].sai })
        assertFalse(e.horario)
        assertEquals("Casa", e.de)
        assertEquals("Sacavém", e.para)
    }

    @Test fun aEsperaEstasNaParagemDeEmbarque() {
        val c = ecra(ex.espera(plano, 1)).cartoes[1]
        assertEquals(5, c.faltam)
        assertEquals(listOf(0), c.paragens.indices.filter { c.paragens[it].aqui })
        assertTrue(c.paragens.none { it.passada })
    }

    @Test fun etapasFeitasETapasPorFazer() {
        val e = ecra(ex.aBordo(plano, 1, k = 2))
        assertTrue(e.cartoes[0].paragens.isEmpty() && e.cartoes[0].faltam == 0) // a pé: sem paragens
        assertTrue(e.cartoes[1].paragens.none { it.sai && it.aqui })
        val seguinte = e.cartoes[3]
        assertEquals(2, seguinte.faltam) // ainda não embarcaste: contam-se todas até sair
        assertTrue(seguinte.paragens.none { it.passada || it.aqui })
        assertEquals(listOf(2), seguinte.paragens.indices.filter { seguinte.paragens[it].sai })
        // Chegada à etapa seguinte: a anterior fica feita, com as paragens todas passadas e nenhum "estás aqui".
        val depois = ecra(ex.aPe(plano, etapa = 2, falta = 90.0))
        assertEquals(listOf(Situacao.FEITA, Situacao.FEITA, Situacao.ATUAL, Situacao.SEGUINTE), depois.cartoes.map { it.situacao })
        assertTrue(depois.cartoes[1].paragens.all { it.passada && !it.aqui })
    }

    @Test fun osAtrasosMexemNasHorasDaEtapaAtualENaChegada() {
        val base = ex.aBordo(plano, 1, k = 2)
        val atrasado = ecra(base.copy(atrasoS = 240))
        val horario = ecra(base).cartoes[1].paragens.map { it.hora }
        assertEquals(horario.map { it.plusSeconds(240) }, atrasado.cartoes[1].paragens.map { it.hora })
        assertEquals(plano.itinerario.chegada.plusSeconds(240), atrasado.chegada)
        // As outras etapas ficam no horário.
        assertEquals(ecra(base).cartoes[3].paragens, atrasado.cartoes[3].paragens)
        // Adiantado não adianta a chegada: os transportes seguintes saem à hora marcada.
        assertEquals(plano.itinerario.chegada, ecra(base.copy(atrasoS = -90)).chegada)
    }

    @Test fun seloDeHorarioSoEmHorario() {
        assertTrue(ecra(ex.aBordo(plano, 1, k = 2, origem = Origem.HORARIO)).horario)
        assertFalse(ecra(ex.aBordo(plano, 1, k = 2, origem = Origem.GPS)).horario)
    }

    @Test fun terminadaFicaComTudoFeito() {
        val e = ecra(ex.aBordo(plano, 3, k = 1, origem = Origem.HORARIO).copy(fase = Fase.TERMINADA, terminada = true, etapa = 1))
        assertEquals(3, e.atual) // fica na última etapa mesmo que o estado guardado tenha ficado a meio
        assertTrue(e.cartoes.all { it.situacao == Situacao.FEITA })
        assertFalse(e.horario)
    }

    @Test fun lerOsFicheirosAoAtualizar() = runTest(despachante) {
        var lido: Pair<PlanoViagem, EstadoViagem>? = plano to ex.aBordo(plano, 1, k = 2)
        val vm = ViagemViewModel(ler = { lido }, io = despachante)
        assertEquals(VistaViagem.ALer, vm.vista.value)

        vm.atualizar()
        advanceUntilIdle()
        assertEquals(1, (vm.vista.value as VistaViagem.Ativa).ecra.atual)

        lido = plano to ex.aPe(plano, etapa = 2, falta = 90.0)
        vm.atualizar()
        advanceUntilIdle()
        assertEquals(2, (vm.vista.value as VistaViagem.Ativa).ecra.atual)

        lido = null // PARAR apagou os ficheiros
        vm.atualizar()
        advanceUntilIdle()
        assertEquals(VistaViagem.SemViagem, vm.vista.value)
    }
}
