package pt.pixellusitano.paragemcerta.widget

import android.content.Context
import android.widget.FrameLayout
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

    private fun capturar(conteudo: ConteudoWidget, nome: String) {
        // A captura pede uma vista dentro de uma atividade. Um 4×2 num telemóvel comum; a mdpi, 1 dp = 1 px.
        val atividade = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val v = vistas(contexto, 1, conteudo).apply(atividade, FrameLayout(atividade))
        atividade.setContentView(FrameLayout(atividade).apply { addView(v, FrameLayout.LayoutParams(340, 170)) })
        ShadowLooper.idleMainLooper()
        v.captureRoboImage("src/test/screenshots/$nome.png")
    }

    @Test fun widget_claro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "14:00", cinzento = false, EstadoWidget.Normal), "widget_claro")

    @Config(qualifiers = "+night")
    @Test fun widget_escuro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "14:00", cinzento = false, EstadoWidget.Normal), "widget_escuro")

    @Test fun widget_antigo_claro() = capturar(ConteudoWidget("Cais do Sodré", linhas, "13:20", cinzento = true, EstadoWidget.Normal), "widget_antigo_claro")
}
