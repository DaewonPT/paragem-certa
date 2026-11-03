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
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinhaValida
import pt.pixellusitano.paragemcerta.core.comunidade.paragemAFrente
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.regras.pausaDepoisDeFalha
import pt.pixellusitano.paragemcerta.core.viagem.Entrada
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.core.viagem.paragensDe
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
private const val CM_VIAGEM_S = 60L
/** Quem continua à espera 2 min depois da partida ao vivo do autocarro perdeu-o. */
private val CM_PERDIDO = Duration.ofMinutes(2)
// vehicles tem 306 KB (42 KB comprimido): só a bordo, na reta final ou sem GPS, e no máximo a cada 15 s.
private val VEICULOS_INTERVALO = Duration.ofSeconds(15)
private const val VEICULO_IDADE_S = 60L
private const val VEICULO_PERTO_M = 150.0
// Os relatos confirmados nas linhas da viagem: um pedido a cada 5 min, até 20 linhas (o máximo do /api/avisos).
private const val AVISOS_MS = 300_000L
private const val MAX_LINHAS_AVISOS = 20

/**
 * A viagem a correr, no processo :trip (ver o manifesto): alimenta o motor (core/viagem) com as posições do GPS e o
 * relógio, escreve o trip-state.json, atualiza a notificação fixa e toca os avisos. Não usa a base de dados, as
 * definições nem o WorkManager: só o trip-plan.json que a app escreveu, a Carris Metropolitana, o servidor da Paragem
 * Certa (os avisos da comunidade) e o GPS. Tudo corre na thread principal (posições, relógio e respostas), por isso o
 * estado não precisa de trincos.
 */
class ServicoViagem : Service() {
    /** O relógio, o alarme, a Carris Metropolitana e o servidor da Paragem Certa, trocáveis nos testes. */
    internal var agora: () -> Instant = Instant::now
    internal var alarme: (Evento, Boolean, Boolean) -> Unit = { evento, som, vibrar -> Alarme.tocar(this, evento, som, vibrar) }
    internal var cm: CarrisMetropolitana? = null
    internal var servidor: ServidorParagemCerta? = null

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

    /** O autocarro que a pessoa espera (trip_id e partida ao vivo), tirado das chegadas enquanto se espera por ele. */
    private var viagemCm: AutocarroCm? = null

    /** A última posição que o motor aceitou: os autocarros perto dela podem ser o da viagem. */
    private var ultimaAceite: Entrada.Gps? = null

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
            launch { veiculosCm() }
            launch { avisosComunidade() }
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

    private fun servidorCm() = cm ?: CarrisMetropolitana(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).also { cm = it }

    /**
     * Enquanto espera um autocarro da Carris Metropolitana, as chegadas da paragem de embarque (a cada 30 s) dizem quando
     * ele parte e qual é o trip_id dele. A cada volta (com ou sem consulta nova) vê se a pessoa já o perdeu.
     */
    private suspend fun chegadasCm() {
        var proxima = Instant.MIN
        var falhas = 0
        while (true) {
            val c = corrida ?: return
            val i = c.estado.etapa
            val etapa = c.plano.itinerario.etapas[i]
            val paragem = etapa.paragens.firstOrNull()?.id?.let(::cmParagemDeTransitous)
            if (c.estado.fase == Fase.ESPERA && paragem != null) {
                var chegadas = emptyList<Chegada>()
                if (agora() >= proxima) when (val r = servidorCm().chegadas(paragem, agora())) {
                    is Resultado.Ok -> {
                        falhas = 0
                        proxima = agora().plus(CM_INTERVALO)
                        chegadas = r.valor
                        partidaAoVivo(r.valor, etapa)?.let { aplicar(Entrada.Partida(i, it, agora())) }
                    }
                    is Resultado.Falha -> proxima = agora().plus(maxOf(CM_INTERVALO, pausaDepoisDeFalha(Fonte.CM, falhas++)))
                }
                viagemCm = autocarroDaEspera(chegadas, etapa, i, viagemCm, agora())
            }
            delay(RELOGIO_MS)
        }
    }

    /**
     * A bordo de um autocarro da Carris Metropolitana, na reta final (alta precisão) ou sem GPS: o veículo da viagem em
     * vehicles (a cada 15 s) confirma onde vai e pode dar o Sai agora mais cedo.
     */
    private suspend fun veiculosCm() {
        var proxima = Instant.MIN
        var falhas = 0
        var candidato: Pair<Int, String>? = null // (etapa, id do veículo) da consulta anterior
        while (true) {
            val c = corrida ?: return
            val e = c.estado
            val i = e.etapa
            val etapa = c.plano.itinerario.etapas[i]
            val daCm = etapa.paragens.firstOrNull()?.id?.let(::cmParagemDeTransitous) != null
            if (e.fase != Fase.A_BORDO || !daCm || (!e.altaPrecisao && e.origem == Origem.GPS)) {
                candidato = null // as 2 consultas seguidas recomeçam
            } else if (agora() >= proxima) {
                when (val r = servidorCm().veiculos()) {
                    is Resultado.Ok -> {
                        falhas = 0
                        proxima = agora().plus(VEICULOS_INTERVALO)
                        val atual = corrida?.estado ?: return
                        if (atual.etapa == i) {
                            val viagem = viagemCm?.takeIf { it.etapa == i && !it.perdido }?.viagem
                            val (x, perto) = escolherVeiculo(
                                r.valor, etapa, i, viagem, ultimaAceite, atual.velocidade, agora(), candidato?.takeIf { it.first == i }?.second,
                            )
                            candidato = perto?.let { i to it }
                            x?.let(::aplicar)
                        }
                    }
                    is Resultado.Falha -> proxima = agora().plus(maxOf(VEICULOS_INTERVALO, pausaDepoisDeFalha(Fonte.CM, falhas++)))
                }
            }
            delay(RELOGIO_MS)
        }
    }

    private fun servidorPc() = servidor ?: ServidorParagemCerta(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).also { servidor = it }

    /**
     * Com os avisos da comunidade ligados no plano: os relatos confirmados nas linhas em veículo da viagem, um pedido logo e
     * depois a cada 5 min. A quota em pausa ou a comunidade desligada no servidor param os pedidos até ao fim da viagem;
     * outra falha espera os 5 min.
     */
    private suspend fun avisosComunidade() {
        val plano = corrida?.plano?.takeIf { it.avisosComunidade } ?: return
        // Só chaves que o servidor aceita: uma má dava 400 ao pedido inteiro.
        val linhas = plano.itinerario.etapas.filter { !it.aPe }.mapNotNull { it.linhaNome?.let(::chaveLinha) }
            .filter(::chaveLinhaValida).distinct().take(MAX_LINHAS_AVISOS)
        if (linhas.isEmpty()) return
        var proxima = Instant.MIN
        while (corrida?.estado?.terminada == false) {
            if (agora() >= proxima) {
                proxima = agora().plusMillis(AVISOS_MS)
                when (val r = servidorPc().avisos(linhas)) {
                    is Resultado.Ok -> avisarRelatos(r.valor)
                    is Resultado.Falha -> if (r.emPausa || r.erro == "desligado") return
                }
            }
            delay(RELOGIO_MS)
        }
    }

    /** Uma notificação por relato confirmado novo (nem ocultado, nem já avisado); os avisados ficam no trip-state. */
    private fun avisarRelatos(relatos: List<RelatoRemoto>) {
        val c = corrida ?: return
        val novos = relatos.filter { it.confirmado && "relato:${it.id}" !in c.plano.ocultos && it.id !in c.estado.relatosAvisados }
        if (novos.isEmpty()) return
        // O id de cada notificação conta os já avisados: uma viagem retomada não tapa as de antes.
        novos.forEachIndexed { i, r ->
            Notificacoes.textoRelato(this, r, c.viagem.paragemAFrente(c.estado, r.paragem))
                ?.let { Notificacoes.relatoComunidade(this, c.estado.relatosAvisados.size + i, it, viagem = true) }
        }
        c.estado = c.estado.copy(relatosAvisados = c.estado.relatosAvisados + novos.map { it.id })
        publicar()
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
        viagemCm = null
        ultimaAceite = null
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
        val x = Entrada.Gps(l.latitude, l.longitude, precisao, hora)
        aplicar(x)
        if (corrida?.estado?.ultimoGps == hora) ultimaAceite = x
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

/**
 * O trip_id (inteiro, como aparece em vehicles) do autocarro da etapa: o da chegada com tempo real da mesma linha cuja hora
 * programada difere até 60 s da do horário. Só as chegadas com tempo real trazem trip_id.
 */
internal fun viagemAoVivo(chegadas: List<Chegada>, etapa: Etapa): String? =
    chegadas
        .filter { it.linhaNome == etapa.linhaNome && it.prevista != null && it.idViagem != null }
        .map { it to abs(Duration.between(it.programada, etapa.partidaProgramada).seconds) }
        .filter { it.second <= CM_VIAGEM_S }
        .minByOrNull { it.second }?.first?.idViagem

/** O autocarro que a pessoa espera na etapa: o trip_id e a partida ao vivo; perdido, o trip_id já não conta. */
internal data class AutocarroCm(val etapa: Int, val viagem: String, val prevista: Instant, val perdido: Boolean = false)

/**
 * O autocarro que a pessoa espera na etapa i, depois de uma consulta (chegadas vazias sem consulta nova): o guardado, com
 * a partida ao vivo dele se ainda aparecer; sem nenhum, o da etapa (viagemAoVivo). Quem continua à espera 2 min depois de
 * ele partir perdeu-o: passa ao seguinte da mesma linha com tempo real e, até haver um, fica sem trip_id. O perdido ia à
 * frente e, com 60 s sem GPS, puxava a viagem e dava o Sai agora antes do tempo; por isso nunca volta.
 */
internal fun autocarroDaEspera(chegadas: List<Chegada>, etapa: Etapa, i: Int, guardado: AutocarroCm?, agora: Instant): AutocarroCm? {
    val vivas = chegadas.filter { it.linhaNome == etapa.linhaNome && it.prevista != null && it.idViagem != null }
    val g = guardado?.takeIf { it.etapa == i }
        ?: return viagemAoVivo(chegadas, etapa)?.let { v -> AutocarroCm(i, v, vivas.first { it.idViagem == v }.prevista!!) }
    val atual = vivas.firstOrNull { !g.perdido && it.idViagem == g.viagem }?.let { g.copy(prevista = it.prevista!!) } ?: g
    if (!atual.perdido && agora <= atual.prevista + CM_PERDIDO) return atual
    val seguinte = vivas.filter { it.idViagem != atual.viagem && it.prevista!! > atual.prevista }.minByOrNull { it.prevista!! }
    return seguinte?.let { AutocarroCm(i, it.idViagem!!, it.prevista!!) } ?: atual.copy(perdido = true)
}

/**
 * A leitura de vehicles a dar ao motor (etapa i) e o candidato para a consulta seguinte. O veículo da viagem é o que tem
 * o trip_id guardado à espera; sem ele, o da mesma linha mais perto da última posição aceite (a menos de 150 m +
 * velocidade × o tempo entre as duas), mas só se for o mesmo em 2 consultas seguidas. Leituras com mais de 60 s (ou com a
 * hora 60 s à frente) não contam, nem as de uma paragem que não é da etapa.
 */
internal fun escolherVeiculo(
    veiculos: List<Veiculo>, etapa: Etapa, i: Int, viagem: String?, posicao: Entrada.Gps?, velocidade: Double, agora: Instant, candidato: String?,
): Pair<Entrada.Veiculo?, String?> {
    val frescos = veiculos.filter { abs(Duration.between(it.ultimoSinal, agora).seconds) <= VEICULO_IDADE_S }
    val daViagem = viagem?.let { v -> frescos.firstOrNull { it.viagem == v } }
    val perto = if (daViagem != null || posicao == null) null else frescos
        .filter { it.linhaId == etapa.linhaNome }
        .map { v -> Triple(v, distanciaMetros(v.lat, v.lon, posicao.lat, posicao.lon), abs(Duration.between(v.ultimoSinal, posicao.hora).toMillis()) / 1000.0) }
        .filter { (_, m, s) -> s <= VEICULO_IDADE_S && m <= VEICULO_PERTO_M + velocidade * s }
        .minByOrNull { it.second }?.first
    val escolhido = daViagem ?: perto?.takeIf { it.id == candidato }
    val k = paragensDe(etapa).indexOfFirst { p -> escolhido?.paragem != null && p.id?.let(::cmParagemDeTransitous) == escolhido.paragem }
    return (if (escolhido != null && k >= 0) Entrada.Veiculo(i, k, escolhido.aChegar, agora, escolhido.lat, escolhido.lon) else null) to perto?.id
}
