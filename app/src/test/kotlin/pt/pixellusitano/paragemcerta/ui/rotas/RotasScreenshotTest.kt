package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp") // ecrã alto: os cartões ficam por baixo dos campos e filtros
class RotasScreenshotTest {
    private val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
    private val prontas = EstadoRotas.Prontas(rotas, etiquetas(rotas))
    private val pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL))

    @Composable
    private fun Rotas(estado: EstadoRotas) = RotasConteudo(
        pedido = pedido, estado = estado, filtros = Filtros(acessivel = true), alertas = setOf("210"),
        agora = Instant.parse("2026-11-01T02:20:00Z"),
        aoEscolherOrigem = {}, aoEscolherDestino = {}, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
        aoAbrirRota = {}, aoTentarOutraVez = {}, modifier = Modifier.width(360.dp),
    )

    @Test fun rotas_claro() = captureRoboImage("src/test/screenshots/rotas_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Rotas(prontas) }
    }

    @Test fun rotas_escuro() = captureRoboImage("src/test/screenshots/rotas_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Rotas(prontas) }
    }

    @Test fun rotas_vazias() = captureRoboImage("src/test/screenshots/rotas_vazias.png") {
        TemaParagemCerta(ModoTema.CLARO) { Rotas(EstadoRotas.Vazias(Motivo.SEM_LIGACOES)) }
    }

    @Test fun rotas_letra200() = captureRoboImage("src/test/screenshots/rotas_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Rotas(prontas) }
        }
    }
}
