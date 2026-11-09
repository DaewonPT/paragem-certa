package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp") // ecrã alto: os cartões ficam por baixo dos campos e filtros
class RotasScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
    private val prontas = EstadoRotas.Prontas(rotas, etiquetas(rotas))
    private val pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL))
    private val agora = Instant.parse("2026-11-01T02:20:00Z")

    private fun partida(programada: String, prevista: String?) =
        Chegada(Fonte.CM, "1", "210", "Oriente", Instant.parse(programada), prevista?.let(Instant::parse), null, null, Modo.AUTOCARRO, null, null)

    /** A primeira rota com partidas ao vivo e do horário; a segunda fica sem elas e mostra o "Sai de". */
    private val comPartidas = prontas.copy(
        partidas = mapOf(
            0 to listOf(
                partida("2026-11-01T02:22:00Z", "2026-11-01T02:23:00Z"),
                partida("2026-11-01T02:38:00Z", "2026-11-01T02:38:00Z"),
                partida("2026-11-01T03:37:00Z", null),
            ),
        ),
    )

    @Composable
    private fun Rotas(estado: EstadoRotas) = RotasConteudo(
        pedido = pedido, estado = estado, filtros = Filtros(acessivel = true), alertas = setOf("210"),
        agora = agora,
        aoEscolherOrigem = {}, aoEscolherDestino = {}, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
        aoAbrirRota = {}, aoTentarOutraVez = {}, aoMaisCedo = {}, aoMaisTarde = {}, modifier = Modifier.width(360.dp),
    )

    @Test fun rotas_claro() = captureRoboImage("src/test/screenshots/rotas_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Rotas(comPartidas) }
    }

    /** O fim da lista (só uma rota, para caber no ecrã): "Mais cedo" e "Mais tarde" e o aviso de que a página não carregou. */
    @Test fun rotas_paginas() = captureRoboImage("src/test/screenshots/rotas_paginas.png") {
        val uma = rotas.take(1)
        TemaParagemCerta(ModoTema.CLARO) {
            Rotas(EstadoRotas.Prontas(uma, etiquetas(uma), "EARLIER|1793498820", "LATER|1793503740", comPartidas.partidas, erroPagina = true))
        }
    }

    @Test fun rotas_escuro() = captureRoboImage("src/test/screenshots/rotas_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Rotas(prontas) }
    }

    @Test fun rotas_vazias() = captureRoboImage("src/test/screenshots/rotas_vazias.png") {
        TemaParagemCerta(ModoTema.CLARO) { Rotas(EstadoRotas.Vazias(Motivo.SEM_LIGACOES)) }
    }

    @Test fun rotas_letra200() = captureRoboImage("src/test/screenshots/rotas_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Rotas(prontas) }
        }
    }

    // O diálogo da hora é outra janela: abre-se a sério e captura-se o ecrã inteiro, que a inclui.
    private fun horaAberta(modo: ModoTema, ficheiro: String) {
        regra.setContent { TemaParagemCerta(modo) { Rotas(prontas) } }
        regra.onNodeWithText("Partir às").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Cancelar").assertIsDisplayed()
        captureScreenRoboImage(ficheiro)
    }

    @Test fun rotas_hora_claro() = horaAberta(ModoTema.CLARO, "src/test/screenshots/rotas_hora_claro.png")

    @Test fun rotas_hora_escuro() = horaAberta(ModoTema.ESCURO, "src/test/screenshots/rotas_hora_escuro.png")
}
