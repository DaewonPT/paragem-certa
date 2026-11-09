package pt.pixellusitano.paragemcerta.ui.tema

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.AjustarJanela

@RunWith(RobolectricTestRunner::class)
class JanelaTest {
    @get:Rule val regra = createAndroidComposeRule<ComponentActivity>()

    private fun fundoDoTema(): Int {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tema: Resources.Theme = c.resources.newTheme().apply { applyStyle(R.style.Theme_ParagemCerta, true) }
        val v = TypedValue()
        check(tema.resolveAttribute(android.R.attr.windowBackground, v, true))
        return v.data
    }

    @Test fun windowBackgroundDoTemaClaro() = assertEquals(0xFFF4F7FC.toInt(), fundoDoTema())

    @Config(qualifiers = "night")
    @Test fun windowBackgroundDoTemaNoite() = assertEquals(0xFF0B1222.toInt(), fundoDoTema())

    @Test fun janelaSegueOModoDaApp() {
        val escuro = mutableStateOf(true)
        regra.setContent { AjustarJanela(escuro.value) }
        regra.waitForIdle()
        fun fundo() = (regra.activity.window.decorView.background as ColorDrawable).color
        assertEquals(Azulejo.Noite.toArgb(), fundo())
        escuro.value = false
        regra.waitForIdle()
        assertEquals(Azulejo.Marfim.toArgb(), fundo())
    }
}
