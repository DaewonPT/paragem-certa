package pt.pixellusitano.paragemcerta

import org.junit.Assert.assertEquals
import org.junit.Test

class DonativosTest {
    @Test fun aVersaoDoGithubTemALigacaoDosDonativos() {
        // A página não existe em português: em inglês é mais legível do que o neerlandês de omissão.
        assertEquals("https://pixel-lusitano.dotatie.com/?lang=en", BuildConfig.URL_DOACOES)
    }
}
