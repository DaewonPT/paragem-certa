package pt.pixellusitano.paragemcerta.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AtualizadorCasaTrabalhoTest {
    private val dados = DadosWidget(RuntimeEnvironment.getApplication())
    // Em novembro Lisboa está em UTC: 18:40Z lê-se 18:40.
    private val inicio = Instant.parse("2026-11-09T18:40:00Z")
    private val casa = LocalGuardado(1, "Casa", Lugar("Casa", 38.7369, -9.1427, TipoLugar.MORADA), "CASA", 0)
    private val trabalho = LocalGuardado(2, "Trabalho", Lugar("Trabalho", 38.7058, -9.1431, TipoLugar.MORADA), "TRABALHO", 1)
    private val guardada = PosicaoComHora(38.6979, -9.2064, hora = inicio.minusSeconds(1_200).toEpochMilli()) // Belém, às 18:20
    private val atual = PosicaoComHora(38.7036, -9.1772, hora = inicio.toEpochMilli()) // Alcântara, agora
    private val locais = listOf(casa, trabalho)
    private val desenhos = mutableListOf<Pair<Int, ConteudoCasaTrabalho>>()
    private val pedidos = mutableListOf<Pair<Lugar, Lugar>>()
    private val widgets = mutableListOf(1)

    private fun itinerario(em: Instant, linha: String = "210"): Itinerario {
        val sai = em.plusSeconds(120)
        val apanha = sai.plusSeconds(300)
        val chega = apanha.plusSeconds(1_500)
        val l = Lugar("Paragem", 38.7, -9.15, TipoLugar.PARAGEM)
        return Itinerario(
            listOf(
                Etapa(true, Modo.OUTRO, null, null, null, l, l, sai, apanha, sai, false, 0, emptyList(), null),
                Etapa(false, Modo.AUTOCARRO, linha, null, null, l, l, apanha, chega, apanha, false, 3, emptyList(), "#E2001A"),
            ),
            sai, chega, 0,
        )
    }

    private val responde: suspend (Lugar, Lugar, Instant) -> Resultado<PlanoRotas> =
        { _, _, em -> Resultado.Ok(PlanoRotas(listOf(itinerario(em)), null, null)) }

    private fun TestScope.atualizador(
        posicaoAtual: suspend () -> PosicaoComHora? = { null },
        lerLocais: suspend () -> List<LocalGuardado> = { locais },
        despacho: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        planear: suspend (Lugar, Lugar, Instant) -> Resultado<PlanoRotas> = responde,
    ) = AtualizadorCasaTrabalho(
        dados,
        lerLocais,
        { de, para, em -> pedidos += de to para; planear(de, para, em) },
        posicaoAtual,
        { id, c -> desenhos += id to c },
        { widgets },
        { inicio.plusMillis(testScheduler.currentTime) },
        despacho,
    )

    private val ultimo get() = desenhos.last().second

    @Test fun planeiaAPartirDaPosicaoAtual() = runTest {
        dados.guardarPosicao(guardada)
        atualizador(posicaoAtual = { atual }).atualizar()

        assertEquals(setOf(casa.lugar, trabalho.lugar), pedidos.map { it.second }.toSet())
        assertTrue(pedidos.all { (de, _) -> de.lat == atual.lat && de.lon == atual.lon && de.tipo == TipoLugar.POSICAO })
        assertEquals("a posição atual passa a ser a guardada", atual, dados.posicao())
        assertEquals("enquanto calcula", Destino.ACalcular("Casa"), desenhos.first().second.casa)
        val l = (ultimo.casa as Destino.Ligacao).ligacao
        assertEquals("210", l.linha)
        assertEquals(30, l.duracaoMin)
        assertEquals(inicio.plusSeconds(120).toEpochMilli(), l.partida)
        assertEquals(Modo.AUTOCARRO, l.modo)
        assertEquals(0xFFE2001A.toInt(), l.cor)
        assertTrue(ultimo.trabalho is Destino.Ligacao)
        assertEquals(Rodape("18:40", daPosicaoGuardada = false), ultimo.rodape)
    }

    @Test fun semPosicaoAtualUsaAGuardada() = runTest {
        dados.guardarPosicao(guardada)
        atualizador(posicaoAtual = { delay(5_000); atual }).atualizar()

        assertEquals("a posição espera no máximo 3 s", 3_000, testScheduler.currentTime)
        assertEquals(2, pedidos.size)
        assertTrue(pedidos.all { (de, _) -> de.lat == guardada.lat && de.lon == guardada.lon })
        assertEquals(guardada, dados.posicao())
        assertTrue(ultimo.casa is Destino.Ligacao)
        assertEquals(Rodape("18:20", daPosicaoGuardada = true), ultimo.rodape)
        atualizador().redesenhar()
        assertEquals("um redesenho mais tarde continua a dizer de quando é a posição", Rodape("18:20", daPosicaoGuardada = true), ultimo.rodape)
    }

    @Test fun semNenhumaPosicaoNaoPedeRotas() = runTest {
        atualizador(posicaoAtual = { null }).atualizar()
        assertTrue(pedidos.isEmpty())
        assertEquals(Destino.SemPosicao("Casa"), ultimo.casa)
        assertEquals(Destino.SemPosicao("Trabalho"), ultimo.trabalho)
    }

    @Test fun posicaoQueRebentaUsaAGuardada() = runTest {
        dados.guardarPosicao(guardada)
        atualizador(posicaoAtual = { throw SecurityException("sem permissão") }).atualizar()
        assertEquals(2, pedidos.size)
        assertEquals(Rodape("18:20", daPosicaoGuardada = true), ultimo.rodape)
    }

    @Test fun doisToquesSeguidosSoPedemUmaVez() = runTest {
        dados.guardarPosicao(guardada)
        // A leitura dos locais suspende: o segundo toque entra antes de o primeiro chegar aos pedidos.
        val a = atualizador(lerLocais = { delay(100); locais })
        launch { a.atualizar() }
        launch { a.atualizar() }
        testScheduler.advanceUntilIdle()
        assertEquals("um pedido por destino", 2, pedidos.size)

        val depoisDoPrimeiro = desenhos.size
        advanceTimeBy(29_999 - testScheduler.currentTime) // os 30 s contam do toque, não do fim dos pedidos
        runCurrent()
        a.atualizar()
        assertEquals(2, pedidos.size)
        assertEquals("um toque ignorado não desenha nada", depoisDoPrimeiro, desenhos.size)
        advanceTimeBy(1)
        runCurrent()
        a.atualizar()
        testScheduler.advanceUntilIdle()
        assertEquals("aos 30 s já pode", 4, pedidos.size)
    }

    @Test fun doisWidgetsPartilhamOsTrintaSegundos() = runTest {
        widgets += 2
        dados.guardarPosicao(guardada)
        val a = atualizador()
        a.atualizar() // o ↻ do widget 1
        a.atualizar() // o ↻ do widget 2, logo a seguir
        assertEquals("só os dois pedidos do primeiro toque", 2, pedidos.size)
        assertEquals("o primeiro toque redesenha os dois widgets", listOf(1, 2), desenhos.takeLast(2).map { it.first })
        assertTrue(desenhos.takeLast(2).all { it.second.casa is Destino.Ligacao })
    }

    @Test fun relogioQueAndouParaTrasNaoPrendeOBotao() = runTest {
        dados.guardarPosicao(guardada)
        dados.marcarTentativaCasaTrabalho(inicio.plusSeconds(3_600))
        atualizador().atualizar()
        assertEquals(2, pedidos.size)
    }

    @Test fun pedidoQueFalhaFicaSemLigacao() = runTest {
        dados.guardarPosicao(guardada)
        atualizador { _, para, _ -> if (para == casa.lugar) Resultado.Falha(null, false) else throw IOException("rebentou") }.atualizar()
        assertEquals(Destino.SemLigacao("Casa"), ultimo.casa)
        assertEquals(Destino.SemLigacao("Trabalho"), ultimo.trabalho)
        assertEquals(EstadoLigacao.FALHOU, dados.ligacao("CASA")?.estado)
        atualizador().redesenhar()
        assertEquals("um redesenho ao sair da app não apaga a mensagem", Destino.SemLigacao("Casa"), ultimo.casa)
    }

    @Test fun planoVazioFicaSemTransportes() = runTest {
        dados.guardarPosicao(guardada)
        atualizador { _, _, _ -> Resultado.Ok(PlanoRotas(emptyList(), null, null)) }.atualizar()
        assertEquals(Destino.SemTransportes("Casa"), ultimo.casa)
        assertEquals(EstadoLigacao.SEM_TRANSPORTES, dados.ligacao("TRABALHO")?.estado)
        atualizador().redesenhar()
        assertEquals(Destino.SemTransportes("Trabalho"), ultimo.trabalho)
    }

    // O pedido do Trabalho não liga ao cancelamento, como o execute() do OkHttp: tem de cortar na mesma aos 6 s.
    @Test(timeout = 30_000)
    fun pedidoLentoCortaAosSeisSegundos() = runTest {
        dados.guardarPosicao(guardada)
        val nunca = CompletableDeferred<Resultado<PlanoRotas>>()
        atualizador { de, para, em -> if (para == casa.lugar) responde(de, para, em) else withContext(NonCancellable) { nunca.await() } }.atualizar()
        assertEquals(6_000, testScheduler.currentTime)
        assertTrue("o que respondeu a tempo conta", ultimo.casa is Destino.Ligacao)
        assertEquals(Destino.SemLigacao("Trabalho"), ultimo.trabalho)
    }

    @Test fun destinoPertoNaoPedeRota() = runTest {
        // Cerca de 445 m a norte da Casa.
        dados.guardarPosicao(PosicaoComHora(casa.lugar.lat + 0.004, casa.lugar.lon, guardada.hora))
        atualizador().atualizar()
        assertEquals(listOf(trabalho.lugar), pedidos.map { it.second })
        assertEquals(Destino.Perto("Casa"), ultimo.casa)
        assertTrue(ultimo.trabalho is Destino.Ligacao)
    }

    @Test fun painelAtualizaSemPedidosNovos() = runTest {
        widgets += 2
        atualizador().aoSugerir(casa, itinerario(inicio), atual.lat to atual.lon)

        assertTrue(pedidos.isEmpty())
        assertEquals(EstadoLigacao.OK, dados.ligacao("CASA")?.estado)
        assertEquals(listOf(1, 2), desenhos.map { it.first })
        assertEquals("210", (ultimo.casa as Destino.Ligacao).ligacao.linha)
        assertEquals("sem posição guardada o Trabalho continua a pedir que se abra a app", Destino.SemPosicao("Trabalho"), ultimo.trabalho)
        assertEquals(Rodape("18:40", daPosicaoGuardada = false), ultimo.rodape)
        assertNull("a origem do painel não se guarda como posição: não tem hora", dados.posicao())
        assertNull("o painel não conta como toque no ↻", dados.ultimaTentativaCasaTrabalho())
    }

    @Test fun painelNaoTrocaAPosicaoGuardada() = runTest {
        dados.guardarPosicao(guardada)
        atualizador().aoSugerir(casa, itinerario(inicio), atual.lat to atual.lon)
        assertEquals(guardada, dados.posicao())
    }

    @Test fun apagarDadosEsqueceERedesenha() = runTest {
        widgets += 2
        dados.guardarPosicao(guardada)
        val a = atualizador()
        a.aoSugerir(casa, itinerario(inicio), guardada.lat to guardada.lon)
        dados.marcarTentativaCasaTrabalho(inicio)
        desenhos.clear()

        a.aoApagarDados()

        assertNull(dados.posicao())
        assertNull(dados.ligacao("CASA"))
        assertNull(dados.ultimaTentativaCasaTrabalho())
        assertEquals(listOf(1, 2), desenhos.map { it.first })
        assertEquals(Destino.SemPosicao("Casa"), ultimo.casa)
        assertNull(ultimo.rodape)
        assertTrue(pedidos.isEmpty())
    }
}
