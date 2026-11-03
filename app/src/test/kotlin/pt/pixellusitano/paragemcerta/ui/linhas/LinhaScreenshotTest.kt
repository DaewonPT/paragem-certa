package pt.pixellusitano.paragemcerta.ui.linhas

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
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.LocalTime

// As capturas só se gravam no CI; aqui compõem-se (o MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class LinhaScreenshotTest {
    private val relato = RelatoRemoto(
        "a", "desvio", "TRANSITOUS:pt-Carris_3803", "15E", AGORA_DA_LINHA.minusSeconds(600).toEpochMilli(),
        AGORA_DA_LINHA.plusSeconds(3600).toEpochMilli(), 3, true,
    )

    // Uma linha favorita com outra paragem escolhida, o horário aberto e um relato: tudo o que o ecrã tem.
    private val estado = ESTADO_DA_LINHA.copy(
        favorita = LinhaFavorita(REF_15E, 0, PRACA_FIGUEIRA.ref, "15E", "Algés", 0),
        horarioAberto = true,
        horario = listOf(LocalTime.of(6, 31), LocalTime.of(6, 52), LocalTime.of(7, 14), LocalTime.of(23, 56), LocalTime.of(1, 20)),
        relatos = listOf(relato),
    )

    @Composable
    private fun Linha() = LinhaConteudo(
        estado = estado,
        aoMudarSentido = {}, aoEscolherParagem = {}, aoAbrirParagem = {}, aoFavorita = {}, aoUsarComoHabitual = {}, aoVerHorario = {},
        aoAbrirMapaOficial = {},
        mapa = { Box(Modifier.fillMaxSize().background(Color(0xFFDDE5F0))) },
        modifier = Modifier.width(360.dp),
        agora = AGORA_DA_LINHA,
    )

    @Test fun linha_claro() = captureRoboImage("src/test/screenshots/linha_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Linha() }
    }

    @Test fun linha_escuro() = captureRoboImage("src/test/screenshots/linha_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Linha() }
    }

    @Test fun linha_letra200() = captureRoboImage("src/test/screenshots/linha_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Linha() }
        }
    }
}
