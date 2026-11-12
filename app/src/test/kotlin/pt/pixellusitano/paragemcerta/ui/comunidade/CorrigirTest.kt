package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.CorrecaoRemota
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.ParagemEntidade
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemConteudo
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemEcra
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.io.IOException
import java.time.Instant

/** O servidor da Paragem Certa para as correções: [correcoes] é o que a leitura devolve; [porteiro] segura o envio até o teste o largar. */
private class ServidorDeCorrecoes : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true").replace("\"correcoes\": false", "\"correcoes\": true")
    var correcoes: List<String> = emptyList()
    var semRede = false
    var porteiro: CompletableDeferred<Unit>? = null
    val pedidos = mutableListOf<Triple<String, String, String?>>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/google" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/comunidade" to { Resposta(200, """{"correcoes":[${correcoes.joinToString(",")}]}""") },
        "/api/correcoes" to { Resposta(201, """{"id":"c9"}""") },
        "/api/termos" to { Resposta(200, "{}") },
    )

    fun a(rota: String) = pedidos.filter { rota in it.second }

    // Só a config existe; as chegadas e o resto da rede dão 404 (o ecrã da paragem verdadeiro pede-os).
    override suspend fun get(url: String): Resposta = if (url.endsWith("/dados/config.json")) Resposta(200, config) else Resposta(404, "")

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += Triple(metodo, url, corpo)
        if (semRede) throw IOException("sem rede")
        porteiro?.await()
        return rotas.entries.first { it.key in url }.value()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class CorrigirTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDeCorrecoes()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var repositorio: Repositorio
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val agora = Instant.parse("2026-11-03T12:00:00Z")
    private var abriuConta = 0
    private var centroDeArranque: LatLng? = null
    private var encontrada: Paragem? = null

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
        conta = Conta(contexto, servidor, repositorio)
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
    }

    @After fun fechar() = bd.close()

    private fun ligar() = runBlocking { configuracoes.aoAbrir() }

    private fun entrar() = runBlocking {
        assertEquals(Resultado.Ok(Unit), conta.entrarComGoogle("token-de-teste", juntarFavoritos = true))
        http.pedidos.clear() // contam-se só os pedidos da comunidade
    }

    /** Um mapa de brincar: guarda o centro com que abre e, ao carregar no botão, move-se para (38.71, -9.14). */
    private val mapaFalso: @Composable (LatLng, (LatLng) -> Unit) -> Unit = { centro, aoMover ->
        SideEffect { if (centroDeArranque == null) centroDeArranque = centro }
        Button(onClick = { aoMover(LatLng(38.71, -9.14)) }) { Text("Mover o mapa") }
    }

    /** Uma correção aprovada como o servidor a devolve. */
    private fun correcao(tipo: String, extra: String = "") = """{"paragem":"CM:060419","tipo":"$tipo"$extra}"""

    private val correcaoDoNome = correcao("nome", ",\"nome\":\"Cais do Sodré (Terminal)\"")

    @Composable
    private fun Ecra() {
        val relatos = rememberRelatosDaParagem(cais.ref.chave, cais, comunidade, configuracoes, preferencias, aoAbrirConta = { abriuConta++ })
        val (vista, jaNaoExiste) = aplicarCorrecoes(cais, relatos.correcoes)
        ParagemConteudo(
            nome = vista.nome, codigo = "060419", acessivel = null, favorita = false, vigiada = false, estado = null, filtro = null,
            linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
            aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
            aoEncontrar = { encontrada = vista },
            jaNaoExiste = jaNaoExiste,
            corrigir = { CorrigirNaParagem(vista, comunidade, configuracoes, aoAbrirConta = { abriuConta++ }, mapa = mapaFalso) },
            modifier = Modifier.width(360.dp),
        )
    }

    private fun abrir() = regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra() } }

    private fun toast(): String {
        regra.waitUntil(5_000) { regra.waitForIdle(); ShadowToast.getTextOfLatestToast() != null }
        return ShadowToast.getTextOfLatestToast()
    }

    private fun esperarPor(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun esperarQueSaia(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isEmpty() }

    /** Abre a folha: o botão "Corrigir dados" aparece com o interruptor ligado. */
    private fun abrirFolha() {
        abrir()
        esperarPor("Corrigir dados")
        regra.onNodeWithText("Corrigir dados").performClick()
        esperarPor("O que está errado?")
    }

    private fun escolher(opcao: String) = regra.onNodeWithText(opcao).performClick()

    private fun enviar() = regra.onNodeWithText("Enviar").performClick()

    private val obrigado = "Obrigado. A correção fica em revisão até o autor a aprovar."

    // ---- a folha ----

    @Test fun corrigirTemTresOpcoes() {
        ligar()
        abrirFolha()
        listOf("A paragem está no sítio errado", "O nome está errado", "A paragem já não existe").forEach { regra.onNodeWithText(it).assertIsDisplayed() }
        regra.onNodeWithText("Cancelar").assertIsDisplayed()
        assertTrue(http.a("/api/correcoes").isEmpty())
    }

    @Test fun posicaoEnviaOCentroDoMapa() {
        ligar()
        entrar()
        abrirFolha()
        escolher("A paragem está no sítio errado")
        esperarPor("Arrasta o mapa até o ponto ficar na paragem.")
        assertEquals(LatLng(cais.lat, cais.lon), centroDeArranque) // o mapa abre onde os dados põem a paragem
        regra.onNodeWithText("Mover o mapa").performClick()
        enviar()
        assertEquals(obrigado, toast())
        val (metodo, url, corpo) = http.a("/api/correcoes").single()
        assertEquals("POST", metodo)
        assertTrue(url.endsWith("/api/correcoes"))
        assertEquals("""{"paragem":"CM:060419","tipo":"posicao","lat":38.71,"lon":-9.14}""", corpo)
        esperarQueSaia("Arrasta o mapa")
    }

    @Test fun nomeVazioNaoEnvia() {
        ligar()
        entrar()
        abrirFolha()
        escolher("O nome está errado")
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
        regra.onNodeWithText("Nome certo").performTextInput("   ") // só espaços não é um nome
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
        regra.onNodeWithText("Nome certo").performTextInput("Cais")
        regra.onNodeWithText("Enviar").assertIsEnabled()
        assertTrue(http.a("/api/correcoes").isEmpty())
    }

    @Test fun nomeVaiCortado() {
        ligar()
        entrar()
        abrirFolha()
        escolher("O nome está errado")
        regra.onNodeWithText("Nome certo").performTextInput("  Cais do Sodré (Terminal)  ")
        enviar()
        assertEquals(obrigado, toast())
        assertEquals("""{"paragem":"CM:060419","tipo":"nome","nome":"Cais do Sodré (Terminal)"}""", http.a("/api/correcoes").single().third)
        esperarQueSaia("Nome certo")
    }

    @Test fun jaNaoExisteVaiSemMais() {
        ligar()
        entrar()
        abrirFolha()
        escolher("A paragem já não existe")
        enviar()
        assertEquals(obrigado, toast())
        assertEquals("""{"paragem":"CM:060419","tipo":"ja_nao_existe"}""", http.a("/api/correcoes").single().third)
    }

    @Test fun nomeConta80PontosDeCodigoComoOServidor() {
        ligar()
        entrar()
        abrirFolha()
        escolher("O nome está errado")
        // Um emoji ocupa 2 unidades UTF-16 mas é 1 caracter para o servidor: 81 cortam-se em 80 (não em 40) e os 80 passam.
        regra.onNodeWithText("Nome certo").performTextInput("😀".repeat(81))
        enviar()
        assertEquals(obrigado, toast())
        val nome = Json.parseToJsonElement(http.a("/api/correcoes").single().third!!).jsonObject["nome"]!!.jsonPrimitive.content
        assertEquals(80, nome.codePointCount(0, nome.length))
    }

    @Test fun nomeFicaNumaSoLinha() {
        ligar()
        entrar()
        abrirFolha()
        escolher("O nome está errado")
        regra.onNodeWithText("Nome certo").performTextInput("Cais\ndo Sodré\r")
        enviar()
        assertEquals(obrigado, toast())
        assertEquals("""{"paragem":"CM:060419","tipo":"nome","nome":"Cais do Sodré"}""", http.a("/api/correcoes").single().third)
    }

    // ---- depois de enviar ----

    @Test fun limiteDiarioMostraMensagem() {
        ligar()
        entrar()
        http.rotas["/api/correcoes"] = { Resposta(429, """{"erro":"muitos_pedidos"}""") }
        abrirFolha()
        escolher("A paragem já não existe")
        enviar()
        assertEquals("Chegaste ao limite de correções de hoje.", toast())
        regra.onNodeWithText("Enviar").assertIsEnabled() // a folha fica aberta
    }

    @Test fun semRedeAvisaEDeixaTentarOutraVezComOMesmoNome() {
        ligar()
        entrar()
        abrirFolha()
        escolher("O nome está errado")
        regra.onNodeWithText("Nome certo").performTextInput("Cais do Sodré (Terminal)")
        http.semRede = true
        enviar()
        assertEquals("Sem ligação. Não foi enviado.", toast())
        ShadowToast.reset()
        http.semRede = false
        enviar() // o nome continua no campo
        assertEquals(obrigado, toast())
        assertEquals(2, http.a("/api/correcoes").size)
        assertEquals(http.a("/api/correcoes")[0].third, http.a("/api/correcoes")[1].third)
    }

    @Test fun enquantoEnviaNaoDeixaEnviarOutraVez() {
        ligar()
        entrar()
        val porteiro = CompletableDeferred<Unit>()
        http.porteiro = porteiro
        abrirFolha()
        escolher("A paragem já não existe")
        enviar()
        regra.waitUntil(5_000) { regra.waitForIdle(); http.a("/api/correcoes").size == 1 }
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
        enviar()
        regra.waitForIdle()
        porteiro.complete(Unit)
        assertEquals(obrigado, toast())
        assertEquals(1, http.a("/api/correcoes").size)
    }

    @Test fun semContaPedeEntrarComOTextoDasCorrecoes() {
        ligar()
        abrirFolha()
        escolher("A paragem já não existe")
        enviar()
        esperarPor("Entra com o Google para corrigir dados")
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertTrue(http.a("/api/correcoes").isEmpty())
    }

    @Test fun servidorDesligadoEscondeOBotao() {
        ligar()
        entrar()
        http.rotas["/api/correcoes"] = { Resposta(403, """{"erro":"desligado"}""") }
        abrirFolha()
        escolher("A paragem já não existe")
        enviar()
        assertEquals("As correções estão desligadas por agora.", toast())
        esperarQueSaia("Corrigir dados")
    }

    // ---- as correções aprovadas ----

    @Test fun correcaoAprovadaMudaONome() {
        val (p, jaNaoExiste) = aplicarCorrecoes(
            cais,
            listOf(
                CorrecaoRemota("CM:060419", "nome", nome = "Cais do Sodré (Terminal)"),
                CorrecaoRemota("CM:060419", "posicao", lat = 38.7061, lon = -9.144),
                CorrecaoRemota("CM:020047", "nome", nome = "Outra paragem"), // de outra paragem: não se aplica
            ),
        )
        assertEquals(cais.copy(nome = "Cais do Sodré (Terminal)", lat = 38.7061, lon = -9.144), p)
        assertFalse(jaNaoExiste)
        assertEquals(cais to false, aplicarCorrecoes(cais, emptyList()))
        assertEquals(cais to false, aplicarCorrecoes(cais, listOf(CorrecaoRemota("CM:060419", "nome", nome = " "), CorrecaoRemota("CM:060419", "posicao"))))
    }

    @Test fun ecraUsaONomeCorrigidoTambemParaEncontrar() {
        ligar()
        http.correcoes = listOf(correcaoDoNome, correcao("posicao", ",\"lat\":38.7061,\"lon\":-9.144"))
        abrir()
        esperarPor("Cais do Sodré (Terminal)")
        regra.onNodeWithText("Cais do Sodré").assertDoesNotExist() // o nome antigo já não se mostra (a busca é pelo texto todo)
        regra.onNodeWithText("Encontrar a paragem").performClick()
        assertEquals(cais.copy(nome = "Cais do Sodré (Terminal)", lat = 38.7061, lon = -9.144), encontrada)
    }

    @Test fun ecraDaParagemLigadoAosDadosUsaACorrigida() {
        ligar()
        runBlocking { bd.paragens().guardar(listOf(ParagemEntidade("CM:060419", "CM", "060419", cais.nome, "cais do sodre", cais.lat, cais.lon, null))) }
        http.correcoes = listOf(correcaoDoNome, correcao("posicao", ",\"lat\":38.7061,\"lon\":-9.144"), correcao("ja_nao_existe"))
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ParagemEcra(
                    cais.ref, repositorio, comunidade, configuracoes, preferencias, carregarFoto = { null },
                    aoEncontrar = { encontrada = it }, aoAbrirConta = {}, aoVoltar = {},
                )
            }
        }
        esperarPor("Cais do Sodré (Terminal)")
        esperarPor("Segundo os passageiros, esta paragem já não existe.")
        esperarPor("Corrigir dados")
        regra.onNodeWithText("Encontrar a paragem").performClick()
        assertEquals(cais.copy(nome = "Cais do Sodré (Terminal)", lat = 38.7061, lon = -9.144), encontrada)
    }

    @Test fun paragemQueJaNaoExisteMostraAviso() {
        ligar()
        http.correcoes = listOf(correcao("ja_nao_existe"))
        abrir()
        esperarPor("Segundo os passageiros, esta paragem já não existe.")
        regra.onNodeWithText("Cais do Sodré").assertIsDisplayed() // a paragem continua a ver-se
    }

    @Test fun semCorrecoesNaoHaAviso() {
        ligar()
        abrir()
        esperarPor("Corrigir dados")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size)
        regra.onNodeWithText("Segundo os passageiros", substring = true).assertDoesNotExist()
    }

    @Test fun correcoesDesligadasEscondemOBotaoEIgnoramAsAprovadas() {
        http.config = http.config.replace("\"correcoes\": true", "\"correcoes\": false")
        ligar()
        http.correcoes = listOf(correcaoDoNome, correcao("ja_nao_existe"))
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size)
        regra.onNodeWithText("Cais do Sodré (Terminal)").assertDoesNotExist()
        regra.onNodeWithText("Segundo os passageiros", substring = true).assertDoesNotExist()
        regra.onNodeWithText("Corrigir dados").assertDoesNotExist()
    }

    @Test fun semAComunidadeNaoHaBotaoNemPedidos() {
        http.config = http.config.replace("\"comunidade\": true", "\"comunidade\": false")
        ligar()
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        regra.onNodeWithText("Corrigir dados").assertDoesNotExist()
        assertTrue(http.a("/api/comunidade").isEmpty())
    }
}
