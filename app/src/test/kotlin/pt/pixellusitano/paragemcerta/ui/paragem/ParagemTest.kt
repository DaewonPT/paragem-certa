package pt.pixellusitano.paragemcerta.ui.paragem

import android.content.Intent
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParagemTest {
    private val agora = Instant.parse("2026-10-27T14:00:00Z")
    private fun ch(linha: String, destino: String, minutos: Long, atraso: Long?) = Chegada(
        Fonte.CM, linha, linha, destino, agora.plusSeconds(minutos * 60), atraso?.let { agora.plusSeconds(minutos * 60 + it) },
        null, null, Modo.AUTOCARRO, null, null,
    )
    private val chegadas = listOf(ch("3708", "Costa da Caparica", 3, 0), ch("1001", "Reboleira", 5, 240), ch("3708", "Costa da Caparica", 25, null))
    private val estado = EstadoChegadas(chegadas, agora, semLigacao = false, tempoRealIndisponivel = false)
    private val alerta = Alerta(
        "a", setOf("3708"), emptySet(), agora, null, "Cascais | 3708: Desvio de percurso",
        "Durante as obras, a linha 3708 segue pela Av. Marginal.", Gravidade.AVISO, null,
    )

    @Test fun filtroPorLinha() {
        val vm = ParagemViewModel(flowOf(estado))
        assertEquals(listOf("1001" to "1001", "3708" to "3708"), vm.linhasPresentes(estado))
        vm.escolherFiltro("3708")
        assertTrue(vm.aplicarFiltro(estado).chegadas.all { it.linhaId == "3708" })
        assertEquals(2, vm.aplicarFiltro(estado).chegadas.size)
        vm.escolherFiltro(null)
        assertEquals(3, vm.aplicarFiltro(estado).chegadas.size)
    }

    @Test fun chipsComONomeDaLinhaNoTransitous() {
        fun tr(id: String, nome: String) = Chegada(Fonte.TRANSITOUS, id, nome, "Cais Sodré", agora.plusSeconds(300), null, null, null, Modo.AUTOCARRO, null, null)
        val e = estado.copy(chegadas = listOf(tr("pt-Carris_60_0", "201"), tr("pt-Carris_136_0", "206")))
        assertEquals(listOf("pt-Carris_60_0" to "201", "pt-Carris_136_0" to "206"), ParagemViewModel(flowOf(e)).linhasPresentes(e))
    }

    // No Transitous a 728 tem uma variante por sentido (ids diferentes, o mesmo nome): é uma só linha para quem a escolhe.
    private fun tr(id: String, nome: String, destino: String, minutos: Long) =
        Chegada(Fonte.TRANSITOUS, id, nome, destino, agora.plusSeconds(minutos * 60), null, null, null, Modo.AUTOCARRO, null, null)
    private val duasVariantes = estado.copy(
        chegadas = listOf(
            tr("pt-Carris_728_0", "728", "Cais Sodré", 3), tr("pt-Carris_201_0", "201", "Odivelas", 5), tr("pt-Carris_728_1", "728", "Oriente", 7),
        ),
    )

    @Test fun duasVariantesDaMesmaLinhaDaoUmaSoPastilha() {
        val vm = ParagemViewModel(flowOf(duasVariantes))
        assertEquals(listOf("201", "728"), vm.linhasPresentes(duasVariantes).map { it.second })
    }

    @Test fun filtrarPelaLinhaMostraTodasAsVariantes() {
        val vm = ParagemViewModel(flowOf(duasVariantes))
        vm.escolherFiltro("728")
        assertEquals(listOf("Cais Sodré", "Oriente"), vm.aplicarFiltro(duasVariantes).chegadas.map { it.destino })
        vm.escolherFiltro("201")
        assertEquals(listOf("Odivelas"), vm.aplicarFiltro(duasVariantes).chegadas.map { it.destino })
    }

    @get:org.junit.Rule val regra = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test fun tocarNaPastilhaFiltraPeloNomeDaLinha() {
        val vm = ParagemViewModel(flowOf(duasVariantes))
        var escolhida: String? = null
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Ecra(duasVariantes.copy(chegadas = emptyList()), presentes = vm.linhasPresentes(duasVariantes), aoFiltrar = { escolhida = it })
            }
        }
        regra.onNodeWithText("728").performClick()
        assertEquals("728", escolhida) // o nome da linha, não o id da primeira variante
    }

    @Test fun chegadaQueJaPassouNaoAparece() {
        val passada = Chegada(Fonte.CM, "1625", "1625", "Rio de Mouro", agora.minusSeconds(180), agora.minusSeconds(120), null, null, Modo.AUTOCARRO, null, null)
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado.copy(chegadas = listOf(passada) + chegadas)) } }
        regra.onNodeWithText("Rio de Mouro", substring = true, useUnmergedTree = true).assertDoesNotExist()
        regra.onNodeWithText("Reboleira", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test fun semRedeENadaGuardadoNaoDizQueNaoHaAutocarros() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(EstadoChegadas(emptyList(), null, semLigacao = true, tempoRealIndisponivel = false), emptyList()) }
        }
        regra.onNodeWithText("Sem ligação — não há chegadas guardadas por mostrar").assertExists()
        regra.onNodeWithText("Não há chegadas nas próximas 2 horas").assertDoesNotExist()
    }

    @Test fun fonteEmBaixoENadaGuardadoNaoDizQueNaoHaAutocarros() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(EstadoChegadas(emptyList(), null, semLigacao = false, tempoRealIndisponivel = true), emptyList()) }
        }
        regra.onNodeWithText("Tempo real indisponível — não há chegadas guardadas por mostrar").assertExists()
        regra.onNodeWithText("Não há chegadas nas próximas 2 horas").assertDoesNotExist()
    }

    @Test fun tempoRealIndisponivelDizAIdadeDosDados() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Ecra(estado.copy(atualizadoEm = agora.minusSeconds(240), tempoRealIndisponivel = true), emptyList())
            }
        }
        regra.onNodeWithText("Tempo real indisponível — atualizado há 4 min").assertExists()
    }

    @Test fun aCarregarEnquantoNaoHaDados() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ParagemConteudo(
                    nome = "Cais do Sodré", codigo = "060419", acessivel = null, favorita = false, vigiada = false, estado = null, filtro = null,
                    linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
                    aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
                )
            }
        }
        regra.onNodeWithText("A carregar…").assertExists()
    }

    @Test fun reportarErroPorEmail() {
        val i = intentReportarErro("0.1.0", "CM:060419", "Cais do Sodré (Terminal)")
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("mailto:contact@pixellusitano.com", i.data.toString())
        assertTrue(i.getStringExtra(Intent.EXTRA_SUBJECT)!!.contains("Cais do Sodré (Terminal)"))
        assertTrue(i.getStringExtra(Intent.EXTRA_TEXT)!!.contains("CM:060419"))
    }

    @Test fun encontrarAParagemFicaEntreAvisaMeEReportarErroEAbreABussola() {
        var toques = 0
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado, aoEncontrar = { toques++ }) } }
        listOf("Reportar erro", "Encontrar a paragem", "Avisa-me").forEach { regra.onNodeWithText(it).performScrollTo() }
        val topo = { texto: String -> regra.onNodeWithText(texto).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(topo("Avisa-me") < topo("Encontrar a paragem"))
        assertTrue(topo("Encontrar a paragem") < topo("Reportar erro"))
        regra.onNodeWithText("Encontrar a paragem").performClick()
        assertEquals(1, toques)
    }

    @Test fun semAParagemCarregadaNaoHaBussola() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) } }
        regra.onNodeWithText("Encontrar a paragem").assertDoesNotExist()
    }

    @Test fun voltarEUmBotaoDe48dpComADescricaoVoltar() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) } }
        regra.onNodeWithContentDescription("Voltar").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertHasClickAction()
    }

    @Test fun estrelaEUmBotaoDe48dpQueMudaAoTocar() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                var favorita by remember { mutableStateOf(false) }
                ParagemConteudo(
                    nome = "Cais do Sodré", codigo = "060419", acessivel = null, favorita = favorita, vigiada = false, estado = estado, filtro = null,
                    linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
                    aoFiltrar = {}, aoFavoritar = { favorita = !favorita }, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
                )
            }
        }
        regra.onNodeWithContentDescription("Tirar das favoritas").assertDoesNotExist()
        regra.onNodeWithContentDescription("Juntar às favoritas").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        regra.onNodeWithContentDescription("Juntar às favoritas").assertDoesNotExist()
        regra.onNodeWithContentDescription("Tirar das favoritas").assertHeightIsAtLeast(48.dp).assertHasClickAction()
    }

    @Test fun comLetraA200NomeCompridoNaoCortaVoltarNemEstrela() {
        val nome = "Avenida Infante Dom Henrique, junto ao Terminal Fluvial do Cais do Sodré"
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) {
                    ParagemConteudo(
                        nome = nome, codigo = "060419", acessivel = true, favorita = true, vigiada = false, estado = estado, filtro = null,
                        linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
                        aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
        regra.onNodeWithContentDescription("Voltar").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        regra.onNodeWithContentDescription("Tirar das favoritas").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        regra.onNodeWithText(nome).assertIsDisplayed()
        val voltar = regra.onNodeWithContentDescription("Voltar").fetchSemanticsNode().boundsInRoot
        val estrela = regra.onNodeWithContentDescription("Tirar das favoritas").fetchSemanticsNode().boundsInRoot
        val titulo = regra.onNodeWithText(nome).fetchSemanticsNode().boundsInRoot
        assertTrue(voltar.left >= 0f && estrela.right <= regra.onRoot().fetchSemanticsNode().boundsInRoot.right)
        assertTrue(titulo.top >= maxOf(voltar.bottom, estrela.bottom)) // o título vem por baixo, sem se sobrepor
    }

    @Test fun soOFiltroEscolhidoFicaSelecionado() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) } }
        regra.onNodeWithText("Todas").assertIsSelected()
        regra.onNodeWithText("1001").assertIsNotSelected()
        regra.onNodeWithText("3708").assertIsNotSelected()
    }

    @Config(qualifiers = "w360dp")
    @Test fun aFilaDeFiltrosRolaAteABordaDoEcra() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) } }
        val fila = regra.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNode().boundsInRoot
        val ecra = regra.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(ecra.left, fila.left, 0.5f)
        assertEquals(ecra.right, fila.right, 0.5f)
        // O primeiro filtro continua afastado da borda.
        assertTrue(regra.onNodeWithText("Todas").fetchSemanticsNode().boundsInRoot.left > 0f)
    }

    @Composable
    private fun Ecra(
        e: EstadoChegadas, alertas: List<Alerta> = listOf(alerta), aoEncontrar: (() -> Unit)? = null,
        presentes: List<Pair<String, String>> = listOf("1001" to "1001", "3708" to "3708"), aoFiltrar: (String?) -> Unit = {},
    ) = ParagemConteudo(
        nome = "Cais do Sodré (Terminal)", codigo = "060419", acessivel = true, favorita = true, vigiada = false,
        estado = e, filtro = null, linhasPresentes = presentes, alertas = alertas, linhas = emptyMap(),
        agora = agora, aoFiltrar = aoFiltrar, aoFavoritar = {}, aoAvisaMe = {}, aoEncontrar = aoEncontrar, aoReportar = {}, aoVoltar = {},
        modifier = Modifier.width(360.dp),
    )

    @Test fun paragem_claro() = captureRoboImage("src/test/screenshots/paragem_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) }
    }

    @Test fun paragem_escuro() = captureRoboImage("src/test/screenshots/paragem_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Ecra(estado) }
    }

    @Test fun paragem_sem_chegadas() = captureRoboImage("src/test/screenshots/paragem_sem_chegadas.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(estado.copy(chegadas = emptyList()), alertas = emptyList()) }
    }

    @Test fun paragem_letra200() = captureRoboImage("src/test/screenshots/paragem_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(estado) }
        }
    }
}
