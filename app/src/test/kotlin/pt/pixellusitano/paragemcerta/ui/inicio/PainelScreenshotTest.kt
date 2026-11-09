package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.dados.ViagemRecente
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PainelScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val agora = Instant.parse("2026-10-27T14:00:00Z")
    private fun ch(linha: String, destino: String, minutos: Long, atrasoSeg: Long?, acessivel: Boolean? = null) = Chegada(
        Fonte.CM, linha, linha, destino, agora.plusSeconds(minutos * 60), atrasoSeg?.let { agora.plusSeconds(minutos * 60 + it) },
        null, null, Modo.AUTOCARRO, null, acessivel,
    )
    private val linhas = mapOf(
        "728" to Linha(Fonte.CM, "728", "728", "Restelo", "#D1352B", "#FFFFFF", Modo.AUTOCARRO),
        "15E" to Linha(Fonte.CM, "15E", "15E", "Algés", "#E2A400", "#FFFFFF", Modo.ELETRICO),
    )
    private val chegadas = listOf(ch("15E", "Algés", 3, 0, acessivel = true), ch("728", "Restelo", 4, 180), ch("732", "Caselas", 12, null))
    private val cais = RefParagem(Fonte.CM, "062000")

    private fun blocos(e: EstadoChegadas) = listOf(BlocoParagem(cais, "Cais do Sodré", e, maximo = 3))

    @Composable
    private fun Painel(e: EstadoChegadas, perto: List<BlocoParagem>? = emptyList(), favoritas: Boolean = true) =
        PainelConteudo(
            favoritas = if (favoritas) blocos(e) else emptyList(),
            pertoDeTi = perto,
            linhas = linhas,
            agora = agora,
            aoAbrirParagem = {},
            aoPesquisar = {},
            modifier = Modifier.width(360.dp),
        )

    private val bom = EstadoChegadas(chegadas, agora, semLigacao = false, tempoRealIndisponivel = false)

    @Test fun painel_claro() = captureRoboImage("src/test/screenshots/painel_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Painel(bom, perto = blocos(bom)) }
    }

    @Test fun painel_escuro() = captureRoboImage("src/test/screenshots/painel_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Painel(bom, perto = blocos(bom)) }
    }

    @Test fun painel_sem_ligacao() = captureRoboImage("src/test/screenshots/painel_sem_ligacao.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            Painel(bom.copy(semLigacao = true, atualizadoEm = agora.minusSeconds(240)))
        }
    }

    @Test fun painel_vazio() = captureRoboImage("src/test/screenshots/painel_vazio.png") {
        TemaParagemCerta(ModoTema.CLARO) { Painel(bom.copy(chegadas = emptyList()), perto = null, favoritas = false) }
    }

    @Test fun painel_letra200() = captureRoboImage("src/test/screenshots/painel_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Painel(bom) }
        }
    }

    // A primeira rota da fixture: a pé, a linha 210 das 02:28 às 02:58 (30 min), a pé.
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]
    private val aqui = 38.7058 to -9.1431
    private fun local(nome: String, tipo: String, lat: Double, id: Long = 1) =
        LocalGuardado(id, nome, Lugar(nome, lat, -9.1431, TipoLugar.LOCAL), tipo, 0)
    private fun casa(lat: Double) = local("Casa", "CASA", lat)
    private val perto = aqui.first + 0.0027 // uns 300 m
    private val longe = aqui.first + 0.027 // uns 3 km

    @Test fun estadoDoLocalEmCadaCaso() {
        assertEquals(EstadoLocal.PERTO, estadoDoLocal(casa(perto), aqui))
        assertEquals(EstadoLocal.PERTO, estadoDoLocal(local("Café", "OUTRO", perto), aqui)) // perto vale para todos os sítios
        assertEquals(EstadoLocal.AUTOMATICO, estadoDoLocal(casa(aqui.first + 0.0054), aqui)) // uns 600 m: já não é a pé
        assertEquals(EstadoLocal.AUTOMATICO, estadoDoLocal(casa(longe), aqui))
        assertEquals(EstadoLocal.AUTOMATICO, estadoDoLocal(local("Trabalho", "TRABALHO", longe), aqui))
        assertEquals(EstadoLocal.AO_TOQUE, estadoDoLocal(local("Café", "OUTRO", longe), aqui))
        assertEquals(EstadoLocal.SEM_POSICAO, estadoDoLocal(casa(longe), null))
        assertEquals(EstadoLocal.SEM_POSICAO, estadoDoLocal(local("Café", "OUTRO", perto), null))
    }

    @Test fun itinerarioGuardadoSoServeSePerto() {
        val origem = aqui
        fun valido(minutos: Long, dLat: Double) = guardadoValido(agora, origem, agora.plusSeconds(minutos * 60), origem.first + dLat to origem.second)
        assertTrue(valido(5, 0.00045)) // uns 50 m
        assertFalse(valido(11, 0.00045)) // velho demais
        assertFalse(valido(5, 0.027)) // uns 3 km
    }

    @Test fun validadeDeQuinzeMinutos() {
        val quinze = Duration.ofMinutes(15)
        fun valido(minutos: Long) = guardadoValido(agora, aqui, agora.plusSeconds(minutos * 60), aqui, quinze)
        assertTrue(valido(14))
        assertFalse(valido(16))
    }

    @Test fun primeiroToqueSoCalculaNoAoToque() {
        assertTrue(toqueCalcula(EstadoLocal.AO_TOQUE, null, aCalcular = false))
        assertFalse(toqueCalcula(EstadoLocal.AO_TOQUE, rota, aCalcular = false)) // o segundo toque abre as rotas
        assertFalse(toqueCalcula(EstadoLocal.AO_TOQUE, null, aCalcular = true))
        assertFalse(toqueCalcula(EstadoLocal.SEM_POSICAO, null, aCalcular = false)) // abre logo as rotas
        assertFalse(toqueCalcula(EstadoLocal.AUTOMATICO, null, aCalcular = false))
        assertFalse(toqueCalcula(EstadoLocal.PERTO, null, aCalcular = false))
    }

    private val ultima = ViagemRecente(
        Lugar("A minha posição", 38.7, -9.14, TipoLugar.POSICAO), Lugar("Oriente", 38.7678, -9.099, TipoLugar.LOCAL), agora,
    )

    @Test fun painel_casa() = captureRoboImage("src/test/screenshots/painel_casa.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                favoritas = emptyList(), pertoDeTi = emptyList(), linhas = linhas, agora = agora,
                aoAbrirParagem = {}, aoPesquisar = {}, modifier = Modifier.width(360.dp),
                locais = listOf(BlocoLocal(casa(longe), EstadoLocal.AUTOMATICO, rota)),
                ultima = ultima,
            )
        }
    }

    private val cafe = local("Café Central", "OUTRO", longe, id = 3)
    private val trabalho = local("Trabalho", "TRABALHO", longe, id = 2)

    @Test fun painel_favoritos() = captureRoboImage("src/test/screenshots/painel_favoritos.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                favoritas = blocos(bom), pertoDeTi = emptyList(), linhas = linhas, agora = agora,
                aoAbrirParagem = {}, aoPesquisar = {}, modifier = Modifier.width(360.dp),
                locais = listOf(
                    BlocoLocal(casa(perto), EstadoLocal.PERTO),
                    BlocoLocal(trabalho, EstadoLocal.AUTOMATICO, rota),
                    BlocoLocal(cafe, EstadoLocal.AO_TOQUE),
                ),
            )
        }
    }

    @Test fun painelFechadoMostraOPrimeiroCartaoDepoisDeCasaETrabalho() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Box(Modifier.width(360.dp).height(PAINEL_FECHADO - ALTURA_PEGA)) {
                    PainelConteudo(
                        favoritas = blocos(bom), pertoDeTi = emptyList(), linhas = linhas, agora = agora,
                        aoAbrirParagem = {}, aoPesquisar = {},
                        // Sem altura limitada, como dentro do painel (que rola): com o limite do Box os cartões de baixo encolhiam para 0 e o teste passava sempre.
                        modifier = Modifier.width(360.dp).wrapContentHeight(Alignment.Top, unbounded = true),
                        locais = listOf(
                            BlocoLocal(casa(perto), EstadoLocal.PERTO),
                            BlocoLocal(trabalho, EstadoLocal.AUTOMATICO, rota),
                            BlocoLocal(cafe, EstadoLocal.AO_TOQUE),
                        ),
                        ultima = ultima,
                    )
                }
            }
        }
        val b = regra.onNodeWithText("Cais do Sodré", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("${b.bottom} > ${PAINEL_FECHADO - ALTURA_PEGA}", b.bottom <= PAINEL_FECHADO - ALTURA_PEGA)
    }

    @Test fun favoritosPelaOrdemDoPainel() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                PainelConteudo(
                    favoritas = blocos(bom), pertoDeTi = emptyList(), linhas = linhas, agora = agora,
                    aoAbrirParagem = {}, aoPesquisar = {}, modifier = Modifier.width(360.dp),
                    // Fora de ordem de propósito: a ordem vem do painel, não de quem o chama.
                    locais = listOf(BlocoLocal(cafe, EstadoLocal.AO_TOQUE), BlocoLocal(trabalho, EstadoLocal.AUTOMATICO), BlocoLocal(casa(longe), EstadoLocal.AUTOMATICO)),
                )
            }
        }
        fun topo(texto: String) = regra.onNodeWithText(texto).fetchSemanticsNode().boundsInRoot.top
        val ordem = listOf("Casa", "Trabalho", "Cais do Sodré", "Café Central").map(::topo)
        assertEquals(ordem.sorted(), ordem)
        assertEquals(4, ordem.toSet().size)
    }

    // A 728 do Restelo, tirada das chegadas do Cais do Sodré (sem o sentido: pelo destino), e uma linha que o catálogo já não tem.
    private val f728 = LinhaFavorita(RefLinha(Fonte.CM, "728"), 0, cais, "728", "Restelo", 0)
    private val sumida = LinhaFavorita(RefLinha(Fonte.TRANSITOUS, "pt-Carris:999"), 0, RefParagem(Fonte.TRANSITOUS, "pt-Carris_3803"), "999", "Algés", 1)

    @Test fun painel_com_linha_claro() = captureRoboImage("src/test/screenshots/painel_com_linha_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            PainelConteudo(
                favoritas = blocos(bom), pertoDeTi = emptyList(), linhas = linhas, agora = agora,
                aoAbrirParagem = {}, aoPesquisar = {}, modifier = Modifier.width(360.dp),
                linhasFavoritas = listOf(
                    blocoDaLinha(f728, bom, existe = true, nomeParagem = "Cais do Sodré"),
                    BlocoLinha(sumida, "Cais Sodré", null, existe = false),
                ),
            )
        }
    }

    @Test fun semFavoritosDizOndeGuardar() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                PainelConteudo(emptyList(), emptyList(), linhas, agora, {}, {}, Modifier.width(360.dp))
            }
        }
        regra.onNodeWithText("Toca na ☆ de uma paragem, de uma linha ou de um sítio para o veres aqui").assertIsDisplayed()
    }

    @Test fun semLocalizacaoOSitioContinuaAVista() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                PainelConteudo(
                    emptyList(), emptyList(), linhas, agora, {}, {}, Modifier.width(360.dp),
                    locais = listOf(BlocoLocal(cafe, EstadoLocal.SEM_POSICAO)),
                )
            }
        }
        regra.onNodeWithText("Toca na ☆ de uma paragem, de uma linha ou de um sítio para o veres aqui").assertDoesNotExist()
        // Sem localização o sítio continua à vista e convida a tocar (as rotas pedem a origem).
        regra.onNodeWithText("Toca para ver como ir").assertIsDisplayed()
    }

    @Test fun tocarNoSitioChamaOCallbackComOBloco() {
        var tocado: BlocoLocal? = null
        val bloco = BlocoLocal(cafe, EstadoLocal.AO_TOQUE)
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                PainelConteudo(
                    emptyList(), emptyList(), linhas, agora, {}, {}, Modifier.width(360.dp),
                    locais = listOf(bloco), aoTocarLocal = { tocado = it },
                )
            }
        }
        regra.onNodeWithText("Café Central").performClick()
        assertEquals(bloco, tocado)
    }

    private fun cartao(estado: EstadoLocal, itinerario: Itinerario? = null, aCalcular: Boolean = false, aoTocar: () -> Unit = {}, aoVerMais: () -> Unit = {}) =
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                CartaoLocal(casa(longe), estado, itinerario, aCalcular, aoTocar, aoVerMais)
            }
        }

    @Test fun cartaoPertoDizQueEstasPerto() {
        cartao(EstadoLocal.PERTO, itinerario = rota) // mesmo com uma sugestão antiga em memória
        regra.onNodeWithText("Estás perto deste local").assertIsDisplayed()
        regra.onNodeWithText("Ver mais opções").assertDoesNotExist()
    }

    @Test fun cartaoComSugestaoMostraALinhaADuracaoEVerMais() {
        var verMais = 0
        var tocou = 0
        cartao(EstadoLocal.AUTOMATICO, itinerario = rota, aoTocar = { tocou++ }, aoVerMais = { verMais++ })
        regra.onNodeWithText("Casa").assertIsDisplayed()
        regra.onNodeWithText("210").assertIsDisplayed() // o selo da linha da primeira etapa em veículo
        regra.onNodeWithText("30 min · sai às 02:28").assertIsDisplayed()
        regra.onNodeWithText("Ver mais opções").performClick()
        assertEquals(1, verMais)
        assertEquals(0, tocou) // o botão tem o seu próprio alvo
    }

    @Test fun cartaoACalcularEConvite() {
        cartao(EstadoLocal.AUTOMATICO, aCalcular = true)
        regra.onNodeWithText("A calcular…").assertIsDisplayed()
        regra.onNodeWithText("Ver mais opções").assertDoesNotExist()
    }

    @Test fun cartaoAoToqueConvidaATocar() {
        var tocou = 0
        cartao(EstadoLocal.AO_TOQUE, aoTocar = { tocou++ })
        regra.onNodeWithText("Toca para ver como ir").assertIsDisplayed().performClick()
        assertEquals(1, tocou)
    }

    @Test fun cartaoSemPosicaoConvidaATocar() {
        var tocou = 0
        cartao(EstadoLocal.SEM_POSICAO, aoTocar = { tocou++ })
        regra.onNodeWithText("Toca para ver como ir").assertIsDisplayed().performClick()
        assertEquals(1, tocou)
    }

    @Test fun atrasoEstaNaDescricao() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Painel(bom) } }
        regra.onNodeWithContentDescription("Linha 728 para Restelo, daqui a 7 minutos, atrasado 3 minutos").assertExists()
    }
}
