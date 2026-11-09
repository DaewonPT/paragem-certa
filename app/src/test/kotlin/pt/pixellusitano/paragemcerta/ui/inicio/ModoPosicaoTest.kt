package pt.pixellusitano.paragemcerta.ui.inicio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.ui.inicio.ModoPosicao.BUSSOLA
import pt.pixellusitano.paragemcerta.ui.inicio.ModoPosicao.CENTRADO
import pt.pixellusitano.paragemcerta.ui.inicio.ModoPosicao.LIVRE

class ModoPosicaoTest {
    @Test fun transicoes() {
        assertEquals(CENTRADO, aoTocar(LIVRE, true, true)); assertEquals(BUSSOLA, aoTocar(CENTRADO, true, true))
        assertEquals(CENTRADO, aoTocar(CENTRADO, false, true)); assertEquals(LIVRE, aoTocar(BUSSOLA, true, true))
        ModoPosicao.entries.forEach { assertEquals(LIVRE, aoArrastar(it)) }
    }

    @Test fun semPermissaoNuncaSaiDeLivre() = ModoPosicao.entries.forEach { assertEquals(LIVRE, aoTocar(it, true, false)) }

    @Test fun filtroDaCamara() {
        assertTrue(deveEnviarCamara(null, 38.7, -9.14, 16.0))
        val u = CamaraEnviada(38.7, -9.14, 16.0)
        assertFalse(deveEnviarCamara(u, 38.7 + 0.00026, -9.14, 16.0))   // ~29 m
        assertTrue(deveEnviarCamara(u, 38.7 + 0.00028, -9.14, 16.0))    // ~31 m
        assertFalse(deveEnviarCamara(u, 38.7, -9.14, 16.4)); assertTrue(deveEnviarCamara(u, 38.7, -9.14, 16.5))
    }

    @Test fun duracao() { assertEquals(750, duracaoCamara(true)); assertEquals(0, duracaoCamara(false)) }
}
