package pt.pixellusitano.paragemcerta.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import android.widget.RemoteViews
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import pt.pixellusitano.paragemcerta.ui.comum.animacoesLigadas

/** "Casa e Trabalho": só desenha o que está guardado quando o Android o acorda; a única ida à rede daqui é o toque em ↻. */
open class WidgetCasaTrabalho : AppWidgetProvider() {
    internal open fun atualizador(contexto: Context): AtualizadorCasaTrabalho = (contexto.applicationContext as ParagemCertaApp).ligacoes.casaTrabalho

    override fun onReceive(contexto: Context, intent: Intent) {
        when (intent.action) {
            ACAO_ATUALIZAR -> emSegundoPlano { atualizador(contexto).atualizar() }
            ACAO_TROCAR -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                emSegundoPlano {
                    DadosWidget(contexto).mostrar(id, intent.getIntExtra(EXTRA_MOSTRAR, 0))
                    atualizador(contexto).redesenhar(listOf(id))
                }
            }
            else -> super.onReceive(contexto, intent)
        }
    }

    override fun onUpdate(contexto: Context, gestor: AppWidgetManager, ids: IntArray) {
        emSegundoPlano { atualizador(contexto).redesenhar(ids.toList()) }
    }

    // No Android 8 a 11 cada desenho escolhe o tamanho pela altura: mudar o tamanho pede outro desenho.
    override fun onAppWidgetOptionsChanged(contexto: Context, gestor: AppWidgetManager, id: Int, opcoes: Bundle) {
        emSegundoPlano { atualizador(contexto).redesenhar(listOf(id)) }
    }

    override fun onDeleted(contexto: Context, ids: IntArray) {
        val dados = DadosWidget(contexto)
        ids.forEach(dados::esquecer)
    }

    companion object {
        const val ACAO_ATUALIZAR = "pt.pixellusitano.paragemcerta.widget.ATUALIZAR_CASA_TRABALHO"
        const val ACAO_TROCAR = "pt.pixellusitano.paragemcerta.widget.TROCAR_CASA_TRABALHO"

        /** O destino a pôr à vista no 4×1 (0 = Casa, 1 = Trabalho). */
        const val EXTRA_MOSTRAR = "pt.pixellusitano.paragemcerta.widget.MOSTRAR"
    }
}

/**
 * O que vai para o ecrã inicial, e o único sítio onde o desenho depende da versão: no Android 12 ou mais o launcher escolhe o tamanho
 * pelo mapa; antes, escolhe-se aqui pela altura que ele deu.
 */
internal fun desenhoDoWidget(contexto: Context, id: Int, conteudo: ConteudoCasaTrabalho): RemoteViews {
    val talkBack = contexto.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    val automatica = trocaAutomatica(animacoesLigadas(contexto), talkBack)
    val mostrado = DadosWidget(contexto).mostrado(id)
    fun desenho(t: TamanhoWidget) = vistasCasaTrabalho(contexto, id, conteudo, t, automatica, mostrado)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return RemoteViews(CHAVES_TAMANHO.mapValues { desenho(it.value) })
    return desenho(tamanhoPelaAltura(alturaDoWidget(AppWidgetManager.getInstance(contexto).getAppWidgetOptions(id))))
}
