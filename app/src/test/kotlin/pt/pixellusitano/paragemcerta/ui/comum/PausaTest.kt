package pt.pixellusitano.paragemcerta.ui.comum

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PausaTest {
    @Test fun horaEmLisboa() {
        assertEquals("às 00:00", horaDoFimDaPausa(Instant.parse("2026-11-10T21:00:00Z")))
        assertEquals("à 01:00", horaDoFimDaPausa(Instant.parse("2026-07-15T21:00:00Z")))
        assertEquals("à 01:00", horaDoFimDaPausa(Instant.parse("2026-07-15T23:30:00Z")))
        assertEquals("às 00:00", horaDoFimDaPausa(Instant.parse("2026-03-28T22:00:00Z"))) // noite da mudança para a hora de verão
        assertEquals("à 01:00", horaDoFimDaPausa(Instant.parse("2026-10-24T22:00:00Z")))  // noite da mudança para a hora de inverno
    }
}
