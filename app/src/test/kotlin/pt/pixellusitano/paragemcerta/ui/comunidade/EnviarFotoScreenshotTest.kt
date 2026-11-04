package pt.pixellusitano.paragemcerta.ui.comunidade

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemConteudo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

/** Compõem-se aqui; as imagens de referência gravam-se no CI. A folha é uma janela à parte, que as capturas não apanham: capta-se o cartão. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EnviarFotoScreenshotTest {
    @Test fun enviar_foto_claro() = captureRoboImage("src/test/screenshots/enviar_foto_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FolhaEnviarFotoConteudo(
                pronta = true, ocupado = false, falhou = true, mensagem = R.string.comunidade_sem_ligacao, erro = true,
                aoEscolher = {}, aoTirar = {}, aoEnviar = {}, aoFechar = {}, modifier = Modifier.width(312.dp),
            )
        }
    }

    // Um céu e um chão, para o retrato se perceber nas capturas; a imagem é feita aqui, sem esperar por nenhum pedido.
    private fun imagem() = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888).also {
        val tela = Canvas(it)
        tela.drawColor(Color.rgb(120, 170, 220))
        tela.drawRect(0f, 480f, 960f, 720f, Paint().apply { color = Color.rgb(90, 90, 100) })
    }

    @Composable
    private fun Ecra(fotos: @Composable () -> Unit) {
        ParagemConteudo(
            nome = "Cais do Sodré (Terminal)", codigo = "060419", acessivel = true, favorita = false, vigiada = false, estado = null, filtro = null,
            linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = Instant.parse("2026-11-03T12:00:00Z"),
            aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
            fotos = fotos, modifier = Modifier.width(360.dp),
        )
    }

    @Test fun foto_paragem_claro() = captureRoboImage("src/test/screenshots/foto_paragem_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            Ecra { FotoDaParagemConteudo(imagem().asImageBitmap(), falhou = false, aoOcultar = {}, aoDenunciar = {}) }
        }
    }

    @Test fun foto_rua_claro() = captureRoboImage("src/test/screenshots/foto_rua_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            Ecra {
                FotoDeRuaConteudo(
                    FotoDeRua("pan-1", "https://panoramax.exemplo/pan-1/thumb.jpg", "Robot8A", "CC BY-SA 4.0", "2022-03-13"),
                    imagem().asImageBitmap(), falhou = false, aoAbrirPagina = {}, aoOcultar = {},
                )
            }
        }
    }
}
