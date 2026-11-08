package pt.pixellusitano.paragemcerta.dados

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginGoogleTest {
    @Test fun pedidoDoBotaoLevaOClienteEONonceDoServidor() {
        val opcao = opcaoGoogle("cliente-web", "nonce-do-servidor")
        assertEquals("cliente-web", opcao.serverClientId)
        assertEquals("nonce-do-servidor", opcao.nonce)
    }
}
