package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

/** A folha é uma janela à parte, que as capturas não apanham: capta-se o cartão. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class RelatarScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val linhas = listOf("736", "15E", "728")

    // O pictograma fica no início e o texto alinha à esquerda em todos os tipos: uma coluna de pictogramas, não um ziguezague.
    @Test fun ostextosDosTiposComecamTodosNaMesmaColuna() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                FolhaRelatarConteudo(linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
            }
        }
        val esquerdas = TipoRelato.entries.map {
            regra.onNodeWithText(RuntimeEnvironment.getApplication().getString(nomeDoTipo(it)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(esquerdas.toString(), 1, esquerdas.distinct().size)
    }

    @Test fun relatar_claro() = captureRoboImage("src/test/screenshots/relatar_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FolhaRelatarConteudo(linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
        }
    }

    @Test fun relatar_escuro() = captureRoboImage("src/test/screenshots/relatar_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) {
            FolhaRelatarConteudo(linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
        }
    }

    @Test fun relatar_letra200() = captureRoboImage("src/test/screenshots/relatar_letra200.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                FolhaRelatarConteudo(linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
            }
        }
    }

    @Test fun relatar_linha_claro() = captureRoboImage("src/test/screenshots/relatar_linha_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FolhaRelatarConteudo(
                linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp), tipoInicial = TipoRelato.DESVIO,
            )
        }
    }
}
