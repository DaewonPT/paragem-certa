package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.time.Instant

class TransitousTest {
    private val agora = Instant.parse("2026-10-27T01:50:00Z")

    @Test fun chegadasDaParagemPedida() {
        val c = TransitousParser.chegadas(fixture("transitous-stoptimes.json"), "pt-Carris_3803", agora)
        assertEquals(1, c.size)
        assertEquals("201", c[0].linhaNome)
        assertEquals(Instant.parse("2026-10-27T01:54:00Z"), c[0].programada)
        assertNull(c[0].prevista)
        assertEquals(Modo.AUTOCARRO, c[0].modo)
        assertEquals(Fonte.TRANSITOUS, c[0].fonte)
        assertEquals(false, c[0].acessivel)
    }

    @Test fun outraParagemNaoAparece() {
        assertTrue(TransitousParser.chegadas(fixture("transitous-stoptimes.json"), "pt-Comboios-de-Portugal(CP)_94_69005", agora).isEmpty())
    }

    @Test fun canceladasSaoDescartadas() {
        val cancelada = fixture("transitous-stoptimes.json").replaceFirst("\"cancelled\": false", "\"cancelled\": true")
        assertTrue(TransitousParser.chegadas(cancelada, "pt-Carris_3803", agora).isEmpty())
    }

    /** Muda o campo [campo] do 1.º item de stopTimes (não o do place) para true. */
    private fun cancelarNoItem(campo: String): String {
        val json = fixture("transitous-stoptimes.json")
        val inicioItem = json.indexOf("\"pickupDropoffType\"")
        val i = json.indexOf("\"$campo\": false", inicioItem)
        return json.substring(0, i) + "\"$campo\": true" + json.substring(i + "\"$campo\": false".length)
    }

    @Test fun partidaCanceladaEDescartada() {
        assertTrue(TransitousParser.chegadas(cancelarNoItem("cancelled"), "pt-Carris_3803", agora).isEmpty())
    }

    @Test fun viagemCanceladaEDescartada() {
        assertTrue(TransitousParser.chegadas(cancelarNoItem("tripCancelled"), "pt-Carris_3803", agora).isEmpty())
    }

    @Test fun nomeDoOperador() {
        assertEquals("CP", operadorDe("pt-Comboios-de-Portugal(CP)_94_69005"))
        assertEquals("Transtejo", operadorDe("pt-Transtejo-Soflusa_5"))
        assertEquals("Metro de Lisboa", operadorDe("pt-Metro-Lisboa_CS"))
        assertEquals("Carris", operadorDe("pt-Carris_3803"))
        assertNull(operadorDe("eu-FlixBus_123"))
    }

    @Test fun tempoRealUsaAHoraPrevista() {
        val aoVivo = fixture("transitous-stoptimes.json")
            .replaceFirst("\"realTime\": false", "\"realTime\": true")
            .replaceFirst("\"departure\": \"2026-10-27T01:54:00Z\"", "\"departure\": \"2026-10-27T01:57:00Z\"")
        val c = TransitousParser.chegadas(aoVivo, "pt-Carris_3803", agora).single()
        assertEquals(Instant.parse("2026-10-27T01:57:00Z"), c.prevista)
    }

    @Test fun mapaEscondeACarrisMetropolitana() {
        val p = TransitousParser.paragensNoMapa(fixture("transitous-map-stops.json"))
        assertEquals(3, p.size)
        assertTrue(p.none { it.ref.id.startsWith("pt-Carris-Metropolitana_") })
    }

    @Test fun pesquisaSoEmPortugalESemCarrisMetropolitana() {
        val p = TransitousParser.pesquisa(fixture("transitous-geocode.json"))
        assertEquals(listOf("pt-Comboios-de-Portugal(CP)_94_69005", "pt-Transtejo-Soflusa_5"), p.map { it.ref.id })
    }

    @Test fun pedeOsEnderecosCertos() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-stoptimes.json")) })
        Transitous(http).chegadas("pt-Comboios-de-Portugal(CP)_94_69005", agora)
        assertEquals(
            // radius=0 + exactRadius=true: só a própria paragem (sem isto o Metro do Cais do Sodré trazia 1 em 10 partidas).
            "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Comboios-de-Portugal%28CP%29_94_69005&n=30&radius=0&exactRadius=true",
            http.pedidos.single(),
        )
    }
}
