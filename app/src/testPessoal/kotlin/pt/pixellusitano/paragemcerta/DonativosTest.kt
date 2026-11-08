package pt.pixellusitano.paragemcerta

import org.junit.Assert.assertEquals
import org.junit.Test

class DonativosTest {
    @Test fun aVersaoDoGithubTemALigacaoDosDonativos() {
        assertEquals("https://pixel-lusitano.dotatie.com", BuildConfig.URL_DOACOES)
    }
}
