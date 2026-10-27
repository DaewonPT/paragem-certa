package pt.pixellusitano.paragemcerta.ui.pesquisa

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem

class SubtituloTest {
    private fun p(fonte: Fonte, id: String) = Paragem(RefParagem(fonte, id), "Cais do Sodré", 38.7, -9.14, null)

    @Test fun distingueParagensComOMesmoNome() {
        assertEquals("Carris Metropolitana · 060419", subtituloParagem(p(Fonte.CM, "060419")))
        assertEquals("CP · 69005", subtituloParagem(p(Fonte.TRANSITOUS, "pt-Comboios-de-Portugal(CP)_94_69005")))
        assertEquals("Transtejo · 5", subtituloParagem(p(Fonte.TRANSITOUS, "pt-Transtejo-Soflusa_5")))
        assertEquals("Metro de Lisboa · CS", subtituloParagem(p(Fonte.TRANSITOUS, "pt-Metro-Lisboa_CS")))
    }
}
