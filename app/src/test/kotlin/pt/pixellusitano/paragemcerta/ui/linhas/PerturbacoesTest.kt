package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.perturbacoes.FontePerturbacao
import pt.pixellusitano.paragemcerta.core.perturbacoes.Perturbacao
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.FontePerturbacoes
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

private val AGORA = Instant.parse("2026-11-03T10:00:00Z")
private val INICIO = AGORA.epochSecond - 3600
private val DIA_12 = Instant.parse("2026-11-12T22:00:00Z")
private const val NAO_CARREGOU = "Não foi possível carregar as perturbações. Tenta outra vez mais tarde."

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class PerturbacoesTest {
    @get:Rule val regra = createComposeRule()

    private val preferencias = Preferencias(LojaEmMemoria())
    private val pedidos = mutableListOf<String>()
    private var alertas = "[]"
    private var codigo = 200

    // A Carris Metropolitana do teste: responde [alertas] aos avisos (com o [codigo]: fora de 200 é uma falha).
    private val fonte = FontePerturbacoes(CarrisMetropolitana(object : Http {
        override suspend fun get(url: String): Resposta {
            pedidos += url
            return Resposta(codigo, alertas)
        }
    }, "https://cm.teste/"))

    // As tuas linhas: a 1009 e a 1604 da CM e a 728 da Carris.
    private val minhas = setOf("1009", "1604", "728")

    /** Os avisos da CM, cada um com o título e as linhas dele (ativos de há 1 h até amanhã). */
    private fun responder(vararg avisos: Pair<String, List<String>>) {
        alertas = avisos.joinToString(",", "[", "]") { (titulo, linhas) ->
            """{"active_period":[{"start":$INICIO,"end":${AGORA.epochSecond + 86400}}],
               |"header_text":{"translation":[{"language":"pt","text":"$titulo"}]},
               |"description_text":{"translation":[{"language":"pt","text":"Mais sobre: $titulo"}]},
               |"informed_entity":[${linhas.joinToString(",") { """{"route_id":"${it}_0"}""" }}]}""".trimMargin()
        }
    }

    private fun id(titulo: String) = "cm:$titulo|$INICIO"

    private fun perturbacao(titulo: String, linhas: Set<String>, fonte: FontePerturbacao = FontePerturbacao.CM, fim: Instant? = null) =
        Perturbacao("x:$titulo", fonte, linhas, titulo, "Mais sobre: $titulo", null, fim)

    /** Os avisos chegam por outra thread: espera que o texto apareça. */
    private fun esperarPor(texto: String) = regra.waitUntil(3_000) { regra.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }

    private fun vistas() = runBlocking { preferencias.perturbacoesVistas.first() }

    @Test fun faixaSoComAsNovasDasMinhasLinhas() {
        responder(
            "Obras na Rua Direita" to listOf("1009"), "Desvio em Odivelas" to listOf("1604", "2000"),
            "Já vista" to listOf("1009"), "Noutra linha" to listOf("3708"),
        )
        runBlocking { preferencias.marcarVistas(setOf(id("Já vista"))) }
        regra.setContent { FaixaAoAbrir(fonte, { minhas }, preferencias, escondida = false, aoAbrir = {}, agora = { AGORA }) }
        esperarPor("2 perturbações novas nas tuas linhas")
    }

    @Test fun fecharMarcaComoVistas() {
        responder("Obras na Rua Direita" to listOf("1009"))
        regra.setContent { FaixaAoAbrir(fonte, { minhas }, preferencias, escondida = false, aoAbrir = {}, agora = { AGORA }) }
        esperarPor("1 perturbação nova nas tuas linhas")
        regra.onNodeWithContentDescription("Fechar").performClick()
        regra.onNodeWithText("1 perturbação nova nas tuas linhas").assertDoesNotExist()
        regra.waitUntil(3_000) { vistas() == setOf(id("Obras na Rua Direita")) }
    }

    @Test fun faixaNaoTapaDialogos() {
        responder("Obras na Rua Direita" to listOf("1009"))
        var dialogo by mutableStateOf(true)
        regra.setContent { FaixaAoAbrir(fonte, { minhas }, preferencias, escondida = dialogo, aoAbrir = {}, agora = { AGORA }) }
        // Dá tempo à resposta de chegar: com o diálogo à vista, a faixa não aparece na mesma.
        regra.waitForIdle()
        Thread.sleep(300)
        regra.waitForIdle()
        assertEquals(1, pedidos.size)
        regra.onNodeWithText("1 perturbação nova nas tuas linhas").assertDoesNotExist()
        // Fechado o diálogo, a faixa aparece sem pedir outra vez.
        dialogo = false
        esperarPor("1 perturbação nova nas tuas linhas")
        assertEquals(1, pedidos.size)
    }

    @Test fun faixaAbreAsDasMinhasLinhasPrimeiro() {
        responder("Noutra linha" to listOf("3708"), "Obras na Rua Direita" to listOf("1009"))
        var aberto by mutableStateOf(false)
        regra.setContent {
            if (aberto) PerturbacoesEcra(fonte, { minhas }, aoVoltar = {}, agora = { AGORA })
            else FaixaAoAbrir(fonte, { minhas }, preferencias, escondida = false, aoAbrir = { aberto = true }, agora = { AGORA })
        }
        esperarPor("1 perturbação nova nas tuas linhas")
        regra.onNodeWithText("1 perturbação nova nas tuas linhas").performClick()
        regra.waitUntil(3_000) { vistas() == setOf(id("Obras na Rua Direita")) }
        esperarPor("Nas tuas linhas")
        esperarPor("Noutra linha")
        // As das tuas linhas primeiro, depois as outras por operador.
        val ordem = listOf("Nas tuas linhas", "Obras na Rua Direita", "Carris Metropolitana", "Noutra linha")
            .map { regra.onNodeWithText(it).getUnclippedBoundsInRoot().top.value }
        assertEquals(ordem.sorted(), ordem)
    }

    @Test fun listaPorOperadorComFiltro() {
        val obras = perturbacao("Obras na Rua Direita", setOf("1009"), fim = DIA_12)
        val metro = perturbacao("Linha Verde: perturbação", setOf("VD"), FontePerturbacao.METRO)
        var filtro by mutableStateOf<Modo?>(null)
        regra.setContent {
            PerturbacoesConteudo(emptyList(), listOf(obras, metro), filtro, { filtro = it }, aoVoltar = {}, modifier = Modifier.width(360.dp))
        }
        regra.onNodeWithText("Carris Metropolitana").assertExists()
        regra.onNodeWithText("Metro de Lisboa").assertExists()
        // A data de fim na hora de Lisboa; o texto só ao tocar no título.
        regra.onNodeWithText("até 12/11").assertExists()
        regra.onNodeWithText("Mais sobre: Obras na Rua Direita").assertDoesNotExist()
        regra.onNodeWithText("Obras na Rua Direita").performClick()
        regra.onNodeWithText("Mais sobre: Obras na Rua Direita").assertExists()
        // Sem nada nas tuas linhas, não há essa secção.
        regra.onNodeWithText("Nas tuas linhas").assertDoesNotExist()
        regra.onNodeWithText("Ⓜ Metro").performClick()
        assertEquals(Modo.METRO, filtro)
        regra.onNodeWithText("Obras na Rua Direita").assertDoesNotExist()
        regra.onNodeWithText("Linha Verde: perturbação").assertExists()
        regra.onNodeWithText("Todos").performClick()
        assertNull(filtro)
        regra.onNodeWithText("Obras na Rua Direita").assertExists()
    }

    @Test fun falhaDaCmNaoEUmaListaVazia() {
        codigo = 503
        runBlocking { assertNull(fonte.todas(AGORA)) }
        // A falha não fica guardada: o pedido seguinte tenta outra vez e já vê as perturbações.
        codigo = 200
        responder("Obras na Rua Direita" to listOf("1009"))
        runBlocking { assertEquals(listOf("Obras na Rua Direita"), fonte.todas(AGORA)?.map { it.titulo }) }
        assertEquals(2, pedidos.size)
    }

    @Test fun faixaEViagemTratamAFalhaDaCmComoSemPerturbacoes() {
        codigo = 503
        runBlocking { assertEquals(emptyList<Perturbacao>(), fonte.dasLinhas(minhas, AGORA)) }
    }

    @Test fun linhaPerturbacoesContaAsAtivas() {
        responder("Obras na Rua Direita" to listOf("1009"), "Desvio em Odivelas" to listOf("1604"))
        regra.setContent { LinhaPerturbacoes(fonte, aoAbrir = {}, agora = { AGORA }) }
        esperarPor("Perturbações (2)")
    }

    @Test fun linhaPerturbacoesEscondidaQuandoACmFalha() {
        codigo = 503
        regra.setContent { LinhaPerturbacoes(fonte, aoAbrir = {}, agora = { AGORA }) }
        // Dá tempo ao pedido de falhar: sem saber quantas há, a linha "Perturbações (0)" não pode aparecer.
        regra.waitUntil(3_000) { pedidos.size == 1 }
        regra.waitForIdle()
        Thread.sleep(300)
        regra.waitForIdle()
        regra.onNodeWithText("Perturbações (0)").assertDoesNotExist()
    }

    @Test fun ecraSemPerturbacoesDizQueNaoHa() {
        regra.setContent { PerturbacoesEcra(fonte, { minhas }, aoVoltar = {}, agora = { AGORA }) }
        esperarPor("Não há perturbações para mostrar.")
        regra.onNodeWithText(NAO_CARREGOU).assertDoesNotExist()
    }

    @Test fun ecraComFalhaDaCmDizQueNaoCarregou() {
        codigo = 503
        regra.setContent { PerturbacoesEcra(fonte, { minhas }, aoVoltar = {}, agora = { AGORA }) }
        esperarPor(NAO_CARREGOU)
        regra.onNodeWithText("Não há perturbações para mostrar.").assertDoesNotExist()
    }

    @Test fun ecraACarregarDizACarregar() {
        regra.setContent {
            PerturbacoesConteudo(emptyList(), emptyList(), null, {}, aoVoltar = {}, modifier = Modifier.width(360.dp), aCarregar = true)
        }
        regra.onNodeWithText("A carregar…").assertExists()
        regra.onNodeWithText("Não há perturbações para mostrar.").assertDoesNotExist()
    }

    @Test fun faixa_perturbacoes_claro() = captureRoboImage("src/test/screenshots/faixa_perturbacoes_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { FaixaPerturbacoes(2, aoAbrir = {}, aoFechar = {}) }
    }

    @Test fun perturbacoes_claro() = captureRoboImage("src/test/screenshots/perturbacoes_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            PerturbacoesConteudo(
                minhas = listOf(perturbacao("Cascais | 1625: Desvio de percurso Alcoitão", setOf("1625"), fim = DIA_12)),
                outras = listOf(
                    perturbacao("Odivelas | 1604: Paragem desativada", setOf("1604")),
                    perturbacao("Setúbal | 4725: Obras na Avenida", setOf("4725"), fim = DIA_12),
                ),
                filtro = null, aoFiltrar = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
            )
        }
    }
}
