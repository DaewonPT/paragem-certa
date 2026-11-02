package pt.pixellusitano.paragemcerta.viagem

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Evento
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.Percurso
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

// 10:00 UTC, 11:00 em Lisboa (verão): as horas do texto só batem certo se a zona for mesmo a pedida.
private val T0 = Instant.parse("2026-07-01T10:00:00Z")
private val LISBOA = ZoneId.of("Europe/Lisbon")

private fun lugar(nome: String, lat: Double = 38.70) = Lugar(nome, lat, -9.14, TipoLugar.PARAGEM)

private fun aPe(de: String, para: String, partida: Instant, metros: Double) = Etapa(
    aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null, de = lugar(de), para = lugar(para),
    partida = partida, chegada = partida.plusSeconds(180), partidaProgramada = partida, aoVivo = false,
    paragensIntermedias = 0, desenho = emptyList(), cor = null, distancia = metros,
)

/** Um autocarro que pára nas paragens dadas, a 0,005° (cerca de 550 m) umas das outras e 2 min entre cada. */
private fun autocarro(linha: String, nomes: List<String>, partida: Instant, aoVivo: Boolean = false): Etapa {
    val paragens = nomes.mapIndexed { i, n -> ParagemEtapa("$linha-$i", n, 38.70 + i * 0.005, -9.14, partida.plusSeconds(i * 120L)) }
    return Etapa(
        aPe = false, modo = Modo.AUTOCARRO, linhaNome = linha, linhaId = "id$linha", destino = nomes.last(),
        de = lugar(nomes.first()), para = lugar(nomes.last(), paragens.last().lat), partida = partida, chegada = paragens.last().hora,
        partidaProgramada = partida, aoVivo = aoVivo, paragensIntermedias = nomes.size - 2, desenho = emptyList(), cor = null,
        idViagem = "viagem$linha", paragens = paragens,
    )
}

private fun itinerario(vararg etapas: Etapa) =
    Itinerario(etapas.toList(), etapas.first().partida, etapas.last().chegada, etapas.count { !it.aPe } - 1)

private val PARAGENS_736 = listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificacoesTest {
    private val c = RuntimeEnvironment.getApplication()

    // A pé até ao Cais do Sodré, o 736 até ao Oriente, a pé até ao destino.
    private val simples = itinerario(
        aPe("Casa", "Cais do Sodré", T0, 140.0),
        autocarro("736", PARAGENS_736, T0.plusSeconds(180)),
        aPe("Oriente", "Trabalho", T0.plusSeconds(1500), 90.0),
    )

    // O 736 até ao Oriente, a pé até à outra paragem e o 28 até Sacavém.
    private val comTransbordo = itinerario(
        aPe("Casa", "Cais do Sodré", T0, 140.0),
        autocarro("736", PARAGENS_736, T0.plusSeconds(180)),
        aPe("Oriente", "Oriente (Norte)", T0.plusSeconds(1500), 90.0),
        autocarro("28", listOf("Oriente (Norte)", "Moscavide", "Sacavém"), T0.plusSeconds(1800)),
    )

    // O comboio da Linha de Cascais (nome próprio, sem número) até Algés e, a pé até à paragem, o 750.
    private val comLinhaComNome = itinerario(
        aPe("Casa", "Cais do Sodré", T0, 140.0),
        autocarro("Linha de Cascais", listOf("Cais do Sodré", "Santos", "Alcântara", "Algés"), T0.plusSeconds(180)).copy(modo = Modo.COMBOIO),
        aPe("Algés", "Algés (Estação)", T0.plusSeconds(900), 90.0),
        autocarro("750", listOf("Algés (Estação)", "Mercado", "Oeiras"), T0.plusSeconds(1200)),
    )

    private fun plano(i: Itinerario) = PlanoViagem(i, som = true, vibrar = true)

    /** O estado a bordo da etapa i, com o veículo já para lá da paragem k (e antes da k + 1). */
    private fun aBordo(i: Itinerario, etapa: Int, k: Int, origem: Origem = Origem.GPS): EstadoViagem {
        val e = i.etapas[etapa]
        val a = Percurso(e.desenho, e.paragens).noPercurso
        return Viagem(i).inicio(T0).copy(etapa = etapa, fase = Fase.A_BORDO, progresso = a[k] + 100, origem = origem, atualizado = T0)
    }

    private fun progresso(i: Itinerario, e: EstadoViagem) = Notificacoes.progresso(c, plano(i), e, Viagem(i), LISBOA)
    private fun aviso(i: Itinerario, ev: Evento, e: EstadoViagem) = Notificacoes.aviso(c, ev, plano(i), e, Viagem(i), LISBOA)
    private fun titulo(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test fun osDoisCanaisTemAImportanciaCerta() {
        Notificacoes.criarCanais(c)
        Notificacoes.criarCanais(c) // pode correr em cada arranque do serviço
        val gestor = c.getSystemService(NotificationManager::class.java)
        assertEquals(NotificationManager.IMPORTANCE_LOW, gestor.getNotificationChannel("viagem").importance)
        val avisos = gestor.getNotificationChannel("avisos")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, avisos.importance)
        assertNull(avisos.sound) // o som e a vibração são do Alarme, para as definições do alarme valerem sempre
        assertFalse(avisos.shouldVibrate())
    }

    @Test fun progressoEmVeiculoComOsTresBotoes() {
        val n = progresso(simples, aBordo(simples, 1, k = 2))
        assertEquals("Faltam 3 paragens · sais em Oriente", titulo(n))
        assertEquals("viagem", n.channelId)
        assertTrue((n.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        // Não há getter público para isto: lê-se o campo da notificação.
        val comportamento = Notification::class.java.getDeclaredField("mFgsDeferBehavior").apply { isAccessible = true }.get(n)
        assertEquals(Notification.FOREGROUND_SERVICE_IMMEDIATE, comportamento)
        assertEquals(listOf("Silenciar", "Já saí", "Parar"), n.actions.map { it.title.toString() })
        n.actions.forEach { assertTrue(shadowOf(it.actionIntent).isService) }
        assertEquals(
            listOf(Notificacoes.ACAO_SILENCIAR, Notificacoes.ACAO_JA_SAI, Notificacoes.ACAO_PARAR),
            n.actions.map { shadowOf(it.actionIntent).savedIntent.action },
        )
        assertEquals(
            "pt.pixellusitano.paragemcerta.viagem.ServicoViagem",
            shadowOf(n.actions[0].actionIntent).savedIntent.component!!.className,
        )
    }

    @Test fun jaSaiSoApareceAbordo() {
        fun botoes(e: EstadoViagem) = progresso(simples, e).actions.map { it.title.toString() }
        // A pé não há de que sair, e à espera "Já saí" saltava o autocarro que a pessoa ainda vai apanhar.
        assertEquals(listOf("Silenciar", "Parar"), botoes(Viagem(simples).inicio(T0)))
        assertEquals(listOf("Silenciar", "Parar"), botoes(Viagem(simples).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA)))
        assertEquals(listOf("Silenciar", "Já saí", "Parar"), botoes(aBordo(simples, 1, k = 2)))
    }

    @Test fun tocarNaNotificacaoAbreOEcraDaViagem() {
        val e = aBordo(simples, 1, k = 2)
        for (n in listOf(progresso(simples, e), aviso(simples, Evento.PREPARA_TE, e))) {
            val aberto = shadowOf(n.contentIntent).savedIntent
            assertEquals(MainActivity::class.java.name, aberto.component!!.className)
            assertTrue(aberto.getBooleanExtra(Notificacoes.EXTRA_ABRIR_VIAGEM, false))
        }
    }

    @Test fun semZonaPedidaAsHorasSaoDeLisboa() {
        // Com o telemóvel noutro fuso a hora do horário é a de Lisboa, como no ecrã da viagem.
        val antes = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val espera = Viagem(simples).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA, atualizado = T0.plusSeconds(60))
            assertEquals("O 736 parte às 11:03", titulo(Notificacoes.progresso(c, plano(simples), espera, Viagem(simples))))
        } finally {
            TimeZone.setDefault(antes)
        }
    }

    @Test fun faltaUmaParagem() {
        assertEquals("Falta 1 paragem · sais em Oriente", titulo(progresso(simples, aBordo(simples, 1, k = 4))))
    }

    @Test fun aPeDizADistancia() {
        val e = Viagem(simples).inicio(T0).copy(progresso = 124.0)
        assertEquals("A pé até Cais do Sodré · 120 m", titulo(progresso(simples, e)))
    }

    @Test fun aEsperaComTempoRealDizQuantoFalta() {
        val espera = Viagem(simples).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA)
        // Horário 11:03 em Lisboa, atrasado 2 min, são 11:01: o 736 chega às 11:05, daqui a 4 min.
        val e = espera.copy(atrasoS = 120, atualizado = T0.plusSeconds(60))
        assertEquals("O 736 chega em 4 min", titulo(progresso(simples, e)))
    }

    @Test fun aEsperaSemTempoRealDizAHoraDoHorario() {
        val espera = Viagem(simples).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA, atualizado = T0.plusSeconds(60))
        assertEquals("O 736 parte às 11:03", titulo(progresso(simples, espera)))
    }

    @Test fun aEsperaComTempoRealDaFonteTambemDizQuantoFalta() {
        val aoVivo = simples.copy(etapas = simples.etapas.mapIndexed { i, e -> if (i == 1) e.copy(aoVivo = true) else e })
        val espera = Viagem(aoVivo).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA, atualizado = T0.plusSeconds(60))
        assertEquals("O 736 chega em 2 min", titulo(progresso(aoVivo, espera)))
    }

    @Test fun depoisDaHoraDoHorarioDizAHoraDeAgora() {
        val e = Viagem(simples).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA, atrasoS = 120, atualizado = T0.plusSeconds(300))
        assertEquals("O 736 parte às 11:05", titulo(progresso(simples, e)))
    }

    @Test fun pelaHoraAcrescentaAPosicaoEstimada() {
        val n = progresso(simples, aBordo(simples, 1, k = 2, Origem.HORARIO))
        assertEquals("Faltam 3 paragens · sais em Oriente · posição estimada pelo horário", titulo(n))
    }

    @Test fun avisoPrepareTe() {
        val n = aviso(simples, Evento.PREPARA_TE, aBordo(simples, 1, k = 3))
        assertEquals("Prepara-te — sais daqui a 2 paragens, em Oriente", titulo(n))
        assertEquals("avisos", n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertEquals("Prepara-te — sais daqui a 1 paragem, em Oriente", titulo(aviso(simples, Evento.PREPARA_TE, aBordo(simples, 1, k = 4))))
    }

    @Test fun umaLinhaComNomeProprioDizOModoAntesDoNome() {
        val espera = Viagem(comLinhaComNome).inicio(T0).copy(etapa = 1, fase = Fase.ESPERA, atualizado = T0.plusSeconds(60))
        assertEquals("O comboio da Linha de Cascais parte às 11:03", titulo(progresso(comLinhaComNome, espera)))
        assertEquals("O comboio da Linha de Cascais chega em 4 min", titulo(progresso(comLinhaComNome, espera.copy(atrasoS = 120))))
        // Com número fica como estava: "O 750".
        val outra = Viagem(comLinhaComNome).inicio(T0).copy(etapa = 3, fase = Fase.ESPERA, atualizado = T0.plusSeconds(60))
        assertEquals("O 750 parte às 11:20", titulo(progresso(comLinhaComNome, outra)))
    }

    @Test fun oAvisoDeTransbordoTambemDizOModoAntesDoNomeProprio() {
        // Do 750 para o comboio: a linha a apanhar tem nome próprio.
        val inverso = itinerario(
            aPe("Casa", "Algés", T0, 140.0),
            autocarro("750", listOf("Algés", "Mercado", "Oeiras"), T0.plusSeconds(180)),
            aPe("Oeiras", "Oeiras (Estação)", T0.plusSeconds(900), 90.0),
            autocarro("Linha de Cascais", listOf("Oeiras (Estação)", "Paço de Arcos", "Cascais"), T0.plusSeconds(1200)).copy(modo = Modo.COMBOIO),
        )
        assertEquals(
            "Sai agora — Oeiras e apanha o comboio da Linha de Cascais",
            titulo(aviso(inverso, Evento.SAI_AGORA, aBordo(inverso, 1, k = 2))),
        )
    }

    @Test fun avisoSaiAgora() {
        val n = aviso(simples, Evento.SAI_AGORA, aBordo(simples, 1, k = 4))
        assertEquals("Sai agora — Oriente", titulo(n))
        assertEquals("avisos", n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
    }

    @Test fun numTransbordoDizOQueApanhar() {
        // Entre os dois autocarros há uma etapa a pé; o aviso continua a dizer qual é o seguinte.
        assertEquals("Sai agora — Oriente e apanha o 28", titulo(aviso(comTransbordo, Evento.SAI_AGORA, aBordo(comTransbordo, 1, k = 4))))
        assertEquals(
            "Prepara-te — sais daqui a 2 paragens, em Oriente e apanha o 28",
            titulo(aviso(comTransbordo, Evento.PREPARA_TE, aBordo(comTransbordo, 1, k = 3))),
        )
        // Na última etapa em veículo já não há nada a apanhar.
        assertEquals("Sai agora — Sacavém", titulo(aviso(comTransbordo, Evento.SAI_AGORA, aBordo(comTransbordo, 3, k = 1))))
    }

    @Test fun pelaHoraOAvisoPedeParaConfirmar() {
        assertEquals(
            "Sai agora — Oriente (posição estimada, confirma a paragem)",
            titulo(aviso(simples, Evento.SAI_AGORA, aBordo(simples, 1, k = 4, Origem.HORARIO))),
        )
        assertEquals(
            "Sai agora — Oriente e apanha o 28 (posição estimada, confirma a paragem)",
            titulo(aviso(comTransbordo, Evento.SAI_AGORA, aBordo(comTransbordo, 1, k = 4, Origem.HORARIO))),
        )
    }

    @Test fun seAEtapaAcabouNoMesmoPassoOAvisoContinuaAFalarDela() {
        // "Já saí" (ou o fim da etapa pelo horário) dá Sai agora e Etapa seguinte de seguida: o estado já é o da etapa a pé.
        val depois = Viagem(comTransbordo).inicio(T0).copy(etapa = 2, progresso = 90.0)
        assertEquals("Sai agora — Oriente e apanha o 28", titulo(aviso(comTransbordo, Evento.SAI_AGORA, depois)))
        assertEquals(
            "Prepara-te — sais daqui a 1 paragem, em Oriente e apanha o 28",
            titulo(aviso(comTransbordo, Evento.PREPARA_TE, depois)),
        )
        // Na última etapa em veículo o estado fica "terminada", ou a pé até ao destino.
        val fim = aBordo(comTransbordo, 3, k = 1).copy(fase = Fase.TERMINADA, terminada = true)
        assertEquals("Sai agora — Sacavém", titulo(aviso(comTransbordo, Evento.SAI_AGORA, fim)))
        val aPeAteAoFim = Viagem(simples).inicio(T0).copy(etapa = 2, progresso = 90.0)
        assertEquals("Sai agora — Oriente", titulo(aviso(simples, Evento.SAI_AGORA, aPeAteAoFim)))
    }

    @Test fun chegouEEtapaSeguinteTambemTemNotificacao() {
        val fim = aBordo(simples, 1, k = 4).copy(etapa = 2, fase = Fase.TERMINADA, terminada = true)
        assertEquals("Chegaste a Trabalho", titulo(aviso(simples, Evento.CHEGOU, fim)))
        val seguinte = Viagem(simples).inicio(T0).copy(etapa = 2, progresso = 90.0)
        assertEquals("A pé até Trabalho · 90 m", titulo(aviso(simples, Evento.ETAPA_SEGUINTE, seguinte)))
    }
}
