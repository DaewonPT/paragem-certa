package pt.pixellusitano.paragemcerta.core.fontes.cm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CmIdsTest {
    @Test fun paragemDoTransitousParaACm() {
        // Três paragens conferidas à mão: o 3 inicial passa a 0, os outros dígitos ficam.
        assertEquals("020047", cmParagemDeTransitous("pt-Carris-Metropolitana_320047"))
        assertEquals("060155", cmParagemDeTransitous("pt-Carris-Metropolitana_360155"))
        assertEquals("160099", cmParagemDeTransitous("pt-Carris-Metropolitana_160099"))
    }

    @Test fun paragemDeOutroOperadorOuVaziaNaoConverte() {
        assertNull(cmParagemDeTransitous("pt-Carris_3804"))
        assertNull(cmParagemDeTransitous("pt-Carris-Metropolitana_"))
        assertNull(cmParagemDeTransitous("320047"))
    }

    @Test fun sentidoDoPadraoDaCm() {
        assertEquals(0, sentidoDoPadraoCm("3708_0_1"))
        assertEquals(1, sentidoDoPadraoCm("3708_0_2"))
        assertEquals(0, sentidoDoPadraoCm("[PL3CL][YA15B]3708_0_1"))
        assertNull(sentidoDoPadraoCm("3708_0_3"))
        assertNull(sentidoDoPadraoCm("3708_0"))
        assertNull(sentidoDoPadraoCm("3708"))
        assertNull(sentidoDoPadraoCm(""))
    }
}
