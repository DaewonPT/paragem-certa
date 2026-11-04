package pt.pixellusitano.paragemcerta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import pt.pixellusitano.paragemcerta.R

/** Só desenha quando o Android o acorda; a única ida à rede daqui é o toque em ↻. */
open class WidgetChegadas : AppWidgetProvider() {
    internal open fun atualizador(contexto: Context) = atualizadorWidgets(contexto)

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != ACAO_ATUALIZAR) return super.onReceive(contexto, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) emSegundoPlano { atualizador(contexto).aoTocar(id) }
    }

    override fun onUpdate(contexto: Context, gestor: AppWidgetManager, ids: IntArray) {
        emSegundoPlano { atualizador(contexto).redesenhar(ids.toList()) }
    }

    override fun onDeleted(contexto: Context, ids: IntArray) {
        val dados = DadosWidget(contexto)
        ids.forEach(dados::esquecer)
    }

    /**
     * Na linha principal, onde os onReceive correm um de cada vez e o immediate entra logo: dois toques seguidos não passam os dois
     * pela regra dos 30 s. Uma falha aqui não pode deitar a app abaixo, e sem o finish() o Android dava o receiver por encravado.
     */
    private fun emSegundoPlano(tarefa: suspend () -> Unit) {
        val pendente = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                tarefa()
            } catch (e: Exception) {
                // o widget fica como estava; o próximo toque ou redesenho tenta outra vez
            } finally {
                pendente.finish()
            }
        }
    }

    companion object {
        const val ACAO_ATUALIZAR = "pt.pixellusitano.paragemcerta.widget.ATUALIZAR"
    }
}

/** As ligações só se criam aqui, com o Android a acordar o widget: o resto da app continua sem correr nada ao arrancar. */
internal fun atualizadorWidgets(contexto: Context): AtualizadorWidgets {
    val app = contexto.applicationContext
    val repositorio = (app as ParagemCertaApp).ligacoes.repositorio
    val gestor = AppWidgetManager.getInstance(app)
    return AtualizadorWidgets(
        DadosWidget(app), repositorio::chegadasGuardadas, repositorio::atualizarGuardadas,
        desenhar = { id, conteudo -> gestor.updateAppWidget(id, vistas(app, id, conteudo)) },
    )
}

private class IdsLinha(val linha: Int, val nome: Int, val destino: Int, val hora: Int)

private val LINHAS = listOf(
    IdsLinha(R.id.widget_linha_1, R.id.widget_linha_1_nome, R.id.widget_linha_1_destino, R.id.widget_linha_1_hora),
    IdsLinha(R.id.widget_linha_2, R.id.widget_linha_2_nome, R.id.widget_linha_2_destino, R.id.widget_linha_2_hora),
    IdsLinha(R.id.widget_linha_3, R.id.widget_linha_3_nome, R.id.widget_linha_3_destino, R.id.widget_linha_3_hora),
)

// shortcut: o widget fica no modo (claro ou escuro) de quando se desenhou até ao redesenho seguinte, mas sempre legível; quando o minSdk for 31, setColorStateList deixa-o acompanhar o modo.
fun vistas(contexto: Context, id: Int, conteudo: ConteudoWidget): RemoteViews {
    val v = RemoteViews(contexto.packageName, R.layout.widget_chegadas)
    if (conteudo.paragem == null) {
        v.setTextViewText(R.id.widget_aviso, contexto.getString(R.string.widget_escolher))
        v.setViewVisibility(R.id.widget_atualizar, View.GONE)
        v.setOnClickPendingIntent(R.id.widget_raiz, abrirEscolha(contexto, id))
        return v
    }
    val cinzento = contexto.getColor(R.color.widget_cinzento)
    fun cor(normal: Int) = if (conteudo.cinzento) cinzento else contexto.getColor(normal)

    // O launcher volta a aplicar isto quando o telemóvel muda de modo: o que seguisse o modo novo deixava de se ler contra o que ficou fixo.
    val escuro = contexto.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    v.setInt(R.id.widget_raiz, "setBackgroundResource", if (escuro) R.drawable.widget_fundo_escuro else R.drawable.widget_fundo_claro)
    v.setInt(R.id.widget_atualizar, "setColorFilter", contexto.getColor(R.color.widget_texto))
    v.setTextColor(R.id.widget_aviso, cor(R.color.widget_texto))
    v.setTextColor(R.id.widget_rodape, cinzento)

    v.setTextViewText(R.id.widget_paragem, conteudo.paragem)
    v.setTextColor(R.id.widget_paragem, cor(R.color.widget_texto))
    LINHAS.zip(conteudo.linhas).forEach { (ids, l) ->
        v.setViewVisibility(ids.linha, View.VISIBLE)
        v.setTextViewText(ids.nome, l.linha)
        v.setTextViewText(ids.destino, l.destino)
        v.setTextViewText(ids.hora, l.hora)
        v.setTextColor(ids.nome, cor(R.color.widget_texto))
        v.setTextColor(ids.destino, cor(R.color.widget_texto))
        v.setTextColor(ids.hora, cor(if (l.aoVivo) R.color.widget_verde else R.color.widget_texto))
        val descricao = if (l.aoVivo) R.string.widget_linha_descricao_tempo_real else R.string.widget_linha_descricao
        v.setContentDescription(ids.linha, contexto.getString(descricao, l.linha, l.destino, l.hora))
    }
    if (conteudo.linhas.isEmpty()) v.setTextViewText(R.id.widget_aviso, contexto.getString(R.string.widget_sem_dados))
    else v.setViewVisibility(R.id.widget_aviso, View.GONE)
    v.setTextViewText(R.id.widget_rodape, rodape(contexto, conteudo))
    v.setOnClickPendingIntent(R.id.widget_raiz, abrirParagem(contexto, id))
    v.setOnClickPendingIntent(R.id.widget_atualizar, tocarAtualizar(contexto, id))
    return v
}

private fun rodape(contexto: Context, c: ConteudoWidget): String = when (c.estado) {
    EstadoWidget.AAtualizar -> contexto.getString(R.string.widget_a_atualizar)
    EstadoWidget.SemLigacao -> c.atualizadoAs?.let { contexto.getString(R.string.widget_sem_ligacao, it) } ?: contexto.getString(R.string.widget_sem_ligacao_nunca)
    EstadoWidget.Normal -> c.atualizadoAs?.let { contexto.getString(R.string.widget_atualizado, it) }.orEmpty()
}

// O id como requestCode: o Android não distingue dois PendingIntent só pelos extras, e cada widget tem a sua paragem.
private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

private fun tocarAtualizar(contexto: Context, id: Int) = PendingIntent.getBroadcast(
    contexto, id,
    Intent(contexto, WidgetChegadas::class.java).setAction(WidgetChegadas.ACAO_ATUALIZAR).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
    FLAGS,
)

private fun abrirParagem(contexto: Context, id: Int) = PendingIntent.getActivity(
    contexto, id,
    Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_ABRIR_PARAGEM, DadosWidget(contexto).escolha(id)?.first),
    FLAGS,
)

private fun abrirEscolha(contexto: Context, id: Int) = PendingIntent.getActivity(
    contexto, id,
    Intent(contexto, EscolherParagemAtividade::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
    FLAGS,
)
