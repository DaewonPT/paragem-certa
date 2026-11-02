package pt.pixellusitano.paragemcerta.core.rotas

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.fontes.transitous.urlPlano
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant

class RotasTest {
    @Test fun rotasDaResposta() {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        assertEquals(3, rotas.size)
        val r = rotas[0]
        assertEquals(3, r.etapas.size)
        assertTrue(r.etapas[0].aPe)
        assertEquals(121.0, r.etapas[0].distancia)
        assertEquals(Modo.AUTOCARRO, r.etapas[1].modo)
        assertEquals("210", r.etapas[1].linhaNome)
        assertTrue(r.etapas[1].desenho.size > 10)
        assertTrue(r.etapas[2].aPe)
        assertEquals(Duration.ofMinutes(30), r.duracao)
        assertEquals(0, r.transbordos)
    }

    @Test fun paragensDaEtapa() {
        val etapas = TransitousParser.rotas(fixture("transitous-plan.json"))[0].etapas
        val bus = etapas[1]
        assertNotNull(bus.idViagem)
        assertEquals(bus.paragensIntermedias + 2, bus.paragens.size)
        assertEquals("pt-Carris_3804", bus.paragens.first().id)
        assertEquals(bus.partida, bus.paragens.first().hora)
        assertEquals("pt-Carris_7805", bus.paragens.last().id)
        assertEquals(bus.chegada, bus.paragens.last().hora)
        assertTrue(etapas.filter { it.aPe }.all { it.paragens.isEmpty() && it.idViagem == null })
    }

    @Test fun itinerarioIdaEVolta() {
        TransitousParser.rotas(fixture("transitous-plan.json")).forEach {
            assertEquals(it, Json.decodeFromString<Itinerario>(Json.encodeToString(it)))
        }
    }

    @Test fun cursoresDaResposta() {
        val plano = TransitousParser.plano(fixture("transitous-plan.json"))
        assertEquals("EARLIER|1793498820", plano.anterior)
        assertEquals("LATER|1793503740", plano.seguinte)
        assertEquals(TransitousParser.rotas(fixture("transitous-plan.json")), plano.itinerarios)
    }

    @Test fun semCursoresNaResposta() {
        val json = fixture("transitous-plan.json").replace("previousPageCursor", "semAnterior").replace("nextPageCursor", "semSeguinte")
        val plano = TransitousParser.plano(json)
        assertNull(plano.anterior)
        assertNull(plano.seguinte)
        assertEquals(3, plano.itinerarios.size)
    }

    @Test fun polilinhaConhecida() {
        // Exemplo da documentação pública do formato de polilinha codificada.
        val p = descodificarPolilinha("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        val esperado = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)
        assertEquals(3, p.size)
        p.zip(esperado).forEach { (a, b) ->
            assertEquals(b.first, a.first, 1e-5)
            assertEquals(b.second, a.second, 1e-5)
        }
    }

    @Test fun etapaSemDesenhoNemModoConhecido() {
        val json = fixture("transitous-plan.json")
            .replace("\"legGeometry\"", "\"semDesenho\"") // campo desconhecido: ignorado, como se faltasse
            .replaceFirst("\"mode\": \"BUS\"", "\"mode\": \"AIRPLANE\"")
        val etapa = TransitousParser.rotas(json)[0].etapas[1]
        assertTrue(etapa.desenho.isEmpty())
        assertEquals(Modo.OUTRO, etapa.modo)
        assertFalse(etapa.aPe)
    }

    private val t0 = Instant.parse("2026-11-01T08:00:00Z")
    private val l = Lugar("x", 38.7, -9.1, TipoLugar.LOCAL)
    private fun etapa(aPe: Boolean, de: Long, ate: Long) = Etapa(
        aPe, if (aPe) Modo.OUTRO else Modo.AUTOCARRO, if (aPe) null else "728", null, null, l, l,
        t0.plusSeconds(de * 60), t0.plusSeconds(ate * 60), t0.plusSeconds(de * 60), false, 0, emptyList(), null,
    )
    private fun itinerario(transbordos: Int, vararg e: Etapa) = Itinerario(e.toList(), e.first().partida, e.last().chegada, transbordos)

    @Test fun etiquetasPorRegras() {
        val a = itinerario(1, etapa(true, 0, 5), etapa(false, 5, 12), etapa(true, 12, 14), etapa(false, 16, 22), etapa(true, 22, 25))
        val b = itinerario(0, etapa(true, 0, 2), etapa(false, 2, 28), etapa(true, 28, 30))
        val c = itinerario(
            2, etapa(true, 0, 2), etapa(false, 2, 10), etapa(true, 10, 12), etapa(false, 18, 25), etapa(true, 25, 27),
            etapa(false, 33, 38), etapa(true, 38, 40),
        )
        // A mais rápida é também a que chega primeiro: a etiqueta "chegada mais cedo" (0.4) junta-se às de antes.
        assertEquals(
            listOf(
                setOf(Etiqueta.MAIS_RAPIDA, Etiqueta.CHEGADA_MAIS_CEDO, Etiqueta.TRANSBORDO_APERTADO),
                setOf(Etiqueta.MENOS_A_PE, Etiqueta.DIRETA),
                emptySet(),
            ),
            etiquetas(listOf(a, b, c)),
        )
    }

    @Test fun etiquetasNovas() {
        // x parte primeiro e chega primeiro; y parte mais tarde e é mais rápida (25 min contra 30), mas chega depois.
        val x = itinerario(0, etapa(true, 0, 6), etapa(false, 6, 24), etapa(true, 24, 30))
        val y = itinerario(0, etapa(true, 10, 12), etapa(false, 12, 33), etapa(true, 33, 35))
        val (ex, ey) = etiquetas(listOf(x, y))
        assertTrue(Etiqueta.CHEGADA_MAIS_CEDO in ex)
        assertFalse(Etiqueta.CHEGADA_MAIS_CEDO in ey)
        assertTrue(Etiqueta.MAIS_RAPIDA in ey)
        assertFalse(Etiqueta.MAIS_RAPIDA in ex)
        // Com um só itinerário não há "mais cedo" que outro.
        assertFalse(Etiqueta.CHEGADA_MAIS_CEDO in etiquetas(listOf(x)).single())
        // Duas a chegar à mesma hora: as duas levam a etiqueta, como na "mais rápida".
        val z = itinerario(0, etapa(true, 5, 7), etapa(false, 7, 28), etapa(true, 28, 30))
        assertEquals(listOf(true, false, true), etiquetas(listOf(x, y, z)).map { Etiqueta.CHEGADA_MAIS_CEDO in it })
    }

    @Test fun caminhadaLongaAPartirDeDozeMinutos() {
        val doze = itinerario(0, etapa(true, 0, 6), etapa(false, 6, 24), etapa(true, 24, 30)) // 6 + 6 a pé
        val onze = itinerario(0, etapa(true, 0, 6), etapa(false, 6, 24), etapa(true, 24, 29)) // 6 + 5 a pé
        assertTrue(Etiqueta.CAMINHADA_LONGA in etiquetas(listOf(doze)).single())
        assertFalse(Etiqueta.CAMINHADA_LONGA in etiquetas(listOf(onze)).single())
    }

    @Test fun etiquetasPelaOrdemDeMostrar() {
        val x = itinerario(0, etapa(true, 0, 6), etapa(false, 6, 24), etapa(true, 24, 30))
        val y = itinerario(0, etapa(true, 0, 2), etapa(false, 2, 40), etapa(true, 40, 42))
        assertEquals(
            listOf(Etiqueta.MAIS_RAPIDA, Etiqueta.CHEGADA_MAIS_CEDO, Etiqueta.DIRETA, Etiqueta.CAMINHADA_LONGA),
            etiquetas(listOf(x, y))[0].toList(),
        )
        assertEquals(
            listOf(
                Etiqueta.MAIS_RAPIDA, Etiqueta.CHEGADA_MAIS_CEDO, Etiqueta.MENOS_A_PE,
                Etiqueta.DIRETA, Etiqueta.CAMINHADA_LONGA, Etiqueta.TRANSBORDO_APERTADO,
            ),
            Etiqueta.entries,
        )
    }

    // Cada ordem dá uma lista diferente destas três.
    private val p = itinerario(0, etapa(true, 0, 10), etapa(false, 10, 30)) // dura 30, chega aos 30, 10 a pé, 0 transbordos
    private val q = itinerario(2, etapa(true, 10, 14), etapa(false, 14, 35)) // dura 25, chega aos 35, 4 a pé, 2 transbordos
    private val r = itinerario(1, etapa(true, 5, 7), etapa(false, 7, 45)) // dura 40, chega aos 45, 2 a pé, 1 transbordo

    @Test fun ordenarPorCadaOrdem() {
        val lista = listOf(p, q, r)
        assertEquals(listOf(q, p, r), ordenar(lista, Ordem.MAIS_RAPIDA))
        assertEquals(listOf(p, q, r), ordenar(lista, Ordem.CHEGADA_MAIS_CEDO))
        assertEquals(listOf(r, q, p), ordenar(lista, Ordem.MENOS_A_PE))
        assertEquals(listOf(p, r, q), ordenar(lista, Ordem.MENOS_TRANSBORDOS))
        assertEquals(emptyList<Itinerario>(), ordenar(emptyList(), Ordem.MENOS_A_PE))
    }

    @Test fun ordenarDesempataPelaChegadaEMantemAOrdemSeContinuaEmpatado() {
        // Os dois sem transbordos: o que chega aos 20 passa à frente do que chega aos 30, qualquer que seja a ordem de entrada.
        val t = itinerario(0, etapa(false, 0, 20))
        assertEquals(listOf(t, p), ordenar(listOf(p, t), Ordem.MENOS_TRANSBORDOS))
        // Mesma chegada e o mesmo a pé, só os transbordos mudam: fica como veio.
        val u = itinerario(1, etapa(false, 0, 20))
        assertEquals(listOf(u, t), ordenar(listOf(u, t), Ordem.MENOS_A_PE))
        assertEquals(listOf(t, u), ordenar(listOf(t, u), Ordem.MENOS_A_PE))
    }

    private fun chegada(linha: String, destino: String, programada: Long, prevista: Long? = null, fonte: Fonte = Fonte.TRANSITOUS) = Chegada(
        fonte, "id-$linha", linha, destino, t0.plusSeconds(programada * 60), prevista?.let { t0.plusSeconds(it * 60) },
        null, null, Modo.AUTOCARRO, null, null,
    )

    @Test fun proximasPartidasDaMesmaLinhaESentido() {
        // A etapa parte (no horário) aos 10 min, para o Oriente, na linha "15E"; são 5 min depois das 08:00.
        val etapa = Etapa(
            false, Modo.AUTOCARRO, "15E", "id-15E", "Oriente", l, l, t0.plusSeconds(600), t0.plusSeconds(1800), t0.plusSeconds(600),
            true, 3, emptyList(), null,
        )
        val agora = t0.plusSeconds(300)
        val limite = chegada("15e", "Oriente", 9) // 60 s antes da partida planeada: ainda conta (e a linha sem diferença de maiúsculas)
        val atrasada = chegada("15E", "Oriente", 10, prevista = 27) // programada aos 10, mas só chega aos 27
        val daCm = chegada("15E", "Cais do Sodré", 25, fonte = Fonte.CM) // a CM não diz o sentido como o Transitous: não se compara
        val chegadas = listOf(
            chegada("15E", "Oriente", 40), // a quarta: fica de fora
            atrasada,
            chegada("728", "Oriente", 11), // outra linha
            chegada("15E", "Cais do Sodré", 12), // outro sentido, do Transitous
            chegada("15E", "Oriente", 8), // aos 8 min: antes da partida planeada menos 60 s
            chegada("15E", "Oriente", 9, prevista = 3), // já passou (a hora prevista foi há mais de 30 s)
            daCm,
            limite,
        )
        // Pela hora prevista (ou, sem ela, a programada): 9, 25 (CM), 27 (a atrasada), e a das 40 fica fora por serem só 3.
        assertEquals(listOf(limite, daCm, atrasada), proximasPartidas(chegadas, etapa, agora))
        assertEquals(listOf(limite, daCm), proximasPartidas(chegadas, etapa, agora, n = 2))
        assertTrue(proximasPartidas(chegadas, etapa.copy(linhaNome = null, aPe = true), agora).isEmpty())
    }

    @Test fun proximasPartidasSemSentidoNaEtapaNaoFiltramPorSentido() {
        val etapa = Etapa(
            false, Modo.AUTOCARRO, "15E", "id-15E", null, l, l, t0.plusSeconds(600), t0.plusSeconds(1800), t0.plusSeconds(600),
            true, 3, emptyList(), null,
        )
        val c = chegada("15E", "Cais do Sodré", 12)
        assertEquals(listOf(c), proximasPartidas(listOf(c), etapa, t0))
    }
    private val de = Lugar("Cais do Sodré", 38.7058, -9.1431, TipoLugar.PARAGEM)
    private val para = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)

    @Test fun urlComFiltros() {
        val url = urlPlano(
            "https://api.transitous.org", de, para, t0, chegarAs = true,
            Filtros(menosAPe = true, acessivel = true, modos = setOf(Modo.METRO, Modo.AUTOCARRO)),
        )
        listOf(
            "fromPlace=38.7058,-9.1431", "toPlace=38.7678,-9.099", "time=2026-11-01T08:00:00Z", "arriveBy=true",
            "maxPreTransitTime=600", "maxPostTransitTime=600", "pedestrianProfile=WHEELCHAIR", "transitModes=BUS,SUBWAY",
        ).forEach { assertTrue("falta $it em $url", url.contains(it)) }
    }

    @Test fun urlSemFiltros() {
        val url = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros())
        listOf("maxTransfers", "pedestrianProfile", "pedestrianSpeed", "transitModes", "pageCursor", "directModes").forEach { assertFalse("$it em $url", url.contains(it)) }
        // O tempo máximo a pé vai sempre (a omissão da app, 15 min, e a do servidor, 900 s, coincidem, mas é a app que decide).
        listOf("arriveBy=false", "maxPreTransitTime=900", "maxPostTransitTime=900", "numItineraries=5", "maxItineraries=8", "language=pt")
            .forEach { assertTrue("falta $it em $url", url.contains(it)) }
    }

    @Test fun urlComPreferencias() {
        val url = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(maxAPeMin = 10, ritmo = Ritmo.LENTO))
        listOf("maxPreTransitTime=600", "maxPostTransitTime=600", "pedestrianSpeed=0.9", "language=pt", "maxItineraries=8")
            .forEach { assertTrue("falta $it em $url", url.contains(it)) }
        val rapido = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(maxAPeMin = 5, ritmo = Ritmo.RAPIDO))
        assertTrue(rapido, rapido.contains("maxPreTransitTime=300&maxPostTransitTime=300") && rapido.contains("pedestrianSpeed=1.6"))
        // O ritmo normal é o do servidor: não se manda.
        assertFalse(urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(ritmo = Ritmo.NORMAL)).contains("pedestrianSpeed"))
        // A ordem é só da app: não vai no pedido.
        assertFalse(urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(ordem = Ordem.MENOS_A_PE)).contains("MENOS_A_PE"))
    }

    @Test fun menosAPeNuncaPassaDosDezMinutos() {
        fun url(maxAPeMin: Int) = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(menosAPe = true, maxAPeMin = maxAPeMin))
        assertTrue(url(20).contains("maxPreTransitTime=600&maxPostTransitTime=600"))
        assertTrue(url(15).contains("maxPreTransitTime=600&maxPostTransitTime=600"))
        // Abaixo dos 10, vale o valor das preferências (o menor dos dois).
        assertTrue(url(5).contains("maxPreTransitTime=300&maxPostTransitTime=300"))
    }

    @Test fun paginaUsaOCursor() {
        val url = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(), cursor = "LATER|1793503740")
        assertTrue("falta o cursor em $url", url.contains("&pageCursor=LATER%7C1793503740"))
        assertTrue("falta directModes vazio em $url", url.endsWith("&directModes="))
    }

    @Test fun planearPedeAPaginaEDevolveOPlano() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-plan.json")) })
        val r = Transitous(http).planear(de, para, t0, false, Filtros(), cursor = "EARLIER|1793498820")
        val plano = (r as Resultado.Ok).valor
        assertEquals(3, plano.itinerarios.size)
        assertEquals("LATER|1793503740", plano.seguinte)
        assertTrue(http.pedidos.single().contains("&pageCursor=EARLIER%7C1793498820&directModes="))
        // Sem cursor é o pedido de sempre.
        val sem = HttpFalso({ Resposta(200, fixture("transitous-plan.json")) })
        Transitous(sem).planear(de, para, t0, false, Filtros())
        assertFalse(sem.pedidos.single().contains("pageCursor"))
    }

    @Test fun comboioSemMetro() {
        val url = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(modos = setOf(Modo.COMBOIO)))
        assertTrue("falta os modos de comboio em $url", url.contains("transitModes=HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_FAST_RAIL,REGIONAL_RAIL,SUBURBAN"))
        assertFalse("metro em $url", url.contains("SUBWAY"))
    }

    @Test fun todosOsModosSemFiltro() {
        val todos = setOf(Modo.AUTOCARRO, Modo.ELETRICO, Modo.METRO, Modo.COMBOIO, Modo.BARCO)
        val url = urlPlano("https://api.transitous.org", de, para, t0, chegarAs = false, Filtros(modos = todos))
        assertFalse("transitModes em $url", url.contains("transitModes"))
    }

    @Test fun horaEmSegundosInteiros() {
        val url = urlPlano("https://api.transitous.org", de, para, t0.plusNanos(123_456_789), chegarAs = false, Filtros())
        assertTrue("hora com fração em $url", url.contains("time=2026-11-01T08:00:00Z&"))
    }
}
