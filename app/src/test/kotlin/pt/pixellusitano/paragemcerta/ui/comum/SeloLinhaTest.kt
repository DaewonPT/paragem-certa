package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo

class SeloLinhaTest {
    @Test fun amareloLevaTexto_escuro() = assertEquals(Azulejo.Tinta, corDoTextoSobre(Color(0xFFE2A400)))
    @Test fun cobaltoLevaTexto_branco() = assertEquals(Color.White, corDoTextoSobre(Azulejo.Cobalto))
    @Test fun vermelhoLevaTexto_branco() = assertEquals(Color.White, corDoTextoSobre(Color(0xFFD1352B)))
}

@RunWith(RobolectricTestRunner::class)
class SeloLinhaModifierTest {
    @get:Rule val regra = createComposeRule()

    @Test fun omodifierChegaAoNoDoSelo() {
        regra.setContent { SeloLinha("15E", Azulejo.Cobalto, modifier = Modifier.testTag("selo")) }
        regra.onNodeWithTag("selo").assertTextEquals("15E")
    }
}
