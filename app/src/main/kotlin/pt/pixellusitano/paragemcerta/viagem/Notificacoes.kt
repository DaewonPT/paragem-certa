package pt.pixellusitano.paragemcerta.viagem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.viagem.AvisoChegada
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.rotas.nomeDaLinha
import pt.pixellusitano.paragemcerta.ui.rotas.textoDistancia
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/**
 * As notificações da viagem: a fixa do serviço (progresso) e a urgente (Prepara-te, Sai agora); e as dos avisos de chegada,
 * que reutilizam os mesmos canais. Corre nos dois processos.
 */
object Notificacoes {
    const val CANAL_VIAGEM = "viagem"
    const val CANAL_AVISOS = "avisos"
    const val ID_PROGRESSO = 1
    const val ID_AVISO = 2
    const val ID_LOCALIZACAO = 3
    const val ID_VIGIA = 4

    /** Os avisos de chegada ficam todos (um id por aviso, a contar daqui), para um não apagar o outro. */
    const val ID_CHEGADA = 100

    // As ações dos botões; quem as atende é o ServicoViagem (processo :trip), que fica no mesmo pacote.
    const val ACAO_SILENCIAR = "pt.pixellusitano.paragemcerta.viagem.SILENCIAR"
    const val ACAO_JA_SAI = "pt.pixellusitano.paragemcerta.viagem.JA_SAI"
    const val ACAO_PARAR = "pt.pixellusitano.paragemcerta.viagem.PARAR"

    /** O botão Parar da vigia dos avisos de chegada, atendido pelo ServicoAvisoChegada (processo principal). */
    const val ACAO_PARAR_VIGIA = "pt.pixellusitano.paragemcerta.aviso.PARAR"
    const val EXTRA_ABRIR_VIAGEM = "pt.pixellusitano.paragemcerta.ABRIR_VIAGEM"
    private const val SERVICO = "pt.pixellusitano.paragemcerta.viagem.ServicoViagem"
    private const val SERVICO_AVISOS = "pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada"

    /** Os dois canais; pode chamar-se sempre que o serviço ou a app arrancam. O dos avisos não tem som nem vibração: toca o Alarme. */
    fun criarCanais(c: Context) {
        val gestor = c.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_VIAGEM, c.getString(R.string.viagem_canal_viagem), NotificationManager.IMPORTANCE_LOW)
                .apply { description = c.getString(R.string.viagem_canal_viagem_desc) },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_AVISOS, c.getString(R.string.viagem_canal_avisos), NotificationManager.IMPORTANCE_HIGH).apply {
                description = c.getString(R.string.viagem_canal_avisos_desc)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** A notificação fixa do serviço: onde vais e o que falta, com os botões Silenciar, Já saí (só a bordo) e Parar. */
    fun progresso(c: Context, plano: PlanoViagem, estado: EstadoViagem, viagem: Viagem, zona: ZoneId = LISBOA_FUSO): Notification =
        NotificationCompat.Builder(c, CANAL_VIAGEM)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(textoProgresso(c, plano, estado, viagem, zona))
            .setContentIntent(abrirApp(c))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, c.getString(R.string.viagem_silenciar), pedirAoServico(c, ACAO_SILENCIAR))
            .apply { if (estado.fase == Fase.A_BORDO) addAction(0, c.getString(R.string.viagem_ja_sai), pedirAoServico(c, ACAO_JA_SAI)) }
            .addAction(0, c.getString(R.string.viagem_parar), pedirAoServico(c, ACAO_PARAR))
            .build()

    /**
     * A notificação urgente de um evento. Para Prepara-te e Sai agora, estado pode ser o de depois do passo mesmo que
     * a etapa tenha acabado nesse passo: o aviso fala da etapa em veículo que acabou. ETAPA_SEGUINTE e CHEGOU não
     * tocam (ver Alarme), mas também têm texto.
     */
    fun aviso(
        c: Context, evento: Evento, plano: PlanoViagem, estado: EstadoViagem, viagem: Viagem, zona: ZoneId = LISBOA_FUSO,
    ): Notification =
        NotificationCompat.Builder(c, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(textoAviso(c, evento, plano, estado, viagem, zona))
            .setContentIntent(abrirApp(c))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

    /**
     * A notificação fixa dos avisos de chegada: "A vigiar 2 linhas em Cais do Sodré", ou "em 2 paragens" quando são várias.
     * [linhas] são as que ainda faltam avisar e [paragens] os nomes das paragens vigiadas (pelo menos uma).
     */
    fun vigia(c: Context, linhas: Int, paragens: List<String>): Notification =
        NotificationCompat.Builder(c, CANAL_VIAGEM)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(
                if (paragens.size == 1) c.resources.getQuantityString(R.plurals.vigia_linhas, linhas, linhas, paragens[0])
                else c.resources.getQuantityString(R.plurals.vigia_paragens, linhas, linhas, paragens.size),
            )
            .setContentIntent(abrirApp(c, viagem = false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, c.getString(R.string.viagem_parar), pedirAoServico(c, ACAO_PARAR_VIGIA, SERVICO_AVISOS))
            .build()

    /** O aviso urgente de que a linha pedida está a chegar à [paragem]; tocar nele abre a app. */
    fun chegada(c: Context, aviso: AvisoChegada, paragem: String): Notification =
        NotificationCompat.Builder(c, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(textoChegada(c, aviso, paragem))
            .setContentIntent(abrirApp(c, viagem = false))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

    internal fun textoChegada(c: Context, a: AvisoChegada, paragem: String): String {
        val texto = if (a.minutos < 1) c.getString(R.string.aviso_chegada_agora, nomeDaLinha(a.modo, a.linhaNome), paragem)
        else c.getString(R.string.aviso_chegada, nomeDaLinha(a.modo, a.linhaNome), a.minutos, paragem)
        return if (a.peloHorario) c.getString(R.string.aviso_chegada_horario, texto) else texto
    }

    /** "Liga a localização": toca-se nela para abrir as definições. O serviço cancela-a quando o GPS volta e quando a viagem acaba. */
    fun semLocalizacao(c: Context): Notification =
        NotificationCompat.Builder(c, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(c.getString(R.string.viagem_liga_localizacao))
            .setContentIntent(
                PendingIntent.getActivity(c, 0, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), PendingIntent.FLAG_IMMUTABLE),
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()

    /** Para o serviço que arranca sem nada a seguir: o Android exige uma notificação a quem usa startForegroundService. */
    fun vazia(c: Context): Notification =
        NotificationCompat.Builder(c, CANAL_VIAGEM).setSmallIcon(R.drawable.ic_notificacao).setContentTitle(c.getString(R.string.app_name)).build()

    internal fun textoProgresso(c: Context, plano: PlanoViagem, e: EstadoViagem, viagem: Viagem, zona: ZoneId): String {
        val etapas = plano.itinerario.etapas
        if (e.fase == Fase.TERMINADA) return c.getString(R.string.aviso_chegou, etapas.last().para.nome)
        val etapa = etapas[e.etapa]
        val texto = when {
            etapa.aPe -> c.getString(R.string.viagem_a_pe, etapa.para.nome, textoDistancia(e.progresso))
            e.fase == Fase.ESPERA -> textoEspera(c, etapa, e, zona)
            else -> viagem.faltam(e).coerceAtLeast(1).let { c.resources.getQuantityString(R.plurals.viagem_faltam, it, it, etapa.para.nome) }
        }
        return if (e.origem == Origem.HORARIO) c.getString(R.string.viagem_horario, texto) else texto
    }

    /** "chega em M min" só quando há tempo real (da fonte, ou da paragem de embarque: o atraso mexeu); senão, a hora do horário. */
    internal fun textoEspera(c: Context, etapa: Etapa, e: EstadoViagem, zona: ZoneId): String {
        val linha = nomeDaLinha(etapa.modo, etapa.linhaNome)
        val partida = (etapa.paragens.firstOrNull()?.hora ?: etapa.partida).plusSeconds(e.atrasoS)
        val minutos = (Duration.between(e.atualizado, partida).seconds + 59) / 60
        return if ((etapa.aoVivo || e.atrasoS != 0L) && minutos >= 1) c.getString(R.string.viagem_chega_em, linha, minutos)
        else c.getString(R.string.viagem_parte_as, linha, HORA.format(partida.atZone(zona)))
    }

    internal fun textoAviso(c: Context, evento: Evento, plano: PlanoViagem, e: EstadoViagem, viagem: Viagem, zona: ZoneId): String {
        val etapas = plano.itinerario.etapas
        val i = etapaDoAviso(etapas, e)
        val destino = etapas[i].para.nome
        var texto = when (evento) {
            // Se a etapa já acabou neste passo, o "faltam" do estado é o da etapa seguinte: diz 1 em vez desse.
            Evento.PREPARA_TE -> (if (i == e.etapa) viagem.faltam(e).coerceAtLeast(1) else 1)
                .let { c.resources.getQuantityString(R.plurals.aviso_prepara_te, it, it, destino) }
            Evento.SAI_AGORA -> c.getString(R.string.aviso_sai_agora, destino)
            Evento.ETAPA_SEGUINTE -> return textoProgresso(c, plano, e, viagem, zona)
            Evento.CHEGOU -> return c.getString(R.string.aviso_chegou, etapas.last().para.nome)
        }
        // Num transbordo, diz o que apanhar a seguir (mesmo com uma etapa a pé pelo meio).
        etapas.drop(i + 1).firstOrNull { !it.aPe }?.let { texto = c.getString(R.string.aviso_apanha, texto, nomeDaLinha(it.modo, it.linhaNome)) }
        return if (e.origem == Origem.HORARIO) c.getString(R.string.aviso_horario, texto) else texto
    }

    /**
     * A etapa de que o aviso fala: a atual, se vai a bordo; se o passo que o deu também acabou a etapa, o estado já é o da
     * seguinte (à espera, a pé ou, na última, terminada) e o aviso é da última etapa em veículo antes dela.
     */
    private fun etapaDoAviso(etapas: List<Etapa>, e: EstadoViagem): Int =
        if (e.fase == Fase.A_BORDO || (e.fase == Fase.TERMINADA && !etapas[e.etapa].aPe)) e.etapa
        else etapas.subList(0, e.etapa).indexOfLast { !it.aPe }.takeIf { it >= 0 } ?: e.etapa

    /**
     * Tocar na notificação leva ao ecrã da viagem (o MainActivity trata do extra), ou só abre a app (dos avisos de chegada).
     * Outro requestCode, porque o Android compara os PendingIntent sem olhar para os extras e um apagava o extra do outro.
     */
    private fun abrirApp(c: Context, viagem: Boolean = true): PendingIntent = PendingIntent.getActivity(
        c, if (viagem) 0 else 1,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (viagem) putExtra(EXTRA_ABRIR_VIAGEM, true) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pedirAoServico(c: Context, acao: String, servico: String = SERVICO): PendingIntent = PendingIntent.getService(
        c, 0, Intent(acao).setClassName(c, servico), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
