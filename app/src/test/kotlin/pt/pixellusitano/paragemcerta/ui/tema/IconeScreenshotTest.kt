package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.R

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconeScreenshotTest {

    @Composable
    private fun Icone(forma: Shape) {
        Box(Modifier.size(108.dp).clip(forma)) {
            Image(painterResource(R.drawable.ic_launcher_fundo), contentDescription = null, modifier = Modifier.size(108.dp))
            Image(painterResource(R.drawable.ic_launcher_frente), contentDescription = null, modifier = Modifier.size(108.dp))
        }
    }

    // Máscara redonda e de cantos arredondados, como os lançadores recortam o ícone adaptativo.
    @Test fun icone_azulejo() = captureRoboImage("src/test/screenshots/icone_azulejo.png") {
        Row(Modifier.padding(8.dp)) {
            Icone(CircleShape)
            Spacer(Modifier.size(8.dp))
            Icone(RoundedCornerShape(28))
        }
    }
}
