package pt.pixellusitano.paragemcerta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.pixellusitano.paragemcerta.ui.inicio.InicioEcra
import pt.pixellusitano.paragemcerta.ui.inicio.Painel
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ligacoes = (application as ParagemCertaApp).ligacoes
        setContent {
            TemaParagemCerta {
                InicioEcra(
                    repositorio = ligacoes.repositorio,
                    aoAbrirParagem = {},
                    painel = { posicao -> Painel(ligacoes.repositorio, posicao, aoAbrirParagem = {}, aoPesquisar = {}) },
                )
            }
        }
    }
}
