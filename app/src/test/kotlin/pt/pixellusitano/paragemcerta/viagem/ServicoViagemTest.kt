package pt.pixellusitano.paragemcerta.viagem

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.viagem.Entrada
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.Percurso
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val T0 = Instant.parse("2026-07-01T10:00:00Z")

private fun lugar(nome: String, lat: Double) = Lugar(nome, lat, -9.14, TipoLugar.PARAGEM)

private fun aPe(de: Lugar, para: Lugar, partida: Instant) = Etapa(
    aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null, de = de, para = para,
    partida = partida, chegada = partida.plusSeconds(180), partidaProgramada = partida, aoVivo = false,
    paragensIntermedias = 0, desenho = emptyList(), cor = null, distancia = 110.0,
)

/**
 * Um autocarro em linha reta para norte: seis paragens a 0,005° (cerca de 550 m) umas das outras e 2 min entre cada.
 * Com idsCm, todas são da Carris Metropolitana: 020040 a 020045.
 */
private fun autocarro(linha: String, partida: Instant, idPrimeira: String? = null, idsCm: Boolean = false): Etapa {
    val nomes = listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente")
    val paragens = nomes.mapIndexed { i, n ->
        val id = if (idsCm) "pt-Carris-Metropolitana_32004$i" else if (i == 0) idPrimeira else null
        ParagemEtapa(id, n, 38.70 + i * 0.005, -9.14, partida.plusSeconds(i * 120L))
    }
    return Etapa(
        aPe = false, modo = Modo.AUTOCARRO, linhaNome = linha, linhaId = "id$linha", destino = "Oriente",
        de = lugar(nomes.first(), 38.70), para = lugar(nomes.last(), paragens.last().lat), partida = partida,
        chegada = paragens.last().hora, partidaProgramada = partida, aoVivo = false, paragensIntermedias = 4,
        desenho = emptyList(), cor = null, idViagem = "viagem$linha", paragens = paragens,
    )
}

private fun itinerario(vararg etapas: Etapa) =
    Itinerario(etapas.toList(), etapas.first().partida, etapas.last().chegada, etapas.count { !it.aPe } - 1)

// A pé até ao Cais do Sodré (a 110 m), o 736 até ao Oriente e a pé até ao trabalho.
private val CASA_AO_TRABALHO = itinerario(
    aPe(lugar("Casa", 38.699), lugar("Cais do Sodré", 38.70), T0),
    autocarro("736", T0.plusSeconds(180)),
    aPe(lugar("Oriente", 38.725), lugar("Trabalho", 38.7255), T0.plusSeconds(900)),
)

// Só o 736: o serviço começa logo à espera dele.
private val SO_O_736 = itinerario(autocarro("736", T0.plusSeconds(600)))

// O 736 com paragens do Transitous que não são da Carris Metropolitana (TRANSITOUS:p0 a p5): os relatos têm onde cair e a CM fica de fora.
private val DO_TRANSITOUS = itinerario(autocarro("736", T0.plusSeconds(600)).let { e -> e.copy(paragens = e.paragens.mapIndexed { i, p -> p.copy(id = "p$i") }) })

/** Um relato como o /api/avisos o devolve. */
private fun relato(id: String, paragem: String, tipo: String = "desvio", linha: String = "736", confirmado: Boolean = true) =
    """{"id":"$id","tipo":"$tipo","paragem":"$paragem","linha":"$linha","criadoEm":0,"expiraEm":0,"confirmacoes":2,"confirmado":$confirmado}"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServicoViagemTest {
    private val c = RuntimeEnvironment.getApplication()
    private val gestor get() = c.getSystemService(NotificationManager::class.java)
    private val localizacao get() = c.getSystemService(LocationManager::class.java)
    private val fio get() = shadowOf(Looper.getMainLooper())
    private val alarmes = mutableListOf<Triple<Evento, Boolean, Boolean>>()
    private var agora = T0

    private fun plano(i: Itinerario, som: Boolean = true, vibrar: Boolean = false) = PlanoViagem(i, som, vibrar)

    /** A preferência ligada e, com [relatos], a comunidade ligada ao começar a viagem. */
    private fun comAvisos(i: Itinerario, ocultos: Set<String> = emptySet(), relatos: Boolean = true, vistas: Set<String> = emptySet()) =
        PlanoViagem(i, som = true, vibrar = false, avisosComunidade = true, ocultos = ocultos, relatos = relatos, vistas = vistas)

    // A Carris Metropolitana do teste para as perturbações: guarda os pedidos aos alertas e responde [alertas].
    private val pedidosAlertas = CopyOnWriteArrayList<String>()
    @Volatile private var alertas = "[]"
    private val cmAlertas = CarrisMetropolitana(object : Http {
        override suspend fun get(url: String): Resposta {
            if (url.endsWith("alerts")) pedidosAlertas += url
            return Resposta(200, if (url.endsWith("alerts")) alertas else "[]")
        }
    }, "https://cm.teste/")

    /** Os avisos oficiais da CM, cada um com o título e as linhas dele (ativo de T0 − 1 h a T0 + 1 dia). */
    private fun responderAlertas(vararg avisos: Pair<String, List<String>>) {
        alertas = avisos.joinToString(",", "[", "]") { (titulo, linhas) ->
            """{"active_period":[{"start":${T0.epochSecond - 3600},"end":${T0.epochSecond + 86400}}],
               |"header_text":{"translation":[{"language":"pt","text":"$titulo"}]},
               |"description_text":{"translation":[{"language":"pt","text":"Mais sobre: $titulo"}]},
               |"informed_entity":[${linhas.joinToString(",") { """{"route_id":"${it}_0"}""" }}]}""".trimMargin()
        }
    }

    /** O id de um aviso de [responderAlertas] na Paragem Certa. */
    private fun idDoAlerta(titulo: String) = "cm:$titulo|${T0.epochSecond - 3600}"

    /** A viagem de DO_TRANSITOUS noutra linha (1009, 1612: nomes de linhas da CM). */
    private fun naLinha(linha: String) = itinerario(DO_TRANSITOUS.etapas[0].copy(linhaNome = linha))

    // O servidor da Paragem Certa do teste: guarda os endereços pedidos e responde sempre [respostaAvisos].
    private val pedidosAvisos = CopyOnWriteArrayList<String>()
    @Volatile private var respostaAvisos = Resposta(200, """{"relatos":[]}""")

    private fun responder(vararg relatos: String) {
        respostaAvisos = Resposta(200, """{"relatos":[${relatos.joinToString(",")}]}""")
    }

    private fun servidorPc() = ServidorParagemCerta(object : Http {
        override suspend fun get(url: String): Resposta = error("só pela API")
        override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
            pedidosAvisos += url
            return respostaAvisos
        }
    }, "https://pc.teste", relogio = { agora })

    /** O serviço do Robolectric, com o relógio e o alarme do teste; o Android entrega-lhe a ação como no telemóvel. */
    private fun servico(acao: String, cm: CarrisMetropolitana? = null): ServiceController<ServicoViagem> =
        Robolectric.buildService(ServicoViagem::class.java, Intent(c, ServicoViagem::class.java).setAction(acao)).also {
            it.get().agora = { agora }
            it.get().alarme = { ev, som, vibrar -> alarmes += Triple(ev, som, vibrar) }
            it.get().cm = cm
            it.get().servidor = servidorPc()
        }

    /** Os avisos da comunidade postados (os que começam por "⚠"), sem ordem. */
    private fun relatosPostados() = shadowOf(gestor).allNotifications.mapNotNull(::titulo).filter { it.startsWith("⚠") }.toSet()

    private fun comecar(i: Itinerario, p: PlanoViagem = plano(i), cm: CarrisMetropolitana? = null) =
        servico(ServicoViagem.ACAO_COMECAR, cm).also {
            FicheirosViagem.gravarPlano(c, p)
            it.create().startCommand(0, 1)
        }

    private fun pedir(s: ServiceController<ServicoViagem>, acao: String) =
        s.withIntent(Intent(c, ServicoViagem::class.java).setAction(acao)).startCommand(0, 2)

    private fun titulo(n: Notification?) = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun avisoPostado() = titulo(shadowOf(gestor).getNotification(Notificacoes.ID_AVISO))

    /** O 736 já a bordo, com o GPS ligado, retomado a [progresso] metros ao longo da linha (dadas as paragens dela). */
    private fun retomarABordo(avisados: Set<Evento> = emptySet(), progresso: (DoubleArray) -> Double): ServiceController<ServicoViagem> {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val a = Percurso(emptyList(), SO_O_736.etapas[0].paragens).noPercurso
        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        FicheirosViagem.gravarEstado(
            c, Viagem(SO_O_736).inicio(T0).copy(fase = Fase.A_BORDO, progresso = progresso(a), avisados = avisados, ultimoGps = T0, atualizado = T0),
        )
        agora = T0.plusSeconds(30)
        return servico(ServicoViagem.ACAO_RETOMAR).create().startCommand(0, 1)
    }

    /** 100 m antes de sair da antepenúltima paragem (a 3 de 0 a 5). */
    private fun retomarAntesDaAntepenultima() = retomarABordo { it[3] - 100 }

    /** 60 m para lá da antepenúltima paragem. */
    private fun saiDaAntepenultima() {
        shadowOf(localizacao).simulateLocation(posicao(38.70 + 3 * 0.005 + 0.00054))
        fio.idle()
    }

    private fun posicao(lat: Double, precisao: Float = 10f) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = lat
        longitude = -9.14
        accuracy = precisao
        elapsedRealtimeNanos = 1L
    }

    private fun passarOTempo(s: Long) {
        agora = agora.plusSeconds(s)
        fio.idleFor(Duration.ofSeconds(s))
    }

    @Test fun comecarPoeOServicoEmPrimeiroPlanoEEscreveOEstado() {
        val s = comecar(CASA_AO_TRABALHO)
        val sombra = shadowOf(s.get())
        assertEquals(Notificacoes.ID_PROGRESSO, sombra.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, s.get().foregroundServiceType)
        val n = sombra.lastForegroundNotification
        assertEquals("viagem", n.channelId)
        assertTrue(titulo(n)!!.startsWith("A pé até Cais do Sodré · "))
        // "Já saí" só a bordo: a pé não há de que sair, e à espera saltava o autocarro que a pessoa ainda vai apanhar.
        assertEquals(listOf("Silenciar", "Parar"), n.actions.map { it.title.toString() })
        val estado = FicheirosViagem.lerEstado(c, agora)!!
        assertEquals(0, estado.etapa)
        assertEquals(Fase.A_PE, estado.fase)
        assertFalse(sombra.isStoppedBySelf)
        // Quem está com a app aberta fica a saber, e o telemóvel não adormece sem posições (Metro).
        val avisoDeEstado = shadowOf(c).broadcastIntents.single { it.action == FicheirosViagem.ACAO_ESTADO }
        assertEquals(c.packageName, avisoDeEstado.`package`)
        assertTrue(bloqueioPreso())
    }

    @Test fun silenciarGravaSilenciado() {
        val s = comecar(CASA_AO_TRABALHO)
        pedir(s, Notificacoes.ACAO_SILENCIAR)
        assertTrue(FicheirosViagem.lerEstado(c, agora)!!.silenciado)
    }

    @Test fun pararApagaOsFicheirosEPara() {
        val s = comecar(CASA_AO_TRABALHO)
        pedir(s, Notificacoes.ACAO_PARAR)
        assertNull(FicheirosViagem.lerPlano(c))
        assertNull(FicheirosViagem.lerEstado(c, agora))
        val sombra = shadowOf(s.get())
        assertTrue(sombra.isStoppedBySelf)
        assertTrue(sombra.isForegroundStopped)
        assertFalse(bloqueioPreso())
    }

    @Test fun osBotoesSemViagemARodarSoPedemPararOServico() {
        // O Android pode entregar o botão de uma notificação velha a um processo :trip acabado de nascer.
        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        for (acao in listOf(Notificacoes.ACAO_SILENCIAR, Notificacoes.ACAO_JA_SAI)) {
            val s = servico(acao).create().startCommand(0, 1)
            assertTrue(acao, shadowOf(s.get()).isStoppedBySelf)
        }
        assertNotNull(FicheirosViagem.lerPlano(c)) // Silenciar e Já saí não apagam nada
        val parar = servico(Notificacoes.ACAO_PARAR).create().startCommand(0, 1)
        assertTrue(shadowOf(parar.get()).isStoppedBySelf)
        assertNull(FicheirosViagem.lerPlano(c))
        assertTrue(alarmes.isEmpty())
    }

    @Test fun semPermissaoDeNotificacoesOEventoTocaOAlarmeSemErro() {
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        retomarAntesDaAntepenultima()
        saiDaAntepenultima()
        // O Prepara-te toca com as definições do plano, e o resto da viagem continua.
        assertEquals(listOf(Triple(Evento.PREPARA_TE, true, false)), alarmes)
        assertEquals(setOf(Evento.PREPARA_TE), FicheirosViagem.lerEstado(c, agora)!!.avisados)
    }

    @Test fun silenciadoNaoTocaMasAvisaNaMesma() {
        val s = retomarAntesDaAntepenultima()
        pedir(s, Notificacoes.ACAO_SILENCIAR)
        saiDaAntepenultima()
        assertTrue(alarmes.isEmpty())
        assertEquals("Prepara-te — sais daqui a 2 paragens, em Oriente", avisoPostado())
    }

    @Test fun oJaSaiNaoToca() {
        // A bordo, 100 m para lá da antepenúltima paragem: "Já saí" acaba a etapa (e a viagem) sem alarme.
        val s = retomarABordo { it[3] + 100 }
        pedir(s, Notificacoes.ACAO_JA_SAI)
        assertTrue(alarmes.isEmpty())
        assertEquals("Chegaste a Oriente", avisoPostado())
        assertTrue(shadowOf(s.get()).isStoppedBySelf) // nada a tocar: pára logo
    }

    @Test fun oJaSaiAEsperaDoAutocarroEIgnorado() {
        // Só o 736: o serviço começa à espera dele. Saltar a etapa dava "Chegaste" sem a pessoa ter entrado no autocarro.
        val s = comecar(SO_O_736)
        val antes = FicheirosViagem.lerEstado(c, agora)!!
        assertEquals(Fase.ESPERA, antes.fase)
        pedir(s, Notificacoes.ACAO_JA_SAI)
        assertEquals(antes, FicheirosViagem.lerEstado(c, agora))
        assertNull(avisoPostado())
        assertTrue(alarmes.isEmpty())
        assertFalse(shadowOf(s.get()).isStoppedBySelf)
    }

    @Test fun oJaSaiAPeEIgnorado() {
        val s = comecar(CASA_AO_TRABALHO)
        val antes = FicheirosViagem.lerEstado(c, agora)!!
        assertEquals(Fase.A_PE, antes.fase)
        pedir(s, Notificacoes.ACAO_JA_SAI)
        assertEquals(antes, FicheirosViagem.lerEstado(c, agora))
        assertNull(avisoPostado())
        assertFalse(shadowOf(s.get()).isStoppedBySelf)
    }

    @Test fun depoisDeChegarOServicoSoParaQuandoOAlarmeAcaba() {
        val s = retomarABordo(avisados = setOf(Evento.PREPARA_TE)) { it[4] + 100 }
        // 11 m antes de Oriente: Sai agora e Chegou no mesmo passo.
        shadowOf(localizacao).simulateLocation(posicao(38.70 + 5 * 0.005 - 0.0001))
        fio.idle()
        val sombra = shadowOf(s.get())
        assertEquals(listOf(Evento.SAI_AGORA), alarmes.map { it.first })
        assertFalse(sombra.isStoppedBySelf) // o Sai agora ainda está a tocar: o Android 15 e 17 calam o áudio sem serviço
        assertTrue(bloqueioPreso())
        passarOTempo(6)
        assertTrue(sombra.isStoppedBySelf)
        assertTrue(sombra.isForegroundStopped)
        assertFalse(bloqueioPreso())
        assertTrue(FicheirosViagem.lerEstado(c, agora)!!.terminada) // o ecrã da viagem ainda mostra que chegaste
        assertEquals("Chegaste a Oriente", avisoPostado())
    }

    @Test fun aGuardaAcabaAViagemSemEventoDeChegada() {
        // A última etapa é a pé e a pessoa nunca chega a 50 m do destino: só a guarda da viagem a acaba, sem CHEGOU.
        val i = CASA_AO_TRABALHO
        FicheirosViagem.gravarPlano(c, plano(i))
        FicheirosViagem.gravarEstado(c, Viagem(i).inicio(T0).copy(etapa = 2, progresso = 400.0, ultimoGps = T0, atualizado = T0))
        agora = i.chegada.plus(Duration.ofMinutes(29))
        val s = servico(ServicoViagem.ACAO_RETOMAR).create().startCommand(0, 1)
        assertFalse(shadowOf(s.get()).isStoppedBySelf)
        passarOTempo(120)
        assertTrue(shadowOf(s.get()).isStoppedBySelf)
        assertFalse(bloqueioPreso())
        assertTrue(alarmes.isEmpty())
        // Já passa da guarda, por isso lerEstado apagava-o: lê-se o ficheiro tal como o :trip o deixou.
        val deixado = Json.decodeFromString<EstadoViagem>(File(c.filesDir, "viagem/trip-state.json").readText())
        assertTrue(deixado.terminada)
    }

    @Test fun retomarContinuaOndeFicouESemRepetirAvisos() {
        val i = CASA_AO_TRABALHO
        val a = Percurso(emptyList(), i.etapas[1].paragens).noPercurso
        // A bordo, já para lá da antepenúltima: o Prepara-te já tocou, e o alarme está silenciado.
        val guardado = Viagem(i).inicio(T0).copy(
            etapa = 1, fase = Fase.A_BORDO, progresso = a[3] + 100, avisados = setOf(Evento.PREPARA_TE),
            silenciado = true, altaPrecisao = true, ultimoGps = T0, atualizado = T0,
        )
        FicheirosViagem.gravarPlano(c, plano(i))
        FicheirosViagem.gravarEstado(c, guardado)
        agora = T0.plusSeconds(30)
        val s = servico(ServicoViagem.ACAO_RETOMAR).create().startCommand(0, 1)
        assertEquals("Faltam 2 paragens · sais em Oriente", titulo(shadowOf(s.get()).lastForegroundNotification))
        passarOTempo(10)
        assertTrue(alarmes.isEmpty())
        assertNull(shadowOf(gestor).getNotification(Notificacoes.ID_AVISO))
        assertTrue(FicheirosViagem.lerEstado(c, agora)!!.silenciado) // a viagem retomada continua silenciada
    }

    @Test fun semPlanoOuSemEstadoParaRetomarSaiSemRebentar() {
        // Quem arranca o serviço com startForegroundService tem de lhe dar a notificação, mesmo que não haja nada a seguir.
        // (O Robolectric esquece a notificação ao parar o primeiro plano; o tipo fica a provar que startForeground foi chamado.)
        val semPlano = servico(ServicoViagem.ACAO_COMECAR).create().startCommand(0, 1)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, semPlano.get().foregroundServiceType)
        assertTrue(shadowOf(semPlano.get()).isStoppedBySelf)

        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        val semEstado = servico(ServicoViagem.ACAO_RETOMAR).create().startCommand(0, 1)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, semEstado.get().foregroundServiceType)
        assertTrue(shadowOf(semEstado.get()).isStoppedBySelf)
        assertNotNull(FicheirosViagem.lerPlano(c)) // o plano fica: não foi a pessoa que parou
    }

    @Test fun seOAndroidRecusarAoRetomarOEstadoFicaParaViagemInterrompidaEAAppSabe() {
        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        val guardado = Viagem(SO_O_736).inicio(T0).copy(fase = Fase.A_BORDO, progresso = 700.0, ultimoGps = T0, atualizado = T0)
        FicheirosViagem.gravarEstado(c, guardado)
        agora = T0.plusSeconds(30)
        val s = servico(ServicoViagem.ACAO_RETOMAR)
        shadowOf(s.get()).setThrowInStartForeground(IllegalStateException("não podes arrancar em segundo plano"))
        s.create().startCommand(0, 1)
        assertTrue(shadowOf(s.get()).isStoppedBySelf)
        assertFalse(bloqueioPreso())
        assertEquals(guardado, FicheirosViagem.lerEstado(c, agora)) // a pessoa ainda pode tentar retomar
        // O ecrã da viagem, já aberto, tem de saber que o serviço não arrancou: os ficheiros ficam, por isso só o aviso o fecha.
        val aviso = shadowOf(c).broadcastIntents.single { it.action == FicheirosViagem.ACAO_ESTADO }
        assertEquals(c.packageName, aviso.`package`)
        assertTrue(aviso.getBooleanExtra(FicheirosViagem.EXTRA_SEM_SERVICO, false))
    }

    @Test fun seOAndroidRecusarAoComecarNaoFicaViagemNenhumaEAAppSabe() {
        // Sem a localização (Android 14+) o startForeground rebenta: o ecrã da viagem, aberto antes do serviço, não pode ficar parado.
        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        val s = servico(ServicoViagem.ACAO_COMECAR)
        shadowOf(s.get()).setThrowInStartForeground(SecurityException("falta a permissão de localização"))
        s.create().startCommand(0, 1)
        assertTrue(shadowOf(s.get()).isStoppedBySelf)
        assertFalse(bloqueioPreso())
        assertNull(FicheirosViagem.lerPlano(c))
        assertNull(FicheirosViagem.lerEstado(c, agora))
        val aviso = shadowOf(c).broadcastIntents.single { it.action == FicheirosViagem.ACAO_ESTADO }
        assertEquals(c.packageName, aviso.`package`)
        assertTrue(aviso.getBooleanExtra(FicheirosViagem.EXTRA_SEM_SERVICO, false))
    }

    @Test fun semLocalizacaoPrecisaAViagemCorrePeloHorario() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        comecar(CASA_AO_TRABALHO)
        assertTrue(shadowOf(localizacao).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }

    @Test fun umaPosicaoDoGpsEntraNoEstadoEFicaNaGravacaoDeDepuracao() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        comecar(CASA_AO_TRABALHO)
        assertEquals(1, shadowOf(localizacao).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        assertEquals(10_000L, pedidoAtual().intervalMillis) // longe do destino: poupa bateria

        passarOTempo(2)
        shadowOf(localizacao).simulateLocation(posicao(38.6995))
        fio.idle()
        val estado = FicheirosViagem.lerEstado(c, agora)!!
        assertEquals(agora, estado.ultimoGps)
        assertEquals(55.0, estado.progresso, 5.0) // a 55 m da paragem
        // Debug: s,lat,lon,acc, com os segundos desde 1970 e sempre com ponto decimal (a locale pt usa vírgula).
        val csv = File(c.filesDir, "viagem/gps.csv").readLines()
        assertEquals(listOf("${agora.epochSecond}.000,38.699500,-9.140000,10"), csv)
    }

    @Test fun umSegundoComecarRecomecaSemDuplicarPedidosNemBloqueios() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val s = comecar(CASA_AO_TRABALHO)
        val primeiro = ShadowPowerManager.getLatestWakeLock()
        pedir(s, ServicoViagem.ACAO_COMECAR)
        assertEquals(1, shadowOf(localizacao).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        assertFalse(primeiro!!.isHeld)
        assertTrue(bloqueioPreso())
    }

    @Test fun deVagarParaAltaPrecisaoQuandoOVeiculoSaiDaAntepenultima() {
        retomarAntesDaAntepenultima()
        assertEquals(10_000L, pedidoAtual().intervalMillis)

        // 60 m para lá da paragem 3 (a antepenúltima): liga o GPS a cada segundo, sem ficar com dois pedidos.
        saiDaAntepenultima()
        assertTrue(FicheirosViagem.lerEstado(c, agora)!!.altaPrecisao)
        assertEquals(1_000L, pedidoAtual().intervalMillis)
        assertEquals(1, shadowOf(localizacao).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
    }

    @Test fun seOGpsSeDesligarAvisaEDepoisDeLigarOAvisoSai() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        comecar(CASA_AO_TRABALHO)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        fio.idle()
        val textos = shadowOf(gestor).allNotifications.map(::titulo)
        assertTrue("Liga a localização para a Paragem Certa saber onde estás." in textos)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        fio.idle()
        assertFalse("Liga a localização para a Paragem Certa saber onde estás." in shadowOf(gestor).allNotifications.map(::titulo))
    }

    @Test fun comOGpsDesligadoAoComecarAvisaLogo() {
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        comecar(CASA_AO_TRABALHO)
        assertTrue("Liga a localização para a Paragem Certa saber onde estás." in shadowOf(gestor).allNotifications.map(::titulo))
    }

    @Test fun enquantoEsperaUmaChegadaAoVivoDaCmMudaOAtraso() {
        val primeira = autocarro("736", T0.plusSeconds(600), idPrimeira = "pt-Carris-Metropolitana_320047")
        val i = itinerario(primeira)
        val prevista = primeira.partidaProgramada.plusSeconds(240)
        val corpo = """[{"line_id":"[YA15B]736","headsign":"Oriente","scheduled_arrival_unix":${primeira.partidaProgramada.epochSecond},
            |"estimated_arrival_unix":${prevista.epochSecond}}]""".trimMargin()
        val pedidos = mutableListOf<String>()
        val http = object : Http {
            override suspend fun get(url: String): Resposta {
                pedidos += url
                return Resposta(200, if (url.endsWith("arrivals/by_stop/020047")) corpo else "[]")
            }
        }
        comecar(i, cm = CarrisMetropolitana(http, "https://cm.teste/"))
        var atraso: Long? = null
        for (volta in 1..100) { // a resposta volta por outra thread: espera-se até 2 s por ela
            fio.idle()
            atraso = FicheirosViagem.lerEstado(c, agora)?.atrasoS
            if (atraso == 240L) break
            Thread.sleep(20)
        }
        assertEquals(listOf("https://cm.teste/arrivals/by_stop/020047"), pedidos)
        assertEquals(240L, atraso)
    }

    @Test fun partidaAoVivoCasaALinhaEAHoraProgramada() {
        val etapa = autocarro("736", T0.plusSeconds(600))
        fun chegada(linha: String, programada: Instant, prevista: Instant?) = Chegada(
            Fonte.CM, linha, linha, "Oriente", programada, prevista, null, null, Modo.AUTOCARRO, null, null,
        )
        val partida = etapa.partidaProgramada
        assertEquals(partida.plusSeconds(240), partidaAoVivo(listOf(chegada("736", partida.plusSeconds(60), partida.plusSeconds(240))), etapa))
        // A mesma linha uma hora depois, ou outra linha à mesma hora, não são o veículo da viagem.
        assertNull(partidaAoVivo(listOf(chegada("736", partida.plusSeconds(3600), partida.plusSeconds(3700))), etapa))
        assertNull(partidaAoVivo(listOf(chegada("28", partida, partida.plusSeconds(100))), etapa))
        // Sem tempo real não há nada a dizer.
        assertNull(partidaAoVivo(listOf(chegada("736", partida, null)), etapa))
        // 91 s de diferença já é outra viagem.
        assertNull(partidaAoVivo(listOf(chegada("736", partida.plusSeconds(91), partida.plusSeconds(120))), etapa))
    }

    @Test fun viagemAoVivoGuardaOTripIdDaPartidaDaEtapa() {
        val etapa = autocarro("736", T0.plusSeconds(600))
        val partida = etapa.partidaProgramada
        fun chegada(linha: String, programada: Instant, prevista: Instant?, viagem: String?) =
            Chegada(Fonte.CM, linha, linha, "Oriente", programada, prevista, viagem, null, Modo.AUTOCARRO, null, null)
        // O trip_id inteiro, com os prefixos: é assim que aparece em vehicles.
        assertEquals("[PL3CL][YA15B]736_1", viagemAoVivo(listOf(chegada("736", partida.plusSeconds(60), partida.plusSeconds(200), "[PL3CL][YA15B]736_1")), etapa))
        // 61 s de diferença já é outra viagem; outra linha, ou sem tempo real (sem trip_id), também não.
        assertNull(viagemAoVivo(listOf(chegada("736", partida.plusSeconds(61), partida, "x")), etapa))
        assertNull(viagemAoVivo(listOf(chegada("28", partida, partida, "x")), etapa))
        assertNull(viagemAoVivo(listOf(chegada("736", partida, null, null)), etapa))
        // Das que servem, a de hora programada mais perto.
        assertEquals("b", viagemAoVivo(listOf(chegada("736", partida.minusSeconds(50), partida, "a"), chegada("736", partida.plusSeconds(10), partida, "b")), etapa))
    }

    @Test fun oAutocarroPerdidoEEsquecidoETrocadoPeloSeguinte() {
        val etapa = autocarro("736", T0.plusSeconds(600))
        val partida = etapa.partidaProgramada
        fun chegada(linha: String, programada: Instant, prevista: Instant?, viagem: String?) =
            Chegada(Fonte.CM, linha, linha, "Oriente", programada, prevista, viagem, null, Modo.AUTOCARRO, null, null)
        val t1 = chegada("736", partida, partida.plusSeconds(60), "T1")
        val t2 = chegada("736", partida.plusSeconds(600), partida.plusSeconds(660), "T2")
        val outras = listOf(chegada("28", partida.plusSeconds(200), partida.plusSeconds(200), "L28"), chegada("736", partida.plusSeconds(300), null, null))
        val guardado = AutocarroCm(0, "T1", partida.plusSeconds(60))
        // Sem nada guardado, o da etapa (viagemAoVivo), com a hora ao vivo dele.
        assertEquals(guardado, autocarroDaEspera(listOf(t2, t1), etapa, 0, null, T0))
        // O de outra etapa não conta.
        assertEquals(guardado, autocarroDaEspera(listOf(t1), etapa, 0, guardado.copy(etapa = 1, viagem = "X"), T0))
        // Atrasado: a hora ao vivo segue-o, e os 2 min contam a partir dela.
        val atrasado = autocarroDaEspera(listOf(t1.copy(prevista = partida.plusSeconds(400)), t2), etapa, 0, guardado, partida.plusSeconds(300))
        assertEquals(guardado.copy(prevista = partida.plusSeconds(400)), atrasado)
        // Já não aparece (ou não houve consulta nova): fica até 2 min depois de partir.
        assertEquals(guardado, autocarroDaEspera(emptyList(), etapa, 0, guardado, partida.plusSeconds(180)))
        // A pessoa continua à espera passados os 2 min: perdeu-o. O seguinte da mesma linha com tempo real fica no lugar dele.
        assertEquals(AutocarroCm(0, "T2", partida.plusSeconds(660)), autocarroDaEspera(outras + t2, etapa, 0, guardado, partida.plusSeconds(181)))
        // Sem o seguinte ao vivo, fica marcado como perdido até ele aparecer; o perdido não volta, mesmo que reapareça.
        val perdido = autocarroDaEspera(outras, etapa, 0, guardado, partida.plusSeconds(181))
        assertEquals(guardado.copy(perdido = true), perdido)
        assertEquals(perdido, autocarroDaEspera(listOf(t1.copy(prevista = partida.plusSeconds(400))), etapa, 0, perdido, partida.plusSeconds(190)))
        assertEquals(AutocarroCm(0, "T2", partida.plusSeconds(660)), autocarroDaEspera(listOf(t2), etapa, 0, perdido, partida.plusSeconds(220)))
    }

    private fun veiculo(id: String, lat: Double, sinal: Instant, viagem: String? = null, paragem: String? = "020045", aChegar: Boolean = true, linha: String = "736") =
        Veiculo(id, linha, lat, -9.14, null, sinal, viagem = viagem, paragem = paragem, aChegar = aChegar)

    @Test fun oVeiculoDaViagemEOQueTemOTripId() {
        val etapa = autocarro("736", T0, idsCm = true)
        val agora = T0.plusSeconds(400)
        val gps = Entrada.Gps(38.715, -9.14, 10f, agora.minusSeconds(5))
        val daViagem = veiculo("[YA15B]2261", 38.72, agora.minusSeconds(15), viagem = "T1")
        val maisPerto = veiculo("[YA15B]9", 38.715, agora.minusSeconds(15), paragem = "020043")
        // O do trip_id conta logo, mesmo com outro mais perto (o motor é que vê se ele vai longe de mais da pessoa). A leitura leva a posição dele.
        assertEquals(Entrada.Veiculo(0, 5, true, agora, 38.72, -9.14),escolherVeiculo(listOf(maisPerto, daViagem), etapa, 0, "T1", gps, 7.0, agora, null).first)
        // Leitura com mais de 60 s, ou com a hora 1 h à frente (a API diz que usa a hora de verão): não conta.
        for (sinal in listOf(agora.minusSeconds(61), agora.plusSeconds(3600))) {
            assertNull("$sinal", escolherVeiculo(listOf(daViagem.copy(ultimoSinal = sinal)), etapa, 0, "T1", null, 7.0, agora, null).first)
        }
        // Uma paragem que não é da etapa (já passou o destino), ou nenhuma: nada.
        assertNull(escolherVeiculo(listOf(daViagem.copy(paragem = "020046")), etapa, 0, "T1", null, 7.0, agora, null).first)
        assertNull(escolherVeiculo(listOf(daViagem.copy(paragem = null)), etapa, 0, "T1", null, 7.0, agora, null).first)
    }

    @Test fun semTripIdOVeiculoMaisPertoEmDuasConsultasSeguidas() {
        val etapa = autocarro("736", T0, idsCm = true)
        val agora = T0.plusSeconds(400)
        // A última posição aceite foi há 5 s e as leituras têm 15 s: 10 s entre as duas, o limite é 150 + 7 × 10 = 220 m.
        val gps = Entrada.Gps(38.715, -9.14, 10f, agora.minusSeconds(5))
        val este = veiculo("[YA15B]1", 38.716, agora.minusSeconds(15), paragem = "020043", aChegar = false) // a 111 m
        val outro = veiculo("[YA15B]2", 38.7165, agora.minusSeconds(15)) // a 167 m
        val doutraLinha = veiculo("[YA15B]3", 38.715, agora.minusSeconds(15), linha = "28")
        val todos = listOf(outro, doutraLinha, este)
        // Na primeira consulta fica só como candidato; na segunda, o mesmo, conta.
        val (primeira, candidato) = escolherVeiculo(todos, etapa, 0, null, gps, 7.0, agora, null)
        assertNull(primeira)
        assertEquals("[YA15B]1", candidato)
        assertEquals(Entrada.Veiculo(0, 3, false, agora, 38.716, -9.14) to "[YA15B]1", escolherVeiculo(todos, etapa, 0, null, gps, 7.0, agora, candidato))
        // Com um trip_id que não aparece em vehicles também se escolhe assim.
        assertEquals(Entrada.Veiculo(0, 3, false, agora, 38.716, -9.14) to "[YA15B]1",escolherVeiculo(todos, etapa, 0, "T9", gps, 7.0, agora, candidato))
        // Se o mais perto mudar, recomeça.
        assertEquals(null to "[YA15B]2", escolherVeiculo(listOf(outro), etapa, 0, null, gps, 7.0, agora, candidato))
        // A 334 m é longe de mais; sem posição aceite, ou com ela a mais de 60 s da leitura, não há com que comparar.
        val longe = veiculo("[YA15B]4", 38.718, agora.minusSeconds(15))
        assertEquals(null to null, escolherVeiculo(listOf(longe), etapa, 0, null, gps, 7.0, agora, "[YA15B]4"))
        assertEquals(null to null, escolherVeiculo(todos, etapa, 0, null, null, 7.0, agora, candidato))
        assertEquals(null to null, escolherVeiculo(todos, etapa, 0, null, gps.copy(hora = agora.minusSeconds(80)), 7.0, agora, candidato))
    }

    @Test fun aBordoOAutocarroDaViagemTocaOSaiAgoraAntesDoHorario() {
        // À espera, as chegadas dão o trip_id do 736 da etapa; a bordo (pelo horário: sem GPS), vehicles diz que esse
        // autocarro está a chegar ao destino. O Sai agora toca já, e não 9 min depois, quando o horário o dizia.
        val etapa = autocarro("736", T0.plusSeconds(600), idsCm = true)
        val partida = etapa.partidaProgramada.epochSecond
        val chegadas = """[{"line_id":"[YA15B]736","headsign":"Oriente","scheduled_arrival_unix":$partida,"estimated_arrival_unix":${partida + 60},
            |"trip_id":"[PL3CL][YA15B]736_0_1","vehicle_id":"2261"}]""".trimMargin()
        fun veiculos() = """[{"id":"[YA15B]2261","line_id":"736","lat":38.724,"lon":-9.14,"timestamp":${agora.toEpochMilli() - 15_000},
            |"stop_id":"020045","current_status":"INCOMING_AT","trip_id":"[PL3CL][YA15B]736_0_1"}]""".trimMargin()
        val pedidos = CopyOnWriteArrayList<String>()
        val http = object : Http {
            override suspend fun get(url: String): Resposta {
                pedidos += url
                return Resposta(200, if (url.endsWith("/vehicles")) veiculos() else if (url.endsWith("arrivals/by_stop/020040")) chegadas else "[]")
            }
        }
        comecar(itinerario(etapa), cm = CarrisMetropolitana(http, "https://cm.teste/"))
        esperarAte { FicheirosViagem.lerEstado(c, agora)?.atrasoS == 60L }
        repeat(131) { passarOTempo(5) }
        assertEquals(Fase.ESPERA, FicheirosViagem.lerEstado(c, agora)!!.fase)
        assertTrue(pedidos.none { it.endsWith("/vehicles") }) // os 306 KB de vehicles só a bordo
        passarOTempo(5) // T0 + 660: parte, com o minuto de atraso
        esperarAte { alarmes.size == 2 }
        assertEquals(listOf(Evento.PREPARA_TE, Evento.SAI_AGORA), alarmes.map { it.first })
        assertEquals(Fase.A_BORDO, FicheirosViagem.lerEstado(c, agora)!!.fase)
        assertEquals(Origem.VEICULO, FicheirosViagem.lerEstado(c, agora)!!.origem)
        // O estado leva a posição do autocarro, para o mapa da viagem o desenhar.
        assertEquals(38.724 to -9.14, FicheirosViagem.lerEstado(c, agora)!!.let { it.veiculoLat to it.veiculoLon })
    }

    @Test fun quemPerdeOAutocarroNaoEPuxadoPorEle() {
        // À espera do 736 da etapa (T1, parte às 10:11 ao vivo) com o GPS na paragem: ele passa e a pessoa fica. 3 min
        // depois continua à espera e apanha o seguinte (T2, às 10:21). A bordo, 60 s sem GPS: vehicles mostra o T1 a
        // chegar ao destino e o T2 a caminho da paragem 2. Só o T2 conta: nada de Sai agora minutos antes do tempo.
        shadowOf(c).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(localizacao).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val etapa = autocarro("736", T0.plusSeconds(600), idsCm = true)
        val p = etapa.partidaProgramada.epochSecond
        val chegadas = """[{"line_id":"[YA15B]736","headsign":"Oriente","scheduled_arrival_unix":$p,"estimated_arrival_unix":${p + 60},"trip_id":"T1"},
            |{"line_id":"[YA15B]736","headsign":"Oriente","scheduled_arrival_unix":${p + 600},"estimated_arrival_unix":${p + 660},"trip_id":"T2"}]""".trimMargin()
        fun veiculos() = """[{"id":"[YA15B]1","line_id":"736","lat":38.724,"lon":-9.14,"timestamp":${agora.toEpochMilli() - 15_000},
            |"stop_id":"020045","current_status":"INCOMING_AT","trip_id":"T1"},
            |{"id":"[YA15B]2","line_id":"736","lat":38.709,"lon":-9.14,"timestamp":${agora.toEpochMilli() - 15_000},
            |"stop_id":"020042","current_status":"IN_TRANSIT_TO","trip_id":"T2"}]""".trimMargin()
        val http = object : Http {
            override suspend fun get(url: String) =
                Resposta(200, if (url.endsWith("/vehicles")) veiculos() else if (url.endsWith("arrivals/by_stop/020040")) chegadas else "[]")
        }
        // O Robolectric só entrega uma posição com a hora do relógio do sistema um intervalo à frente da anterior.
        fun gps(lat: Double) = shadowOf(localizacao).simulateLocation(posicao(lat).apply { elapsedRealtimeNanos = Duration.between(T0, agora).toNanos() })
        comecar(itinerario(etapa), cm = CarrisMetropolitana(http, "https://cm.teste/"))
        esperarAte { FicheirosViagem.lerEstado(c, agora)?.atrasoS == 60L } // o T1 ficou guardado
        // Parada na paragem até o T2 partir (T0 + 1260): o T1 sai das chegadas às 10:11:30 e os 2 min passam às 10:13.
        while (agora < T0.plusSeconds(1260)) {
            passarOTempo(5)
            if (agora.epochSecond % 10 == 0L) gps(38.70)
        }
        fio.idle()
        assertEquals(Fase.ESPERA, FicheirosViagem.lerEstado(c, agora)!!.fase)
        // Entra no T2: 100 m e 200 m ao longo da linha, 10 s entre cada.
        for (lat in listOf(38.7009, 38.7018)) {
            passarOTempo(10)
            gps(lat)
            fio.idle()
        }
        assertEquals(Fase.A_BORDO, FicheirosViagem.lerEstado(c, agora)!!.fase)
        // 60 s sem GPS: vehicles passa a contar.
        repeat(16) { passarOTempo(5) }
        esperarAte { FicheirosViagem.lerEstado(c, agora)?.ultimoVeiculo != null }
        val estado = FicheirosViagem.lerEstado(c, agora)!!
        assertEquals(Origem.VEICULO, estado.origem)
        assertTrue(alarmes.map { it.first }.toString(), alarmes.isEmpty())
        val a = Percurso(emptyList(), etapa.paragens).noPercurso
        // O T2 a caminho da paragem 2 já saiu da 1; o T1 tinha-a levado para lá da 4.
        assertTrue("${estado.progresso}", estado.progresso >= a[1] + 31 && estado.progresso < a[3])
        // E é o T2 que o mapa mostra, não o T1 (38.724).
        assertEquals(38.709 to -9.14, estado.veiculoLat to estado.veiculoLon)
    }

    @Test fun relatoConfirmadoNaLinhaNotifica() {
        // À espera do 736 em p0: p3 está à frente; p0, a paragem onde se espera, não.
        responder(relato("r1", "TRANSITOUS:p3"), relato("r2", "TRANSITOUS:p0", tipo = "acidente"))
        comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS))
        esperarAte { relatosPostados().size == 2 }
        assertEquals(setOf("⚠ Desvio confirmado na 736, à frente da tua paragem", "⚠ Acidente confirmado na 736"), relatosPostados())
        assertEquals(listOf("https://pc.teste/api/avisos?linhas=736"), pedidosAvisos)
        val n = shadowOf(gestor).allNotifications.first { titulo(it)!!.startsWith("⚠") }
        assertEquals(Notificacoes.CANAL_AVISOS, n.channelId)
        assertTrue(alarmes.isEmpty()) // sem alarme: é uma notificação, não um "Sai agora"
    }

    @Test fun relatoParaTrasNaoDizAFrente() {
        // A bordo, já depois de p3: o relato em p2 ficou para trás.
        val a = Percurso(emptyList(), DO_TRANSITOUS.etapas[0].paragens).noPercurso
        FicheirosViagem.gravarPlano(c, comAvisos(DO_TRANSITOUS))
        FicheirosViagem.gravarEstado(
            c, Viagem(DO_TRANSITOUS).inicio(T0).copy(fase = Fase.A_BORDO, progresso = a[3] + 100, avisados = setOf(Evento.PREPARA_TE), atualizado = T0),
        )
        responder(relato("r1", "TRANSITOUS:p2"))
        servico(ServicoViagem.ACAO_RETOMAR).create().startCommand(0, 1)
        esperarAte { relatosPostados().isNotEmpty() }
        assertEquals(setOf("⚠ Desvio confirmado na 736"), relatosPostados())
    }

    @Test fun pedeDe5Em5Minutos() {
        comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS))
        assertEquals(1, pedidosAvisos.size)
        repeat(59) { passarOTempo(5) }
        assertEquals(1, pedidosAvisos.size)
        passarOTempo(5) // 5 min
        assertEquals(2, pedidosAvisos.size)
        repeat(60) { passarOTempo(5) } // 10 min
        assertEquals(List(3) { "https://pc.teste/api/avisos?linhas=736" }, pedidosAvisos)
    }

    @Test fun mesmoRelatoSoNotificaUmaVez() {
        responder(relato("r1", "TRANSITOUS:p3"))
        comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS))
        esperarAte { relatosPostados().isNotEmpty() }
        repeat(120) { passarOTempo(5) }
        assertEquals(3, pedidosAvisos.size)
        assertEquals(1, shadowOf(gestor).allNotifications.count { titulo(it)!!.startsWith("⚠") })
        // Fica no trip-state: retomar a viagem não volta a avisar.
        assertEquals(setOf("r1"), FicheirosViagem.lerEstado(c, agora)!!.relatosAvisados)
    }

    @Test fun ocultadoNaoNotifica() {
        responder(relato("r1", "TRANSITOUS:p3"), relato("r2", "TRANSITOUS:p4", tipo = "evento"), relato("r3", "TRANSITOUS:p4", confirmado = false))
        comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS, ocultos = setOf("relato:r1")))
        esperarAte { relatosPostados().isNotEmpty() }
        assertEquals(setOf("⚠ Evento confirmado na 736, à frente da tua paragem"), relatosPostados())
    }

    @Test fun semInterruptorNaoPede() {
        responder(relato("r1", "TRANSITOUS:p3"))
        val s = comecar(DO_TRANSITOUS, plano(DO_TRANSITOUS))
        repeat(120) { passarOTempo(5) }
        assertTrue(pedidosAvisos.isEmpty())
        // Uma linha cujo nome o servidor recusa (com "/") não vai; sem nenhuma que sirva, não se pede nada.
        pedir(s, Notificacoes.ACAO_PARAR)
        val estranha = itinerario(DO_TRANSITOUS.etapas[0].copy(linhaNome = "A/B"))
        comecar(estranha, comAvisos(estranha))
        repeat(120) { passarOTempo(5) }
        assertTrue(pedidosAvisos.isEmpty())
    }

    @Test fun emPausaOuDesligadaParaDePedir() {
        for (resposta in listOf(Resposta(503, """{"erro":"em_pausa"}"""), Resposta(403, """{"erro":"desligado"}"""))) {
            pedidosAvisos.clear()
            respostaAvisos = resposta
            val s = comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS))
            repeat(240) { passarOTempo(5) } // 20 min
            assertEquals(resposta.corpo, 1, pedidosAvisos.size)
            pedir(s, Notificacoes.ACAO_PARAR)
            agora = T0
        }
    }

    @Test fun acabouAViagemParaDePedir() {
        val s = comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS))
        assertEquals(1, pedidosAvisos.size)
        pedir(s, Notificacoes.ACAO_PARAR)
        repeat(120) { passarOTempo(5) }
        assertEquals(1, pedidosAvisos.size)
    }

    /** As notificações das perturbações postadas (o título começa por "⚠ Perturbação"). */
    private fun perturbacoesPostadas() = shadowOf(gestor).allNotifications.filter { titulo(it)?.startsWith("⚠ Perturbação") == true }

    @Test fun perturbacoesDe5Em5Minutos() {
        val i = naLinha("1009")
        comecar(i, comAvisos(i), cm = cmAlertas)
        esperarAte { pedidosAlertas.size == 1 }
        assertEquals(1, pedidosAlertas.size)
        repeat(59) { passarOTempo(5) }
        assertEquals(1, pedidosAlertas.size)
        passarOTempo(5) // 5 min
        esperarAte { pedidosAlertas.size == 2 }
        repeat(60) { passarOTempo(5) } // 10 min
        esperarAte { pedidosAlertas.size == 3 }
        assertEquals(List(3) { "https://cm.teste/alerts" }, pedidosAlertas)
    }

    @Test fun perturbacaoDaCmNaLinhaNotifica() {
        responderAlertas("Obras na Rua Direita" to listOf("1009"), "Desvio na Amadora" to listOf("1503"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i), cm = cmAlertas)
        esperarAte { perturbacoesPostadas().isNotEmpty() }
        val n = perturbacoesPostadas().single()
        assertEquals("⚠ Perturbação na 1009", titulo(n))
        assertEquals("Obras na Rua Direita", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Obras na Rua Direita", n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals(Notificacoes.CANAL_AVISOS, n.channelId)
        assertEquals("⚠ Perturbação na 1009", titulo(shadowOf(gestor).getNotification(Notificacoes.ID_PERTURBACAO)))
        assertTrue(alarmes.isEmpty())
    }

    @Test fun perturbacaoDeVariasLinhasDizALinhaDaViagem() {
        responderAlertas("Obras em Odivelas" to listOf("1604", "1612"))
        val i = naLinha("1612")
        comecar(i, comAvisos(i), cm = cmAlertas)
        esperarAte { perturbacoesPostadas().isNotEmpty() }
        assertEquals(listOf("⚠ Perturbação na 1612"), perturbacoesPostadas().map(::titulo))
    }

    @Test fun mesmaPerturbacaoSoNotificaUmaVez() {
        responderAlertas("Obras na Rua Direita" to listOf("1009"))
        val i = naLinha("1009")
        val s = comecar(i, comAvisos(i), cm = cmAlertas)
        esperarAte { perturbacoesPostadas().isNotEmpty() }
        repeat(120) { passarOTempo(5) }
        esperarAte { pedidosAlertas.size == 3 }
        assertEquals(1, perturbacoesPostadas().size)
        assertEquals(setOf(idDoAlerta("Obras na Rua Direita")), FicheirosViagem.lerEstado(c, agora)!!.perturbacoesAvisadas)
        // Fica no trip-state: o Android fecha o serviço e a viagem retomada não volta a avisar.
        s.destroy()
        gestor.cancelAll()
        pedidosAlertas.clear()
        servico(ServicoViagem.ACAO_RETOMAR, cmAlertas).create().startCommand(0, 1)
        esperarAte { pedidosAlertas.isNotEmpty() }
        fio.idle()
        assertEquals(1, pedidosAlertas.size)
        assertTrue(perturbacoesPostadas().isEmpty())
    }

    @Test fun perturbacaoJaVistaNaoNotificaNaViagem() {
        responderAlertas("Obras na Rua Direita" to listOf("1009"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i, vistas = setOf(idDoAlerta("Obras na Rua Direita"))), cm = cmAlertas)
        esperarAte { pedidosAlertas.isNotEmpty() }
        fio.idle()
        assertEquals(1, pedidosAlertas.size)
        assertTrue(perturbacoesPostadas().isEmpty())
    }

    @Test fun comunidadeDesligadaAindaAvisaDePerturbacoes() {
        responderAlertas("Obras na Rua Direita" to listOf("1009"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i, relatos = false), cm = cmAlertas)
        esperarAte { perturbacoesPostadas().isNotEmpty() }
        assertEquals(listOf("⚠ Perturbação na 1009"), perturbacoesPostadas().map(::titulo))
    }

    @Test fun semComunidadeSoPedeACm() {
        responder(relato("r1", "TRANSITOUS:p3", linha = "1009"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i, relatos = false), cm = cmAlertas)
        repeat(120) { passarOTempo(5) }
        esperarAte { pedidosAlertas.size == 3 }
        assertEquals(3, pedidosAlertas.size)
        assertTrue(pedidosAvisos.isEmpty())
    }

    @Test fun viagemSemLinhaDaCmNaoPedeAlertasDaCm() {
        // O 736 da Carris: os relatos vão ao servidor, mas a CM não tem avisos dele.
        comecar(DO_TRANSITOUS, comAvisos(DO_TRANSITOUS), cm = cmAlertas)
        repeat(120) { passarOTempo(5) }
        fio.idle()
        assertEquals(3, pedidosAvisos.size)
        assertTrue(pedidosAlertas.isEmpty())
    }

    @Test fun servidorEmPausaAvisaDaCm() {
        respostaAvisos = Resposta(503, """{"erro":"em_pausa"}""")
        responderAlertas("Obras na Rua Direita" to listOf("1009"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i), cm = cmAlertas)
        esperarAte { perturbacoesPostadas().isNotEmpty() }
        repeat(120) { passarOTempo(5) }
        esperarAte { pedidosAlertas.size == 3 }
        assertEquals(1, pedidosAvisos.size) // o servidor pára até ao fim da viagem; a CM continua
        assertEquals(3, pedidosAlertas.size)
        assertEquals(listOf("⚠ Perturbação na 1009"), perturbacoesPostadas().map(::titulo))
    }

    @Test fun preferenciaDesligadaNaoPedeNada() {
        responderAlertas("Obras na Rua Direita" to listOf("1009"))
        val i = naLinha("1009")
        comecar(i, comAvisos(i).copy(avisosComunidade = false), cm = cmAlertas)
        repeat(120) { passarOTempo(5) }
        fio.idle()
        assertTrue(pedidosAlertas.isEmpty())
        assertTrue(pedidosAvisos.isEmpty())
        assertTrue(perturbacoesPostadas().isEmpty())
    }

    /** As respostas da CM voltam por outra thread: espera até 2 s que a condição se cumpra. */
    private fun esperarAte(condicao: () -> Boolean) {
        repeat(100) {
            fio.idle()
            if (condicao()) return
            Thread.sleep(20)
        }
    }

    /** O wake lock do serviço ficou preso? */
    private fun bloqueioPreso() = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

    /** O pedido de posições que o serviço fez ao GPS (e que o Robolectric guardou). */
    private fun pedidoAtual() = shadowOf(localizacao).getLocationRequests(LocationManager.GPS_PROVIDER).single()
}
