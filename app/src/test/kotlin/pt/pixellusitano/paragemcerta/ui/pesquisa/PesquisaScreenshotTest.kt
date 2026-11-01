package pt.pixellusitano.paragemcerta.ui.pesquisa

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.Grupo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class PesquisaScreenshotTest {
    private val linha = Linha(Fonte.CM, "3708", "3708", "Costa da Caparica - Lisboa", "#C61D23", "#FFFFFF", Modo.AUTOCARRO)
    private val estacao = Lugar("Estação Ferroviária de Algés", 38.7019, -9.2300, TipoLugar.LOCAL, "Oeiras")
    private fun cais(n: String) = Paragem(RefParagem(Fonte.CM, "0604$n"), "Algés (Estação) P$n", 38.7005, -9.2297, false)
    private val varios = Grupo("Algés (Estação)", 38.7005, -9.2297, listOf(cais("1"), cais("2"), cais("12")))
    private val so = Paragem(RefParagem(Fonte.CM, "060777"), "Algés (Jardim)", 38.7011, -9.2312, false)
    private val um = Grupo(so.nome, so.lat, so.lon, listOf(so))

    @Test fun pesquisa_destino() = captureRoboImage("src/test/screenshots/pesquisa_destino.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            PesquisaConteudo(
                texto = "algés",
                resultados = listOf(
                    ResultadoPesquisa.DeLinha(linha),
                    ResultadoPesquisa.DeLugar(estacao),
                    ResultadoPesquisa.DeEstacao(varios),
                    ResultadoPesquisa.DeEstacao(um),
                ),
                semRede = false,
                aoEscrever = {}, aoAbrirLinha = {}, aoIrPara = {}, aoAbrirParagem = {}, aoVoltar = {},
                modifier = Modifier.width(360.dp),
            )
        }
    }
}
