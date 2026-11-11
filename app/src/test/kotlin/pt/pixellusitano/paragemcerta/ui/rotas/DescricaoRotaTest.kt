package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.fixture
import java.time.Instant

class DescricaoRotaTest {
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    @Test fun descricaoDaPrimeiraRota() {
        assertEquals(
            "Rota de 30 min, das 02:28 às 02:58. A pé 2 min, autocarro 210, a pé 2 min. 4 min a pé, sem transbordos. " +
                "Sai de Cais Sodré às 02:30, hora do horário, sem tempo real. Etiquetas: mais rápida, direta. Aviso numa linha.",
            descricaoRota(rota, listOf("mais rápida", "direta"), comAlerta = true, Limites()),
        )
    }

    private val agora = Instant.parse("2026-11-01T11:10:00Z")

    private fun partida(programada: String, prevista: String?) =
        Chegada(Fonte.CM, "1", "210", "Oriente", Instant.parse(programada), prevista?.let(Instant::parse), null, null, Modo.AUTOCARRO, null, null)

    @Test fun descricaoComPartidas() {
        val partidas = listOf(
            partida("2026-11-01T11:12:00Z", "2026-11-01T11:13:00Z"),
            partida("2026-11-01T11:28:00Z", "2026-11-01T11:28:00Z"),
            partida("2026-11-01T11:37:00Z", null),
        )
        assertEquals(
            "Rota de 30 min, das 02:28 às 02:58. A pé 2 min, autocarro 210, a pé 2 min. 4 min a pé, sem transbordos. " +
                "Sai de Cais Sodré às 02:30, hora do horário, sem tempo real. " +
                "Próximas partidas: daqui a 3 minutos, 18 minutos, às 11:37. Etiquetas: mais rápida, direta.",
            descricaoRota(rota, listOf("mais rápida", "direta"), comAlerta = false, Limites(), partidas, agora),
        )
    }

    @Test fun partidasAChegarEUmMinuto() {
        val partidas = listOf(
            partida("2026-11-01T11:10:00Z", "2026-11-01T11:10:20Z"),
            partida("2026-11-01T11:11:00Z", "2026-11-01T11:11:30Z"),
            partida("2026-11-01T11:37:00Z", null),
        )
        val d = descricaoRota(rota, emptyList(), comAlerta = false, Limites(), partidas, agora)
        assertTrue(d, d.endsWith("Próximas partidas: a chegar, daqui a 1 minuto, às 11:37."))
    }

    @Test fun semPartidasNaoAcrescentaNada() {
        assertFalse(descricaoRota(rota, emptyList(), comAlerta = false, Limites(), null, agora).contains("Próximas partidas"))
        assertFalse(descricaoRota(rota, emptyList(), comAlerta = false, Limites(), emptyList(), agora).contains("Próximas partidas"))
    }

    @Test fun semEtiquetasNemAviso() {
        val d = descricaoRota(rota, emptyList(), comAlerta = false, Limites())
        assertTrue(d, d.endsWith("hora do horário, sem tempo real."))
        assertFalse(d, d.contains("Etiquetas"))
        assertFalse(d, d.contains("Aviso"))
    }
}
