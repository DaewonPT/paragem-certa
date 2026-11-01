package pt.pixellusitano.paragemcerta

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.dados.AtualizacaoDiaria
import pt.pixellusitano.paragemcerta.ui.Navegacao
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.viagem.Notificacoes

class MainActivity : ComponentActivity() {
    /** A notificação da viagem foi tocada: a Navegacao abre o ecrã da viagem e desliga isto. */
    private var abrirViagem by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Só na abertura: depois de rodar o ecrã o intent é o mesmo e o ecrã da viagem não se abria outra vez.
        abrirViagem = savedInstanceState == null && intent.getBooleanExtra(Notificacoes.EXTRA_ABRIR_VIAGEM, false)
        // Aqui e não na Application: essa corre também no processo :trip, que não pode tocar no WorkManager.
        AtualizacaoDiaria.agendar(applicationContext)
        Atualizacoes.agendar(this)
        enableEdgeToEdge()
        val ligacoes = (application as ParagemCertaApp).ligacoes
        setContent {
            val modo by ligacoes.preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
            val limites by ligacoes.preferencias.limites.collectAsStateWithLifecycle(initialValue = Limites())
            val escuro = when (modo) {
                ModoTema.SISTEMA -> isSystemInDarkTheme()
                ModoTema.CLARO -> false
                ModoTema.ESCURO -> true
            }
            // Os ícones da barra de estado seguem o modo da app, não só o do telemóvel.
            DisposableEffect(escuro) {
                val estilo = if (escuro) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose { }
            }
            TemaParagemCerta(modo) {
                CompositionLocalProvider(LocalLimites provides limites) {
                    Navegacao(ligacoes, abrirViagem, aoAbrirViagem = { abrirViagem = false })
                }
            }
        }
    }

    /** A app já estava aberta (SINGLE_TOP) e a notificação da viagem foi tocada. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Notificacoes.EXTRA_ABRIR_VIAGEM, false)) abrirViagem = true
    }
}
