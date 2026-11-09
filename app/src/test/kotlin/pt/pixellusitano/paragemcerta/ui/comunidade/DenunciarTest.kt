package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class DenunciarTest {
    @get:Rule val regra = createComposeRule()

    @Test fun soEnviaComNomeEBoaFe() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { DenunciarConteudo("ana@exemplo.pt", aoEnviar = { _, _, _ -> }, aoFechar = {}) } }
        regra.onNodeWithText("Enviar denúncia").assertIsNotEnabled()
        regra.onNodeWithText("O teu nome").performTextInput("Rita")
        regra.onNodeWithText("Enviar denúncia").assertIsNotEnabled() // falta a boa-fé
        regra.onNodeWithText("Declaro que faço esta denúncia de boa-fé e que o que digo é verdade.").performClick()
        regra.onNodeWithText("Enviar denúncia").assertIsEnabled()
    }
}
