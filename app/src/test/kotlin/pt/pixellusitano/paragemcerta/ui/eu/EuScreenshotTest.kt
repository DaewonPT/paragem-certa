package pt.pixellusitano.paragemcerta.ui.eu

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EuScreenshotTest {
    private val favoritas = listOf(
        Favorita(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 0, 38.7, -9.14),
        Favorita(RefParagem(Fonte.TRANSITOUS, "pt-Metro-Lisboa_CS"), "Cais do Sodré", 1, 38.7, -9.14),
    )

    @Composable
    private fun Eu() = EuConteudo(
        favoritas = favoritas, modo = ModoTema.SISTEMA, limites = Limites(),
        aoAbrirFavorita = {}, aoSubir = {}, aoRemover = {}, aoMudarModo = {}, aoMudarLimites = {},
        aoAbrirSobre = {}, aoEnviarOpiniao = {}, modifier = Modifier.width(360.dp),
    )

    @Test fun eu_claro() = captureRoboImage("src/test/screenshots/eu_claro.png") { TemaParagemCerta(ModoTema.CLARO) { Eu() } }

    @Test fun eu_escuro() = captureRoboImage("src/test/screenshots/eu_escuro.png") { TemaParagemCerta(ModoTema.ESCURO) { Eu() } }

    @Test fun definicoes_claro() = captureRoboImage("src/test/screenshots/definicoes_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            Definicoes(ModoTema.ESCURO, Limites(3, 8), aoMudarModo = {}, aoMudarLimites = {}, modifier = Modifier.width(360.dp))
        }
    }
}
