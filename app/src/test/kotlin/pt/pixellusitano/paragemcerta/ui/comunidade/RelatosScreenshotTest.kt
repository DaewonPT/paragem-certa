package pt.pixellusitano.paragemcerta.ui.comunidade

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
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemConteudo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

/** Compõem-se aqui; as imagens de referência gravam-se no CI. O diálogo é uma janela à parte, que as capturas não apanham: capta-se o cartão. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RelatosScreenshotTest {
    private val agora = Instant.parse("2026-11-03T12:00:00Z")

    private fun relato(id: String, tipo: String, linha: String, confirmacoes: Int, minutos: Long) = RelatoRemoto(
        id, tipo, "CM:060419", linha, agora.minusSeconds(minutos * 60).toEpochMilli(), agora.plusSeconds(3600).toEpochMilli(), confirmacoes, confirmacoes >= 2,
    )

    private val relatos = listOf(
        relato("r1", "desvio", "736", 2, 4), relato("r2", "vai_cheio", "15E", 1, 12), relato("r3", "elevador", "", 1, 190),
    )

    @Composable
    private fun Ecra() = ParagemConteudo(
        nome = "Cais do Sodré (Terminal)", codigo = "060419", acessivel = true, favorita = false, vigiada = false, estado = null, filtro = null,
        linhasPresentes = listOf("15E" to "15E", "736" to "736"), alertas = emptyList(), linhas = emptyMap(), agora = agora,
        aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
        relatos = relatos, aoVotar = { _, _ -> }, aoDenunciar = {}, aoOcultar = {},
        modifier = Modifier.width(360.dp),
    )

    @Test fun relatos_paragem_claro() = captureRoboImage("src/test/screenshots/relatos_paragem_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra() }
    }

    @Test fun relatos_paragem_escuro() = captureRoboImage("src/test/screenshots/relatos_paragem_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Ecra() }
    }

    @Test fun relatos_paragem_letra200() = captureRoboImage("src/test/screenshots/relatos_paragem_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Ecra() }
        }
    }

    @Test fun denunciar_claro() = captureRoboImage("src/test/screenshots/denunciar_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            DenunciarConteudo("ana@exemplo.pt", aoEnviar = { _, _, _ -> }, aoFechar = {}, modifier = Modifier.width(312.dp))
        }
    }
}
