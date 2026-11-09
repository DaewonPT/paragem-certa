package pt.pixellusitano.paragemcerta.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

/** Abre ao pôr o widget (e, no Android 12+, para o reconfigurar) e no "Toca para escolher a paragem". */
class EscolherParagemAtividade : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sair sem escolher tem de dizer que não: ao pôr o widget, é isto que faz o Android tirá-lo.
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        val ligacoes = (application as ParagemCertaApp).ligacoes
        setContent {
            val modo by ligacoes.preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
            val fluxo = remember { ligacoes.repositorio.favoritas() }
            val favoritas by fluxo.collectAsStateWithLifecycle(initialValue = null)
            TemaParagemCerta(modo) {
                EscolherParagem(favoritas, aoEscolher = { escolher(id, it) }, aoAbrirApp = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                })
            }
        }
    }

    private fun escolher(id: Int, f: Favorita) {
        DadosWidget(this).escolher(id, f.ref.chave, f.nome)
        lifecycleScope.launch {
            // Com uma atividade de configuração o Android não chama o onUpdate: sem isto o widget acabado de pôr ficava vazio.
            atualizadorWidgets(this@EscolherParagemAtividade).redesenhar(listOf(id))
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}

/** [favoritas] null: ainda a ler da base de dados. */
@Composable
private fun EscolherParagem(favoritas: List<Favorita>?, aoEscolher: (Favorita) -> Unit, aoAbrirApp: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp)) {
        Text(
            stringResource(R.string.widget_escolher_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (favoritas?.isEmpty() == true) {
            Text(stringResource(R.string.widget_sem_favoritas), color = MaterialTheme.colorScheme.onBackground)
            Button(aoAbrirApp, Modifier.padding(top = 16.dp), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.widget_abrir_app)) }
        }
        LazyColumn {
            items(favoritas.orEmpty(), key = { it.ref.chave }) { f ->
                Text(
                    f.nome,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { aoEscolher(f) }.padding(vertical = 12.dp),
                )
            }
        }
    }
}
