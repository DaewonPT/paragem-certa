package pt.pixellusitano.paragemcerta.ui.rotas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val recentes = mutableListOf<Pair<Lugar, Lugar>>()
    private val filtrosUsados = mutableListOf<Filtros>()

    private fun vm(guardados: Flow<Filtros> = filtros) = RotasViewModel(
        planear = { de, para, _, _, f -> pedidos += de to para; filtrosUsados += f; resposta },
        registarRecente = { de, para -> recentes += de to para },
        linhasComAlerta = { emptySet() },
        filtrosGuardados = guardados,
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

    @Test fun naoPlaneiaAntesDosFiltrosGuardados() = runTest(despachante) {
        val guardados = MutableSharedFlow<Filtros>()
        val vm = vm(guardados)
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertTrue(pedidos.isEmpty())
        assertEquals(EstadoRotas.ACarregar, vm.estado.value)
        guardados.emit(Filtros(acessivel = true))
        advanceUntilIdle()
        assertEquals(1, pedidos.size)
        assertEquals(Filtros(acessivel = true), filtrosUsados.single())
    }

    @Test fun posicaoNovaLongeVoltaAPlanear() = runTest(despachante) {
        val vm = vm()
        vm.definirDestino(oriente)
        vm.definirPosicao(38.75 to -9.30) // o último ponto conhecido, de outra terra
        advanceUntilIdle()
        assertEquals(1, pedidos.size)
        vm.definirPosicao(cais.lat to cais.lon) // o fix fresco
        advanceUntilIdle()
        assertEquals(2, pedidos.size)
        assertEquals(cais.lat, pedidos.last().first.lat, 0.0)
        vm.definirPosicao(cais.lat + 0.0002 to cais.lon) // uns 22 m: ruído do GPS
        advanceUntilIdle()
        assertEquals(2, pedidos.size)
    }

    @Test fun recenteDaMinhaPosicaoNaoGuardaCoordenadas() = runTest(despachante) {
        val vm = vm()
        vm.definirDestino(oriente)
        vm.definirPosicao(cais.lat to cais.lon)
        advanceUntilIdle()
        assertEquals(Lugar(MINHA_POSICAO, 0.0, 0.0, TipoLugar.POSICAO), recentes.single().first)
        assertEquals(cais.lat, pedidos.single().first.lat, 0.0)
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
