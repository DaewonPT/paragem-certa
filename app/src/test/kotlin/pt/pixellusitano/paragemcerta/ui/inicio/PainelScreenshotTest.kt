package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
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

    @Test fun atrasoEstaNaDescricao() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Painel(bom) } }
        regra.onNodeWithContentDescription("Linha 728 para Restelo, daqui a 7 minutos, atrasado 3 minutos").assertExists()
    }
}
