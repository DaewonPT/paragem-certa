package pt.pixellusitano.paragemcerta

import org.junit.Assert.assertEquals
import org.junit.Test

class DonativosTest {
    // A Play só aceita pagamentos fora dela em casos apertados: a versão da Play não leva a ligação dos donativos.
    @Test fun aVersaoDaPlayNaoTemLigacaoDosDonativos() {
        assertEquals("", BuildConfig.URL_DOACOES)
    }
}
