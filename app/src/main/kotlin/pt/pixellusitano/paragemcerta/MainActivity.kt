package pt.pixellusitano.paragemcerta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.ui.Navegacao
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ligacoes = (application as ParagemCertaApp).ligacoes
        setContent {
            val modo by ligacoes.preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
            val limites by ligacoes.preferencias.limites.collectAsStateWithLifecycle(initialValue = Limites())
            TemaParagemCerta(modo) {
                CompositionLocalProvider(LocalLimites provides limites) { Navegacao(ligacoes) }
            }
        }
    }
}
