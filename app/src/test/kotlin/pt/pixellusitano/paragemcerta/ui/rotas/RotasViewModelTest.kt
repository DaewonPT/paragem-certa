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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.core.rotas.ordenar
import pt.pixellusitano.paragemcerta.dados.fixture
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RotasViewModelTest {
    private val despachante = StandardTestDispatcher()
    private val pedidos = mutableListOf<Pair<Lugar, Lugar>>()
    private var resposta: Resultado<PlanoRotas> = Resultado.Ok(PlanoRotas(emptyList(), null, null))
    private var respostaPagina: Resultado<PlanoRotas> = Resultado.Ok(PlanoRotas(emptyList(), null, null))
    private val cursoresPedidos = mutableListOf<String?>()
    private val filtros = MutableStateFlow(Filtros())
    private val cais = Lugar("Cais do Sodré", 38.7058, -9.1431, TipoLugar.PARAGEM)
    private val oriente = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)

    private val recentes = mutableListOf<Pair<Lugar, Lugar>>()
    private val filtrosUsados = mutableListOf<Filtros>()
    private val etapasPedidas = mutableListOf<Etapa>()
    private var partidasDaParagem: List<Chegada>? = null

    private fun vm(guardados: Flow<Filtros> = filtros) = RotasViewModel(
        planear = { de, para, _, _, f, cursor ->
            pedidos += de to para; filtrosUsados += f; cursoresPedidos += cursor
            if (cursor == null) resposta else respostaPagina
        },
        registarRecente = { de, para -> recentes += de to para },
        linhasComAlerta = { emptySet() },
        filtrosGuardados = guardados,
        guardarFiltros = { filtros.value = it },
        proximasPartidas = { etapa -> etapasPedidas += etapa; partidasDaParagem },
        relogio = { Instant.parse("2026-11-01T09:00:00Z") },
    )

    private fun plano(vararg itinerarios: Itinerario, anterior: String? = null, seguinte: String? = null) =
        Resultado.Ok(PlanoRotas(itinerarios.toList(), anterior, seguinte))

    private fun partida(linha: String, minutos: Long) = Chegada(
        Fonte.TRANSITOUS, "id$linha", linha, "Oriente", Instant.parse("2026-11-01T09:00:00Z").plusSeconds(minutos * 60), null, null, null,
        Modo.AUTOCARRO, null, null,
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
        val dadas = TransitousParser.plano(fixture("transitous-plan.json"))
        resposta = Resultado.Ok(dadas)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        // Por omissão, a mais rápida primeiro (a segunda rota da fixture é a mais demorada); as etiquetas seguem a lista mostrada.
        val rotas = ordenar(dadas.itinerarios, Ordem.MAIS_RAPIDA)
        assertEquals(EstadoRotas.Prontas(rotas, etiquetas(rotas), "EARLIER|1793498820", "LATER|1793503740"), vm.estado.value)
        assertEquals(3, rotas.size)
        assertEquals(1, pedidos.size)
    }

    @Test fun rotasOrdenadasPelaPreferencia() = runTest(despachante) {
        val dadas = TransitousParser.plano(fixture("transitous-plan.json"))
        resposta = Resultado.Ok(dadas)
        filtros.value = Filtros(ordem = Ordem.MENOS_A_PE)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val rotas = ordenar(dadas.itinerarios, Ordem.MENOS_A_PE)
        assertEquals(dadas.itinerarios[1], rotas.first()) // a mais demorada, mas a que anda menos a pé
        assertEquals(rotas, (vm.estado.value as EstadoRotas.Prontas).itinerarios)
        assertEquals(etiquetas(rotas), (vm.estado.value as EstadoRotas.Prontas).etiquetas)
    }

    @Test fun proximasPartidasSoAgora() = runTest(despachante) {
        resposta = Resultado.Ok(TransitousParser.plano(fixture("transitous-plan.json")))
        partidasDaParagem = listOf(partida("210", 3), partida("210", 18))
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val agora = vm.estado.value as EstadoRotas.Prontas
        assertEquals(setOf(0, 1, 2), agora.partidas.keys) // um por rota
        assertEquals(partidasDaParagem, agora.partidas[0])
        val pedidas = etapasPedidas.size
        vm.definirHora(TipoHora.PARTIR, LocalTime.of(10, 0))
        advanceUntilIdle()
        assertTrue((vm.estado.value as EstadoRotas.Prontas).partidas.isEmpty())
        assertEquals(pedidas, etapasPedidas.size) // a pedir para mais tarde não se pedem partidas
    }

    @Test fun paragemRepetidaPedeUmaVez() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        // Duas rotas com a mesma primeira etapa em veículo (a da 210) e uma de outra linha.
        resposta = plano(rotas[0], rotas[1], rotas[0])
        partidasDaParagem = listOf(partida("210", 3))
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertEquals(listOf("210", "208"), etapasPedidas.map { it.linhaNome })
        assertEquals(setOf(0, 1, 2), (vm.estado.value as EstadoRotas.Prontas).partidas.keys)
    }

    @Test fun partidasQueFalhamNaoEstragamAsRotas() = runTest(despachante) {
        val dadas = TransitousParser.plano(fixture("transitous-plan.json"))
        resposta = Resultado.Ok(dadas)
        partidasDaParagem = null
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val prontas = vm.estado.value as EstadoRotas.Prontas
        assertEquals(3, prontas.itinerarios.size)
        assertTrue(prontas.partidas.isEmpty())
        assertEquals(3, etapasPedidas.size) // pediu-se (três etapas diferentes), todos falharam e a lista ficou
        assertFalse(prontas.erroPagina)
    }

    @Test fun maisTardeTrocaAPagina() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = plano(rotas[0], rotas[1], anterior = "EARLIER|1", seguinte = "LATER|2")
        respostaPagina = plano(rotas[2], anterior = "EARLIER|3", seguinte = null)
        partidasDaParagem = listOf(partida("210", 3))
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        assertTrue((vm.estado.value as EstadoRotas.Prontas).partidas.isNotEmpty())
        val pedidasAntes = etapasPedidas.size
        vm.maisTarde()
        advanceUntilIdle()
        assertEquals(listOf(null, "LATER|2"), cursoresPedidos)
        assertEquals(EstadoRotas.Prontas(listOf(rotas[2]), etiquetas(listOf(rotas[2])), "EARLIER|3", null), vm.estado.value)
        assertEquals(pedidasAntes, etapasPedidas.size) // as páginas não pedem partidas
        vm.maisCedo()
        advanceUntilIdle()
        assertEquals(listOf(null, "LATER|2", "EARLIER|3"), cursoresPedidos)
    }

    @Test fun doisToquesSeguidosPedemUmaPagina() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = plano(rotas[0], anterior = "EARLIER|1", seguinte = "LATER|2")
        respostaPagina = plano(rotas[1], anterior = "EARLIER|3", seguinte = "LATER|4")
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        vm.maisTarde()
        assertTrue((vm.estado.value as EstadoRotas.Prontas).aCarregarPagina)
        vm.maisTarde()
        vm.maisCedo()
        advanceUntilIdle()
        assertEquals(listOf(null, "LATER|2"), cursoresPedidos)
        assertFalse((vm.estado.value as EstadoRotas.Prontas).aCarregarPagina)
    }

    @Test fun semPaginaSeguinteNaoPede() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = plano(rotas[0], anterior = "EARLIER|1", seguinte = null)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val antes = vm.estado.value
        vm.maisTarde()
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), cursoresPedidos)
        assertEquals(antes, vm.estado.value)
    }

    @Test fun falhaNaPaginaMantemAsRotas() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = plano(rotas[0], rotas[1], anterior = null, seguinte = "LATER|2")
        respostaPagina = Resultado.Falha(null, false)
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val antes = vm.estado.value as EstadoRotas.Prontas
        vm.maisTarde()
        advanceUntilIdle()
        assertEquals(antes.copy(erroPagina = true), vm.estado.value)
        // Voltar a tocar tenta outra vez e, se a página chegar, a mensagem desaparece.
        respostaPagina = plano(rotas[2])
        vm.maisTarde()
        advanceUntilIdle()
        assertEquals(EstadoRotas.Prontas(listOf(rotas[2]), etiquetas(listOf(rotas[2]))), vm.estado.value)
    }

    @Test fun paginaVaziaMantemAsRotasEEscondeOBotao() = runTest(despachante) {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        resposta = plano(rotas[0], anterior = "EARLIER|1", seguinte = "LATER|2")
        respostaPagina = plano()
        val vm = vm()
        vm.definirOrigem(cais)
        vm.definirDestino(oriente)
        advanceUntilIdle()
        val antes = vm.estado.value as EstadoRotas.Prontas
        vm.maisTarde()
        advanceUntilIdle()
        assertEquals(antes.copy(seguinte = null), vm.estado.value)
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

    @Test fun lugarNaRotaRecusaCoordenadasInvalidasENomesEnormes() {
        assertNull(lugarDaRota("X|NaN|NaN"))
        assertNull(lugarDaRota("X|200|0"))
        assertNull(lugarDaRota("X|0|-181"))
        assertNull(lugarDaRota("X|Infinity|0"))
        assertNull(lugarDaRota("a".repeat(501) + "|38.7|-9.14"))
        assertEquals(Lugar("a".repeat(500), 90.0, -180.0, TipoLugar.LOCAL), lugarDaRota("a".repeat(500) + "|90|-180"))
    }
}
