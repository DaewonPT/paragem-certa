package pt.pixellusitano.paragemcerta.ui.viagem

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.Notificacoes
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class ViagemScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val ex = Exemplo()
    private val plano = ex.plano()

    @Composable
    private fun Ecra(
        e: EstadoViagem, p: PlanoViagem = plano,
        aoSilenciar: () -> Unit = {}, aoJaSai: () -> Unit = {}, aoParar: () -> Unit = {}, aoPartilhar: () -> Unit = {},
        aoEncontrar: (Lugar, String?) -> Unit = { _, _ -> },
    ) = ViagemConteudo(
        ecraDaViagem(p, e, ex.viagem(p)), aoSilenciar, aoJaSai, aoParar, aoPartilhar, aoVoltar = {},
        aoEncontrar = aoEncontrar, modifier = Modifier.width(360.dp),
    )

    private fun mostrar(e: EstadoViagem, p: PlanoViagem = plano) = regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(e, p) } }

    private fun texto(t: String) = regra.onNodeWithText(t)

    @Test fun aBordoDizQuantasFaltamEOndeSais() {
        mostrar(ex.aBordo(plano, 1, k = 2))
        texto("Etapa 2 de 4 · Agora").assertExists()
        texto("Faltam 3 paragens · sais em Oriente").assertExists()
        texto("Alcântara").assertExists()
        texto("Estás aqui").assertExists()
        texto("Chegada prevista às 11:34").assertExists() // 10:34 UTC, Lisboa no verão
        texto("Posição estimada pelo horário").assertDoesNotExist()
        // Os três botões estão à vista com o painel recolhido; o "Partilhar" fica no painel, por baixo da lista.
        listOf("Estás aqui", "Silenciar", "Já saí", "Parar").forEach { texto(it).assertIsDisplayed() }
        regra.abrirPainel()
        texto("Partilhar chegada").assertIsDisplayed()
    }

    @Test fun comLetraA200OsBotoesEOTituloContinuamAVista() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aBordo(plano, 1, k = 2)) }
            }
        }
        listOf("Faltam 3 paragens · sais em Oriente", "Silenciar", "Já saí", "Parar").forEach { texto(it).assertIsDisplayed() }
        regra.abrirPainel()
        texto("Partilhar chegada").assertIsDisplayed()
    }

    @Test fun emHorarioMostraOSelo() {
        mostrar(ex.aBordo(plano, 1, k = 2, origem = Origem.HORARIO))
        texto("Posição estimada pelo horário").assertExists()
    }

    @Test fun aEsperaDizQuandoChega() {
        val aoVivo = ex.plano(aoVivo = true)
        // O 736 parte às 10:03; às 09:59 faltam 4 min.
        mostrar(ex.espera(aoVivo, 1, atualizado = Instant.parse("2026-07-01T09:59:00Z")), aoVivo)
        texto("O 736 chega em 4 min").assertExists()
        texto("5 paragens · sai em Oriente").assertExists()
        texto("Estás aqui").assertExists()
    }

    @Test fun aPeDizADistanciaEOTempoEJaSaiNaoServe() {
        mostrar(ex.aPe(plano, etapa = 0, falta = 120.0))
        texto("Etapa 1 de 4 · Agora").assertExists()
        texto("A pé até Cais do Sodré · 120 m").assertExists()
        texto("3 min a pé").assertExists()
        texto("Já saí").assertIsNotEnabled()
        texto("Silenciar").assertIsEnabled()
    }

    @Test fun aPeAteUmaParagemOfereceEncontrarAParagem() {
        var lugar: Lugar? = null
        var chave: String? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aPe(plano, etapa = 0, falta = 120.0), aoEncontrar = { l, c -> lugar = l; chave = c }) } }
        texto("Encontrar a paragem").performClick()
        assertEquals(plano.itinerario.etapas[0].para, lugar)
        assertEquals("Cais do Sodré", lugar?.nome)
        // A foto da bússola é a da paragem de embarque: a primeira da etapa em veículo que vem a seguir.
        assertEquals("TRANSITOUS:736-0", chave)
    }

    @Test fun aBordoNaoHaParagemParaEncontrar() {
        mostrar(ex.aBordo(plano, 1, k = 2))
        texto("Encontrar a paragem").assertDoesNotExist()
    }

    @Test fun aUltimaEtapaAPeVaiParaODestinoEnaoParaUmaParagem() {
        val etapas = plano.itinerario.etapas.take(3)
        val ateOFim = plano.copy(itinerario = plano.itinerario.copy(etapas = etapas, chegada = etapas.last().chegada))
        mostrar(ex.aPe(ateOFim, etapa = 2, falta = 90.0), ateOFim)
        texto("A pé até Oriente (Norte) · 90 m").assertExists()
        texto("Encontrar a paragem").assertDoesNotExist()
    }

    @Test fun aEsperaNaoDeixaDizerQueJaSaiu() {
        // "Já saí" à espera saltava o autocarro que a pessoa ainda vai apanhar, e com ele os avisos.
        mostrar(ex.espera(plano, 1))
        texto("Já saí").assertIsNotEnabled()
        texto("Silenciar").assertIsEnabled()
    }

    @Test fun aBordoDeixaDizerQueJaSaiu() {
        mostrar(ex.aBordo(plano, 1, k = 2))
        texto("Já saí").assertIsEnabled()
    }

    @Test fun deslizarMostraAEtapaSeguinte() {
        mostrar(ex.aBordo(plano, 1, k = 2))
        // A etapa desliza-se no painel; no resto do ecrã é o mapa que se arrasta.
        texto("Etapa 2 de 4 · Agora").performTouchInput { swipeLeft() }
        regra.waitForIdle()
        texto("Etapa 3 de 4 · A seguir").assertExists()
        texto("A pé até Oriente (Norte) · 90 m").assertExists()
    }

    @Test fun osBotoesPedemAsAcoes() {
        val pedidos = mutableListOf<String>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Ecra(
                    ex.aBordo(plano, 1, k = 2), plano,
                    aoSilenciar = { pedidos += "silenciar" }, aoJaSai = { pedidos += "jaSai" }, aoParar = { pedidos += "parar" },
                    aoPartilhar = { pedidos += "partilhar" },
                )
            }
        }
        listOf("Silenciar", "Já saí", "Parar").forEach { texto(it).performClick() }
        regra.abrirPainel()
        texto("Partilhar chegada").performClick()
        assertEquals(listOf("silenciar", "jaSai", "parar", "partilhar"), pedidos)
    }

    @Test fun silenciadoNaoSeSilenciaOutraVez() {
        mostrar(ex.aBordo(plano, 1, k = 2).copy(silenciado = true))
        texto("Silenciado").assertIsNotEnabled()
        texto("Silenciar").assertDoesNotExist()
    }

    @Test fun terminadaDizQueChegasteEFechaComConcluir() {
        mostrar(ex.aBordo(plano, 3, k = 1).copy(fase = Fase.TERMINADA, terminada = true))
        texto("Chegaste a Sacavém").assertExists()
        texto("Concluir").assertExists()
        listOf("Silenciar", "Já saí", "Parar", "Partilhar chegada").forEach { texto(it).assertDoesNotExist() }
    }

    @Test fun osBotoesPedemAoServicoAsMesmasAcoesDaNotificacao() {
        pedirAoServico(contexto, Notificacoes.ACAO_SILENCIAR)
        val pedido = shadowOf(contexto as Application).nextStartedService
        assertEquals(Notificacoes.ACAO_SILENCIAR, pedido.action)
        assertEquals(ServicoViagem::class.java.name, pedido.component!!.className)
    }

    @Test fun oEcraLeOsFicheirosEAcompanhaOsAvisosDoServico() {
        // Os ficheiros só valem até chegada + 30 min: esta viagem tem de ser a de agora.
        val agora = Exemplo(Instant.now())
        val p = agora.plano()
        FicheirosViagem.gravarPlano(contexto, p)
        FicheirosViagem.gravarEstado(contexto, agora.aBordo(p, 1, k = 2))
        var saiu = false
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { ViagemEcra(aoVoltar = { saiu = true }, mapa = SEM_MAPA) } }

        esperarPor("Faltam 3 paragens · sais em Oriente") // ao abrir
        FicheirosViagem.gravarEstado(contexto, agora.aBordo(p, 1, k = 3))
        contexto.sendBroadcast(Intent(FicheirosViagem.ACAO_ESTADO).setPackage(contexto.packageName))
        esperarPor("Faltam 2 paragens · sais em Oriente") // a cada aviso do serviço

        FicheirosViagem.apagar(contexto) // PARAR
        contexto.sendBroadcast(Intent(FicheirosViagem.ACAO_ESTADO).setPackage(contexto.packageName))
        regra.waitUntil(5_000) { regra.waitForIdle(); saiu } // sem nada na tela para consultar, é o waitForIdle que deixa a leitura acabar
        assertTrue(saiu)
    }

    @Test fun seOServicoNaoArrancarOEcraSaiEOsFicheirosFicamParaRetomar() {
        val agora = Exemplo(Instant.now())
        val p = agora.plano()
        FicheirosViagem.gravarPlano(contexto, p)
        FicheirosViagem.gravarEstado(contexto, agora.aBordo(p, 1, k = 2))
        var saiu = false
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { ViagemEcra(aoVoltar = { saiu = true }, mapa = SEM_MAPA) } }

        esperarPor("Faltam 3 paragens · sais em Oriente")
        // O serviço recusou o RETOMAR: não apagou nada (é a "Viagem interrompida"), por isso ler os ficheiros outra vez não mudava o ecrã.
        contexto.sendBroadcast(Intent(FicheirosViagem.ACAO_ESTADO).setPackage(contexto.packageName).putExtra(FicheirosViagem.EXTRA_SEM_SERVICO, true))
        regra.waitUntil(5_000) { regra.waitForIdle(); saiu }
        assertTrue(saiu)
        assertNotNull(FicheirosViagem.lerEstado(contexto))
    }

    // O MapLibre não desenha no Robolectric: no lugar do mapa fica um retângulo.
    private val SEM_MAPA: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> Box(Modifier.fillMaxSize()) }

    private fun esperarPor(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }

    @Test fun viagem_claro() = captureRoboImage("src/test/screenshots/viagem_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aBordo(plano, 1, k = 2)) }
    }

    @Test fun viagem_escuro() = captureRoboImage("src/test/screenshots/viagem_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { Ecra(ex.aBordo(plano, 1, k = 2)) }
    }

    @Test fun viagem_horario() = captureRoboImage("src/test/screenshots/viagem_horario.png") {
        TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aBordo(plano, 1, k = 2, origem = Origem.HORARIO)) }
    }

    @Test fun viagem_letra200() = captureRoboImage("src/test/screenshots/viagem_letra200.png") {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
            TemaParagemCerta(ModoTema.CLARO) { Ecra(ex.aBordo(plano, 1, k = 2)) }
        }
    }
}
