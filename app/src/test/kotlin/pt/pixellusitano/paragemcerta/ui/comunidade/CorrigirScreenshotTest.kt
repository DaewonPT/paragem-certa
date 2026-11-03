package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

/** Compõem-se aqui; as imagens de referência gravam-se no CI. A folha é uma janela à parte, que as capturas não apanham: capta-se o cartão. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CorrigirScreenshotTest {
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)

    // O mapa verdadeiro é uma vista do MapLibre, que não se compõe aqui: um fundo liso no lugar dele.
    @Test fun corrigir_dados_claro() = captureRoboImage("src/test/screenshots/corrigir_dados_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FolhaCorrigirConteudo(
                cais, aoEnviar = {}, aoFechar = {}, ocupado = false,
                mapa = { _, _ -> Box(Modifier.fillMaxSize().background(Color(0xFFDDE6F0))) },
                modifier = Modifier.width(312.dp).height(520.dp), opcaoInicial = OpcaoCorrigir.POSICAO,
            )
        }
    }
}
