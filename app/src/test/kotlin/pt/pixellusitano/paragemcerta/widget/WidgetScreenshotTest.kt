package pt.pixellusitano.paragemcerta.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import java.time.Instant

/** Compõem-se aqui; as imagens de referência gravam-se no CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-mdpi")
class WidgetScreenshotTest {
    private val contexto: Context = RuntimeEnvironment.getApplication()
    private val cobalto = 0xFF1F4FA3.toInt()
    private val linhas = listOf(
        LinhaWidget("728", "Restelo (Estação)", "14:32", aoVivo = true, cor = cobalto),
        LinhaWidget("15E", "Algés", "14:40", aoVivo = false, cor = cobalto),
        LinhaWidget("3708", "Um destino com um nome muito comprido que não cabe todo", "14:52", aoVivo = true, cor = cobalto),
    )

    /** [altura]: um 4×2 num telemóvel comum tem 170 dp, um 4×1 cerca de 80; a mdpi, 1 dp = 1 px. */
    private fun capturar(vistas: RemoteViews, nome: String, altura: Int = 170) {
        // A captura pede uma vista dentro de uma atividade.
        val atividade = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val v = vistas.apply(atividade, FrameLayout(atividade))
        atividade.setContentView(FrameLayout(atividade).apply { addView(v, FrameLayout.LayoutParams(340, altura)) })
        ShadowLooper.idleMainLooper()
        v.captureRoboImage("src/test/screenshots/$nome.png")
    }

    private fun capturar(conteudo: ConteudoWidget, nome: String) = capturar(vistas(contexto, 1, conteudo), nome)

    @Test fun widget_claro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "14:00", cinzento = false, EstadoWidget.Normal), "widget_claro")

    @Config(qualifiers = "+night")
    @Test fun widget_escuro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "14:00", cinzento = false, EstadoWidget.Normal), "widget_escuro")

    @Test fun widget_antigo_claro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "13:20", cinzento = true, EstadoWidget.Normal), "widget_antigo_claro")

    // A Casa com a linha e o Trabalho de metro (o selo só com o ícone), saídas às 18:42 e 18:55.
    private val saida = Instant.parse("2026-11-09T18:42:00Z")
    private val casaTrabalho = ConteudoCasaTrabalho(
        Destino.Ligacao("Casa", LigacaoGuardada("210", 0xFFE2001A.toInt(), Modo.AUTOCARRO, false, saida.toEpochMilli(), 30, saida.toEpochMilli(), EstadoLigacao.OK)),
        Destino.Ligacao("Trabalho", LigacaoGuardada(null, 0xFF0072BC.toInt(), Modo.METRO, false, saida.plusSeconds(780).toEpochMilli(), 85, saida.toEpochMilli(), EstadoLigacao.OK)),
        Rodape("18:40", daPosicaoGuardada = false), "Casa|38.7369|-9.1427", "Trabalho|38.7058|-9.1431",
    )

    @Test fun widget_casa_trabalho_4x1() =
        capturar(vistasCasaTrabalho(contexto, 1, casaTrabalho, TamanhoWidget.T4x1, automatica = true), "widget_casa_trabalho_4x1", altura = 80)

    @Test fun widget_casa_trabalho_4x2() =
        capturar(vistasCasaTrabalho(contexto, 1, casaTrabalho, TamanhoWidget.T4x2, automatica = true), "widget_casa_trabalho_4x2")

    @Config(qualifiers = "+night")
    @Test fun widget_casa_trabalho_escuro() = capturar(
        vistasCasaTrabalho(contexto, 1, casaTrabalho.copy(trabalho = Destino.SemTransportes("Trabalho")), TamanhoWidget.T4x2, automatica = true),
        "widget_casa_trabalho_escuro",
    )
}
