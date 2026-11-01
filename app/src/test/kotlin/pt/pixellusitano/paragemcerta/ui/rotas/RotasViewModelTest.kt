package pt.pixellusitano.paragemcerta.ui.rotas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RotasViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val pedidos = mutableListOf<Pair<Lugar, Lugar>>()
    private var resposta: Resultado<List<Itinerario>> = Resultado.Ok(emptyList())
    private val filtros = MutableStateFlow(Filtros())
    private val cais = Lugar("Cais do Sodré", 38.7058, -9.1431, TipoLugar.PARAGEM)
    private val oriente = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)

    private fun vm() = RotasViewModel(
        planear = { de, para, _, _, _ -> pedidos += de to para; resposta },
        registarRecente = { _, _ -> },
        linhasComAlerta = { emptySet() },
        filtrosGuardados = filtros,
        guardarFiltros = { filtros.value = it },
        relogio = { Instant.parse("2026-11-01T09:00:00Z") },
    )

    @Before fun antes() = Dispatchers.setMain(despachante)
    @After fun depois() = Dispatchers.resetMain()

    @Test fun muitoPertoNaoPlaneia() = runTest(despachante) {
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(cais.copy(nome = "Ali ao lado", lat = cais.lat + 0.00045)) // uns 50 m a norte
        advanceUntilIdle()
        assertEquals(EstadoRotas.Vazias(Motivo.MUITO_PERTO), vm.estado.value)
        assertTrue(pedidos.isEmpty())
    }

    @Test fun semPosicaoPedeOrigem() = runTest(despachante) {
        val vm = vm()
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertEquals(EstadoRotas.Vazias(Motivo.SEM_ORIGEM), vm.estado.value)
    }

    @Test fun posicaoServeDeOrigem() = runTest(despachante) {
        val vm = vm()
        vm.definirDestino(oriente)
        vm.definirPosicao(cais.lat to cais.lon)
        advanceUntilIdle()
        assertEquals(TipoLugar.POSICAO, pedidos.single().first.tipo)
    }

    @Test fun semLigacoes() = runTest(despachante) {
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertEquals(EstadoRotas.Vazias(Motivo.SEM_LIGACOES), vm.estado.value)
    }

    @Test fun semRede() = runTest(despachante) {
        resposta = Resultado.Falha(null, false)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertEquals(EstadoRotas.Erro(semLigacao = true), vm.estado.value)
    }

    @Test fun rotasComEtiquetas() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = Resultado.Ok(rotas)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertEquals(EstadoRotas.Prontas(rotas, etiquetas(rotas)), vm.estado.value)
        assertEquals(3, rotas.size)
        assertEquals(1, pedidos.size)
    }

    private val lisboa = ZoneId.of("Europe/Lisbon")
    private val nove = ZonedDateTime.of(2026, 11, 1, 9, 0, 0, 0, lisboa)

    @Test fun horaQueJaPassouEAmanha() {
        assertEquals(ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, lisboa), proximaOcorrencia(LocalTime.of(8, 0), nove))
        assertEquals(ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, lisboa), proximaOcorrencia(LocalTime.of(10, 0), nove))
        // O minuto em curso ainda conta como hoje.
        assertEquals(nove, proximaOcorrencia(LocalTime.of(9, 0), nove.plusSeconds(40)))
    }

    @Test fun lugarNaRota() {
        val l = Lugar("Rua A | B", 38.7, -9.14, TipoLugar.LOCAL)
        assertEquals(l, lugarDaRota(l.paraRota()))
        assertNull(lugarDaRota("sem coordenadas"))
    }
}
