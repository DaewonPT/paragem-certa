package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.espresso.Espresso
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstadoConta
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.sobre.URL_PRIVACIDADE
import pt.pixellusitano.paragemcerta.ui.sobre.URL_TERMOS
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.io.IOException
import java.time.Instant

/** O servidor da Paragem Certa à mão: responde ao que o ecrã da conta pede e regista o que recebe (caminho → corpo). */
private class ServidorFalso : Http {
    var semRede = false
    var pedirCodigo = Resposta(204, "")
    var confirmar = Resposta(200, """{"token":"t-ana","email":"ana@exemplo.pt"}""")
    val pedidos = mutableListOf<Pair<String, String?>>()

    override suspend fun get(url: String): Resposta = error("só pedidos à API")

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        if (semRede) throw IOException("sem rede")
        val caminho = url.substringAfter("/api/")
        pedidos += caminho to corpo
        return when (caminho) {
            "auth/email/pedir" -> pedirCodigo
            "auth/email/confirmar" -> confirmar
            "auth/google/nonce" -> Resposta(200, """{"nonce":"nonce-do-servidor"}""")
            "auth/google" -> confirmar
            "sincronizar" -> Resposta(200, """{"revisao":1,"alteracoes":[]}""")
            else -> Resposta(404, "")
        }
    }

    fun enviadas(caminho: String) = pedidos.filter { it.first == caminho }.map { it.second.orEmpty() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContaScreenshotTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = HttpPorUrl("geocode" to listOf({ Resposta(200, "[]") }))
    private val repo = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
    private val servidor = ServidorFalso()
    private val conta = Conta(contexto, ServidorParagemCerta(servidor), repo)
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val sincronizadaEm = Instant.parse("2026-11-02T09:30:00Z") // em Lisboa, em novembro, é a hora de Greenwich

    @Before fun semSessao() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
    }

    @After fun fechar() = bd.close()

    @Composable
    private fun EcraConta(
        modifier: Modifier = Modifier,
        estado: EstadoConta = EstadoConta.SemConta,
        googleDisponivel: Boolean = true,
        passo: PassoEntrada = PassoEntrada.ESCOLHA,
        mensagem: String? = null,
        aoSincronizar: () -> Unit = {},
        aoSair: () -> Unit = {},
        aoApagarConta: () -> Unit = {},
        aoAbrir: (String) -> Unit = {},
    ) = ContaConteudo(
        estado = estado, googleDisponivel = googleDisponivel, passo = passo, mensagem = mensagem, aOcupar = false,
        aoEntrarComGoogle = {}, aoPedirCodigo = {}, aoMudarPasso = {}, aoConfirmarCodigo = { _, _ -> },
        aoSincronizar = aoSincronizar, aoSair = aoSair, aoApagarConta = aoApagarConta, aoAbrir = aoAbrir, aoVoltar = {}, modifier = modifier,
    )

    /** O ecrã a sério, ligado à [conta] de teste; [pedirToken] faz de Credential Manager. */
    private fun abrirEcra(webClientId: String = "cliente-web", pedirToken: suspend (android.app.Activity, String, String) -> String? = { _, _, _ -> null }) =
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ContaEcra(conta, repo, webClientId, aoAbrir = {}, aoVoltar = {}, pedirToken = pedirToken)
            }
        }

    private fun esperarPor(texto: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }

    private fun existe(texto: String) = regra.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()

    /** Do ecrã de entrada até carregar em "Entrar" com o código escrito. */
    private fun entrarPorEmail() {
        regra.onNodeWithText("Entrar com email").performClick()
        regra.onNodeWithText("Email").performTextInput("ana@exemplo.pt")
        regra.onNodeWithText("Enviar código").performClick()
        esperarPor("Código de 6 dígitos")
        regra.onNodeWithText("Código de 6 dígitos").performTextInput("123456")
        regra.onNodeWithText("Entrar").performClick()
    }

    @Test fun conta_sem_conta() = captureRoboImage("src/test/screenshots/conta_sem_conta.png") {
        TemaParagemCerta(ModoTema.CLARO) { EcraConta(modifier = Modifier.width(360.dp)) }
    }

    @Test fun conta_codigo() = captureRoboImage("src/test/screenshots/conta_codigo.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            EcraConta(passo = PassoEntrada.CODIGO, mensagem = "Código errado", modifier = Modifier.width(360.dp))
        }
    }

    @Test fun conta_com_conta() = captureRoboImage("src/test/screenshots/conta_com_conta.png") {
        TemaParagemCerta(ModoTema.CLARO) {
            EcraConta(estado = EstadoConta.Com("ana@exemplo.pt", sincronizadaEm), modifier = Modifier.width(360.dp))
        }
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun contaComContaMostraOEmailAHoraEAsAcoes() {
        var sincronizou = 0
        var saiu = 0
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                EcraConta(estado = EstadoConta.Com("ana@exemplo.pt", sincronizadaEm), aoSincronizar = { sincronizou++ }, aoSair = { saiu++ })
            }
        }
        regra.onNodeWithText("ana@exemplo.pt").assertExists()
        regra.onNodeWithText("Sincronizado às 09:30").assertExists()
        regra.onNodeWithText("Sincronizar agora").performClick()
        regra.onNodeWithText("Sair").performClick()
        assertEquals(1 to 1, sincronizou to saiu)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun apagarContaPedeConfirmacao() {
        var apagou = 0
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { EcraConta(estado = EstadoConta.Com("ana@exemplo.pt", null), aoApagarConta = { apagou++ }) }
        }
        regra.onNodeWithText("Apagar conta").performClick()
        regra.onNodeWithText("Apagar a conta e os favoritos guardados nela? Os favoritos ficam neste telemóvel.").assertExists()
        assertEquals(0, apagou)
        regra.onNodeWithText("Cancelar").performClick()
        assertEquals(0, apagou)
        assertFalse(existe("Apagar a conta e os favoritos guardados nela? Os favoritos ficam neste telemóvel."))
        regra.onNodeWithText("Apagar conta").performClick()
        regra.onNodeWithText("Apagar").performClick()
        assertEquals(1, apagou)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun termosEPoliticaAbremAsPaginasDoServidor() {
        val abertos = mutableListOf<String>()
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { EcraConta(aoAbrir = { abertos += it }) } }
        regra.onNodeWithText("Termos").performClick()
        regra.onNodeWithText("Política de privacidade").performClick()
        assertEquals(listOf(URL_TERMOS, URL_PRIVACIDADE), abertos)
        assertEquals("https://paragemcerta.pixellusitano.com/termos", URL_TERMOS)
        assertEquals("https://paragemcerta.pixellusitano.com/privacidade", URL_PRIVACIDADE)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun semClienteGoogleNaoMostraOBotao() {
        abrirEcra(webClientId = "")
        assertFalse(existe("Iniciar sessão com o Google"))
        regra.onNodeWithText("Entrar com email").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun comClienteGoogleMostraOBotao() {
        abrirEcra(webClientId = "cliente-web")
        regra.onNodeWithText("Iniciar sessão com o Google").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun googleIndisponivelSugereEmail() {
        abrirEcra(pedirToken = { _, _, _ -> null })
        regra.onNodeWithText("Iniciar sessão com o Google").performClick()
        esperarPor("Não foi possível entrar com o Google neste telemóvel. Usa o código por email.")
        regra.onNodeWithText("Entrar com email").assertExists()
        assertTrue(servidor.enviadas("auth/google").isEmpty())
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun googleEntraComOTokenEOPedidoUnicoDoServidor() {
        val pedidos = mutableListOf<Pair<String, String>>()
        abrirEcra(webClientId = "cliente-web") { _, cliente, nonce ->
            pedidos += cliente to nonce
            "token-da-google"
        }
        regra.onNodeWithText("Iniciar sessão com o Google").performClick()
        esperarPor("Sincronizar agora")
        assertEquals(listOf("cliente-web" to "nonce-do-servidor"), pedidos)
        assertEquals(listOf("""{"idToken":"token-da-google"}"""), servidor.enviadas("auth/google"))
        assertTrue(conta.estado.value is EstadoConta.Com)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun entrarPorEmailSemFavoritosNaoPergunta() {
        abrirEcra()
        entrarPorEmail()
        esperarPor("Sincronizar agora")
        assertFalse(existe("Juntar os favoritos deste telemóvel à conta?"))
        assertEquals(listOf("""{"email":"ana@exemplo.pt"}"""), servidor.enviadas("auth/email/pedir"))
        assertEquals(listOf("""{"email":"ana@exemplo.pt","codigo":"123456"}"""), servidor.enviadas("auth/email/confirmar"))
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun comFavoritosPerguntaESeNaoJuntarNaoOsEnvia() {
        runBlocking { repo.alternarFavorita(cais) }
        abrirEcra()
        entrarPorEmail()
        esperarPor("Juntar os favoritos deste telemóvel à conta?")
        assertTrue(servidor.enviadas("auth/email/confirmar").isEmpty()) // ainda não entrou: a pergunta vem antes
        regra.onNodeWithText("Não juntar").performClick()
        esperarPor("Sincronizar agora")
        assertTrue(servidor.enviadas("sincronizar").none { it.contains("CM:060419") })
        assertEquals(listOf("CM:060419"), runBlocking { repo.chavesNoTelemovel() }.toList()) // os favoritos ficam no telemóvel
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun comFavoritosPerguntaESeJuntarEnviaOsFavoritos() {
        runBlocking { repo.alternarFavorita(cais) }
        abrirEcra()
        entrarPorEmail()
        esperarPor("Juntar os favoritos deste telemóvel à conta?")
        regra.onNodeWithText("Juntar").performClick()
        esperarPor("Sincronizar agora")
        assertTrue(servidor.enviadas("sincronizar").any { it.contains("CM:060419") })
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun fecharAPerguntaNaoEntra() {
        runBlocking { repo.alternarFavorita(cais) }
        abrirEcra()
        entrarPorEmail()
        esperarPor("Juntar os favoritos deste telemóvel à conta?")
        Espresso.pressBack() // fechar a pergunta desiste da entrada
        regra.waitForIdle()
        assertFalse(existe("Juntar os favoritos deste telemóvel à conta?"))
        assertEquals(EstadoConta.SemConta, conta.estado.value)
        assertTrue(servidor.enviadas("auth/email/confirmar").isEmpty())
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun codigoErradoDizCodigoErrado() {
        servidor.confirmar = Resposta(401, """{"erro":"codigo_errado"}""")
        abrirEcra()
        entrarPorEmail()
        esperarPor("Código errado")
        regra.onNodeWithText("Código de 6 dígitos").assertExists() // fica no código para tentar outra vez
        assertEquals(EstadoConta.SemConta, conta.estado.value)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun muitosPedidosDeCodigoAvisamDaEspera() {
        servidor.pedirCodigo = Resposta(429, """{"erro":"muitos_pedidos"}""")
        abrirEcra()
        regra.onNodeWithText("Entrar com email").performClick()
        regra.onNodeWithText("Email").performTextInput("ana@exemplo.pt")
        regra.onNodeWithText("Enviar código").performClick()
        esperarPor("Pediste muitos códigos. Tenta daqui a uma hora.")
        assertFalse(existe("Código de 6 dígitos"))
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun semRedeDizSemLigacao() {
        servidor.semRede = true
        abrirEcra()
        regra.onNodeWithText("Entrar com email").performClick()
        regra.onNodeWithText("Email").performTextInput("ana@exemplo.pt")
        regra.onNodeWithText("Enviar código").performClick()
        esperarPor("Sem ligação")
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun servidorEmPausaDizAteQuando() {
        servidor.pedirCodigo = Resposta(503, """{"erro":"em_pausa"}""") // o D1 passou o limite grátis do dia
        abrirEcra()
        regra.onNodeWithText("Entrar com email").performClick()
        regra.onNodeWithText("Email").performTextInput("ana@exemplo.pt")
        regra.onNodeWithText("Enviar código").performClick()
        esperarPor("Algumas funções estão em pausa até à meia-noite")
    }
}
