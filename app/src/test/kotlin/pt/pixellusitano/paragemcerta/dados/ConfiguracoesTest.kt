package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ConfiguracoesTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private fun configuracoes(vararg respostas: () -> Resposta) =
        Configuracoes(contexto, ServidorParagemCerta(HttpPorUrl("config.json" to respostas.toList())))
    private val semRede = { throw IOException("sem rede") }
    private val comConta = { Resposta(200, fixture("servidor-config.json").replace("\"conta\": false", "\"conta\": true")) }

    @Before fun semGuardada() {
        File(contexto.filesDir, "config.json").delete()
    }

    @Test fun semRedeUsaADaApp() = runTest {
        val c = configuracoes(semRede)
        c.aoAbrir()
        assertTrue(c.atual.value.interruptores.conta) // a cópia da app segue o servidor publicado
        assertTrue(c.atual.value.canaisReclamacao.any { it.nome == "Livro de Reclamações Eletrónico" })
    }

    @Test fun novaFicaGuardada() = runTest {
        assertTrue(fixture("servidor-config.json").contains("\"conta\": false")) // senão a troca de comConta não faz nada
        val c = configuracoes(comConta)
        c.aoAbrir()
        assertTrue(c.atual.value.interruptores.conta)
        // Na próxima abertura, ainda sem rede, já começa com a nova.
        assertTrue(configuracoes(semRede).atual.value.interruptores.conta)
    }

    @Test fun jsonEstranhoMantemAAnterior() = runTest {
        configuracoes(comConta).aoAbrir()
        val c = configuracoes({ Resposta(200, "<html>manutenção</html>") })
        c.aoAbrir()
        assertTrue(c.atual.value.interruptores.conta)
        assertTrue(configuracoes(semRede).atual.value.interruptores.conta)
    }
}
