package pt.pixellusitano.paragemcerta.viagem

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import pt.pixellusitano.paragemcerta.core.viagem.Evento

/**
 * O som e a vibração de Prepara-te e Sai agora, tocados pelo próprio serviço com o uso "alarme" do Android: assim
 * funcionam com o telemóvel em silêncio, seguem o volume do alarme e não precisam de acesso a "Não incomodar".
 * Chama-se da thread principal e de dentro do serviço em primeiro plano, antes de ele parar (o Android 15 e 17 calam
 * o áudio de quem já não tem serviço nem ecrã).
 */
object Alarme {
    private const val PULSO_MS = 400L
    private const val PAUSA_MS = 300L

    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val fio = Handler(Looper.getMainLooper())
    private var toque: Ringtone? = null

    /** 3 s no Prepara-te e 6 s no Sai agora; os outros eventos não tocam (0). O serviço só pára depois de este tempo passar. */
    fun duracaoMs(evento: Evento): Long = when (evento) {
        Evento.PREPARA_TE -> 3_000L
        Evento.SAI_AGORA -> 6_000L
        Evento.ETAPA_SEGUINTE, Evento.CHEGOU -> 0L
    }

    /** Toca o que o evento pede (ver duracaoMs). Um aviso novo corta o que ainda estiver a tocar. */
    fun tocar(c: Context, evento: Evento, som: Boolean, vibrar: Boolean) {
        val ms = duracaoMs(evento)
        if (ms == 0L) return
        fio.removeCallbacksAndMessages(null)
        toque?.stop()
        toque = null
        if (som) tocarSom(c, ms)
        if (vibrar) vibrar(c, ms)
    }

    private fun tocarSom(c: Context, ms: Long) {
        // O som de alarme do telemóvel; sem ele, o das notificações.
        val uri = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_NOTIFICATION) ?: return
        val r = RingtoneManager.getRingtone(c, uri) ?: return
        r.audioAttributes = atributos
        if (Build.VERSION.SDK_INT >= 28) r.isLooping = true // o toque pode ser mais curto que o aviso
        r.play()
        toque = r
        fio.postDelayed({ r.stop() }, ms)
    }

    private fun vibrar(c: Context, ms: Long) {
        val vibrador = c.getSystemService(Vibrator::class.java) ?: return
        // 400 ms a vibrar e 300 de pausa, as vezes que couberem em ms; o primeiro 0 é a espera antes do primeiro pulso.
        val pulsos = (ms / (PULSO_MS + PAUSA_MS)).toInt()
        val tempos = LongArray(1 + 2 * pulsos) { if (it == 0) 0 else if (it % 2 == 1) PULSO_MS else PAUSA_MS }
        val efeito = VibrationEffect.createWaveform(tempos, -1)
        if (Build.VERSION.SDK_INT >= 33) vibrador.vibrate(efeito, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        else @Suppress("DEPRECATION") vibrador.vibrate(efeito, atributos)
    }
}
