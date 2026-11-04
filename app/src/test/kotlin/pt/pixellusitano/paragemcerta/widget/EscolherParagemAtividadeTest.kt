package pt.pixellusitano.paragemcerta.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.TextView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

// Esta atividade abre a base de dados verdadeira, em disco: com a máquina muito ocupada, a primeira leitura leva vários segundos.
private const val ESPERA_MS = 30_000L

@RunWith(RobolectricTestRunner::class)
@Config(application = ParagemCertaApp::class)
class EscolherParagemAtividadeTest {
    @get:Rule val regra = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as ParagemCertaApp
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7, -9.1, true)

    private fun abrir(intent: Intent = Intent(app, EscolherParagemAtividade::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7)) =
        ActivityScenario.launchActivityForResult<EscolherParagemAtividade>(intent)

    private fun esperarPor(texto: String) = regra.waitUntil(ESPERA_MS) { regra.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty() }

    // O Robolectric não chega a destruir uma atividade que fez finish() depois de abrir: fica só "a acabar".
    private fun esperarQueFeche(cena: ActivityScenario<EscolherParagemAtividade>) = regra.waitUntil(ESPERA_MS) {
        var aAcabar = cena.state == Lifecycle.State.DESTROYED
        if (!aAcabar) cena.onActivity { aAcabar = it.isFinishing }
        aAcabar
    }

    @After fun fechar() = app.ligacoes.bd.close()

    @Test fun escolherDaResultadoOk() {
        runBlocking { app.ligacoes.repositorio.alternarFavorita(cais) }
        val gestor = shadowOf(AppWidgetManager.getInstance(app))
        gestor.bindAppWidgetId(7, ComponentName(app, WidgetChegadas::class.java))
        val cena = abrir()
        esperarPor("Cais do Sodré")
        regra.onNodeWithText("Cais do Sodré").performClick()
        esperarQueFeche(cena)

        assertEquals(Activity.RESULT_OK, cena.result.resultCode)
        assertEquals(7, cena.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals("CM:060419" to "Cais do Sodré", DadosWidget(app).escolha(7))
        // Com a atividade de configuração o Android não chama o onUpdate: quem deixa o widget desenhado é ela.
        assertEquals("Cais do Sodré", gestor.getViewFor(7).findViewById<TextView>(R.id.widget_paragem).text.toString())
    }

    @Test fun voltarAtrasCancela() {
        val cena = abrir()
        // A lista lê-se noutra linha de execução: sem esta espera, o @After fechava a base de dados com a leitura a meio.
        esperarPor(app.getString(R.string.widget_sem_favoritas))
        cena.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        esperarQueFeche(cena)
        assertEquals(Activity.RESULT_CANCELED, cena.result.resultCode)
        assertNull(DadosWidget(app).escolha(7))
    }

    @Test fun semFavoritasExplica() {
        abrir()
        esperarPor(app.getString(R.string.widget_sem_favoritas))
        regra.onNodeWithText(app.getString(R.string.widget_abrir_app)).performClick()
        regra.waitForIdle()
        assertEquals(MainActivity::class.java.name, shadowOf(app).nextStartedActivity.component?.className)
    }

    @Test fun semIdDoWidgetFecha() {
        val cena = abrir(Intent(app, EscolherParagemAtividade::class.java))
        esperarQueFeche(cena)
        assertEquals(Activity.RESULT_CANCELED, cena.result.resultCode)
    }
}
