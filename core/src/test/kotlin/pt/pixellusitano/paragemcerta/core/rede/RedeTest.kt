package pt.pixellusitano.paragemcerta.core.rede

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class RedeTest {
    @Test fun converteForaDaLinhaDeQuemPede() {
        val ui = newSingleThreadContext("ui")
        val linhaUi = runBlocking(ui) { Thread.currentThread() }
        val r = runBlocking(ui) { HttpFalso({ Resposta(200, "[]") }).pedir("u") { Thread.currentThread() } }
        assertNotEquals(linhaUi, (r as Resultado.Ok).valor)
        ui.close()
    }

    @Test fun cancelamentoNaoViraFalha() {
        val resultado = runCatching {
            runBlocking { HttpFalso({ Resposta(200, "[]") }).pedir("u") { throw kotlinx.coroutines.CancellationException("saiu do ecrã") } }
        }
        assertTrue(resultado.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun dadosEstranhosNaoRebentam() {
        val r = runBlocking { HttpFalso({ Resposta(200, "x") }).pedir("u") { java.time.Instant.parse(it) } }
        assertEquals(Resultado.Falha(200, limite = false), r)
    }
}
