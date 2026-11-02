package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.dados.EcraInicial
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuScreenshotTest {
    private val favoritas = listOf(
        Favorita(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 0, 38.7, -9.14),
        Favorita(RefParagem(Fonte.TRANSITOUS, "pt-Metro-Lisboa_CS"), "Cais do Sodré", 1, 38.7, -9.14),
    )

    private fun local(id: Long, nome: String, tipo: String) =
        LocalGuardado(id, nome, Lugar(nome, 38.7, -9.14, TipoLugar.LOCAL), tipo, id.toInt())

    private val locais = listOf(
        local(1, "Casa", "CASA"), local(2, "Trabalho", "TRABALHO"),
        local(3, "Ginásio", "OUTRO"), local(4, "Casa da avó em Almada", "OUTRO"),
    )

    @get:Rule val regra = createComposeRule()

    @Composable
    private fun EcraMenu(
        modifier: Modifier = Modifier,
        aoAbrirFavoritos: () -> Unit = {}, aoAbrirDefinicoes: () -> Unit = {}, aoAbrirAjuda: () -> Unit = {},
        aoAbrirReclamar: () -> Unit = {}, aoEnviarOpiniao: () -> Unit = {}, aoAbrirSobre: () -> Unit = {},
    ) =MenuEcra(aoAbrirFavoritos, aoAbrirDefinicoes, aoAbrirAjuda, aoAbrirReclamar, aoEnviarOpiniao, aoAbrirSobre, modifier)

    @Composable
    private fun EcraFavoritos(modifier: Modifier = Modifier, aoAbrirFavorita: (Favorita) -> Unit = {}) = FavoritosConteudo(
        favoritas = favoritas, locais = locais, aoAbrirFavorita = aoAbrirFavorita, aoSubirFavorita = {}, aoRemoverFavorita = {},
        aoRenomearLocal = { _, _ -> }, aoSubirLocal = {}, aoApagarLocal = {}, aoVoltar = {}, modifier = modifier,
    )

    @Composable
    private fun EcraDefinicoes(
        modifier: Modifier = Modifier, filtros: Filtros = Filtros(), ecraInicial: EcraInicial = EcraInicial.MAPA,
        aoMudarEcraInicial: (EcraInicial) -> Unit = {},
    ) =DefinicoesConteudo(
        modo = ModoTema.ESCURO, limites = Limites(3, 8), somAviso = true, vibracaoAviso = false, filtros = filtros, ecraInicial = ecraInicial,
        aoMudarModo = {}, aoMudarLimites = {}, aoMudarSom = {}, aoMudarVibracao = {}, aoMudarFiltros = {},
        aoMudarEcraInicial = aoMudarEcraInicial, aoVoltar = {}, modifier = modifier,
    )

    @Test fun menu_claro() = captureRoboImage("src/test/screenshots/menu_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { EcraMenu(modifier = Modifier.width(360.dp)) }
    }

    @Test fun menu_escuro() = captureRoboImage("src/test/screenshots/menu_escuro.png") {
        TemaParagemCerta(ModoTema.ESCURO) { EcraMenu(modifier = Modifier.width(360.dp)) }
    }

    @Test fun favoritos_claro() = captureRoboImage("src/test/screenshots/favoritos_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { EcraFavoritos(modifier = Modifier.width(360.dp)) }
    }

    // Ecrã muito alto: as definições rolam e a captura tem de apanhar o fim (rotas, ecrã inicial, privacidade).
    @Test @Config(qualifiers = "w360dp-h2200dp")
    fun definicoes_claro() = captureRoboImage("src/test/screenshots/definicoes_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            EcraDefinicoes(
                Modifier.width(360.dp),
                Filtros(modos = setOf(Modo.METRO), maxAPeMin = 10, ritmo = Ritmo.LENTO, ordem = Ordem.CHEGADA_MAIS_CEDO, acessivel = true),
                EcraInicial.FAVORITOS,
            )
        }
    }

    @Test fun ajuda_claro() = captureRoboImage("src/test/screenshots/ajuda_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { AjudaEcra(aoVoltar = {}, modifier = Modifier.width(360.dp)) }
    }

    @Test fun reclamar_claro() = captureRoboImage("src/test/screenshots/reclamar_claro.png") {
        TemaParagemCerta(ModoTema.CLARO) { ReclamarEcra(aoAbrir = {}, aoVoltar = {}, modifier = Modifier.width(360.dp)) }
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun cadaItemDoMenuAbreOSeuDestino() {
        val toques = mutableListOf<String>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                EcraMenu(
                    aoAbrirFavoritos = { toques += "favoritos" }, aoAbrirDefinicoes = { toques += "definicoes" },
                    aoAbrirAjuda = { toques += "ajuda" }, aoAbrirReclamar = { toques += "reclamar" },
                    aoEnviarOpiniao = { toques += "opiniao" }, aoAbrirSobre = { toques += "sobre" },
                )
            }
        }
        listOf("Favoritos", "Definições", "Ajuda", "Reclamar de um transporte", "Enviar opinião", "Sobre").forEach {
            regra.onNodeWithText(it).performClick()
        }
        assertEquals(listOf("favoritos", "definicoes", "ajuda", "reclamar", "opiniao", "sobre"), toques)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun tocarNaFavoritaAbreAParagem() {
        var aberta: Favorita? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { EcraFavoritos(aoAbrirFavorita = { aberta = it }) } }
        regra.onNodeWithText("★ Cais do Sodré").performClick()
        assertEquals(favoritas[1], aberta)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun osEcrasComVoltarChamamOCallback() {
        var voltou = 0
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { AjudaEcra(aoVoltar = { voltou++ }) } }
        regra.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, voltou)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun reclamarAbreOOperador() {
        var aberto: String? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { ReclamarEcra(aoAbrir = { aberto = it }, aoVoltar = {}) } }
        regra.onNodeWithText("Carris").performClick()
        assertEquals("https://www.carris.pt/atendimento/falar-connosco/", aberto)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun cadaCanalDeReclamacaoTemOSeuEndereco() {
        val abertos = mutableListOf<String>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { ReclamarEcra(aoAbrir = { abertos += it }, aoVoltar = {}) } }
        val esperados = listOf(
            "Livro de Reclamações Eletrónico" to "https://www.livroreclamacoes.pt/Pedido/Reclamacao",
            "Carris" to "https://www.carris.pt/atendimento/falar-connosco/",
            "Carris Metropolitana" to "https://carrismetropolitana.pt/contacto",
            "Metro de Lisboa" to "https://www.metrolisboa.pt/informar/reclamacoes/",
            "CP" to "https://www.cp.pt/info/pt/w/reclamacao-cp",
        )
        esperados.forEach { (nome, _) -> regra.onNodeWithText(nome).performClick() }
        assertEquals(esperados.map { it.second }, abertos)
        assertEquals(esperados.map { it.second }, CANAIS_RECLAMACAO.map { it.url })
        regra.onNodeWithText("A Paragem Certa não é o operador. As reclamações vão diretamente para eles.").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun ajudaTemAsQuatroPerguntas() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { AjudaEcra(aoVoltar = {}) } }
        listOf(
            "Como funciona o aviso de saída?",
            "Porque devo tirar a app da poupança de bateria?",
            "O que quer dizer “horário” e “tempo real”?",
            "Como guardo um sítio nos favoritos?",
        ).forEach { regra.onNodeWithText(it).assertExists() }
    }

    @Test @Config(qualifiers = "w360dp-h1200dp")
    fun preferenciasRotasMudamOsFiltros() {
        val filtros = Filtros()
        val mudancas = mutableListOf<Filtros>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { PreferenciasRotas(filtros, aoMudar = { mudancas += it }) } }
        regra.onNodeWithText("10 min").performClick()
        regra.onNodeWithText("Rápido").performClick()
        regra.onNodeWithText("Menos transbordos").performClick()
        regra.onNodeWithText("Chegada mais cedo").performClick()
        regra.onNodeWithText("Metro", substring = true).performClick()
        regra.onNodeWithText("Percursos acessíveis").performClick()
        assertEquals(
            listOf(
                filtros.copy(maxAPeMin = 10), filtros.copy(ritmo = Ritmo.RAPIDO), filtros.copy(ordem = Ordem.MENOS_TRANSBORDOS),
                filtros.copy(ordem = Ordem.CHEGADA_MAIS_CEDO), filtros.copy(modos = setOf(Modo.METRO)), filtros.copy(acessivel = true),
            ),
            mudancas,
        )
    }

    @Test @Config(qualifiers = "w360dp-h1200dp")
    fun preferenciasRotasMostramAsEscolhasAtuais() {
        val filtros = Filtros(modos = setOf(Modo.METRO), maxAPeMin = 20, ritmo = Ritmo.LENTO, ordem = Ordem.MENOS_A_PE, acessivel = true)
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { PreferenciasRotas(filtros, aoMudar = {}) } }
        regra.onNodeWithText("20 min").assertIsSelected()
        regra.onNodeWithText("Lento").assertIsSelected()
        regra.onNodeWithText("Menos a pé").assertIsSelected()
        regra.onNodeWithText("Metro", substring = true).assertIsSelected()
        regra.onNodeWithText("Percursos acessíveis").assertIsOn()
    }

    @Test @Config(qualifiers = "w360dp-h2200dp")
    fun oEcraInicialEscolheSeNasDefinicoes() {
        var escolhido: EcraInicial? = null
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { EcraDefinicoes(aoMudarEcraInicial = { escolhido = it }) } }
        regra.onNodeWithText("Mapa").assertIsSelected()
        regra.onNodeWithText("Favoritos — o Início com os favoritos abertos").performClick()
        assertEquals(EcraInicial.FAVORITOS, escolhido)
        regra.onNodeWithText("Rotas").performClick()
        assertEquals(EcraInicial.ROTAS, escolhido)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun asAtualizacoesDizemQuandoProcuram() {
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                SecaoAtualizacoes(sozinha = true, dadosMoveis = false, aoMudarSozinha = {}, aoMudarDadosMoveis = {})
            }
        }
        regra.onNodeWithText("Procura versões novas quando abres a app").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun osInterruptoresDoAvisoMostramOEstadoEMudam() {
        val mudancas = mutableListOf<String>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Definicoes(
                    ModoTema.SISTEMA, Limites(), somAviso = true, vibracaoAviso = false,
                    aoMudarModo = {}, aoMudarLimites = {},
                    aoMudarSom = { mudancas += "som=$it" }, aoMudarVibracao = { mudancas += "vibracao=$it" },
                )
            }
        }
        regra.onNodeWithText("Som do aviso").assertIsOn()
        regra.onNodeWithText("Vibração do aviso").assertIsOff()
        regra.onNodeWithText("Som do aviso").performClick()
        regra.onNodeWithText("Vibração do aviso").performClick()
        assertEquals(listOf("som=false", "vibracao=true"), mudancas)
    }
}
