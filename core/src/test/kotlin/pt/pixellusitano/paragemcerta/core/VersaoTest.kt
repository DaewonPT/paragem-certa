package pt.pixellusitano.paragemcerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersaoTest {
    @Test fun versionCodeFromTag_parsesSemver() {
        assertEquals(100, versionCodeFromTag("v0.1.0"))
        assertEquals(10203, versionCodeFromTag("v1.2.3"))
        assertNull(versionCodeFromTag("latest"))
    }

    @Test fun isNewer_comparesCodes() {
        assertTrue(isNewer(100, "v0.1.1"))
        assertFalse(isNewer(101, "v0.1.1"))
    }
}
