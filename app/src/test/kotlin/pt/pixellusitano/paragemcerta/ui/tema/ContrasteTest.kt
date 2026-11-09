package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade

class ContrasteTest {
    @Test fun cinzentoSobreMarfimPassaAA() = assertEquals(5.57f, contraste(Azulejo.Cinza, Azulejo.Marfim), 0.01f)

    @Test fun brancoSobreCobalto() = assertEquals(7.76f, contraste(Color.White, Azulejo.Cobalto), 0.01f)

    @Test fun cinzentoSobreBranco() = assertEquals(5.98f, contraste(Azulejo.Cinza, Azulejo.Branco), 0.01f)

    @Test fun contornoNoiteFicaVisivel() = assertEquals(2.04f, contraste(Azulejo.ContornoNoite, Azulejo.Noite), 0.01f)

    @Test fun coresDasGravidadesChegamAoMinimo() {
        Gravidade.values().forEach { g ->
            listOf(false, true).forEach { escuro ->
                val c = contraste(CoresGravidade.texto(g, escuro), CoresGravidade.fundo(g, escuro))
                assertTrue("$g escuro=$escuro: $c", c >= 4.5f)
            }
        }
    }

    @Test fun paresNovosChegamAoMinimo() {
        val texto = listOf(
            Azulejo.Cinza to Azulejo.Branco, Azulejo.Tinta to Azulejo.Marfim,
            Azulejo.TextoNoite to Azulejo.NoiteSuperficie, Azulejo.Tinta to Azulejo.Eletrico,
            Azulejo.Cinza to Color(0xFFDCE6F7),
            Azulejo.Vermelho to Azulejo.Branco, Azulejo.Vermelho to Azulejo.Marfim,
            Azulejo.VermelhoNoite to Azulejo.NoiteSuperficie, Azulejo.VermelhoNoite to Azulejo.Noite,
        )
        texto.forEach { (a, b) -> assertTrue("$a/$b", contraste(a, b) >= 4.5f) }
        val graficos = listOf(
            Azulejo.Tinta to Azulejo.Branco, Azulejo.Cobalto to Azulejo.Branco,
            Azulejo.Noite to Azulejo.CeuNoite, Azulejo.CeuNoite to Azulejo.NoiteSuperficie,
            Azulejo.Cobalto to Azulejo.Marfim, Azulejo.CeuNoite to Azulejo.Noite, Azulejo.Eletrico to Azulejo.Cobalto,
        )
        graficos.forEach { (a, b) -> assertTrue("$a/$b", contraste(a, b) >= 3f) }
    }
}
