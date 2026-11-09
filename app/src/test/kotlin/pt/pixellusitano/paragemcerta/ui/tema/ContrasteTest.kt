package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrasteTest {
    @Test fun cinzentoSobreMarfimPassaAA() = assertEquals(4.61f, contraste(Azulejo.Cinza, Azulejo.Marfim), 0.01f)

    @Test fun brancoSobreCobalto() = assertEquals(7.76f, contraste(Color.White, Azulejo.Cobalto), 0.01f)

    @Test fun paresNovosChegamAoMinimo() {
        val texto = listOf(
            Azulejo.Cinza to Azulejo.Branco, Azulejo.Tinta to Azulejo.Marfim,
            Azulejo.TextoNoite to Azulejo.NoiteSuperficie, Azulejo.Tinta to Azulejo.Eletrico,
        )
        texto.forEach { (a, b) -> assertTrue("$a/$b", contraste(a, b) >= 4.5f) }
        val graficos = listOf(
            Azulejo.Tinta to Azulejo.Branco, Azulejo.Cobalto to Azulejo.Branco,
            Azulejo.Noite to Azulejo.CeuNoite, Azulejo.CeuNoite to Azulejo.NoiteSuperficie,
        )
        graficos.forEach { (a, b) -> assertTrue("$a/$b", contraste(a, b) >= 3f) }
    }
}
