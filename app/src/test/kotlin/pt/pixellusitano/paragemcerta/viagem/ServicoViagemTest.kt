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
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Percurso
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.io.File
import java.time.Duration
import java.time.Instant

private val T0 = Instant.parse("2026-07-01T10:00:00Z")

private fun lugar(nome: String, lat: Double) = Lugar(nome, lat, -9.14, TipoLugar.PARAGEM)

private fun aPe(de: Lugar, para: Lugar, partida: Instant) = Etapa(
    aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null, de = de, para = para,
    partida = partida, chegada = partida.plusSeconds(180), partidaProgramada = partida, aoVivo = false,
    paragensIntermedias = 0, desenho = emptyList(), cor = null, distancia = 110.0,
)

/** Um autocarro em linha reta para norte: seis paragens a 0,005° (cerca de 550 m) umas das outras e 2 min entre cada. */
private fun autocarro(linha: String, partida: Instant, idPrimeira: String? = null): Etapa {
    val nomes = listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente")
    val paragens = nomes.mapIndexed { i, n ->
        ParagemEtapa(if (i == 0) idPrimeira else null, n, 38.70 + i * 0.005, -9.14, partida.plusSeconds(i * 120L))
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

    /** O serviço do Robolectric, com o relógio e o alarme do teste; o Android entrega-lhe a ação como no telemóvel. */
    private fun servico(acao: String, cm: CarrisMetropolitana? = null): ServiceController<ServicoViagem> =
        Robolectric.buildService(ServicoViagem::class.java, Intent(c, ServicoViagem::class.java).setAction(acao)).also {
            it.get().agora = { agora }
            it.get().alarme = { ev, som, vibrar -> alarmes += Triple(ev, som, vibrar) }
            it.get().cm = cm
        }

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
        assertEquals(listOf("Silenciar", "Já saí", "Parar"), n.actions.map { it.title.toString() })
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
        val s = comecar(SO_O_736)
        pedir(s, Notificacoes.ACAO_JA_SAI)
        assertTrue(alarmes.isEmpty())
        assertEquals("Chegaste a Oriente", avisoPostado())
        assertTrue(shadowOf(s.get()).isStoppedBySelf) // nada a tocar: pára logo
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

    @Test fun seOAndroidRecusarOEstadoFicaParaViagemInterrompida() {
        FicheirosViagem.gravarPlano(c, plano(SO_O_736))
        val s = servico(ServicoViagem.ACAO_COMECAR)
        shadowOf(s.get()).setThrowInStartForeground(IllegalStateException("não podes arrancar em segundo plano"))
        s.create().startCommand(0, 1)
        assertTrue(shadowOf(s.get()).isStoppedBySelf)
        assertFalse(bloqueioPreso())
        assertFalse(FicheirosViagem.lerEstado(c, agora)!!.terminada)
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

    /** O wake lock do serviço ficou preso? */
    private fun bloqueioPreso() = ShadowPowerManager.getLatestWakeLock()?.isHeld == true

    /** O pedido de posições que o serviço fez ao GPS (e que o Robolectric guardou). */
    private fun pedidoAtual() = shadowOf(localizacao).getLocationRequests(LocationManager.GPS_PROVIDER).single()
}
