package pt.pixellusitano.paragemcerta.aviso

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.viagem.Notificacoes
import java.time.Duration
import java.time.Instant

private val T0 = Instant.parse("2026-11-02T10:00:00Z")
private val SODRE = RefParagem(Fonte.CM, "060001")
private val ALGES = RefParagem(Fonte.CM, "060002")
private val METRO = RefParagem(Fonte.TRANSITOUS, "PT:METRO")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServicoAvisoChegadaTest {
    private val c = RuntimeEnvironment.getApplication()
    private val gestor get() = c.getSystemService(NotificationManager::class.java)
    private val fio get() = shadowOf(Looper.getMainLooper())
    private var agora = T0
    private var definicoes = true to true
    private val respostas = mutableMapOf<RefParagem, Resultado<List<Chegada>>>()
    private val consultas = mutableListOf<RefParagem>()
    private val alarmes = mutableListOf<Pair<Boolean, Boolean>>()
    private val servicos = mutableListOf<ServiceController<ServicoAvisoChegada>>()

    // Os avisos da comunidade do teste: desligados por omissão, para os outros testes não pedirem nada.
    private var comunidade = false
    private var ocultos = emptySet<String>()
    private var respostaAvisos: Resultado<List<RelatoRemoto>> = Resultado.Ok(emptyList())
    private val pedidosAvisos = mutableListOf<List<String>>()

    private fun relato(id: String, tipo: String = "desvio", linha: String = "736", confirmado: Boolean = true) =
        RelatoRemoto(id, tipo, SODRE.chave, linha, 0, 0, 2, confirmado)

    /** Acaba os serviços do teste: o estado partilhado (as paragens vigiadas) é do processo, não do serviço. */
    @After fun acabar() = servicos.forEach { it.destroy() }

    /** Uma chegada da linha às T0 + s segundos; sem tempoReal só tem a hora do horário. */
    private fun chegada(linha: String, s: Long, tempoReal: Boolean = true, modo: Modo = Modo.AUTOCARRO) = Chegada(
        Fonte.CM, linha, linha, "Algés", T0.plusSeconds(s), if (tempoReal) T0.plusSeconds(s) else null, null, null, modo, null, null,
    )

    private fun chegadas(ref: RefParagem, vararg lista: Chegada) {
        respostas[ref] = Resultado.Ok(lista.toList())
    }

    private fun vigiar(ref: RefParagem, nome: String, vararg linhas: String, minutos: Int = 5) =
        ServicoAvisoChegada.vigiar(c, ref, nome, linhas.toSet(), minutos)

    /** O serviço do Robolectric, com o relógio, a rede e o alarme do teste; o Android entrega-lhe a ação como no telemóvel. */
    private fun servico(i: Intent): ServiceController<ServicoAvisoChegada> =
        Robolectric.buildService(ServicoAvisoChegada::class.java, i).also {
            it.get().agora = { agora }
            it.get().consultar = { ref, _ -> consultas += ref; respostas[ref] ?: Resultado.Ok(emptyList()) }
            it.get().definicoes = { definicoes }
            it.get().alarme = { som, vibrar -> alarmes += som to vibrar }
            it.get().avisosLigados = { comunidade }
            it.get().ocultos = { ocultos }
            it.get().avisos = { linhas -> pedidosAvisos += linhas; respostaAvisos }
            servicos += it
        }

    private fun comecar(i: Intent) = servico(i).also { it.create().startCommand(0, 1) }

    private fun pedir(s: ServiceController<ServicoAvisoChegada>, i: Intent) = s.withIntent(i).startCommand(0, 2)

    private fun passarOTempo(s: Long) = repeat(s.toInt()) {
        agora = agora.plusSeconds(1)
        fio.idleFor(Duration.ofSeconds(1))
    }

    private fun titulo(n: Notification?) = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun fixa(s: ServiceController<ServicoAvisoChegada>) = titulo(shadowOf(s.get()).lastForegroundNotification)

    /** Os títulos dos avisos de chegada postados (o canal dos avisos; a fixa vai no canal da viagem). */
    private fun avisosPostados() = shadowOf(gestor).allNotifications.filter { it.channelId == "avisos" }.map { titulo(it) }

    private fun parou(s: ServiceController<ServicoAvisoChegada>) = shadowOf(s.get()).isStoppedBySelf

    private fun bloqueioPreso() = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

    @Test fun vigiarPoeOServicoEmPrimeiroPlanoComOTextoCerto() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        val sombra = shadowOf(s.get())
        assertEquals(Notificacoes.ID_VIGIA, sombra.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, s.get().foregroundServiceType)
        val n = sombra.lastForegroundNotification
        assertEquals("A vigiar 2 linhas em Cais do Sodré", titulo(n))
        assertEquals("viagem", n.channelId)
        assertTrue((n.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        // O botão Parar chega ao próprio serviço, com a ação que ele entende.
        assertEquals(listOf("Parar"), n.actions.map { it.title.toString() })
        val parar = shadowOf(n.actions[0].actionIntent)
        assertTrue(parar.isService)
        assertEquals(Notificacoes.ACAO_PARAR_VIGIA, parar.savedIntent.action)
        assertEquals(ServicoAvisoChegada::class.java.name, parar.savedIntent.component!!.className)
        // Tocar nela abre a app, não o ecrã da viagem (que é o que o extra pede).
        val aberto = shadowOf(n.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, aberto.component!!.className)
        assertFalse(aberto.hasExtra(Notificacoes.EXTRA_ABRIR_VIAGEM))
        assertEquals(setOf(SODRE.chave), ServicoAvisoChegada.paragensVigiadas.value)
        assertTrue(ServicoAvisoChegada.aCorrer)
        assertTrue(bloqueioPreso()) // com o ecrã apagado o telemóvel não adormece a meio da vigia
        assertFalse(parou(s))
    }

    @Test fun umaLinhaDizLinhaNoSingular() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        assertEquals("A vigiar 1 linha em Cais do Sodré", fixa(s))
    }

    @Test fun variasParagensJuntamSeEUmPedidoNovoParaAMesmaSubstituiOAnterior() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        pedir(s, vigiar(ALGES, "Algés", "201"))
        assertEquals("A vigiar 3 linhas em 2 paragens", fixa(s))
        assertEquals(setOf(SODRE.chave, ALGES.chave), ServicoAvisoChegada.paragensVigiadas.value)
        // Uma paragem a ser vigiada outra vez troca as linhas e os minutos, não soma.
        pedir(s, vigiar(SODRE, "Cais do Sodré", "736", minutos = 10))
        assertEquals("A vigiar 2 linhas em 2 paragens", fixa(s))
        chegadas(SODRE, chegada("728", 6 * 60), chegada("736", 8 * 60)) // a 728 já não é pedida; a 736 conta pelos 10 min novos
        passarOTempo(30)
        assertEquals(listOf("O 736 chega em 7 min a Cais do Sodré"), avisosPostados())
    }

    @Test fun chegadaDentroDoLimiteAvisaUmaVezSo() {
        chegadas(SODRE, chegada("736", 4 * 60 + 10))
        comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        assertEquals(listOf("O 736 chega em 4 min a Cais do Sodré"), avisosPostados())
        val aviso = shadowOf(gestor).allNotifications.single { it.channelId == "avisos" }
        assertFalse(shadowOf(aviso.contentIntent).savedIntent.hasExtra(Notificacoes.EXTRA_ABRIR_VIAGEM))
        assertEquals(listOf(true to true), alarmes)
        passarOTempo(120) // a 728 nunca chega: continua a vigiar, mas a 736 não volta a avisar
        assertEquals(1, avisosPostados().size)
        assertEquals(1, alarmes.size)
        assertTrue(consultas.size >= 4)
    }

    @Test fun semTempoRealAvisaPeloHorario() {
        chegadas(SODRE, chegada("736", 4 * 60 + 10, tempoReal = false))
        comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        assertEquals(listOf("O 736 chega em 4 min a Cais do Sodré (pelo horário)"), avisosPostados())
    }

    @Test fun aLinhaQueJaEstaAChegarDizAgora() {
        chegadas(SODRE, chegada("736", 20))
        comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        assertEquals(listOf("O 736 chega agora a Cais do Sodré"), avisosPostados())
    }

    @Test fun umaLinhaComNomeProprioDizOModoAntesDoNome() {
        chegadas(SODRE, chegada("Linha de Cascais", 3 * 60, modo = Modo.COMBOIO))
        comecar(vigiar(SODRE, "Cais do Sodré", "Linha de Cascais"))
        assertEquals(listOf("O comboio da Linha de Cascais chega em 3 min a Cais do Sodré"), avisosPostados())
    }

    @Test fun oSomEAVibracaoSaoOsDasDefinicoesDeAviso() {
        definicoes = false to true
        chegadas(SODRE, chegada("736", 60))
        comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        assertEquals(listOf(false to true), alarmes)
    }

    @Test fun umaLinhaChegaAsOutrasContinuamEOServicoEsperaPeloAlarmeAntesDeParar() {
        chegadas(SODRE, chegada("736", 3 * 60), chegada("728", 12 * 60))
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        assertEquals(listOf("O 736 chega em 3 min a Cais do Sodré"), avisosPostados())
        assertEquals("A vigiar 1 linha em Cais do Sodré", fixa(s)) // só falta a 728
        passarOTempo(6 * 60)
        assertEquals(1, avisosPostados().size)
        assertFalse(parou(s))
        passarOTempo(30) // a 728 está a 5 min e meio, que a lista também mostra como 5
        assertEquals(listOf("O 736 chega em 3 min a Cais do Sodré", "O 728 chega em 5 min a Cais do Sodré"), avisosPostados())
        assertEquals(2, alarmes.size)
        // Todas avisaram: o serviço só pára depois de o alarme (3 s) acabar de tocar.
        assertFalse(parou(s))
        passarOTempo(2)
        assertFalse(parou(s))
        passarOTempo(2)
        assertTrue(parou(s))
        assertTrue(shadowOf(s.get()).isForegroundStopped)
        assertFalse(ServicoAvisoChegada.aCorrer)
        assertFalse(bloqueioPreso())
    }

    @Test fun pararPara() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        pedir(s, vigiar(ALGES, "Algés", "201"))
        pedir(s, Intent(c, ServicoAvisoChegada::class.java).setAction(Notificacoes.ACAO_PARAR_VIGIA))
        assertTrue(parou(s))
        assertTrue(shadowOf(s.get()).isForegroundStopped)
        assertFalse(ServicoAvisoChegada.aCorrer)
        assertFalse(bloqueioPreso())
        val feitas = consultas.size
        passarOTempo(120)
        assertEquals(feitas, consultas.size)
    }

    @Test fun cancelarUmaParagemMantemAsOutrasECancelarAUltimaPara() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736", "728"))
        pedir(s, vigiar(ALGES, "Algés", "201"))
        pedir(s, ServicoAvisoChegada.cancelar(c, SODRE))
        assertFalse(parou(s))
        assertEquals("A vigiar 1 linha em Algés", fixa(s))
        assertEquals(setOf(ALGES.chave), ServicoAvisoChegada.paragensVigiadas.value)
        consultas.clear()
        passarOTempo(30)
        assertEquals(listOf(ALGES), consultas) // a cancelada deixou de ser consultada
        pedir(s, ServicoAvisoChegada.cancelar(c, ALGES))
        assertTrue(parou(s))
        assertFalse(ServicoAvisoChegada.aCorrer)
        assertFalse(bloqueioPreso())
    }

    @Test fun aoFimDe90MinutosParaSemAvisarMais() {
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        passarOTempo(90 * 60)
        assertFalse(parou(s))
        chegadas(SODRE, chegada("736", 91 * 60)) // chega a 1 min, mas já ninguém está à espera
        passarOTempo(60)
        assertTrue(parou(s))
        assertTrue(avisosPostados().isEmpty())
        assertTrue(alarmes.isEmpty())
        assertFalse(ServicoAvisoChegada.aCorrer)
    }

    @Test fun comFalhasConsultaComRecuoCrescente() {
        respostas[SODRE] = Resultado.Falha(null, limite = false)
        comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        assertEquals(1, consultas.size)
        passarOTempo(29)
        assertEquals(1, consultas.size)
        passarOTempo(1) // 30 s: o mínimo da Carris Metropolitana
        assertEquals(2, consultas.size)
        passarOTempo(39) // depois da 2.ª falha, o recuo é de 40 s
        assertEquals(2, consultas.size)
        passarOTempo(1)
        assertEquals(3, consultas.size)
    }

    @Test fun oTransitousConsultaUmaVezPorMinuto() {
        comecar(vigiar(METRO, "Alameda", "Vermelha"))
        assertEquals(1, consultas.size)
        passarOTempo(59)
        assertEquals(1, consultas.size)
        passarOTempo(1)
        assertEquals(2, consultas.size)
    }

    private fun relatosPostados() = avisosPostados().filter { it!!.startsWith("⚠") }

    @Test fun avisaMeNotificaRelatoConfirmado() {
        comunidade = true
        ocultos = setOf("relato:r3")
        respostaAvisos = Resultado.Ok(listOf(relato("r1"), relato("r2", confirmado = false), relato("r3", tipo = "acidente")))
        comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        // No "Avisa-me" nunca diz "à frente": não há viagem a dizer onde vais.
        assertEquals(listOf("⚠ Desvio confirmado na 736"), relatosPostados())
        assertTrue(alarmes.isEmpty())
        passarOTempo(5 * 60) // a mesma resposta 5 min depois não avisa outra vez
        assertEquals(2, pedidosAvisos.size)
        assertEquals(1, relatosPostados().size)
    }

    @Test fun avisaMePedeTodasAsLinhasNumSoPedido() {
        comunidade = true
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        pedir(s, vigiar(ALGES, "Algés", "15e"))
        pedidosAvisos.clear()
        passarOTempo(5 * 60)
        assertEquals(listOf(listOf("736", "15E")), pedidosAvisos)
        // Com 25 linhas vão as primeiras 20, o máximo do /api/avisos; um nome que o servidor recusa ("A/B") não vai.
        val muitas = listOf("A/B") + (1..25).map { "L$it" }
        pedir(s, vigiar(METRO, "Alameda", *muitas.toTypedArray()))
        pedidosAvisos.clear()
        passarOTempo(5 * 60)
        assertEquals(listOf(listOf("736", "15E") + (1..18).map { "L$it" }), pedidosAvisos)
    }

    @Test fun avisaMeAcabadoNaoPedeMais() {
        comunidade = true
        val s = comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        passarOTempo(91 * 60)
        assertTrue(parou(s))
        val feitos = pedidosAvisos.size
        passarOTempo(10 * 60)
        assertEquals(feitos, pedidosAvisos.size)
    }

    @Test fun comunidadeDesligadaNaoPedeAvisos() {
        respostaAvisos = Resultado.Ok(listOf(relato("r1")))
        comecar(vigiar(SODRE, "Cais do Sodré", "736"))
        passarOTempo(10 * 60)
        assertTrue(pedidosAvisos.isEmpty())
        assertTrue(relatosPostados().isEmpty())
    }

    @Test fun avisaMeEmPausaOuDesligadoParaDePedir() {
        comunidade = true
        for (falha in listOf(Resultado.Falha(503, true, "em_pausa"), Resultado.Falha(403, false, "desligado"))) {
            respostaAvisos = falha
            pedidosAvisos.clear()
            val s = comecar(vigiar(SODRE, "Cais do Sodré", "736"))
            passarOTempo(10 * 60)
            assertEquals(falha.toString(), 1, pedidosAvisos.size)
            pedir(s, Intent(c, ServicoAvisoChegada::class.java).setAction(Notificacoes.ACAO_PARAR_VIGIA))
        }
    }

    @Test fun pedidosSemNadaAVigiarSoPedemPararOServico() {
        // Quem usa startForegroundService tem de dar a notificação mesmo que não haja nada a seguir.
        val semLinhas = comecar(vigiar(SODRE, "Cais do Sodré"))
        assertTrue(parou(semLinhas))
        assertEquals(Notificacoes.ID_VIGIA, shadowOf(semLinhas.get()).lastForegroundNotificationId)
        assertFalse(ServicoAvisoChegada.aCorrer)
        // O botão de uma notificação velha pode chegar a um serviço novo, sem nada a correr.
        val cancelar = comecar(ServicoAvisoChegada.cancelar(c, SODRE))
        assertTrue(parou(cancelar))
        val parar = comecar(Intent(c, ServicoAvisoChegada::class.java).setAction(Notificacoes.ACAO_PARAR_VIGIA))
        assertTrue(parou(parar))
        assertTrue(consultas.isEmpty())
        assertFalse(bloqueioPreso())
    }
}
