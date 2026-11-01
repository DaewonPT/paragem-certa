package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class DetalheScreenshotTest {
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    // O MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo.
    @Composable
    private fun Detalhe() = DetalheRotaConteudo(
        rota, de = "Cais do Sodré", para = "Oriente",
        mapa = { Box(Modifier.fillMaxSize().background(Color(0xFFDDE5F0))) },
        aoVoltar = {}, aoPartilhar = {}, aoAbrirTvde = {}, modifier = Modifier.width(360.dp),
    )

    @Test fun detalhe_claro() = captureRoboImage("src/test/screenshots/detalhe_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Detalhe() }
    }

    @Test fun detalhe_letra200() = captureRoboImage("src/test/screenshots/detalhe_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Detalhe() }
        }
    }
}
