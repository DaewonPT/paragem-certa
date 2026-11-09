package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.modelo.Modo

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class IconesTest {
    @Test fun saoQuarentaEQuatroComNomesUnicos() {
        assertEquals(44, Icones.todos.size)
        assertEquals(44, Icones.todos.map { it.first }.toSet().size)
    }

    @Test fun todosNaGrelhaDe24() = Icones.todos.forEach { (n, i) ->
        assertEquals(n, 24.dp, i.defaultWidth)
        assertEquals(n, 24f, i.viewportWidth)
        assertEquals(n, 24f, i.viewportHeight)
        assertTrue(n, i.root.size > 0)
    }

    @Test fun modosELocais() {
        assertEquals(Icones.Autocarro, iconeModo(Modo.OUTRO))
        assertEquals(Icones.Metro, iconeModo(Modo.METRO))
        assertEquals(Icones.Casa, iconeLocal("CASA"))
        assertEquals(Icones.Estrela, iconeLocal("OUTRO"))
    }

    @Test fun cadaTipoDeRelatoTemOSeuIcone() {
        val icones = TipoRelato.entries.map(::iconeRelato)
        assertEquals(7, icones.toSet().size)
        assertTrue(Icones.todos.map { it.second }.containsAll(icones))
    }

    @Test fun estrelaCheiaNaoEAEstrela() = assertNotEquals(Icones.Estrela, Icones.EstrelaCheia)

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun folhaDosIcones() = captureRoboImage("src/test/screenshots/icones_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            FlowRow(Modifier.width(360.dp).padding(8.dp), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icones.todos.forEach { (nome, icone) ->
                    Column(Modifier.width(84.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icone, nome)
                        Text(nome, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
