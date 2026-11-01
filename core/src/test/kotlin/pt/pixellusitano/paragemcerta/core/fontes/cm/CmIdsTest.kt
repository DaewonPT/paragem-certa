package pt.pixellusitano.paragemcerta.core.fontes.cm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CmIdsTest {
    @Test fun paragemDoTransitousParaACm() {
        // As três paragens que a spec 6.2 manda verificar: o 3 inicial passa a 0, os outros dígitos ficam.
        assertEquals("020047", cmParagemDeTransitous("pt-Carris-Metropolitana_320047"))
        assertEquals("060155", cmParagemDeTransitous("pt-Carris-Metropolitana_360155"))
        assertEquals("160099", cmParagemDeTransitous("pt-Carris-Metropolitana_160099"))
    }

    @Test fun paragemDeOutroOperadorOuVaziaNaoConverte() {
        assertNull(cmParagemDeTransitous("pt-Carris_3804"))
        assertNull(cmParagemDeTransitous("pt-Carris-Metropolitana_"))
        assertNull(cmParagemDeTransitous("320047"))
    }

    @Test fun viagemDoTransitousParaACm() {
        assertEquals("3708_0_1|1|0525", cmViagemDeTransitous("20261101_10:35_pt-Carris-Metropolitana_3708_0_1|1|0525"))
        assertEquals("[PL3CL][YA15B]3001_0_2_1000_1029_0_ESC_DOM", cmViagemDeTransitous("20261101_10:35_pt-Carris-Metropolitana_[PL3CL][YA15B]3001_0_2_1000_1029_0_ESC_DOM"))
        assertNull(cmViagemDeTransitous("20261101_02:30_pt-Carris_13548_20260606_66_0_4"))
        assertNull(cmViagemDeTransitous("pt-Carris-Metropolitana_3708_0_1|1|0525"))
    }
}
