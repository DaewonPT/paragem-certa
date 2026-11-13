package pt.pixellusitano.paragemcerta.core.fontes.cm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
        val p = CmParser.paragensComLinhas(fixture("cm-stops.json")).map { it.first }.first { it.ref.id == "060419" }
        assertEquals("Cais do Sodré (Terminal)", p.nome)
        assertEquals(false, p.acessivel)
    }

    @Test fun paragensComLinhas() {
        assertEquals(
            mapOf("060419" to listOf("3708"), "060451" to listOf("3708"), "062000" to emptyList()),
            CmParser.paragensComLinhas(fixture("cm-stops.json")).associate { (p, l) -> p.ref.id to l },
        )
    }

    @Test fun linhasComCorOficial() {
        val (l, padroes) = CmParser.linhasComPadroes(fixture("cm-lines.json")).first { it.first.id == "3708" }
        assertEquals("#C61D23", l.cor)
        assertEquals("Costa da Caparica (Terminal) - Lisboa (C. Sodré)", l.nomeLongo)
        assertEquals(listOf("3708_0_2", "3708_0_1"), padroes)
    }

    @Test fun veiculos() {
        val v = CmParser.veiculos(fixture("cm-vehicles.json")).first()
        // O número repete-se entre operadores ([BNA17]2208 e [YA15B]2208): o id fica com o prefixo.
        assertEquals("[YA15B]2261", v.id)
        assertEquals("3708", v.linhaId)
        assertEquals(Instant.ofEpochMilli(1793065970000), v.ultimoSinal)
        assertEquals("[PL3CL][YA15B]3708_0_1_2530_2559_0_ESC_DU", v.viagem) // inteiro: é igual ao das chegadas
        assertEquals("020379", v.paragem)
        assertTrue(v.aChegar) // INCOMING_AT
    }

    @Test fun veiculoAChegarSoAChegarOuParado() {
        fun estado(status: String?) = CmParser.veiculos(
            """[{"id":"[YA15B]1","line_id":"3708","lat":38.7,"lon":-9.1,"timestamp":1793065970000,"stop_id":"[X]020379","current_status":${status?.let { "\"$it\"" }}}]""",
        ).single()
        assertTrue(estado("STOPPED_AT").aChegar)
        assertFalse(estado("IN_TRANSIT_TO").aChegar)
        assertFalse(estado(null).aChegar)
        assertEquals("020379", estado(null).paragem)
        assertNull(estado(null).viagem)
    }

    @Test fun alertasAtivos() {
        val a = CmParser.alertas(fixture("cm-alerts.json"), Instant.parse("2026-10-27T12:00:00Z"))
        val desvio = a.first { "1625" in it.linhaIds }
        assertEquals(Gravidade.AVISO, desvio.gravidade)
        assertEquals(setOf("1625"), desvio.linhaIds)
        assertTrue(desvio.titulo.startsWith("Cascais | 1625"))
        assertTrue(CmParser.alertas(fixture("cm-alerts.json"), Instant.parse("2026-10-01T12:00:00Z")).isEmpty())
    }

    @Test fun datasDoPeriodoQueDecorre() {
        val json = """[{"active_period":[{"start":1793491200,"end":1793663999},{"start":1794096000,"end":1794268799}]""" +
            ""","header_text":{"translation":[{"language":"pt","text":"Obras"}]}}]"""
        val a = CmParser.alertas(json, Instant.parse("2026-11-08T12:00:00Z")).single()
        assertEquals(Instant.parse("2026-11-08T00:00:00Z"), a.inicio)
        assertEquals(Instant.parse("2026-11-09T23:59:59Z"), a.fim)
        assertEquals("Obras|1793491200", a.id) // o id continua no 1.º período: não volta a notificar
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

    @Test fun chegadasCmNoTerminalSoDoSentido() {
        // No terminal da Caparica a mesma paragem tem chegadas (…_1, que acabam aqui) e partidas (…_2): o sentido sai do pattern_id.
        val c = CmParser.chegadas(fixture("cm-arrivals-060419.json"), manha)
        assertEquals(listOf(0, 1, 0), c.map { it.sentido })
        assertEquals(listOf("3708_0_1", "3708_0_2", "3708_0_1"), c.map { it.padrao })
        assertEquals("Costa da Caparica (Terminal)", c.single { it.sentido == 1 }.destino)
        assertEquals(2, c.count { it.sentido == 0 })
    }

    @Test fun padraoCmComHorariosDoDia() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("cm-pattern-completo.json")) })
        val cm = CarrisMetropolitana(http)
        val p = (cm.padraoCompleto("3708_0_1") as Resultado.Ok).valor
        assertEquals("https://api.carrismetropolitana.pt/v2/patterns/3708_0_1", http.pedidos.single())
        assertEquals("3708_0_1", p.id)
        assertEquals(0, p.sentido)
        assertEquals("Lisboa (C. Sodré)", p.destino)
        assertEquals(listOf("020735", "020002", "060419"), p.paragens)
        assertEquals("[2QDAD]3708_0_1", p.formaId) // o id da forma leva os "[" e "]" do operador
        assertEquals(3, p.horarios.size)
        // 03/11: os dois primeiros grupos (o 3.º só corre a 07/11); a viagem das 24:15 chega às 25:25, ou seja, à 01:25.
        assertEquals(listOf(LocalTime.of(5, 25), LocalTime.of(1, 25)), cm.horarioCmDoDia(p, "060419", LocalDate.of(2026, 11, 3)))
        assertEquals(listOf(LocalTime.of(4, 15), LocalTime.of(0, 15)), cm.horarioCmDoDia(p, "020735", LocalDate.of(2026, 11, 3)))
        // 07/11: o 1.º e o 3.º; o 27:55 é 03:55 da manhã seguinte e fica no fim do dia de serviço, não no princípio.
        assertEquals(listOf(LocalTime.of(5, 25), LocalTime.of(3, 55)), cm.horarioCmDoDia(p, "060419", LocalDate.of(2026, 11, 7)))
        // Nenhum grupo corre a 05/11, e uma paragem que não é do percurso não tem horas.
        assertTrue(cm.horarioCmDoDia(p, "060419", LocalDate.of(2026, 11, 5)).isEmpty())
        assertTrue(cm.horarioCmDoDia(p, "999999", LocalDate.of(2026, 11, 3)).isEmpty())
    }

    @Test fun horaDepoisDasVinteQuatroPassaParaAManha() {
        val p = CmParser.padraoCompleto(
            """[{"id":"1_0_2","direction_id":1,"headsign":"X","path":[{"stop_id":"[A]010","stop_sequence":1}],
              "trips":[{"valid_on":["20261103"],"schedule":[{"stop_id":"[A]010","arrival_time":"25:10:00"}]}]}]""",
        )
        assertEquals(1, p.sentido)
        assertNull(p.formaId)
        assertEquals(mapOf("010" to LocalTime.of(1, 10)), p.horarios.single().porParagem)
        assertEquals(setOf("20261103"), p.horarios.single().dias)
    }

    @Test fun padraoCmSemDadosDaFalha() = runTest {
        assertEquals(Resultado.Falha(404, limite = false), CarrisMetropolitana(HttpFalso({ Resposta(404, "") })).padraoCompleto("0000_0_1"))
        assertTrue(CarrisMetropolitana(HttpFalso({ Resposta(200, "{}") })).padraoCompleto("3708_0_1") is Resultado.Falha)
    }

    @Test fun formaCmDoGeojson() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("cm-shape.json")) })
        val forma = (CarrisMetropolitana(http).forma("[2QDAD]3708_0_1") as Resultado.Ok).valor
        // O id tem "[" e "]": vai codificado.
        assertEquals("https://api.carrismetropolitana.pt/v2/shapes/%5B2QDAD%5D3708_0_1", http.pedidos.single())
        assertEquals(6, forma.size)
        // O GeoJSON dá [lon, lat]; a app usa (lat, lon).
        assertEquals(38.64061 to -9.230475, forma.first())
    }

    @Test fun paragemSemServicoDaListaVazia() = runTest {
        val cm = CarrisMetropolitana(HttpFalso({ Resposta(404, "[]") }))
        assertEquals(Resultado.Ok(emptyList<Chegada>()), cm.chegadas("120161", manha))
    }

    @Test fun falhasDoServidorNasChegadasContinuamFalhas() = runTest {
        assertEquals(Resultado.Falha(503, limite = true), CarrisMetropolitana(HttpFalso({ Resposta(503, "") })).chegadas("120161", manha))
        assertEquals(Resultado.Falha(500, limite = false), CarrisMetropolitana(HttpFalso({ Resposta(500, "") })).chegadas("120161", manha))
    }
}
