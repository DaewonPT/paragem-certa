package pt.pixellusitano.paragemcerta.core.fontes.cm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant

class CarrisMetropolitanaTest {
    // As respostas gravadas são do dia de serviço 26/10/2026.
    private val manha = Instant.parse("2026-10-26T05:00:00Z")

    @Test fun limpaPrefixos() {
        assertEquals("3708", cmIdLimpo("[YA15B]3708"))
        assertEquals("3708_0_1", cmIdLimpo("[PL3CL][YA15B]3708_0_1"))
        assertEquals("1625", cmLinhaDaRota("[LA77N]1625_0"))
    }

    @Test fun chegadas() {
        val c = CmParser.chegadas(fixture("cm-arrivals-060419.json"), manha)
        assertEquals(3, c.size)
        assertEquals("3708", c[0].linhaId)
        assertEquals("2261", c[0].idVeiculo)
        assertEquals(Duration.ofSeconds(240), Duration.between(c[0].programada, c[0].prevista))
        assertEquals(EstadoAtraso.ATRASADO, estado(c[0]))
        assertEquals("Costa da Caparica (Terminal)", c[1].destino)
        assertNull(c[1].prevista)
    }

    @Test fun chegadasPassadasDesaparecem() {
        assertTrue(CmParser.chegadas(fixture("cm-arrivals-060419.json"), Instant.parse("2026-10-26T06:35:00Z")).isEmpty())
    }

    @Test fun paragens() {
        val p = CmParser.paragens(fixture("cm-stops.json")).first { it.ref.id == "060419" }
        assertEquals("Cais do Sodré (Terminal)", p.nome)
        assertEquals(false, p.acessivel)
    }

    @Test fun linhasComCorOficial() {
        val l = CmParser.linhas(fixture("cm-lines.json")).first { it.id == "3708" }
        assertEquals("#C61D23", l.cor)
        assertEquals("Costa da Caparica (Terminal) - Lisboa (C. Sodré)", l.nomeLongo)
    }

    @Test fun veiculos() {
        val v = CmParser.veiculos(fixture("cm-vehicles.json")).first()
        assertEquals("2261", v.id)
        assertEquals("3708", v.linhaId)
        assertEquals(Instant.ofEpochMilli(1793065970000), v.ultimoSinal)
    }

    @Test fun alertasAtivos() {
        val a = CmParser.alertas(fixture("cm-alerts.json"), Instant.parse("2026-10-27T12:00:00Z"))
        val desvio = a.first { "1625" in it.linhaIds }
        assertEquals(Gravidade.AVISO, desvio.gravidade)
        assertEquals(setOf("1625"), desvio.linhaIds)
        assertTrue(desvio.titulo.startsWith("Cascais | 1625"))
        assertTrue(CmParser.alertas(fixture("cm-alerts.json"), Instant.parse("2026-10-01T12:00:00Z")).isEmpty())
    }

    @Test fun gravidade() {
        assertEquals(Gravidade.GRAVE, gravidadeDe("NO_SERVICE", null))
        assertEquals(Gravidade.INFO, gravidadeDe("DETOUR", "INFO"))
        assertEquals(Gravidade.AVISO, gravidadeDe("SIGNIFICANT_DELAYS", null))
        assertEquals(Gravidade.INFO, gravidadeDe(null, null))
    }

    @Test fun limiteDePedidos() = runTest {
        val cm = CarrisMetropolitana(HttpFalso({ Resposta(429, "") }))
        assertEquals(Resultado.Falha(429, limite = true), cm.chegadas("060419", manha))
    }

    @Test fun pedeOEnderecoCerto() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("cm-arrivals-060419.json")) })
        val r = CarrisMetropolitana(http).chegadas("060419", manha)
        assertEquals("https://api.carrismetropolitana.pt/v2/arrivals/by_stop/060419", http.pedidos.single())
        assertEquals(3, (r as Resultado.Ok).valor.size)
    }

    @Test fun percursoOrdenadoPorSequencia() {
        assertEquals(listOf("020735", "020002", "060419"), CmParser.padrao(fixture("cm-pattern-3708_0_1.json")))
    }
}
