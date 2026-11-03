package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

/** A folha é uma janela à parte, que as capturas não apanham: capta-se o cartão. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RelatarScreenshotTest {
    private val linhas = listOf("736", "15E", "728")

    @Test fun relatar_claro() = captureRoboImage("src/test/screenshots/relatar_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FolhaRelatarConteudo(linhas, null, aoEscolher = { _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
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
