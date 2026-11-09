package pt.pixellusitano.paragemcerta

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test fun soCasaOuTrabalhoAbremODefinirLocal() {
        assertEquals("CASA", localADefinir("CASA"))
        assertEquals("TRABALHO", localADefinir("TRABALHO"))
        assertNull(localADefinir("PARAGEM"))
        assertNull(localADefinir("X"))
        assertNull(localADefinir(null))
    }

    @Test fun osExtrasSoContamNumaAberturaNova() {
        assertTrue(lerExtrasDaAbertura(restaurada = false, flags = Intent.FLAG_ACTIVITY_NEW_TASK))
        // Rodar o ecrã: o intent é o mesmo e a ação já foi feita.
        assertFalse(lerExtrasDaAbertura(restaurada = true, flags = 0))
        // Reaberta pelos Recentes: o Android repete o intent do widget, que já foi tratado.
        assertFalse(lerExtrasDaAbertura(restaurada = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
