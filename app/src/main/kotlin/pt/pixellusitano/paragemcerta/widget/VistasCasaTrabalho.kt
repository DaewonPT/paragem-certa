package pt.pixellusitano.paragemcerta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.ui.comum.corDoTextoSobre
import pt.pixellusitano.paragemcerta.ui.comum.minutos
import pt.pixellusitano.paragemcerta.ui.linhas.textoDoModo
import pt.pixellusitano.paragemcerta.ui.rotas.horaLisboa
import pt.pixellusitano.paragemcerta.ui.rotas.textoDuracao
import java.time.Duration
import java.time.Instant

enum class TamanhoWidget { T4x1, T4x2 }

// Limite: a faixa, os dois destinos de duas linhas e o rodapé precisam de uns 140 dp; abaixo disso fica o 4×1, um destino de cada vez.
fun tamanhoPelaAltura(alturaDp: Int): TamanhoWidget = if (alturaDp < 140) TamanhoWidget.T4x1 else TamanhoWidget.T4x2

/** As do Android 12 ou mais, com o limiar de [tamanhoPelaAltura]. Largura 0: com uma largura, um launcher que desse menos caía sempre no 4×1. */
val CHAVES_TAMANHO = mapOf(SizeF(0f, 40f) to TamanhoWidget.T4x1, SizeF(0f, 140f) to TamanhoWidget.T4x2)

/** A altura em retrato (a MIN_HEIGHT é a de paisagem); sem opções ainda, a de um 4×2 (o limiar de [tamanhoPelaAltura]). */
fun alturaDoWidget(opcoes: Bundle): Int = opcoes.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140)

/** Sem animações não há movimento; com o TalkBack, trocar sozinho escondia o destino que tem o foco. */
fun trocaAutomatica(animacoes: Boolean, talkBack: Boolean): Boolean = animacoes && !talkBack

private const val TROCA_MS = 8_000

private class IdsDestino(val raiz: Int, val icone: Int, val nome: Int, val selo: Int, val seloIcone: Int, val texto: Int, val trocar: Int)

private val CASA = IdsDestino(R.id.ct_casa, R.id.ct_casa_icone, R.id.ct_casa_nome, R.id.ct_casa_selo, R.id.ct_casa_selo_icone, R.id.ct_casa_texto, R.id.ct_casa_trocar)
private val TRABALHO = IdsDestino(
    R.id.ct_trabalho, R.id.ct_trabalho_icone, R.id.ct_trabalho_nome, R.id.ct_trabalho_selo, R.id.ct_trabalho_selo_icone, R.id.ct_trabalho_texto,
    R.id.ct_trabalho_trocar,
)

/**
 * Um tamanho do widget. [automatica]: se o 4×1 troca sozinho de destino; [mostrado]: o destino à vista no 4×1 (0 = Casa, 1 = Trabalho).
 * As cores que dependem do modo põem-se aqui, e cada desenho repõe tudo o que outro estado mudou: o launcher reaplica as ações sobre
 * as vistas do desenho anterior.
 */
// Limite: como no widget da paragem, fica no modo (claro ou escuro) de quando se desenhou até ao redesenho seguinte.
fun vistasCasaTrabalho(
    contexto: Context, id: Int, conteudo: ConteudoCasaTrabalho, tamanho: TamanhoWidget, automatica: Boolean, mostrado: Int = 0,
): RemoteViews {
    val v = RemoteViews(contexto.packageName, if (tamanho == TamanhoWidget.T4x1) R.layout.widget_casa_trabalho_4x1 else R.layout.widget_casa_trabalho_4x2)
    val escuro = contexto.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    v.setInt(android.R.id.background, "setBackgroundResource", if (escuro) R.drawable.widget_fundo_escuro else R.drawable.widget_fundo_claro)
    val rodape = conteudo.rodape?.let { contexto.getString(if (it.daPosicaoGuardada) R.string.widget_posicao_das else R.string.widget_atualizado, it.hora) }
    // O 4×1 não tem rodapé: o de quando é vai na descrição de cada destino.
    val naDescricao = rodape.takeIf { tamanho == TamanhoWidget.T4x1 }
    desenharDestino(contexto, v, id, CASA, "CASA", conteudo.casa, conteudo.rotaCasa, naDescricao)
    desenharDestino(contexto, v, id, TRABALHO, "TRABALHO", conteudo.trabalho, conteudo.rotaTrabalho, naDescricao)
    v.setOnClickPendingIntent(R.id.ct_atualizar, atualizarCasaTrabalho(contexto, id))
    if (tamanho == TamanhoWidget.T4x2) {
        v.setInt(R.id.ct_divisoria, "setBackgroundColor", contexto.getColor(R.color.widget_contorno))
        v.setTextViewText(R.id.ct_rodape, rodape.orEmpty())
        v.setTextColor(R.id.ct_rodape, contexto.getColor(R.color.widget_cinzento))
        return v
    }
    val icone = contexto.getColor(R.color.widget_icone)
    v.setInt(R.id.ct_atualizar, "setColorFilter", icone)
    // Limite: o RemoteViews não deixa parar o ViewFlipper (de remotável só tem o intervalo); parado são 24 dias até à troca seguinte.
    v.setInt(R.id.ct_flipper, "setFlipInterval", if (automatica) TROCA_MS else Int.MAX_VALUE)
    // Um desenho completo, e não showNext: numa atualização parcial o Android deita-o fora.
    v.setDisplayedChild(R.id.ct_flipper, mostrado)
    for ((ids, outro) in listOf(CASA to 1, TRABALHO to 0)) {
        v.setInt(ids.trocar, "setColorFilter", icone)
        v.setOnClickPendingIntent(ids.trocar, trocarDestino(contexto, id, outro))
    }
    return v
}

/** O estado de um destino fora das ligações: o texto à vista e o da descrição (sem o ↻, que o TalkBack não lê). */
private class Estado(val texto: Int, val descricao: Int = texto)

/** O nome do sítio (null sem morada) e, fora das ligações, o estado. */
private fun lerDestino(d: Destino): Pair<String?, Estado?> = when (d) {
    Destino.SemMorada -> null to Estado(R.string.widget_sem_morada)
    is Destino.Ligacao -> d.nome to null
    is Destino.Passada -> d.nome to null
    is Destino.Perto -> d.nome to Estado(R.string.widget_perto)
    is Destino.TocaAtualizar -> d.nome to Estado(R.string.widget_toca_atualizar, R.string.widget_toca_atualizar_descricao)
    is Destino.ACalcular -> d.nome to Estado(R.string.painel_a_calcular)
    is Destino.SemTransportes -> d.nome to Estado(R.string.widget_sem_transportes)
    is Destino.SemLigacao -> d.nome to Estado(R.string.widget_casa_sem_ligacao, R.string.widget_casa_sem_ligacao_descricao)
    is Destino.SemPosicao -> d.nome to Estado(R.string.widget_sem_posicao)
}

/** [rota]: o destino das Rotas, null sem morada; [quando]: o "atualizado às…" que vai também na descrição. */
private fun desenharDestino(contexto: Context, v: RemoteViews, id: Int, ids: IdsDestino, tipo: String, d: Destino, rota: String?, quando: String?) {
    val tinta = contexto.getColor(R.color.widget_texto)
    val cinzento = contexto.getColor(R.color.widget_cinzento)
    val (doSitio, estado) = lerDestino(d)
    val nome = doSitio ?: contexto.getString(if (tipo == "CASA") R.string.locais_casa else R.string.locais_trabalho)
    v.setTextViewText(ids.nome, nome)
    v.setTextColor(ids.nome, tinta)
    v.setInt(ids.icone, "setColorFilter", contexto.getColor(R.color.widget_icone))
    v.setTextColor(ids.texto, if (d is Destino.Ligacao) tinta else cinzento)
    val l = (d as? Destino.Ligacao)?.ligacao ?: (d as? Destino.Passada)?.ligacao
    val descricao = if (l == null) {
        val e = checkNotNull(estado)
        v.setTextViewText(ids.texto, contexto.getString(e.texto))
        v.setViewVisibility(ids.selo, View.GONE)
        v.setViewVisibility(ids.seloIcone, View.GONE)
        contexto.getString(R.string.widget_destino_estado, nome, contexto.getString(e.descricao).replaceFirstChar { it.lowercase() })
    } else {
        val passada = d is Destino.Passada
        val corSelo = if (passada) cinzento else l.cor
        val sobreOSelo = corDoTextoSobre(Color(corSelo)).toArgb()
        val duracao = textoDuracao(Duration.ofMinutes(l.duracaoMin.toLong()))
        val hora = Instant.ofEpochMilli(l.partida).horaLisboa()
        v.setTextViewText(ids.texto, realcar(contexto.getString(R.string.painel_duracao_saida, duracao, hora), duracao, hora))
        v.setViewVisibility(ids.selo, if (l.linha != null) View.VISIBLE else View.GONE)
        v.setViewVisibility(ids.seloIcone, if (l.linha == null) View.VISIBLE else View.GONE)
        when {
            l.linha != null -> {
                v.setTextViewText(ids.selo, l.linha)
                v.setTextColor(ids.selo, sobreOSelo)
                pintarSelo(v, ids.selo, corSelo)
            }
            l.soAPe -> {
                v.setImageViewResource(ids.seloIcone, R.drawable.widget_a_pe)
                v.setInt(ids.seloIcone, "setBackgroundResource", 0)
                v.setInt(ids.seloIcone, "setColorFilter", if (passada) cinzento else tinta)
            }
            else -> {
                v.setImageViewResource(ids.seloIcone, iconeDoModo(l.modo))
                v.setInt(ids.seloIcone, "setBackgroundResource", R.drawable.widget_selo_pequeno)
                pintarSelo(v, ids.seloIcone, corSelo)
                v.setInt(ids.seloIcone, "setColorFilter", sobreOSelo)
            }
        }
        val meio = if (l.soAPe) contexto.getString(R.string.a_pe).lowercase()
        else listOfNotNull(contexto.getString(textoDoModo(l.modo)).lowercase(), l.linha).joinToString(" ")
        val frase = if (passada) R.string.widget_destino_passada else R.string.widget_destino_ligacao
        contexto.getString(frase, nome, meio, hora, minutos(l.duracaoMin.toLong()))
    }
    v.setContentDescription(ids.raiz, listOfNotNull(descricao, quando).joinToString(", "))
    v.setOnClickPendingIntent(ids.raiz, abrirDestino(contexto, id, tipo, rota))
}

private fun iconeDoModo(m: Modo) = when (m) {
    Modo.AUTOCARRO, Modo.OUTRO -> R.drawable.widget_autocarro
    Modo.ELETRICO -> R.drawable.widget_eletrico
    Modo.METRO -> R.drawable.widget_metro
    Modo.COMBOIO -> R.drawable.widget_comboio
    Modo.BARCO -> R.drawable.widget_barco
}

/** Os minutos e a hora em sans-serif-black, como as horas do widget da paragem. */
private fun realcar(texto: String, vararg partes: String): CharSequence = SpannableString(texto).apply {
    for (p in partes) texto.indexOf(p).takeIf { it >= 0 }?.let { setSpan(TypefaceSpan("sans-serif-black"), it, it + p.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}

// Um Uri para cada toque: o Android não distingue dois PendingIntent só pelos extras, e tocar na Casa abria o Trabalho.
private fun uri(id: Int, toque: String) = Uri.parse("paragemcerta://widget/$id/$toque")

/** Com morada abre as Rotas até lá, como o cartão do Início; sem morada, o Início a escolhê-la. */
private fun abrirDestino(contexto: Context, id: Int, tipo: String, rota: String?) = PendingIntent.getActivity(
    contexto, id,
    Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(uri(id, tipo))
        .apply { if (rota != null) putExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL, rota) else putExtra(MainActivity.EXTRA_DEFINIR_LOCAL, tipo) },
    FLAGS,
)

private fun atualizarCasaTrabalho(contexto: Context, id: Int) = PendingIntent.getBroadcast(
    contexto, id,
    Intent(contexto, WidgetCasaTrabalho::class.java).setAction(WidgetCasaTrabalho.ACAO_ATUALIZAR).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
    FLAGS,
)

private fun trocarDestino(contexto: Context, id: Int, indice: Int) = PendingIntent.getBroadcast(
    contexto, id,
    Intent(contexto, WidgetCasaTrabalho::class.java).setAction(WidgetCasaTrabalho.ACAO_TROCAR).setData(uri(id, "trocar/$indice"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(WidgetCasaTrabalho.EXTRA_MOSTRAR, indice),
    FLAGS,
)
