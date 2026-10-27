package pt.pixellusitano.paragemcerta.ui.paragem

import android.content.Intent
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @get:org.junit.Rule val regra = androidx.compose.ui.test.junit4.v2.createComposeRule()

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
        regra.onNodeWithText("Sem ligação — ainda não há dados guardados").assertExists()
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
                    nome = "Cais do Sodré", codigo = "060419", acessivel = null, favorita = false, estado = null, filtro = null,
                    linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
                    aoFiltrar = {}, aoFavoritar = {}, aoReportar = {}, aoVoltar = {},
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

    @Composable
    private fun Ecra(e: EstadoChegadas, alertas: List<Alerta> = listOf(alerta)) = ParagemConteudo(
        nome = "Cais do Sodré (Terminal)", codigo = "060419", acessivel = true, favorita = true,
        estado = e, filtro = null, linhasPresentes = listOf("1001" to "1001", "3708" to "3708"), alertas = alertas, linhas = emptyMap(),
        agora = agora, aoFiltrar = {}, aoFavoritar = {}, aoReportar = {}, aoVoltar = {},
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
