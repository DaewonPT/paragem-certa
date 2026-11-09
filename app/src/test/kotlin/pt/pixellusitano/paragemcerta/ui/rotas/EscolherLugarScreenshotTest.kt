package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class EscolherLugarScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val cafe = Lugar("Café Central", 38.7101, -9.1402, TipoLugar.LOCAL)
    private val locais = listOf(
        LocalGuardado(1, "Casa", Lugar("Rua Augusta", 38.71, -9.137, TipoLugar.MORADA), "CASA", 0),
        LocalGuardado(2, "Café Central", cafe, "OUTRO", 1),
    )
    private val recentes = listOf(Lugar("Estação Ferroviária de Algés", 38.7019, -9.23, TipoLugar.LOCAL), Lugar("Algés (Jardim)", 38.7011, -9.2312, TipoLugar.PARAGEM))

    @Composable
    private fun Ecra(modo: ModoTema) {
        TemaParagemCerta(modo) {
            EscolherLugarEcra(
                "Onde é a tua casa?", comPosicao = true, locais = locais, recentes = recentes,
                pesquisar = { null }, aoEscolher = {}, aoVoltar = {},
            )
        }
    }

    @Test fun escolher_lugar_claro() = captureRoboImage("src/test/screenshots/escolher_lugar_claro.png") { Ecra(ModoTema.CLARO) }

    @Test fun escolher_lugar_letra200() = captureRoboImage("src/test/screenshots/escolher_lugar_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { Ecra(ModoTema.CLARO) }
    }

    // Com o campo focado, como ao abrir no telemóvel: vê-se a sugestão sobre o branco do campo.
    @Test fun escolher_lugar_escuro() {
        regra.setContent { Ecra(ModoTema.ESCURO) }
        regra.onNode(hasSetTextAction()).performClick()
        regra.onRoot().captureRoboImage("src/test/screenshots/escolher_lugar_escuro.png")
    }
}
