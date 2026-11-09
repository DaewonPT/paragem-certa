package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.DetalheLinha
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.SentidoLinha
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant
import java.time.LocalTime

// A 15E da fixture linhas.json (Pç. Figueira - Algés) com as suas três paragens, nos dois sentidos; é também a das capturas.
internal val AGORA_DA_LINHA: Instant = Instant.parse("2026-11-03T10:00:00Z")
internal val REF_15E = RefLinha(Fonte.TRANSITOUS, "pt-Carris:15E")
private fun paragemDaLinha(id: String, nome: String) = Paragem(RefParagem(Fonte.TRANSITOUS, id), nome, 38.7, -9.1, null)
internal val PRACA_FIGUEIRA = paragemDaLinha("pt-Carris_920", "Pç. Figueira")
internal val CAIS_SODRE = paragemDaLinha("pt-Carris_3803", "Cais Sodré")
internal val ALGES = paragemDaLinha("pt-Carris_4215", "Algés (Jardim)")
internal val DETALHE_15E = DetalheLinha(
    ref = REF_15E, nome = "15E", nomeLongo = "Pç. Figueira - Algés", operador = "Carris", modo = Modo.ELETRICO, cor = null, corTexto = null,
    rotas = setOf("pt-Carris_76_0", "pt-Carris_76_1"),
    sentidos = listOf(
        SentidoLinha(0, "Algés", setOf("Algés"), listOf(PRACA_FIGUEIRA, CAIS_SODRE, ALGES), emptySet()),
        SentidoLinha(1, "Pç. Figueira", setOf("Pç. Figueira"), listOf(ALGES, CAIS_SODRE, PRACA_FIGUEIRA), emptySet()),
    ),
)

internal fun chegadaDaLinha(minutos: Long, atrasoSegundos: Long? = null) = Chegada(
    Fonte.TRANSITOUS, "pt-Carris_76_0", "15E", "Algés", AGORA_DA_LINHA.plusSeconds(minutos * 60),
    atrasoSegundos?.let { AGORA_DA_LINHA.plusSeconds(minutos * 60 + it) }, "viagem-$minutos", null, Modo.ELETRICO, null, null,
)

internal val ESTADO_DA_LINHA = EstadoLinha(
    detalhe = DETALHE_15E, sentido = 0, escolhida = CAIS_SODRE,
    chegadas = EstadoChegadas(listOf(chegadaDaLinha(3, 0), chegadaDaLinha(9), chegadaDaLinha(15, 120), chegadaDaLinha(24)), AGORA_DA_LINHA, false, false),
)

private fun relato(id: String, tipo: String, confirmado: Boolean, confirmacoes: Int) = RelatoRemoto(
    id, tipo, "TRANSITOUS:pt-Carris_3803", "15E", AGORA_DA_LINHA.minusSeconds(600).toEpochMilli(),
    AGORA_DA_LINHA.plusSeconds(3600).toEpochMilli(), confirmacoes, confirmado,
)

// Um ecrã alto: a lista inteira fica composta e à vista, e cada teste vê o que o ecrã tem sem rolar; o do ecrã de telemóvel está no fim.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1600dp")
class LinhaEcraTest {
    @get:Rule val regra = createComposeRule()

    private val escolhidas = mutableListOf<Paragem>()
    private val aberta = mutableListOf<Paragem>()
    private val sentidos = mutableListOf<Int>()
    private var estrelas = 0
    private var habituais = 0
    private var horarios = 0
    private var mapasOficiais = 0
    private var toquesNoMapa = 0

    // O MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo.
    @Composable
    private fun Ecra(estado: EstadoLinha = ESTADO_DA_LINHA, comMapaOficial: Boolean = false, letra: Float = 1f) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = letra)) {
            TemaParagemCerta(ModoTema.CLARO) {
                LinhaConteudo(
                    estado = estado,
                    aoMudarSentido = { sentidos += it },
                    aoEscolherParagem = { escolhidas += it },
                    aoAbrirParagem = { aberta += it },
                    aoFavorita = { estrelas++ },
                    aoUsarComoHabitual = { habituais++ },
                    aoVerHorario = { horarios++ },
                    aoAbrirMapaOficial = if (comMapaOficial) ({ mapasOficiais++ }) else null,
                    mapa = { Box(Modifier.fillMaxSize().testTag("mapa").background(Color(0xFFDDE5F0)).pointerInput(Unit) { detectTapGestures { toquesNoMapa++ } }) },
                    modifier = Modifier.width(360.dp),
                    agora = AGORA_DA_LINHA,
                )
            }
        }
    }

    private fun mostrar(estado: EstadoLinha = ESTADO_DA_LINHA, comMapaOficial: Boolean = false, letra: Float = 1f) =
        regra.setContent { Ecra(estado, comMapaOficial, letra) }

    // As chegadas do LinhaChegada leem-se como "Linha 15E para Algés, daqui a 3 minutos, ...".
    private fun chegadasVisiveis() = regra.onAllNodes(
        SemanticsMatcher("chegada") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Linha 15E para") } == true },
    ).fetchSemanticsNodes().size

    @Test fun tocarNumaParagemEscolheAParagem() {
        mostrar()
        regra.onNodeWithText("Algés (Jardim)").performClick()
        regra.onNodeWithText("Pç. Figueira").performClick()
        assertEquals(listOf(ALGES, PRACA_FIGUEIRA), escolhidas)
        regra.onNodeWithText("Cais Sodré").assertIsSelected()
        regra.onNodeWithText("Algés (Jardim)").assertIsNotSelected()
    }

    @Test fun soAParagemEscolhidaMostraAsTresChegadasEAbrirParagem() {
        mostrar()
        // 4 chegadas na paragem escolhida: só as 3 primeiras, e o botão só nela.
        assertEquals(3, chegadasVisiveis())
        regra.onNodeWithText("Próximas em Cais Sodré").assertIsDisplayed()
        regra.onAllNodes(hasText("Abrir paragem")).assertCountEquals(1)
        regra.onNodeWithText("Abrir paragem").performClick()
        assertEquals(listOf(CAIS_SODRE), aberta)
    }

    @Test fun chegadasDeUmaParagemPorCarregarDizemACarregar() {
        mostrar(ESTADO_DA_LINHA.copy(chegadas = null))
        assertEquals(0, chegadasVisiveis())
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        // O botão para abrir a paragem não espera pelas chegadas.
        regra.onNodeWithText("Abrir paragem").assertIsDisplayed()
    }

    @Test fun semChegadasDizOQueAconteceu() {
        mostrar(ESTADO_DA_LINHA.copy(chegadas = EstadoChegadas(emptyList(), AGORA_DA_LINHA, false, false)))
        regra.onNodeWithText("Não há chegadas nas próximas 2 horas").assertIsDisplayed()
    }

    // Uma linha com 40 paragens: a escolhida pode estar muito abaixo do que cabe no ecrã de um telemóvel.
    private val longa = DETALHE_15E.copy(
        sentidos = listOf(SentidoLinha(0, "Algés", setOf("Algés"), List(40) { paragemDaLinha("pt-Carris_$it", "Paragem $it") }, emptySet())),
    )
    private var estadoDinamico by mutableStateOf(ESTADO_DA_LINHA)

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun abreJaNaParagemEscolhidaMesmoNoFimDeUmaListaLonga() {
        mostrar(ESTADO_DA_LINHA.copy(detalhe = longa, escolhida = longa.sentidos[0].paragens[30]))
        regra.onNodeWithText("Próximas em Paragem 30").assertIsDisplayed()
        regra.onNodeWithText("Abrir paragem").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun escolherOutraParagemLevaAListaAteElaEMostraAsChegadas() {
        estadoDinamico = ESTADO_DA_LINHA.copy(detalhe = longa, escolhida = longa.sentidos[0].paragens[0])
        regra.setContent { Ecra(estadoDinamico) }
        regra.onNodeWithText("Próximas em Paragem 0").assertIsDisplayed()
        // Tocar na paragem 35 no mapa (fora do que se vê): a lista rola até ela.
        estadoDinamico = estadoDinamico.copy(escolhida = longa.sentidos[0].paragens[35])
        regra.waitForIdle()
        regra.onNodeWithText("Próximas em Paragem 35").assertIsDisplayed()
        regra.onNodeWithText("Abrir paragem").assertIsDisplayed()
    }

    @Test fun tocarNaFaixaNaoChegaAoMapa() {
        mostrar()
        regra.onNodeWithText("Carris").performTouchInput { click() }
        regra.waitForIdle()
        assertEquals(0, toquesNoMapa)
    }

    @Test fun mudarDeSentidoPeloChip() {
        mostrar()
        regra.onNodeWithText("Sentido Algés").assertIsSelected()
        regra.onNodeWithText("Sentido Pç. Figueira").assertIsNotSelected()
        regra.onNodeWithText("Sentido Pç. Figueira").performClick()
        assertEquals(listOf(1), sentidos)
    }

    @Test fun horarioSoQuandoASecaoAparece() {
        mostrar()
        // Fechado: nada do horário está à vista e nada o pediu; o botão é que o pede.
        regra.onNodeWithText("Horário de hoje").assertIsDisplayed()
        regra.onAllNodes(hasText("Primeira às", substring = true)).assertCountEquals(0)
        assertEquals(0, horarios)
        regra.onNodeWithText("Horário de hoje").performClick()
        assertEquals(1, horarios)
    }

    @Test fun horarioComPrimeiraEUltima() {
        mostrar(ESTADO_DA_LINHA.copy(horarioAberto = true, horario = listOf(LocalTime.of(6, 31), LocalTime.of(23, 56), LocalTime.of(1, 20))))
        regra.onNodeWithText("Primeira às 06:31 · última às 01:20").assertIsDisplayed()
        regra.onNodeWithText("06:31   23:56   01:20").assertIsDisplayed()
        // Já está aberto: o botão que o pede desaparece.
        regra.onAllNodes(hasText("Horário de hoje") and hasClickAction()).assertCountEquals(0)
    }

    @Test fun semPartidasHojeDizIsso() {
        mostrar(ESTADO_DA_LINHA.copy(horarioAberto = true, horario = emptyList()))
        regra.onNodeWithText("Hoje não há partidas desta linha aqui.").assertIsDisplayed()
        regra.onAllNodes(hasText("Primeira às", substring = true)).assertCountEquals(0)
    }

    @Test fun horarioACarregar() {
        mostrar(ESTADO_DA_LINHA.copy(horarioAberto = true, horario = null))
        regra.onNodeWithText("Horário de hoje").assertIsDisplayed()
        regra.onAllNodes(hasText("A carregar…")).assertCountEquals(1)
    }

    @Test fun horarioQueFalhouDizIsso() {
        mostrar(ESTADO_DA_LINHA.copy(horarioAberto = true, horarioFalhou = true))
        regra.onNodeWithText("Não foi possível carregar o horário. Tenta outra vez mais tarde.").assertIsDisplayed()
    }

    @Test fun relatosDaLinhaAparecem() {
        val estado = ESTADO_DA_LINHA.copy(
            relatos = listOf(relato("a", "desvio", confirmado = true, confirmacoes = 3), relato("b", "acidente", confirmado = false, confirmacoes = 1)),
        )
        mostrar(estado)
        regra.onNodeWithText("Relatos desta linha").assertIsDisplayed()
        // Confirmado e por confirmar, com quem os confirmou.
        regra.onNodeWithText("Desvio").assertIsDisplayed()
        regra.onNodeWithText("Confirmado por 3 passageiros · há 10 min", substring = true).assertIsDisplayed()
        regra.onNodeWithText("Acidente").assertIsDisplayed()
        regra.onNodeWithText("Relatado por 1 passageiro · por confirmar · há 10 min", substring = true).assertIsDisplayed()
    }

    @Test fun semRelatosNaoHaSecao() {
        // Com a comunidade desligada (ou sem relatos) a lista chega vazia: nada de título sem nada por baixo.
        mostrar()
        regra.onAllNodes(hasText("Relatos desta linha")).assertCountEquals(0)
    }

    @Test fun relatoQueJaAcabouNaoAparece() {
        val acabado = relato("c", "desvio", true, 2).copy(expiraEm = AGORA_DA_LINHA.minusSeconds(1).toEpochMilli())
        mostrar(ESTADO_DA_LINHA.copy(relatos = listOf(acabado)))
        regra.onAllNodes(hasText("Relatos desta linha")).assertCountEquals(0)
    }

    @Test fun mapaOficialSoComEndereco() {
        mostrar()
        regra.onAllNodes(hasText("Mapa oficial da rede")).assertCountEquals(0)
    }

    @Test fun mapaOficialAbreQuandoHaEndereco() {
        mostrar(comMapaOficial = true)
        regra.onNodeWithText("Mapa oficial da rede").performClick()
        assertEquals(1, mapasOficiais)
    }

    @Test fun voltarEEstrelaSaoIconesDe48dp() {
        mostrar()
        regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        regra.onNodeWithContentDescription("Juntar a linha às favoritas").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        regra.onNodeWithText("★").assertDoesNotExist()
    }

    @Test fun titulosDasSeccoesSaoCabecalhos() {
        mostrar(completo)
        val ehTitulo = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        regra.onNodeWithText("Relatos desta linha").assert(ehTitulo)
        regra.onNodeWithText("Paragens").assert(ehTitulo)
    }

    // Uma linha sem cor publicada tem o próprio cobalto como cor: sem contorno, o selo desaparecia na faixa.
    @Test fun seloNaFaixaTemContorno() {
        mostrar(ESTADO_DA_LINHA.copy(detalhe = DETALHE_15E.copy(cor = "#1F4FA3")))
        val selo = regra.onNodeWithText("15E").fetchSemanticsNode().boundsInRoot
        val img = regra.onRoot().captureToImage().toPixelMap()
        // O texto do selo tem 6 dp de folga até à borda: o contorno branco está nesse intervalo, à esquerda do texto.
        val aEsquerda = (selo.left.toInt() - 12..selo.left.toInt()).map { img[it, selo.center.y.toInt()] }
        assertTrue("sem contorno branco: $aEsquerda", aEsquerda.any { it.red > 0.9f && it.green > 0.9f && it.blue > 0.9f })
    }

    // Os cantos redondos de baixo da faixa assentam sobre o mapa: o mapa começa por baixo dela.
    @Test fun oMapaEstendeSePorBaixoDaFaixa() {
        mostrar()
        val mapa = regra.onNodeWithTag("mapa").fetchSemanticsNode().boundsInRoot
        val operador = regra.onNodeWithText("Carris").fetchSemanticsNode().boundsInRoot
        assertTrue("o mapa começa em ${mapa.top}, o operador acaba em ${operador.bottom}", mapa.top < operador.bottom)
    }

    @Test fun estrelaPedeOFavoritoEDizOQueFaz(){
        mostrar()
        regra.onNodeWithContentDescription("Juntar a linha às favoritas").performClick()
        assertEquals(1, estrelas)
    }

    @Test fun estrelaDeUmaLinhaFavorita() {
        val fav = LinhaFavorita(REF_15E, 0, CAIS_SODRE.ref, "15E", "Algés", 0)
        mostrar(ESTADO_DA_LINHA.copy(favorita = fav))
        regra.onNodeWithContentDescription("Tirar a linha das favoritas").assertIsDisplayed()
        regra.onAllNodes(hasText("Usar como paragem habitual")).assertCountEquals(0)
    }

    @Test fun outraParagemNumaFavoritaOfereceUsarComoHabitual() {
        // A favorita tem a Pç. Figueira como habitual e a escolhida é o Cais Sodré.
        val fav = LinhaFavorita(REF_15E, 0, PRACA_FIGUEIRA.ref, "15E", "Algés", 0)
        mostrar(ESTADO_DA_LINHA.copy(favorita = fav))
        regra.onNodeWithText("Usar como paragem habitual").performClick()
        assertEquals(1, habituais)
    }

    @Test fun jaNaoExisteSoQuandoSabemosQueNaoExiste() {
        mostrar(EstadoLinha(semLinha = true))
        regra.onNodeWithText("Esta linha já não existe.").assertIsDisplayed()
    }

    @Test fun aCarregarNaoDizQueJaNaoExiste() {
        mostrar(EstadoLinha())
        regra.onNodeWithText("A carregar…").assertIsDisplayed()
        regra.onAllNodes(hasText("Esta linha já não existe.")).assertCountEquals(0)
    }

    @Test fun linhaQueFalhouNaoDizQueJaNaoExiste() {
        mostrar(EstadoLinha(falhou = true))
        regra.onNodeWithText("Não foi possível carregar a linha. Tenta outra vez mais tarde.").assertIsDisplayed()
        regra.onAllNodes(hasText("Esta linha já não existe.")).assertCountEquals(0)
    }

    // ---- acessibilidade: nome em tudo o que se toca, alvos de 48 dp, e a letra a 200 % ----

    private val completo = ESTADO_DA_LINHA.copy(
        favorita = LinhaFavorita(REF_15E, 0, PRACA_FIGUEIRA.ref, "15E", "Algés", 0),
        relatos = listOf(relato("a", "desvio", confirmado = true, confirmacoes = 3)),
    )

    private fun osControlosTemNomeEAlvoDe48dp(pelomenos: Int) {
        val minimo = with(regra.density) { 48.dp.toPx() }
        // Os itens que a lista já compôs mas ainda não pôs no ecrã (à espera de rolar) não contam.
        val controlos = regra.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }
        assertTrue("controlos a menos: ${controlos.size}", controlos.size >= pelomenos)
        controlos.forEach { n ->
            val nome = n.config.getOrNull(SemanticsProperties.Text).orEmpty() + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue("sem nome: ${n.config}", nome.isNotEmpty())
            val alvo = n.touchBoundsInRoot
            // Uma linha cortada pela borda da lista (a meio de rolar para dentro) mede-se quando estiver inteira.
            if (alvo.width < n.size.width || alvo.height < n.size.height) return@forEach
            assertTrue("alvo pequeno ($nome): ${alvo.width} x ${alvo.height}", alvo.width >= minimo - 0.5f && alvo.height >= minimo - 0.5f)
        }
    }

    @Test fun tudoOQueSeTocaTemNomeEAlvoDe48dp() {
        mostrar(completo, comMapaOficial = true)
        // Voltar, ★, os 2 sentidos, as 3 paragens e os botões da escolhida (abrir, horário, usar como habitual) e o mapa oficial.
        osControlosTemNomeEAlvoDe48dp(pelomenos = 11)
    }

    // O ecrã de um telemóvel (360 x 780 dp) com a letra a 200 %.
    @Test @Config(qualifiers = "w360dp-h780dp")
    fun comALetraA200PorCentoContinuaTudoAoAlcance() {
        mostrar(completo, comMapaOficial = true, letra = 2f)
        // Com a letra grande só cabem alguns: o que se vê continua a ter nome e alvo, e o resto chega-se a rolar a lista.
        osControlosTemNomeEAlvoDe48dp(pelomenos = 4)
        listOf("Abrir paragem", "Horário de hoje", "Mapa oficial da rede").forEach { texto ->
            regra.onNode(hasScrollAction()).performScrollToNode(hasText(texto))
            regra.onNodeWithText(texto).assertIsDisplayed()
            osControlosTemNomeEAlvoDe48dp(pelomenos = 1)
        }
    }
}
