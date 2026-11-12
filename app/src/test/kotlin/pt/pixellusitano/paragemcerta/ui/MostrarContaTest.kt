package pt.pixellusitano.paragemcerta.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menu → Conta só aparece quando há forma de entrar (o cliente da Google) ou uma sessão já aberta para sair ou apagar. */
class MostrarContaTest {
    @Test fun semClienteGoogleESemSessaoEscondeAConta() = assertFalse(mostrarConta(true, "", comSessao = false))

    @Test fun comSessaoMostraAContaMesmoSemClienteGoogle() = assertTrue(mostrarConta(true, "", comSessao = true))

    @Test fun comClienteGoogleMostraAConta() = assertTrue(mostrarConta(true, "id.apps.googleusercontent.com", comSessao = false))

    @Test fun interruptorDesligadoEscondeSempre() = assertFalse(mostrarConta(false, "id", comSessao = true))
}
