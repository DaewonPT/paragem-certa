package pt.pixellusitano.paragemcerta.core.atraso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import java.time.Instant
import java.time.ZoneId

class AtrasoTest {
    private val t0 = Instant.parse("2026-10-27T14:00:00Z")
    private fun c(prev: Long?) = Chegada(Fonte.CM, "3708", "3708", "Cais do Sodré", t0, prev?.let { t0.plusSeconds(it) }, null, null, Modo.AUTOCARRO, null, null)

    @Test fun arredondaMeioMinutoParaLongeDeZero() {
        assertEquals(2, minutosDeAtraso(t0, t0.plusSeconds(90)))
        assertEquals(-2, minutosDeAtraso(t0, t0.minusSeconds(90)))
        assertEquals(1, minutosDeAtraso(t0, t0.plusSeconds(89)))
    }

    @Test fun estados() {
        assertEquals(EstadoAtraso.A_HORAS, estado(c(60)))
        assertEquals(EstadoAtraso.A_HORAS, estado(c(-60)))
        assertEquals(EstadoAtraso.ATRASADO, estado(c(120)))
        assertEquals(EstadoAtraso.ATRASADO, estado(c(300)))
        assertEquals(EstadoAtraso.MUITO_ATRASADO, estado(c(360)))
        assertEquals(EstadoAtraso.ADIANTADO, estado(c(-120)))
        assertEquals(EstadoAtraso.HORARIO, estado(c(null)))
    }

    @Test fun limitesConfiguraveis() {
        assertEquals(EstadoAtraso.A_HORAS, estado(c(120), Limites(atrasado = 3, muitoAtrasado = 8)))
    }

    @Test fun rotulos() {
        val lx = ZoneId.of("Europe/Lisbon")
        assertEquals("agora", rotuloTempo(c(0), t0.plusSeconds(-20), lx))
        assertEquals("3 min", rotuloTempo(c(180), t0, lx))
        assertEquals("2 min", rotuloTempo(c(150), t0, lx))
        assertEquals("14:00", rotuloTempo(c(0), t0.minusSeconds(70 * 60), lx))
        assertEquals("14:00", rotuloTempo(c(null), t0.minusSeconds(120), lx))
        assertEquals("+3", rotuloAtraso(c(180)))
        assertEquals("+9", rotuloAtraso(c(540)))
        assertEquals("a horas", rotuloAtraso(c(30)))
        assertEquals("−2 adiantado", rotuloAtraso(c(-120)))
        assertEquals("🕒 horário", rotuloAtraso(c(null)))
    }

    @Test fun passadasSaoDescartadas() {
        assertFalse(aindaPorChegar(c(0), t0.plusSeconds(31)))
        assertTrue(aindaPorChegar(c(0), t0.plusSeconds(29)))
    }
}
