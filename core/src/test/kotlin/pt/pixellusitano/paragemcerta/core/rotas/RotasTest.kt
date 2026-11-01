package pt.pixellusitano.paragemcerta.core.rotas

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.fontes.transitous.urlPlano
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
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
        assertEquals(
            listOf(setOf(Etiqueta.MAIS_RAPIDA, Etiqueta.TRANSBORDO_APERTADO), setOf(Etiqueta.MENOS_A_PE, Etiqueta.DIRETA), emptySet()),
            etiquetas(listOf(a, b, c)),
        )
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
        listOf("maxPreTransitTime", "maxTransfers", "pedestrianProfile", "transitModes").forEach { assertFalse(url.contains(it)) }
        assertTrue(url.contains("arriveBy=false"))
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
