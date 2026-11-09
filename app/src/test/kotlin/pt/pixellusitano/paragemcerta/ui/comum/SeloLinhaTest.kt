package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo

class SeloLinhaTest {
    @Test fun amareloLevaTexto_escuro() = assertEquals(Azulejo.Tinta, corDoTextoSobre(Color(0xFFE2A400)))
    @Test fun cobaltoLevaTexto_branco() = assertEquals(Color.White, corDoTextoSobre(Azulejo.Cobalto))
    @Test fun vermelhoLevaTexto_branco() = assertEquals(Color.White, corDoTextoSobre(Color(0xFFD1352B)))
}
