package pt.pixellusitano.paragemcerta.ui.viagem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.ui.rotas.percursos
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

/** O mapa que segue a pessoa e o painel por baixo (o texto da viagem está em ViagemScreenshotTest). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class ViagemEcraTest {
    @get:Rule val regra = createComposeRule()

    private val ex = Exemplo()
    private val plano = ex.plano()

    /** O que o mapa recebeu da última vez: se a câmara devia seguir a pessoa. */
    private var seguir: Boolean? = null

    // O MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo que recebe o mesmo que o verdadeiro
    // e "deixa de seguir" quando o arrastam, como o MapLibre avisa quando a pessoa mexe na câmara.
    @Composable
    private fun MapaFalso(aSeguir: Boolean, aoDeixarDeSeguir: () -> Unit) {
        SideEffect { seguir = aSeguir }
        Box(
            // No escuro, o fundo do estilo escuro do OpenFreeMap: a folha e os botões por cima têm de se ver contra ele.
            Modifier.fillMaxSize().background(if (LocalEscuro.current) Color(0xFF0C0C0C) else Color(0xFFDDE5F0)).testTag("mapa")
                .pointerInput(Unit) { detectDragGestures { _, _ -> aoDeixarDeSeguir() } },
        )
    }

    @Composable
    private fun Ecra(e: EstadoViagem, comLocalizacao: Boolean) = ViagemConteudo(
        ecraDaViagem(plano, e, ex.viagem(plano)), aoSilenciar = {}, aoJaSai = {}, aoParar = {}, aoPartilhar = {}, aoVoltar = {},
        modifier = Modifier.width(360.dp), comLocalizacao = comLocalizacao, mapa = { s, d -> MapaFalso(s, d) },
    )

    private fun mostrar(e: EstadoViagem = ex.aBordo(plano, 1, k = 2), modo: ModoTema = ModoTema.CLARO, comLocalizacao: Boolean = true) =
        regra.setContent { TemaParagemCerta(modo) { Ecra(e, comLocalizacao) } }

    private fun texto(t: String) = regra.onNodeWithText(t)

    private fun arrastarOMapa() = regra.onNodeWithTag("mapa").performTouchInput { swipeLeft() }

    @Test fun oMapaFicaEmCimaEOTextoDaViagemNoPainelPorBaixo() {
        mostrar()
        regra.onNodeWithTag("mapa").assertIsDisplayed()
        val metade = regra.onRoot().fetchSemanticsNode().boundsInRoot.height / 2
        listOf("Etapa 2 de 4 · Agora", "Faltam 3 paragens · sais em Oriente", "Estás aqui").forEach {
            texto(it).assertIsDisplayed()
            assertTrue("$it no painel, por baixo do mapa", texto(it).fetchSemanticsNode().boundsInRoot.top > metade)
        }
        regra.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun asFilasDasParagensTemMargemDe6dpEPeloMenos48dp() {
        mostrar()
        val dp = regra.density.density
        fun topo(t: String) = texto(t).fetchSemanticsNode().boundsInRoot.top / dp
        // Da fila "Estás aqui" (nome e etiqueta, cerca de 46 dp) até à seguinte: 6 + 6 dp de margem, e não 12 + 12.
        val passo = topo("Belém") - topo("Estás aqui")
        assertTrue("passo de $passo dp", passo in 48f..62f)
        // Uma fila de uma linha não fica abaixo dos 48 dp.
        assertTrue("passo de ${topo("Santos") - topo("Cais do Sodré")} dp", topo("Santos") - topo("Cais do Sodré") >= 48f)
    }

    @Test fun oMapaComecaASeguirAPessoaEArrastarOMapaMostraSeguirMe() {
        mostrar()
        assertEquals(true, seguir)
        texto("Seguir-me").assertDoesNotExist()

        arrastarOMapa()
        regra.waitForIdle()
        assertEquals(false, seguir)
        texto("Seguir-me").assertIsDisplayed()

        texto("Seguir-me").performClick()
        regra.waitForIdle()
        assertEquals(true, seguir)
        texto("Seguir-me").assertDoesNotExist()
    }

    @Test fun semLocalizacaoOMapaNaoSegueENaoHaSeguirMe() {
        mostrar(comLocalizacao = false)
        assertEquals(false, seguir)
        arrastarOMapa()
        regra.waitForIdle()
        texto("Seguir-me").assertDoesNotExist()
        texto("Etapa 2 de 4 · Agora").assertIsDisplayed()
    }

    @Test fun oPercursoTemTodasAsEtapasEEsbateAsQueNaoSaoAAtual() {
        val etapas = plano.itinerario.etapas
        val todas = plano.itinerario.percursos()
        assertEquals(etapas.map { it.aPe }, todas.map { it.aPe })
        assertTrue("sem etapa atual, nenhuma se esbate (o detalhe da rota)", todas.none { it.esbatido })

        val naSegunda = plano.itinerario.percursos(atual = 1)
        assertEquals(listOf(true, false, true, true), naSegunda.map { it.esbatido })
        assertEquals(todas.map { it.pontos }, naSegunda.map { it.pontos })
        assertEquals(todas.map { it.cor }, naSegunda.map { it.cor })
    }

    @Test fun asMarcasDaEtapaEmVeiculoSaoAsParagensComASaidaDestacada() {
        val marcas = marcasDe(plano.itinerario.etapas[1])
        assertEquals(listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente"), marcas.map { it.nome })
        assertEquals(listOf(false, false, false, false, false, true), marcas.map { it.sai })
        assertEquals(38.70 + 5 * 0.005, marcas.last().lat, 1e-9)
    }

    @Test fun aPeAMarcaEOFimDaEtapa() {
        val etapa = plano.itinerario.etapas[0]
        val marcas = marcasDe(etapa)
        assertEquals(1, marcas.size)
        assertEquals("Cais do Sodré", marcas[0].nome)
        assertTrue(marcas[0].sai)
        assertEquals(etapa.para.lat, marcas[0].lat, 0.0)
    }

    private fun ecraDe(e: EstadoViagem) = ecraDaViagem(plano, e, ex.viagem(plano))

    @Test fun oAutocarroDoEstadoVaiParaOMapaComALinhaDaEtapa() {
        val h = Instant.parse("2026-07-01T10:05:00Z")
        val v = veiculoDaViagem(ecraDe(ex.aBordo(plano, 1, k = 2).copy(veiculoLat = 38.71, veiculoLon = -9.13, ultimoVeiculo = h)))!!
        assertEquals(38.71, v.lat, 0.0)
        assertEquals(-9.13, v.lon, 0.0)
        assertEquals("736", v.linhaId)
        assertEquals(h, v.ultimoSinal)
    }

    @Test fun semAutocarroNoEstadoOuForaDeUmaEtapaAbordoNaoHaAutocarroNoMapa() {
        val comPosicao = ex.aBordo(plano, 1, k = 2).copy(veiculoLat = 38.71, veiculoLon = -9.13, ultimoVeiculo = Instant.parse("2026-07-01T10:05:00Z"))
        assertNull(veiculoDaViagem(ecraDe(ex.aBordo(plano, 1, k = 2))))
        assertNull(veiculoDaViagem(ecraDe(comPosicao.copy(fase = Fase.TERMINADA, terminada = true))))
        assertNull(veiculoDaViagem(ecraDe(comPosicao.copy(etapa = 2, fase = Fase.A_PE)))) // a pé não há linha
    }

    @Test fun viagem_mapa() = captureRoboImage("src/test/screenshots/viagem_mapa.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aBordo(plano, 1, k = 2), comLocalizacao = true) }
    }

    @Test fun viagem_mapa_escuro() = captureRoboImage("src/test/screenshots/viagem_mapa_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Ecra(ex.aBordo(plano, 1, k = 2), comLocalizacao = true) }
    }
}
