package pt.pixellusitano.paragemcerta.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AtualizadorWidgetsTest {
    private val dados = DadosWidget(RuntimeEnvironment.getApplication())
    private val inicio = Instant.parse("2026-11-04T14:00:00Z")
    private val cais = RefParagem(Fonte.CM, "060419")
    private val desenhos = mutableListOf<Pair<Int, ConteudoWidget>>()
    private val guardadas = mutableMapOf<RefParagem, Pair<List<Chegada>, Instant>>()
    private var pedidos = 0
    private val widgets = mutableListOf<Int>()

    private fun TestScope.atualizador(
        despacho: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        pedir: suspend (RefParagem, Instant) -> Resultado<List<Chegada>> = { _, _ -> Resultado.Ok(emptyList()) },
    ) = AtualizadorWidgets(
        dados,
        { ref, _ -> guardadas[ref] },
        { ref, em -> pedidos++; pedir(ref, em) },
        { id, c -> desenhos += id to c },
        { widgets },
        { inicio.plusMillis(testScheduler.currentTime) },
        despacho,
    )

    private val estados get() = desenhos.map { it.second.estado }

    @Test fun arranqueDoAndroidNaoVaiARede() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio
        atualizador().redesenhar(listOf(1, 2))
        assertEquals(0, pedidos)
        assertEquals(listOf(1, 2), desenhos.map { it.first })
        assertEquals(listOf("728"), desenhos[0].second.linhas.map { it.linha })
        assertEquals(EstadoWidget.Normal, desenhos[0].second.estado)
        assertNull(desenhos[1].second.paragem)
    }

    @Test fun tocarPedeEGuarda() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        guardadas[cais] = listOf(chegada("velha", inicio.plusSeconds(60))) to inicio.minusSeconds(3600)
        val a = atualizador { ref, em ->
            delay(2_000)
            Resultado.Ok(listOf(chegada("nova", inicio.plusSeconds(600)))).also { guardadas[ref] = it.valor to em }
        }
        a.aoTocar(1)

        assertEquals(1, pedidos)
        assertEquals(listOf(EstadoWidget.AAtualizar, EstadoWidget.Normal), estados)
        // Enquanto atualiza vê-se o que estava guardado; depois, a resposta, com a hora do pedido.
        assertEquals(listOf("velha"), desenhos[0].second.linhas.map { it.linha })
        assertEquals(listOf("nova"), desenhos[1].second.linhas.map { it.linha })
        assertEquals("14:00", desenhos[1].second.atualizadoAs)
        assertEquals(inicio, guardadas[cais]?.second)
        assertEquals(inicio, dados.ultimaTentativa(1))
    }

    @Test fun doisToquesFazemUmPedido() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        val a = atualizador()
        a.aoTocar(1)
        val depoisDoPrimeiro = desenhos.size
        advanceTimeBy(29_999)
        runCurrent()
        a.aoTocar(1)
        assertEquals(1, pedidos)
        assertEquals("um toque ignorado não desenha nada", depoisDoPrimeiro, desenhos.size)
        advanceTimeBy(1)
        runCurrent()
        a.aoTocar(1)
        assertEquals("aos 30 s já pode", 2, pedidos)
    }

    @Test fun doisToquesAoMesmoTempoFazemUmPedido() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        val a = atualizador { _, _ -> delay(1_000); Resultado.Ok(emptyList()) }
        launch { a.aoTocar(1) }
        launch { a.aoTocar(1) }
        testScheduler.advanceUntilIdle()
        assertEquals(1, pedidos)
    }

    @Test fun relogioQueAndouParaTrasNaoPrendeOBotao() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        dados.marcarTentativa(1, inicio.plusSeconds(3600))
        atualizador().aoTocar(1)
        assertEquals(1, pedidos)
    }

    // O fake prende mesmo a linha de execução e não liga ao cancelamento, como o execute() do OkHttp: um withTimeoutOrNull à volta
    // da chamada ficava aqui preso, e é o timeout deste @Test que o apanha.
    @Test(timeout = 30_000)
    fun pedidoLentoDizSemLigacaoEm8Segundos() {
        val entrou = CountDownLatch(1)
        val solta = CountDownLatch(1)
        try {
            runTest {
                dados.escolher(1, cais.chave, "Cais")
                guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.minusSeconds(60)
                val a = atualizador(Dispatchers.IO) { _, _ ->
                    entrou.countDown()
                    solta.await(60, TimeUnit.SECONDS) // com limite: se a regressão voltar, o teste falha mas a linha não fica presa para sempre
                    Resultado.Ok(emptyList())
                }
                val toque = launch { a.aoTocar(1) }
                runCurrent()
                assertTrue("o pedido tinha de ter começado", entrou.await(10, TimeUnit.SECONDS))
                assertEquals(listOf(EstadoWidget.AAtualizar), estados)

                advanceTimeBy(7_999)
                runCurrent()
                assertEquals(listOf(EstadoWidget.AAtualizar), estados)
                assertFalse(toque.isCompleted)

                advanceTimeBy(1)
                runCurrent()
                assertEquals(listOf(EstadoWidget.AAtualizar, EstadoWidget.SemLigacao), estados)
                assertEquals(listOf("728"), desenhos.last().second.linhas.map { it.linha })
                assertTrue(toque.isCompleted)
            }
        } finally {
            solta.countDown()
        }
    }

    @Test(timeout = 30_000)
    fun pedidoQueNaoAceitaSerCanceladoTambemCorta() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        val nunca = CompletableDeferred<Resultado<List<Chegada>>>()
        atualizador { _, _ -> withContext(NonCancellable) { nunca.await() } }.aoTocar(1)
        assertEquals(8_000, testScheduler.currentTime)
        assertEquals(listOf(EstadoWidget.AAtualizar, EstadoWidget.SemLigacao), estados)
    }

    @Test fun respostaAntesDos8SegundosConta() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        atualizador { _, _ -> delay(7_999); Resultado.Ok(emptyList()) }.aoTocar(1)
        assertEquals(EstadoWidget.Normal, estados.last())
    }

    @Test fun falhaDizSemLigacaoComAsGuardadas() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.minusSeconds(60)
        atualizador { _, _ -> Resultado.Falha(null, false) }.aoTocar(1)
        val ultimo = desenhos.last().second
        assertEquals(EstadoWidget.SemLigacao, ultimo.estado)
        assertEquals(listOf("728"), ultimo.linhas.map { it.linha })
        assertEquals("13:59", ultimo.atualizadoAs)
    }

    @Test fun pedidoQueRebentaDizSemLigacao() = runTest {
        dados.escolher(1, cais.chave, "Cais")
        atualizador { _, _ -> throw IOException("rebentou") }.aoTocar(1)
        assertEquals(EstadoWidget.SemLigacao, estados.last())
    }

    @Test fun semParagemPedeParaEscolher() = runTest {
        val a = atualizador()
        a.aoTocar(5)
        a.redesenhar(listOf(5))
        assertEquals(0, pedidos)
        assertEquals(listOf(null, null), desenhos.map { it.second.paragem })
        assertNull("sem paragem não conta como tentativa", dados.ultimaTentativa(5))
    }

    @Test fun chaveQueNaoSeLeContaComoSemParagem() = runTest {
        dados.escolher(1, "isto não é uma chave", "Cais")
        atualizador().aoTocar(1)
        assertEquals(0, pedidos)
        assertNull(desenhos.single().second.paragem)
    }

    private val outra = RefParagem(Fonte.CM, "060500")
    private val pedidasPara = mutableListOf<RefParagem>()

    /** Um `pedir` que guarda a resposta como o repositório faz, para o redesenho a seguir a ler. */
    private fun guardando(linha: String): suspend (RefParagem, Instant) -> Resultado<List<Chegada>> = { ref, em ->
        pedidasPara += ref
        val nova = listOf(chegada(linha, em.plusSeconds(600)))
        guardadas[ref] = nova to em
        Resultado.Ok(nova)
    }

    @Test fun aoAbrirSemWidgetsNaoPedeNada() = runTest {
        dados.escolher(1, cais.chave, "Cais") // sobra de um widget que já não existe
        atualizador().aoAbrirApp()
        assertEquals(0, pedidos)
        assertTrue(desenhos.isEmpty())
    }

    @Test fun aoAbrirComWidgetsSemParagemNaoPedeNada() = runTest {
        widgets += listOf(1, 2)
        atualizador().aoAbrirApp()
        assertEquals(0, pedidos)
        assertEquals(listOf(null, null), desenhos.map { it.second.paragem })
    }

    @Test fun aoAbrirRecenteNaoPede() = runTest {
        widgets += 1
        dados.escolher(1, cais.chave, "Cais")
        // Exatamente 60 s: só "mais de 60 s" conta como velha.
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.minusSeconds(60)
        atualizador().aoAbrirApp()
        assertEquals(0, pedidos)
        assertEquals(listOf("728"), desenhos.single().second.linhas.map { it.linha })
    }

    @Test fun aoAbrirVelhaPedeUmaVezPorParagem() = runTest {
        widgets += listOf(1, 2, 3)
        dados.escolher(1, cais.chave, "Cais")
        dados.escolher(2, cais.chave, "Cais")
        dados.escolher(3, outra.chave, "Outra")
        guardadas[cais] = listOf(chegada("velha", inicio.plusSeconds(600))) to inicio.minusSeconds(61)
        // A segunda paragem não tem nada guardado: também se pede.
        atualizador(pedir = guardando("nova")).aoAbrirApp()

        assertEquals(listOf(cais, outra), pedidasPara)
        assertEquals(listOf(1, 2, 3), desenhos.map { it.first })
        assertEquals(listOf("nova", "nova", "nova"), desenhos.map { it.second.linhas.single().linha })
        assertTrue(estados.all { it == EstadoWidget.Normal })
        assertNull("abrir a app não conta como toque no ↻", dados.ultimaTentativa(1))
    }

    @Test fun aoAbrirComAGuardadaNoFuturoPede() = runTest {
        widgets += 1
        dados.escolher(1, cais.chave, "Cais")
        // O relógio andou para trás: uma guardada "de amanhã" não pode impedir os pedidos até ele voltar.
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.plusSeconds(3600)
        atualizador(pedir = guardando("nova")).aoAbrirApp()
        assertEquals(listOf(cais), pedidasPara)
    }

    @Test fun aoAbrirFalhaNaoParte() = runTest {
        widgets += listOf(1, 2, 3)
        dados.escolher(1, cais.chave, "Cais")
        dados.escolher(2, outra.chave, "Outra")
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.minusSeconds(3600)
        atualizador { ref, _ ->
            pedidasPara += ref
            if (ref == cais) throw IOException("rebentou") else Resultado.Falha(null, false)
        }.aoAbrirApp()

        assertEquals("uma falha não impede o pedido da paragem seguinte", listOf(cais, outra), pedidasPara)
        assertEquals("nem o redesenho, com o que estava guardado", listOf(1, 2, 3), desenhos.map { it.first })
        assertEquals(listOf("728"), desenhos[0].second.linhas.map { it.linha })
    }

    @Test fun aoAbrirCanceladoNaoEngoleOCancelamento() = runTest {
        widgets += 1
        dados.escolher(1, cais.chave, "Cais")
        val toque = launch { atualizador { _, _ -> delay(60_000); Resultado.Ok(emptyList()) }.aoAbrirApp() }
        runCurrent()
        toque.cancel()
        runCurrent()
        assertTrue("sair da app corta o pedido", toque.isCancelled)
        assertTrue("e não desenha por cima do redesenho da saída", desenhos.isEmpty())
    }

    @Test fun aoSairNaoVaiARede() = runTest {
        widgets += listOf(1, 2)
        dados.escolher(1, cais.chave, "Cais")
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio.minusSeconds(3600)
        atualizador().aoSairDaApp()
        assertEquals(0, pedidos)
        assertEquals(listOf(1, 2), desenhos.map { it.first })
        assertEquals(listOf("728"), desenhos[0].second.linhas.map { it.linha })
        assertTrue("uma hora depois da última atualização", desenhos[0].second.cinzento)
        assertNull(desenhos[1].second.paragem)
    }

    @Test fun apagarDadosEsqueceOsWidgets() = runTest {
        widgets += listOf(1, 2)
        dados.escolher(1, cais.chave, "Cais")
        dados.escolher(2, outra.chave, "Outra")
        dados.marcarTentativa(1, inicio)
        guardadas[cais] = listOf(chegada("728", inicio.plusSeconds(600))) to inicio
        atualizador().aoApagarDados()

        assertNull(dados.escolha(1))
        assertNull(dados.escolha(2))
        assertNull(dados.ultimaTentativa(1))
        assertEquals("os widgets continuam no ecrã, a pedir uma paragem", listOf(null, null), desenhos.map { it.second.paragem })
        assertEquals(0, pedidos)
    }
}
