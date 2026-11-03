package pt.pixellusitano.paragemcerta.aviso

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinhaValida
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Avisos
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import pt.pixellusitano.paragemcerta.core.regras.pausaDepoisDeFalha
import pt.pixellusitano.paragemcerta.core.viagem.AvisoChegada
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.PedidoAviso
import pt.pixellusitano.paragemcerta.core.viagem.aDisparar
import pt.pixellusitano.paragemcerta.core.viagem.vigiaAcabou
import pt.pixellusitano.paragemcerta.viagem.Alarme
import pt.pixellusitano.paragemcerta.viagem.Notificacoes
import java.time.Duration
import java.time.Instant

private val CM_INTERVALO = Duration.ofSeconds(30)
private val TRANSITOUS_INTERVALO = Duration.ofSeconds(60) // o Transitous só lê o tempo real uma vez por minuto
// O pedido dura 90 min (ver vigiaAcabou): o telemóvel fica acordado até lá, com uma folga, como na viagem.
private val BLOQUEIO_MAX = Duration.ofMinutes(95)
// Os avisos da comunidade, como na viagem: a cada 5 min, até 20 linhas (o máximo do /api/avisos).
private val AVISOS = Duration.ofMinutes(5)
private const val MAX_LINHAS_AVISOS = 20

// O aviso de chegada toca como o Prepara-te: 3 s de som e vibração com as definições de aviso.
private val EVENTO_ALARME = Evento.PREPARA_TE

/**
 * Os "Avisa-me quando estiver a X min" a correr, no processo principal (não pede o isolamento do :trip e usa o Repositorio):
 * consulta as chegadas de cada paragem vigiada (30 s na Carris Metropolitana, 60 s no Transitous), mostra a notificação fixa
 * "A vigiar 2 linhas em Cais do Sodré" e, quando uma linha está a X min, a notificação e o alarme; com os avisos da comunidade
 * ligados, também os relatos confirmados nessas linhas (ver avisosComunidade). Pára sozinho quando todos
 * os avisos dispararam, quando a pessoa cancela, ou ao fim de 90 min (ver core/viagem/Vigia). Tudo corre na thread
 * principal (pedidos, relógio e respostas), por isso o estado não precisa de trincos.
 */
class ServicoAvisoChegada : Service() {
    /** O relógio, a rede, as definições de aviso e o alarme, trocáveis nos testes. */
    internal var agora: () -> Instant = Instant::now
    internal var consultar: suspend (RefParagem, Instant) -> Resultado<List<Chegada>> =
        { ref, hora -> ligacoes.repositorio.chegadasAgora(ref, hora) }
    internal var definicoes: suspend () -> Pair<Boolean, Boolean> =
        { ligacoes.preferencias.let { it.somAviso.first() to it.vibracaoAviso.first() } }
    internal var alarme: (Boolean, Boolean) -> Unit = { som, vibrar -> Alarme.tocar(this, EVENTO_ALARME, som, vibrar) }

    /** Os avisos da comunidade: o pedido ao /api/avisos, se estão ligados (na config e nas definições) e os relatos ocultados. */
    internal var avisos: suspend (List<String>) -> Resultado<Avisos> = { linhas -> ligacoes.servidor.avisos(linhas) }
    internal var avisosLigados: suspend () -> Boolean =
        { ligacoes.configuracoes.atual.value.interruptores.comunidade && ligacoes.preferencias.avisosComunidade.first() }
    internal var ocultos: suspend () -> Set<String> = { ligacoes.preferencias.ocultos.first() }

    /** Uma paragem vigiada: o pedido, as linhas que já avisaram e a tarefa que a consulta. */
    private class Vigiada(val pedido: PedidoAviso, val avisadas: MutableSet<String> = mutableSetOf(), var tarefa: Job? = null)

    private val ligacoes get() = (application as ParagemCertaApp).ligacoes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gestor by lazy { getSystemService(NotificationManager::class.java) }
    private val paragens = linkedMapOf<String, Vigiada>() // por RefParagem.chave
    private var bloqueio: PowerManager.WakeLock? = null
    private var proximoAviso = 0

    /** O pedido dos avisos da comunidade, um só para todas as paragens, e os relatos de que já avisou. */
    private var tarefaRelatos: Job? = null
    private val relatosAvisados = mutableSetOf<String>()

    /** Até quando o último alarme está a tocar (uptimeMillis): o serviço não pára antes, o Android calava o áudio. */
    private var alarmeAte = 0L

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanais(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACAO_VIGIAR -> juntar(intent)
            ACAO_CANCELAR -> {
                intent.getStringExtra(EXTRA_PARAGEM)?.let { paragens.remove(it)?.tarefa?.cancel() }
                if (paragens.isEmpty()) encerrar() else publicar()
            }
            Notificacoes.ACAO_PARAR_VIGIA -> encerrar()
            // Os botões de uma notificação velha podem chegar a um processo novo, sem nada a vigiar.
            else -> if (paragens.isEmpty()) stopSelf()
        }
        return START_NOT_STICKY // o Android 14 e seguintes não deixa reabrir sozinho
    }

    override fun onDestroy() {
        limpar()
        scope.cancel()
        super.onDestroy()
    }

    /** Junta (ou, para a mesma paragem, substitui) um pedido. Pedido sem paragem, linhas ou minutos: não há nada a vigiar. */
    private fun juntar(i: Intent) {
        val ref = i.getStringExtra(EXTRA_PARAGEM)?.let(RefParagem::daChave)
        val linhas = i.getStringArrayExtra(EXTRA_LINHAS)?.toSet().orEmpty()
        val minutos = i.getIntExtra(EXTRA_MINUTOS, 0)
        val novo = if (ref != null && linhas.isNotEmpty() && minutos > 0) {
            Vigiada(PedidoAviso(ref, i.getStringExtra(EXTRA_NOME).orEmpty(), linhas, minutos, agora()))
        } else null
        novo?.let { paragens.put(it.pedido.ref.chave, it)?.tarefa?.cancel() }
        // Quem usa startForegroundService tem de ter a notificação mesmo que não haja nada a vigiar.
        if (!publicar() || paragens.isEmpty()) {
            encerrar()
            return
        }
        novo?.let {
            prenderOTelemovel()
            // Depois de parar (pausa, desligada) não recomeça: só um serviço novo pede outra vez.
            if (tarefaRelatos == null) tarefaRelatos = scope.launch { avisosComunidade() }
            it.tarefa = scope.launch { vigiar(it) }
        }
    }

    /** O Android pode recusar o serviço em primeiro plano (arranque em segundo plano, falta de permissão): então não há vigia. */
    @SuppressLint("InlinedApi")
    private fun primeiroPlano(n: Notification): Boolean = try {
        ServiceCompat.startForeground(this, Notificacoes.ID_VIGIA, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        true
    } catch (_: RuntimeException) {
        false
    }

    /** A notificação fixa com as linhas que ainda faltam avisar, e as paragens vigiadas para quem as segue. Falso se o Android recusar. */
    private fun publicar(): Boolean {
        vigiadas.value = paragens.keys.toSet()
        val n = if (paragens.isEmpty()) Notificacoes.vazia(this)
        else Notificacoes.vigia(this, paragens.values.sumOf { it.pedido.linhas.size - it.avisadas.size }, paragens.values.map { it.pedido.nomeParagem })
        return primeiroPlano(n)
    }

    /** O telemóvel não adormece com o ecrã apagado: sem isto os temporizadores parariam e o aviso chegava tarde. */
    private fun prenderOTelemovel() {
        val b = bloqueio ?: getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParagemCerta:avisos")
            .apply { setReferenceCounted(false) }.also { bloqueio = it }
        b.acquire(BLOQUEIO_MAX.toMillis()) // um pedido novo recomeça o tempo
    }

    private suspend fun vigiar(v: Vigiada) {
        val p = v.pedido
        val intervalo = if (p.ref.fonte == Fonte.CM) CM_INTERVALO else TRANSITOUS_INTERVALO
        var falhas = 0
        // shortcut: os 90 min só se medem entre consultas, por isso acaba até um intervalo (ou o recuo de uma falha) depois.
        while (!vigiaAcabou(p, v.avisadas, agora())) {
            val hora = agora()
            val espera = when (val r = consultar(p.ref, hora)) {
                is Resultado.Ok -> {
                    falhas = 0
                    avisar(v, aDisparar(p, r.valor, v.avisadas, hora))
                    intervalo
                }
                is Resultado.Falha -> maxOf(intervalo, pausaDepoisDeFalha(p.ref.fonte, falhas++))
            }
            if (!vigiaAcabou(p, v.avisadas, agora())) delay(espera.toMillis())
        }
        terminar(v)
    }

    /**
     * Os relatos confirmados nas linhas de todas as paragens vigiadas, num só pedido a cada 5 min (as primeiras 20 linhas,
     * o máximo do /api/avisos), enquanto houver paragens e os avisos estiverem ligados. A quota em pausa ou a comunidade
     * desligada no servidor param os pedidos; outra falha espera os 5 min. Sem "à frente": não há viagem a dizer onde vais.
     */
    private suspend fun avisosComunidade() {
        while (paragens.isNotEmpty() && avisosLigados()) {
            // Só chaves que o servidor aceita: uma má dava 400 ao pedido inteiro.
            val linhas = paragens.values.flatMap { it.pedido.linhas }.map(::chaveLinha).filter(::chaveLinhaValida).distinct().take(MAX_LINHAS_AVISOS)
            if (linhas.isNotEmpty()) when (val r = avisos(linhas)) {
                is Resultado.Ok -> {
                    val escondidos = ocultos()
                    r.valor.relatos.filter { it.confirmado && it.id !in relatosAvisados && "relato:${it.id}" !in escondidos }.forEach { relato ->
                        relatosAvisados += relato.id
                        Notificacoes.textoRelato(this, relato, aFrente = false)
                            ?.let { Notificacoes.relatoComunidade(this, relatosAvisados.size - 1, it, viagem = false) }
                    }
                }
                is Resultado.Falha -> if (r.emPausa || r.erro == "desligado") return
            }
            delay(AVISOS.toMillis())
        }
    }

    /** A notificação de cada linha e um só alarme (um segundo cortaria o primeiro). */
    private suspend fun avisar(v: Vigiada, avisos: List<AvisoChegada>) {
        if (avisos.isEmpty()) return
        v.avisadas += avisos.map { it.linhaNome }
        avisos.forEach { gestor.notify(Notificacoes.ID_CHEGADA + proximoAviso++, Notificacoes.chegada(this, it, v.pedido.nomeParagem)) }
        val (som, vibrar) = definicoes()
        alarme(som, vibrar)
        alarmeAte = maxOf(alarmeAte, SystemClock.uptimeMillis() + Alarme.duracaoMs(EVENTO_ALARME))
        if (v.avisadas.size < v.pedido.linhas.size) publicar() // se não, o terminar trata do fim
    }

    /** Esta paragem acabou; se era a última, o serviço pára, mas só depois de o alarme acabar de tocar. */
    private suspend fun terminar(v: Vigiada) {
        paragens.remove(v.pedido.ref.chave)
        if (paragens.isNotEmpty()) {
            publicar()
            return
        }
        delay((alarmeAte - SystemClock.uptimeMillis()).coerceAtLeast(0))
        if (paragens.isEmpty()) encerrar() // um pedido novo durante a espera mantém o serviço
    }

    private fun encerrar() {
        limpar()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Larga tudo o que a vigia prende: tarefas, paragens vigiadas e wake lock. */
    private fun limpar() {
        paragens.values.forEach { it.tarefa?.cancel() }
        paragens.clear()
        tarefaRelatos?.cancel()
        tarefaRelatos = null
        relatosAvisados.clear()
        vigiadas.value = emptySet()
        bloqueio?.takeIf { it.isHeld }?.release()
        bloqueio = null
        alarmeAte = 0L
    }

    companion object {
        const val ACAO_VIGIAR = "pt.pixellusitano.paragemcerta.aviso.VIGIAR"
        const val ACAO_CANCELAR = "pt.pixellusitano.paragemcerta.aviso.CANCELAR"
        private const val EXTRA_PARAGEM = "pt.pixellusitano.paragemcerta.aviso.PARAGEM"
        private const val EXTRA_NOME = "pt.pixellusitano.paragemcerta.aviso.NOME"
        private const val EXTRA_LINHAS = "pt.pixellusitano.paragemcerta.aviso.LINHAS"
        private const val EXTRA_MINUTOS = "pt.pixellusitano.paragemcerta.aviso.MINUTOS"

        // O estado é do processo principal, onde o serviço corre; o serviço limpa-o ao acabar.
        private val vigiadas = MutableStateFlow<Set<String>>(emptySet())

        /** As paragens vigiadas agora (RefParagem.chave): o botão "Avisa-me" do ecrã da paragem segue isto. */
        val paragensVigiadas: StateFlow<Set<String>> = vigiadas.asStateFlow()

        /** Há algum aviso de chegada a correr: a atualização automática espera. */
        val aCorrer get() = vigiadas.value.isNotEmpty()

        /** Para quem arranca o serviço (com startForegroundService, a partir do ecrã visível). */
        fun vigiar(c: Context, ref: RefParagem, nomeParagem: String, linhas: Set<String>, minutos: Int): Intent =
            Intent(c, ServicoAvisoChegada::class.java).setAction(ACAO_VIGIAR)
                .putExtra(EXTRA_PARAGEM, ref.chave).putExtra(EXTRA_NOME, nomeParagem)
                .putExtra(EXTRA_LINHAS, linhas.toTypedArray()).putExtra(EXTRA_MINUTOS, minutos)

        fun cancelar(c: Context, ref: RefParagem): Intent =
            Intent(c, ServicoAvisoChegada::class.java).setAction(ACAO_CANCELAR).putExtra(EXTRA_PARAGEM, ref.chave)
    }
}
