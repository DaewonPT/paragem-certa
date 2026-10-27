package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemaScreenshotTest {

    @Composable
    private fun Amostra(escuro: Boolean) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp).width(320.dp)) {
            Text("Cais do Sodré", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            EstadoAtraso.entries.forEach { estado ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("728  Restelo", Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                    Text(estado.name, color = CoresAtraso.cor(estado, escuro))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    @Test fun amostra_claro() = captureRoboImage("src/test/screenshots/amostra_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Amostra(escuro = false) }
    }

    @Test fun amostra_escuro() = captureRoboImage("src/test/screenshots/amostra_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Amostra(escuro = true) }
    }

    @Test fun amostra_letra200() = captureRoboImage("src/test/screenshots/amostra_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Amostra(escuro = false) }
        }
    }
}
