package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp") // ecrã alto: a lista inteira (os botões das páginas e uma rota) cabe sem deslizar
class CartaoRotaTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val agora = Instant.parse("2026-11-01T11:10:00Z") // Lisboa está em UTC em novembro
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    private fun partida(programada: String, prevista: String?) =
        Chegada(Fonte.CM, "1", "210", "Oriente", Instant.parse(programada), prevista?.let(Instant::parse), null, null, Modo.AUTOCARRO, null, null)

    private val aoVivo3 = partida("2026-11-01T11:12:00Z", "2026-11-01T11:13:00Z")
    private val aoVivo18 = partida("2026-11-01T11:28:00Z", "2026-11-01T11:28:00Z")
    private val horario = partida("2026-11-01T11:37:00Z", null)

    @Test fun textoPartidasMinutosEHoras() {
        assertEquals(
            listOf("3" to true, "18" to true, "11:37" to false),
            textoPartidas(listOf(aoVivo3, aoVivo18, horario), agora),
        )
    }

    @Test fun textoPartidasNosLimites() {
        val agoraMesmo = partida("2026-11-01T11:10:00Z", "2026-11-01T11:10:20Z")
        val umaHora = partida("2026-11-01T12:10:00Z", "2026-11-01T12:10:00Z")
        val depoisDeUmaHora = partida("2026-11-01T12:11:00Z", "2026-11-01T12:11:00Z")
        // Menos de um minuto é "a chegar"; até 60 min mostram-se os minutos; acima, a hora (ainda ao vivo).
        assertEquals(
            listOf("a chegar" to true, "60" to true, "12:11" to true),
            textoPartidas(listOf(agoraMesmo, umaHora, depoisDeUmaHora), agora),
        )
    }

    private fun cartao(partidas: List<Chegada>?) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            CartaoRota(rota, emptySet(), comAlerta = false, aoAbrir = {}, partidas = partidas, agora = agora)
        }
    }

    // O cartão limpa a semântica dos filhos (lê-se só a descrição), por isso o texto procura-se na árvore sem fundir.
    private fun saiDe() = regra.onNodeWithText(contexto.getString(R.string.rotas_sai_de, "Cais Sodré"), useUnmergedTree = true)

    private fun parteDe(texto: String) =
        regra.onNodeWithText(contexto.getString(R.string.rotas_parte_de, "Cais Sodré") + " " + texto, useUnmergedTree = true)

    @Test fun cartaoComPartidasMostraParteDe() {
        cartao(listOf(aoVivo3, aoVivo18, horario))
        parteDe("3, 18, 11:37").assertIsDisplayed()
        saiDe().assertDoesNotExist()
    }

    @Test fun soHorasDizAs() {
        // Só horário (Metro, CP): "dentro de 11:37" não é português; com horas lê-se "às 11:37".
        cartao(listOf(horario, partida("2026-11-01T11:52:00Z", null)))
        regra.onNodeWithText("Parte de Cais Sodré às 11:37, 11:52", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("dentro de", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aMenosDeUmMinutoDizAChegar() {
        // "dentro de a chegar" não é português: com a 1.ª partida a menos de 1 min, "Parte de X: a chegar".
        cartao(listOf(partida("2026-11-01T11:10:00Z", "2026-11-01T11:10:20Z"), aoVivo18, horario))
        regra.onNodeWithText("Parte de Cais Sodré: a chegar, 18, 11:37", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("dentro de", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun cartaoSemPartidasMostraSaiDe() {
        cartao(null)
        saiDe().assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.rotas_parte_de, "Cais Sodré"), substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun cartaoComPartidasVaziasMostraSaiDe() {
        cartao(emptyList())
        saiDe().assertIsDisplayed()
    }

    @Test fun cartaoEscondeAsPartidasQueJaPassaram() {
        // Passou há 5 min (a lista fica aberta e o relógio anda): sobra a seguinte; se não sobrar nenhuma, volta o "Sai de".
        val passou = partida("2026-11-01T11:04:00Z", "2026-11-01T11:05:00Z")
        cartao(listOf(passou, aoVivo18))
        parteDe("18").assertIsDisplayed()
    }

    private val umaRota = listOf(rota)
    private val prontas = EstadoRotas.Prontas(umaRota, etiquetas(umaRota))

    private fun lista(estado: EstadoRotas, aoMaisCedo: () -> Unit = {}, aoMaisTarde: () -> Unit = {}) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            RotasConteudo(
                pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)), estado = estado, filtros = Filtros(),
                alertas = emptySet(), agora = agora,
                aoEscolherOrigem = {}, aoEscolherDestino = {}, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
                aoAbrirRota = {}, aoTentarOutraVez = {}, aoMaisCedo = aoMaisCedo, aoMaisTarde = aoMaisTarde,
            )
        }
    }

    @Test fun maisTardeChamaOCallback() {
        var tarde = 0
        var cedo = 0
        lista(prontas.copy(anterior = "EARLIER|1", seguinte = "LATER|2"), aoMaisCedo = { cedo++ }, aoMaisTarde = { tarde++ })
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).performClick()
        assertEquals(1, tarde)
        assertEquals(0, cedo)
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_cedo)).performClick()
        assertEquals(1, cedo)
    }

    @Test fun botoesDasPaginasFicamAntesDasRotas() {
        // No topo chegam-se sem percorrer as rotas e, depois do toque, continuam debaixo do dedo (a página nova começa de cima).
        lista(prontas.copy(anterior = "EARLIER|1", seguinte = "LATER|2"))
        val botao = regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).getUnclippedBoundsInRoot()
        val cartao = saiDe().getUnclippedBoundsInRoot()
        assertTrue("os botões (fundo ${botao.bottom}) deviam ficar por cima do cartão (topo ${cartao.top})", botao.bottom <= cartao.top)
    }

    @Test fun semCursorNaoMostraOsBotoes() {
        lista(prontas.copy(anterior = null, seguinte = "LATER|2"))
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_cedo)).assertDoesNotExist()
    }

    @Test fun semCursorNaoMostraMaisTarde() {
        lista(prontas.copy(anterior = "EARLIER|1", seguinte = null))
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).assertDoesNotExist()
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_cedo)).assertIsDisplayed()
    }

    @Test fun erroDaPaginaAvisaEMantemAsRotas() {
        lista(prontas.copy(seguinte = "LATER|2", erroPagina = true))
        regra.onNodeWithText(contexto.getString(R.string.rotas_erro_pagina)).assertIsDisplayed()
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).assertIsDisplayed()
    }

    @Test fun aCarregarPaginaEscondeOsBotoes() {
        lista(prontas.copy(anterior = "EARLIER|1", seguinte = "LATER|2", aCarregarPagina = true))
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_tarde)).assertDoesNotExist()
        regra.onNodeWithText(contexto.getString(R.string.rotas_mais_cedo)).assertDoesNotExist()
    }
}
