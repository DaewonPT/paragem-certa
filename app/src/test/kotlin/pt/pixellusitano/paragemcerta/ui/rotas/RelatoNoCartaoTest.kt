package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.time.Instant

/** O servidor da Paragem Certa para os cartões: `/api/avisos` devolve [relatos]; guarda o URL de cada pedido à API. */
private class ServidorDosCartoes : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true")
    var relatos: List<String> = emptyList()
    val pedidos = mutableListOf<String>()

    override suspend fun get(url: String): Resposta = Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        return Resposta(200, """{"relatos":[${relatos.joinToString(",")}]}""")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp")
class RelatoNoCartaoTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDosCartoes()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var comunidade: Comunidade

    // A rota da fixture (a pé, autocarro 210, a pé), com a linha a chamar-se "736" como no exemplo do plano.
    private val rota: Itinerario = TransitousParser.rotas(fixture("transitous-plan.json"))[0].let { r ->
        r.copy(etapas = r.etapas.map { e -> if (e.aPe) e else e.copy(linhaNome = "736") })
    }
    private val agora = Instant.parse("2026-11-01T11:10:00Z")

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        val conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)))
        val configuracoes = Configuracoes(contexto, servidor).also { runBlocking { it.aoAbrir() } }
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
    }

    @After fun fechar() = bd.close()

    private fun relato(tipo: String = "desvio", linha: String = "736", confirmado: Boolean = true, id: String = "r-$tipo-$linha") =
        RelatoRemoto(id, tipo, "CM:060419", linha, 1, 2, if (confirmado) 2 else 1, confirmado)

    private fun json(r: RelatoRemoto) =
        """{"id":"${r.id}","tipo":"${r.tipo}","paragem":"${r.paragem}","linha":"${r.linha}","criadoEm":1,"expiraEm":2,"confirmacoes":${r.confirmacoes},"confirmado":${r.confirmado}}"""

    // ---- que relato leva o cartão ----

    @Test fun cartaoLevaOPrimeiroConfirmadoDaLinha() {
        assertEquals("r-desvio-736", relatoDoCartao(rota, listOf(relato("desvio")))?.id)
        assertNull(relatoDoCartao(rota, emptyList()))
    }

    @Test fun relatoPorConfirmarNaoVaiParaOCartao() {
        assertNull(relatoDoCartao(rota, listOf(relato(confirmado = false))))
    }

    @Test fun soContamAsLinhasEmVeiculoDaRota() {
        assertNull(relatoDoCartao(rota, listOf(relato(linha = "210"), relato(linha = "7360"), relato(linha = ""))))
    }

    @Test fun nomeDaLinhaSeEscritoEmMinusculasTambemApanha() {
        val minusculas = rota.copy(etapas = rota.etapas.map { e -> if (e.aPe) e else e.copy(linhaNome = " 15e ") })
        assertEquals("r-desvio-15E", relatoDoCartao(minusculas, listOf(relato(linha = "15E")))?.id)
    }

    @Test fun relatosDaParagemNuncaVaoParaOCartao() {
        // Um elevador avariado não é um aviso da linha, e um tipo que esta versão da app não conhece não se mostra.
        assertNull(relatoDoCartao(rota, listOf(relato("elevador"), relato("problema_paragem"), relato("tipo_novo"))))
    }

    @Test fun cartaoEscolheOMaisGravePelaOrdemFixa() {
        val todos = listOf("vai_cheio", "nao_passou", "evento", "desvio", "acidente").map { relato(it) }
        // Por ordem: acidente, desvio, evento, não passou, vai cheio.
        assertEquals("acidente", relatoDoCartao(rota, todos)?.tipo)
        assertEquals("desvio", relatoDoCartao(rota, todos.filter { it.tipo != "acidente" })?.tipo)
        assertEquals("evento", relatoDoCartao(rota, todos.filter { it.tipo in setOf("evento", "nao_passou", "vai_cheio") })?.tipo)
        assertEquals("nao_passou", relatoDoCartao(rota, todos.filter { it.tipo in setOf("nao_passou", "vai_cheio") })?.tipo)
        assertEquals("vai_cheio", relatoDoCartao(rota, todos.filter { it.tipo == "vai_cheio" })?.tipo)
    }

    @Test fun relatoOcultadoNaoApareceNoCartao() = runBlocking {
        http.relatos = listOf(json(relato("desvio", id = "r1")))
        assertEquals("r1", relatoDoCartao(rota, comunidade.confirmadosNasLinhas(listOf("736")))?.id)
        preferencias.ocultar("relato:r1")
        assertNull(relatoDoCartao(rota, comunidade.confirmadosNasLinhas(listOf("736"))))
    }

    @Test fun linhasDasRotasSaoAsEmVeiculoSemRepetir() {
        assertEquals(listOf("736"), linhasDasRotas(listOf(rota, rota)))
        assertEquals(emptyList<String>(), linhasDasRotas(emptyList()))
    }

    // ---- o que o cartão diz ----

    private val desvio = relato("desvio")

    private fun cartao(relato: RelatoRemoto?, comAlerta: Boolean = false) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            CartaoRota(rota, emptySet(), comAlerta = comAlerta, aoAbrir = {}, agora = agora, relato = relato)
        }
    }

    // O cartão limpa a semântica dos filhos (lê-se só a descrição), por isso o texto procura-se na árvore sem fundir.
    private fun texto(t: String) = regra.onNodeWithText(t, useUnmergedTree = true)

    @Test fun cartaoDizDesvioConfirmado() {
        cartao(desvio)
        texto("⚠ Desvio confirmado na 736").assertIsDisplayed()
        // O leitor de ecrã lê a mesma frase na descrição do cartão.
        regra.onNode(hasContentDescription("⚠ Desvio confirmado na 736", substring = true)).assertExists()
    }

    @Test fun relatoFicaNoLugarDoAvisoNumaLinha() {
        cartao(desvio, comAlerta = true)
        texto("⚠ Desvio confirmado na 736").assertIsDisplayed()
        texto("⚠ aviso numa linha").assertDoesNotExist()
        regra.onNode(hasContentDescription("Aviso numa linha", substring = true)).assertDoesNotExist()
    }

    @Test fun semRelatoOAvisoNumaLinhaFicaComoEstava() {
        cartao(null, comAlerta = true)
        texto("⚠ aviso numa linha").assertIsDisplayed()
        regra.onNode(hasContentDescription("Aviso numa linha", substring = true)).assertExists()
        regra.onNodeWithText("confirmado", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun semRelatoNemAvisoNaoHaLinhaDeAviso() {
        cartao(null)
        regra.onNodeWithText("⚠", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun descricaoDoCartaoUsaOMesmoTextoDoRelato() {
        val d = descricaoRota(rota, emptyList(), comAlerta = true, Limites(), relatoTexto = "⚠ Desvio confirmado na 736")
        assertTrue(d.endsWith("⚠ Desvio confirmado na 736."))
        assertFalse(d.contains("Aviso numa linha"))
        assertTrue(descricaoRota(rota, emptyList(), comAlerta = true, Limites()).endsWith("Aviso numa linha."))
    }

    // ---- a lista de rotas põe o relato no cartão certo ----

    @Test fun listaDeRotasMostraORelatoNoCartaoDaLinha() {
        val rotas = TransitousParser.rotas(fixture("transitous-plan.json"))
        // A fixture tem duas rotas na linha 210 e uma na 208: o relato é só da 208.
        val linhas = rotas.map { r -> r.etapas.filter { !it.aPe }.mapNotNull { it.linhaNome } }
        assertTrue(linhas.any { "208" in it } && linhas.any { "210" in it })
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                RotasConteudo(
                    pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)),
                    estado = EstadoRotas.Prontas(rotas, etiquetas(rotas)), filtros = Filtros(), alertas = emptySet(), agora = agora,
                    aoEscolherOrigem = {}, aoEscolherDestino = {}, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
                    aoAbrirRota = {}, aoTentarOutraVez = {}, aoMaisCedo = {}, aoMaisTarde = {},
                    relatos = listOf(relato("acidente", linha = "208")),
                )
            }
        }
        val cartoes = regra.onAllNodesWithText("⚠ Acidente confirmado na 208", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(rotas.count { r -> r.etapas.any { !it.aPe && it.linhaNome == "208" } }, cartoes)
        assertTrue(cartoes in 1 until rotas.size)
    }
}
