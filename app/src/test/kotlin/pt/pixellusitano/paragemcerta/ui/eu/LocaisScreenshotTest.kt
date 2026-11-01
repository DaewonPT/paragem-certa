package pt.pixellusitano.paragemcerta.ui.eu

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocaisScreenshotTest {
    private fun local(id: Long, nome: String, tipo: String) =
        LocalGuardado(id, nome, Lugar(nome, 38.7, -9.14, TipoLugar.LOCAL), tipo, id.toInt())

    private val locais = listOf(
        local(1, "Casa", "CASA"), local(2, "Trabalho", "TRABALHO"),
        local(3, "Ginásio", "OUTRO"), local(4, "Casa da avó em Almada", "OUTRO"),
    )

    @Test fun locais_claro() = captureRoboImage("src/test/screenshots/locais_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            LocaisConteudo(locais, aoRenomear = { _, _ -> }, aoSubir = {}, aoApagar = {}, aoVoltar = {}, modifier = Modifier.width(360.dp))
        }
    }
}
