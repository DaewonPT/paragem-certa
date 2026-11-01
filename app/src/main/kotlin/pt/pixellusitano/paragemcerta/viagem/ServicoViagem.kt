package pt.pixellusitano.paragemcerta.viagem

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import pt.pixellusitano.paragemcerta.core.regras.pausaDepoisDeFalha
import pt.pixellusitano.paragemcerta.core.viagem.Entrada
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

private const val RELOGIO_MS = 5_000L
private const val GPS_POUPAR_MS = 10_000L
private const val GPS_PRECISO_MS = 1_000L
private const val SEM_PRECISAO = 999f
// O mesmo teto da guarda do motor (2 h de atraso + 30 min): o telemóvel fica acordado enquanto a viagem puder correr.
private val BLOQUEIO_MAX = Duration.ofHours(2).plusMinutes(30)
private val CM_INTERVALO = Duration.ofSeconds(30)
private const val CM_TOLERANCIA_S = 90L

/**
 * A viagem a correr, no processo :trip (ver o manifesto): alimenta o motor (core/viagem) com as posições do GPS e o
 * relógio, escreve o trip-state.json, atualiza a notificação fixa e toca os avisos. Não usa a base de dados, as
 * definições nem o WorkManager: só o trip-plan.json que a app escreveu, a Carris Metropolitana e o GPS. Tudo corre na
 * thread principal (posições, relógio e respostas), por isso o estado não precisa de trincos.
 */
class ServicoViagem : Service() {
    /** O relógio, o alarme e a Carris Metropolitana, trocáveis nos testes. */
    internal var agora: () -> Instant = Instant::now
    internal var alarme: (Evento, Boolean, Boolean) -> Unit = { evento, som, vibrar -> Alarme.tocar(this, evento, som, vibrar) }
    internal var cm: CarrisMetropolitana? = null

    private class Corrida(val plano: PlanoViagem, val viagem: Viagem, var estado: EstadoViagem)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gestor by lazy { getSystemService(NotificationManager::class.java) }
    private var corrida: Corrida? = null
    private var trabalho: Job? = null
    private var fim: Job? = null
    private var bloqueio: PowerManager.WakeLock? = null

    /** O modo do pedido de posições em vigor (true = a cada 1 s); null se não há pedido (sem permissão precisa). */
    private var preciso: Boolean? = null

    /** Até quando o último aviso está a tocar (uptimeMillis): o serviço não pára antes, o Android calava o áudio. */
    private var alarmeAte = 0L

    private val ouvinte = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) = posicao(location)
        override fun onProviderEnabled(provider: String) = semLocalizacao(false)
        override fun onProviderDisabled(provider: String) = semLocalizacao(true)
    }

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACAO_COMECAR -> iniciar(retomar = false)
            ACAO_RETOMAR -> iniciar(retomar = true)
            Notificacoes.ACAO_PARAR -> parar()
            // Os botões de uma notificação velha podem chegar a um processo novo, sem viagem a correr.
            Notificacoes.ACAO_SILENCIAR -> corrida?.let { aplicar(Entrada.Silenciar(agora())) } ?: stopSelf()
            // Só a bordo: à espera, "Já saí" saltava o autocarro que a pessoa ainda vai apanhar (e os avisos dele); a pé não há de que sair.
            Notificacoes.ACAO_JA_SAI -> corrida?.let { if (it.estado.fase == Fase.A_BORDO) aplicar(Entrada.JaSai(agora())) } ?: stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY // o Android 14 e seguintes não deixa reabrir sozinho: a app mostra "Viagem interrompida"
    }

    override fun onDestroy() {
        limpar()
        scope.cancel()
        super.onDestroy()
    }

    private fun iniciar(retomar: Boolean) {
        limpar() // um segundo arranque com a viagem a correr recomeça do zero
        val plano = FicheirosViagem.lerPlano(this)
        val guardado = if (retomar) FicheirosViagem.lerEstado(this, agora()) else null
        if (plano == null || (retomar && guardado == null)) {
            // Quem usa startForegroundService tem de dar a notificação mesmo que não haja nada a seguir.
            primeiroPlano(Notificacoes.vazia(this))
            encerrar()
            return
        }
        val viagem = Viagem(plano.itinerario)
        val estado = guardado ?: viagem.inicio(agora())
        // O estado vai antes do pedido ao Android: se ele recusar, fica o ficheiro para o "Viagem interrompida".
        if (guardado == null) {
            FicheirosViagem.gravacao(this).delete()
            FicheirosViagem.gravarEstado(this, estado)
        }
        if (!primeiroPlano(Notificacoes.progresso(this, plano, estado, viagem))) {
            // Num arranque novo ninguém vai retomar: apagam-se os ficheiros. Os de uma viagem retomada ficam para o "Viagem
            // interrompida". Em ambos o ecrã da viagem, já aberto, fecha em vez de ficar parado.
            if (guardado == null) FicheirosViagem.apagar(this)
            avisarApp(semServico = true)
            stopSelf()
            return
        }
        corrida = Corrida(plano, viagem, estado)
        // shortcut: wake lock durante a viagem toda, passar a alarmes exatos se a bateria se queixar.
        // O limite é só um teto: a viagem atrasada passa da chegada prevista e o motor é que diz quando acaba.
        val limite = Duration.between(agora(), plano.itinerario.chegada).plus(BLOQUEIO_MAX).toMillis().coerceAtLeast(60_000L)
        bloqueio = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParagemCerta:viagem").apply {
            setReferenceCounted(false)
            acquire(limite)
        }
        pedirPosicoes()
        publicar()
        trabalho = scope.launch {
            launch { relogio() }
            launch { chegadasCm() }
        }
    }

    /**
     * O Android pode recusar (arranque em segundo plano com um IllegalStateException; a partir do 14, sem nenhuma permissão de
     * localização, com um SecurityException): então não há serviço. Antes da API 29 o tipo é ignorado.
     */
    @SuppressLint("InlinedApi")
    private fun primeiroPlano(n: Notification): Boolean = try {
        ServiceCompat.startForeground(this, Notificacoes.ID_PROGRESSO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (_: RuntimeException) {
        false
    }

    private suspend fun relogio() {
        while (true) {
            delay(RELOGIO_MS)
            aplicar(Entrada.Relogio(agora()))
        }
    }

    /** Enquanto espera um autocarro da Carris Metropolitana, as chegadas da paragem de embarque (a cada 30 s) dizem quando ele parte. */
    private suspend fun chegadasCm() {
        var proxima = Instant.MIN
        var falhas = 0
        while (true) {
            val c = corrida ?: return
            val i = c.estado.etapa
            val etapa = c.plano.itinerario.etapas[i]
            val paragem = etapa.paragens.firstOrNull()?.id?.let(::cmParagemDeTransitous)
            if (c.estado.fase == Fase.ESPERA && paragem != null && agora() >= proxima) {
                val servidor = cm ?: CarrisMetropolitana(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).also { cm = it }
                when (val r = servidor.chegadas(paragem, agora())) {
                    is Resultado.Ok -> {
                        falhas = 0
                        proxima = agora().plus(CM_INTERVALO)
                        partidaAoVivo(r.valor, etapa)?.let { aplicar(Entrada.Partida(i, it, agora())) }
                    }
                    is Resultado.Falha -> proxima = agora().plus(maxOf(CM_INTERVALO, pausaDepoisDeFalha(Fonte.CM, falhas++)))
                }
            }
            delay(RELOGIO_MS)
        }
    }

    /** Um passo do motor e tudo o que dele sai: avisos, estado, notificação e, se acabou, o fim. */
    private fun aplicar(x: Entrada) {
        val c = corrida ?: return
        val (novo, eventos) = c.viagem.passo(c.estado, x)
        val mudou = novo.copy(atualizado = c.estado.atualizado) != c.estado
        c.estado = novo
        eventos.forEach { avisar(c, it) }
        if (mudou || x is Entrada.Relogio) publicar()
        if (preciso != null && novo.altaPrecisao != preciso) pedirPosicoes()
        if (novo.terminada) acabar()
    }

    /** A notificação do aviso e o alarme (se não estiver silenciado), sempre antes de o serviço parar. */
    private fun avisar(c: Corrida, evento: Evento) {
        gestor.notify(Notificacoes.ID_AVISO, Notificacoes.aviso(this, evento, c.plano, c.estado, c.viagem))
        val ms = Alarme.duracaoMs(evento)
        if (ms > 0 && !c.estado.silenciado) {
            alarme(evento, c.plano.som, c.plano.vibrar)
            alarmeAte = maxOf(alarmeAte, SystemClock.uptimeMillis() + ms)
        }
    }

    /** O estado em ficheiro, a notificação fixa e o aviso à app (sem dados: ela volta a ler o ficheiro). */
    private fun publicar() {
        val c = corrida ?: return
        FicheirosViagem.gravarEstado(this, c.estado)
        gestor.notify(Notificacoes.ID_PROGRESSO, Notificacoes.progresso(this, c.plano, c.estado, c.viagem))
        avisarApp()
    }

    private fun avisarApp(semServico: Boolean = false) =
        sendBroadcast(Intent(FicheirosViagem.ACAO_ESTADO).setPackage(packageName).putExtra(FicheirosViagem.EXTRA_SEM_SERVICO, semServico))

    /**
     * A viagem acabou (CHEGOU, a guarda dos 30 min, ou uma última etapa a pé que nunca chega a 50 m do destino):
     * escreve o estado uma última vez e pára, mas só depois de o alarme acabar de tocar.
     */
    private fun acabar() {
        if (fim != null) return
        val espera = alarmeAte - SystemClock.uptimeMillis()
        fim = scope.launch {
            if (espera > 0) delay(espera)
            publicar()
            encerrar()
        }
    }

    private fun parar() {
        FicheirosViagem.apagar(this)
        gestor.cancel(Notificacoes.ID_AVISO)
        avisarApp()
        encerrar()
    }

    private fun encerrar() {
        limpar()
        gestor.cancel(Notificacoes.ID_LOCALIZACAO)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Larga tudo o que a viagem prende: tarefas, GPS e wake lock. Tirar o pedido de posições não exige permissão (o lint não sabe). */
    @SuppressLint("MissingPermission")
    private fun limpar() {
        trabalho?.cancel()
        fim?.cancel()
        trabalho = null
        fim = null
        LocationManagerCompat.removeUpdates(getSystemService(LocationManager::class.java), ouvinte)
        preciso = null
        bloqueio?.takeIf { it.isHeld }?.release()
        bloqueio = null
        corrida = null
        alarmeAte = 0L
    }

    /** O GPS a cada 10 s, ou a cada 1 s na reta final (altaPrecisao). Sem a permissão precisa a viagem corre pelo horário. */
    private fun pedirPosicoes() {
        val c = corrida ?: return
        val lm = getSystemService(LocationManager::class.java)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (LocationManager.GPS_PROVIDER !in lm.allProviders) return
        val alta = c.estado.altaPrecisao
        val pedido = LocationRequestCompat.Builder(if (alta) GPS_PRECISO_MS else GPS_POUPAR_MS)
            .setQuality(if (alta) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()
        LocationManagerCompat.removeUpdates(lm, ouvinte) // o mesmo ouvinte com outro ritmo: um pedido só
        LocationManagerCompat.requestLocationUpdates(lm, LocationManager.GPS_PROVIDER, pedido, ContextCompat.getMainExecutor(this), ouvinte)
        preciso = alta
        semLocalizacao(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    private fun semLocalizacao(desligada: Boolean) =
        if (desligada) gestor.notify(Notificacoes.ID_LOCALIZACAO, Notificacoes.semLocalizacao(this)) else gestor.cancel(Notificacoes.ID_LOCALIZACAO)

    /** A hora de uma posição é a de quando chega ao serviço: o motor e o relógio usam todos a mesma. */
    private fun posicao(l: Location) {
        val hora = agora()
        val precisao = if (l.hasAccuracy()) l.accuracy else SEM_PRECISAO
        if (BuildConfig.DEBUG) gravarPosicao(hora, l, precisao)
        aplicar(Entrada.Gps(l.latitude, l.longitude, precisao, hora))
    }

    /** Só em depuração: s,lat,lon,acc (s em segundos desde 1970), o formato das gravações que o teste do motor lê. */
    private fun gravarPosicao(hora: Instant, l: Location, precisao: Float) {
        val linha = String.format(Locale.ROOT, "%.3f,%.6f,%.6f,%.0f\n", hora.toEpochMilli() / 1000.0, l.latitude, l.longitude, precisao)
        try {
            FicheirosViagem.gravacao(this).appendText(linha)
        } catch (_: IOException) {
        }
    }

    companion object {
        const val ACAO_COMECAR = "pt.pixellusitano.paragemcerta.viagem.COMECAR"
        const val ACAO_RETOMAR = "pt.pixellusitano.paragemcerta.viagem.RETOMAR"
    }
}

/**
 * A hora a que o autocarro da etapa parte da paragem de embarque segundo o tempo real: a chegada da mesma linha cuja hora
 * programada difere até 90 s da do horário (null se não houver, ou se essa não tiver tempo real).
 */
internal fun partidaAoVivo(chegadas: List<Chegada>, etapa: Etapa): Instant? =
    chegadas
        .filter { it.linhaNome == etapa.linhaNome }
        .map { it to abs(Duration.between(it.programada, etapa.partidaProgramada).seconds) }
        .filter { it.second <= CM_TOLERANCIA_S }
        .minByOrNull { it.second }?.first?.prevista
