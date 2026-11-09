package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CartaoAzulejoTest {
    @get:Rule val regra = createComposeRule()

    @Test fun clicavel_temUmSoNoBotaoComOTexto() {
        regra.setContent { CartaoAzulejo(aoTocar = {}) { Text("dentro") } }
        regra.onNode(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertExists()
        regra.onAllNodes(hasClickAction() and hasText("dentro")).assertCountEquals(1)
    }

    @Test fun semAoTocar_naoTemNosClicaveis() {
        regra.setContent { CartaoAzulejo { Text("dentro") } }
        regra.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
