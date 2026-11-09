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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.atualizar.Atualizacoes
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.dados.AtualizacaoDiaria
import pt.pixellusitano.paragemcerta.ui.AjustarJanela
import pt.pixellusitano.paragemcerta.ui.Navegacao
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.viagem.Notificacoes

/** Nada do que corre ao abrir pode deitar a app abaixo: se falhar, falha em silêncio e a abertura seguinte tenta outra vez. */
private suspend fun semFalhar(tarefa: suspend () -> Unit) {
    try {
        tarefa()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // sem rede, GitHub em baixo, disco cheio: tanto faz
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        /** A chave da paragem que um widget tocado quer abrir. */
        const val EXTRA_ABRIR_PARAGEM = "pt.pixellusitano.paragemcerta.ABRIR_PARAGEM"
    }

    /** A notificação da viagem foi tocada: a Navegacao abre o ecrã da viagem e desliga isto. */
    private var abrirViagem by mutableStateOf(false)

    /** A chave da paragem de um widget tocado: a Navegacao abre-a e põe isto a null. */
    private var abrirParagem by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Só na abertura: depois de rodar o ecrã o intent é o mesmo e o ecrã da viagem não se abria outra vez.
        abrirViagem = savedInstanceState == null && intent.getBooleanExtra(Notificacoes.EXTRA_ABRIR_VIAGEM, false)
        abrirParagem = if (savedInstanceState == null) intent.getStringExtra(EXTRA_ABRIR_PARAGEM) else null
        // Aqui e não na Application: essa corre também no processo :trip, que não pode tocar no WorkManager.
        AtualizacaoDiaria.cancelarTrabalhosAntigos(applicationContext)
        enableEdgeToEdge()
        val ligacoes = (application as ParagemCertaApp).ligacoes
        // Com a app fechada não corre nada: a rede e a versão nova tratam-se enquanto ela está à frente (parar ao sair, recomeçar ao voltar).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { semFalhar { AtualizacaoDiaria.aoAbrir(applicationContext, ligacoes.repositorio) } }
                launch { semFalhar { Atualizacoes.aoAbrir(applicationContext) } }
                launch { semFalhar { ligacoes.configuracoes.aoAbrir() } }
                launch { semFalhar { ligacoes.catalogoLinhas.aoAbrir() } }
                launch { semFalhar { ligacoes.fotosDeRua.aoAbrir() } }
                launch { semFalhar { ligacoes.conta.aoAbrir() } }
                launch { semFalhar { ligacoes.widgets.aoAbrirApp() } }
            }
        }
        setContent {
            val modo by ligacoes.preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
            val limites by ligacoes.preferencias.limites.collectAsStateWithLifecycle(initialValue = Limites())
            val escuro = when (modo) {
                ModoTema.SISTEMA -> isSystemInDarkTheme()
                ModoTema.CLARO -> false
                ModoTema.ESCURO -> true
            }
            AjustarJanela(escuro)
            // Os ícones da barra de estado seguem o modo da app, não só o do telemóvel.
            DisposableEffect(escuro) {
                val estilo = if (escuro) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose { }
            }
            TemaParagemCerta(modo) {
                CompositionLocalProvider(LocalLimites provides limites) {
                    Navegacao(
                        ligacoes, abrirViagem, aoAbrirViagem = { abrirViagem = false },
                        abrirParagem = abrirParagem, aoAbrirParagem = { abrirParagem = null },
                    )
                }
            }
        }
    }

    /**
     * A pessoa saiu da app (rodar o ecrã também pára a atividade, mas não é sair): é a altura de instalar a versão já descarregada
     * e de redesenhar os widgets com o que a app guardou, sem rede.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        Atualizacoes.aoSair(applicationContext)
        val ligacoes = (application as ParagemCertaApp).ligacoes
        lifecycleScope.launch { semFalhar { ligacoes.widgets.aoSairDaApp() } }
    }

    /** A app já estava aberta (SINGLE_TOP) e a notificação da viagem ou um widget foi tocado. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Notificacoes.EXTRA_ABRIR_VIAGEM, false)) abrirViagem = true
        intent.getStringExtra(EXTRA_ABRIR_PARAGEM)?.let { abrirParagem = it }
    }
}
