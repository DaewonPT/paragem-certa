package pt.pixellusitano.paragemcerta.ui.comunidade

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import org.xmlpull.v1.XmlPullParser
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
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
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemConteudo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant

/** O Android a responder ao seletor de fotos e à câmara: [escolhida] é o que o seletor devolve; [aoTirar] faz o que a câmara faria com o URI. */
private class RegistoDeFotos : ActivityResultRegistry() {
    var escolhida: Uri? = null
    var aoTirar: (Uri) -> Boolean = { true }
    var semCamera = false
    val lancadas = mutableListOf<Any?>()

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        lancadas += input
        when (contract) {
            is ActivityResultContracts.PickVisualMedia -> dispatchResult(requestCode, escolhida)
            is ActivityResultContracts.TakePicture -> {
                if (semCamera) throw ActivityNotFoundException("sem câmara")
                dispatchResult(requestCode, aoTirar(input as Uri))
            }
            else -> error("contrato inesperado: $contract")
        }
    }
}

/** O servidor da Paragem Certa para as fotos: [fotos] e [emRevisao] são o que a leitura devolve; guarda cada pedido e cada JPEG que recebe. */
private class ServidorDeFotos : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true").replace("\"fotos\": false", "\"fotos\": true")
    var fotos: List<String> = emptyList()
    var emRevisao: List<String> = emptyList()
    var semRede = false
    val pedidos = mutableListOf<Triple<String, String, String?>>()
    val jpegs = mutableListOf<ByteArray>()
    val cabecalhosDosJpegs = mutableListOf<Map<String, String>>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/email/confirmar" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/comunidade" to { Resposta(200, """{"fotos":[${fotos.joinToString(",")}],"emRevisao":[${emRevisao.joinToString(",") { "\"$it\"" }}]}""") },
        "/api/fotos" to { Resposta(201, """{"id":"f9","estado":"pendente"}""") },
        "/api/denuncias" to { Resposta(204, "") },
        "/api/termos" to { Resposta(200, "{}") },
    )

    fun a(rota: String) = pedidos.filter { rota in it.second }

    override suspend fun get(url: String): Resposta = Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += Triple(metodo, url, corpo)
        if (semRede) throw IOException("sem rede")
        return rotas.entries.first { it.key in url }.value()
    }

    override suspend fun enviarBytes(metodo: String, url: String, corpo: ByteArray, tipo: String, cabecalhos: Map<String, String>): Resposta {
        assertEquals("image/jpeg", tipo)
        pedidos += Triple(metodo, url, null)
        jpegs += corpo
        cabecalhosDosJpegs += cabecalhos
        if (semRede) throw IOException("sem rede")
        return rotas.entries.first { it.key in url }.value()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class EnviarFotoTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDeFotos()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private val registo = RegistoDeFotos()
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val agora = Instant.parse("2026-11-03T12:00:00Z")
    private val jpegDaFoto = jpeg(1600, 1200)
    private val fotoF1 = """{"paragem":"CM:060419","id":"f1","url":"https://fotos.exemplo.pt/f1.jpg"}"""
    private var abriuConta = 0
    private val carregadas = mutableListOf<String>()
    private var carregarFoto: suspend (String) -> ByteArray? = { url -> carregadas += url; jpegDaFoto }
    private val uriDoFileProvider = Captura.uri
    private var capturaPedida: File? = null

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)))
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
        // O FileProvider da androidx compara caminhos com '/' e não corre num PC com Windows: aqui o endereço é o do próprio ficheiro.
        Captura.uri = { _, f -> capturaPedida = f; Uri.fromFile(f) }
    }

    @After fun fechar() {
        Captura.uri = uriDoFileProvider
        File(contexto.cacheDir, "fotos").deleteRecursively()
        bd.close()
    }

    private fun ligar() = runBlocking { configuracoes.aoAbrir() }

    private fun entrar() = runBlocking {
        assertEquals(Resultado.Ok(Unit), conta.confirmarCodigo("ana@exemplo.pt", "123456", juntarFavoritos = true))
        http.pedidos.clear() // contam-se só os pedidos da comunidade
    }

    @Composable
    private fun Ecra() {
        val relatos = rememberRelatosDaParagem(cais.ref.chave, cais, comunidade, configuracoes, preferencias, aoAbrirConta = { abriuConta++ })
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registo }) {
            ParagemConteudo(
                nome = "Cais do Sodré", codigo = "060419", acessivel = null, favorita = false, vigiada = false, estado = null, filtro = null,
                linhasPresentes = emptyList(), alertas = emptyList(), linhas = emptyMap(), agora = agora,
                aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
                relatos = relatos.lista, aoVotar = relatos.aoVotar, aoDenunciar = relatos.aoDenunciar, aoOcultar = relatos.aoOcultar,
                fotos = { FotosDaParagem(cais.ref.chave, relatos, comunidade, configuracoes, carregarFoto, aoAbrirConta = { abriuConta++ }) },
                modifier = Modifier.width(360.dp),
            )
        }
    }

    private fun abrir() = regra.setContent { TemaParagemCerta(ModoTema.CLARO) { Ecra() } }

    private fun toast(): String {
        regra.waitUntil(5_000) { regra.waitForIdle(); ShadowToast.getTextOfLatestToast() != null }
        return ShadowToast.getTextOfLatestToast()
    }

    private fun esperarPor(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun esperarQueSaia(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun semTexto(t: String) = regra.onAllNodesWithText(t, substring = true).assertCountEquals(0)

    /** Abre a folha: espera pela leitura da paragem (só então aparece o botão) e carrega nele. */
    private fun abrirFolha() {
        abrir()
        esperarPor("Enviar foto")
        regra.onNodeWithText("Enviar foto").performClick()
        esperarPor("Escolher foto")
    }

    /** A pessoa escolhe [bytes] no seletor de fotos; espera que a foto fique pronta. */
    private fun escolher(bytes: ByteArray = comGps(jpegDaFoto)) {
        registo.escolhida = Uri.fromFile(File(contexto.cacheDir, "escolhida.jpg").apply { writeBytes(bytes) })
        regra.onNodeWithText("Escolher foto").performClick()
        esperarPor("Foto pronta para enviar.")
    }

    private fun enviar() = regra.onNodeWithText("Enviar").performClick()

    private val rotuloDaFolha = "Fotografa a paragem, sem pessoas nem matrículas."
    private val licenca = "Ao enviar, aceitas a licença CC BY-SA 4.0 e que a foto seja creditada como “Passageiro da Paragem Certa”."

    // ---- o ecrã da paragem ----

    @Test fun enviarFotoSoSemFoto() {
        ligar()
        abrir()
        esperarPor("Enviar foto")
        regra.onNodeWithText("Foto em revisão").assertDoesNotExist()
        regra.onNodeWithText("Foto de um passageiro", substring = true).assertDoesNotExist()
    }

    @Test fun comFotoEmRevisaoNaoHaBotao() {
        ligar()
        http.emRevisao = listOf("CM:060419")
        abrir()
        esperarPor("Foto em revisão")
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
    }

    @Test fun comFotoAprovadaNaoHaBotao() {
        ligar()
        http.fotos = listOf(fotoF1)
        abrir()
        esperarPor("Foto de um passageiro")
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
        regra.onNodeWithText("Foto em revisão").assertDoesNotExist()
    }

    @Test fun semALeituraDaParagemNaoHaBotao() {
        ligar()
        http.semRede = true // sem ela não se sabe se já há foto
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size)
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
    }

    @Test fun fotosDesligadasEscondemTudo() {
        http.config = http.config.replace("\"fotos\": true", "\"fotos\": false")
        ligar()
        http.fotos = listOf(fotoF1)
        http.emRevisao = listOf("CM:060419")
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size)
        listOf("Enviar foto", "Foto em revisão", "Foto de um passageiro").forEach { regra.onNodeWithText(it, substring = true).assertDoesNotExist() }
        assertTrue(carregadas.isEmpty())
    }

    @Test fun semAComunidadeNaoHaFotos() {
        http.config = http.config.replace("\"comunidade\": true", "\"comunidade\": false")
        ligar()
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
        assertTrue(http.a("/api/comunidade").isEmpty()) // com a comunidade desligada nem se pergunta
    }

    // ---- a folha ----

    @Test fun avisoAntesDeEnviar() {
        ligar()
        abrirFolha()
        esperarPor(rotuloDaFolha)
        regra.onNodeWithText(rotuloDaFolha).assertIsDisplayed()
        regra.onNodeWithText(licenca).assertIsDisplayed()
        regra.onNodeWithText("Enviar").assertIsNotEnabled() // ainda sem foto
        val topo = { t: String -> regra.onNodeWithText(t).getUnclippedBoundsInRoot().top }
        assertTrue(topo(rotuloDaFolha) < topo(licenca))
        assertTrue(topo(licenca) < topo("Enviar"))
        assertTrue(http.jpegs.isEmpty())
    }

    @Test fun fotoEscolhidaVaiLimpaERecebeOServidor() {
        ligar()
        entrar()
        http.rotas["/api/fotos"] = {
            http.emRevisao = listOf("CM:060419")
            Resposta(201, """{"id":"f9","estado":"pendente"}""")
        }
        abrirFolha()
        val original = comGps(jpegDaFoto)
        assertTrue("o JPEG de partida devia trazer o Exif", String(original, Charsets.ISO_8859_1).contains("Exif"))
        escolher(original)
        regra.onNodeWithText("Enviar").assertIsEnabled()
        enviar()
        assertEquals("Foto enviada. Fica em revisão até o autor a aprovar.", toast())
        val enviada = http.jpegs.single()
        assertEquals(listOf(0xff, 0xd8, 0xff), enviada.take(3).map { it.toInt() and 0xff })
        assertFalse(String(enviada, Charsets.ISO_8859_1).contains("Exif"))
        assertTrue(enviada.size <= 300 * 1024)
        assertEquals("Bearer tok-ana", http.cabecalhosDosJpegs.single()["Authorization"])
        assertEquals("POST", http.a("/api/fotos").single().first)
        assertTrue(http.a("/api/fotos").single().second.endsWith("/api/fotos?paragem=CM%3A060419"))
        // A folha fecha e a paragem passa a mostrar "Foto em revisão".
        esperarPor("Foto em revisão")
        esperarQueSaia("Escolher foto")
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
    }

    @Test fun fotoQueJaChegouMostraEmRevisao() {
        ligar()
        entrar()
        // A resposta de um envio anterior perdeu-se, mas a foto chegou: o servidor diz 409 e a paragem já está em revisão.
        http.rotas["/api/fotos"] = {
            http.emRevisao = listOf("CM:060419")
            Resposta(409, """{"erro":"ja_tem_foto"}""")
        }
        abrirFolha()
        escolher()
        enviar()
        assertEquals("Já há uma foto desta paragem em revisão.", toast())
        esperarPor("Foto em revisão")
        esperarQueSaia("Escolher foto")
        regra.onNodeWithText("Enviar foto").assertDoesNotExist()
    }

    @Test fun semRedeGuardaAFotoParaTentarOutraVez() {
        ligar()
        entrar()
        abrirFolha()
        escolher()
        http.semRede = true
        enviar()
        esperarPor("Sem ligação. Não foi enviado.")
        assertEquals(1, http.jpegs.size)
        regra.onNodeWithText("Enviar").assertDoesNotExist() // o botão passou a "Tentar outra vez"
        http.semRede = false
        regra.onNodeWithText("Tentar outra vez").performClick()
        assertEquals("Foto enviada. Fica em revisão até o autor a aprovar.", toast())
        assertEquals(2, http.jpegs.size)
        assertArrayEquals(http.jpegs[0], http.jpegs[1]) // a mesma foto, sem a escolher outra vez
        assertEquals(1, registo.lancadas.size) // o seletor abriu uma só vez
    }

    private fun recusa(codigo: Int, erro: String, mensagem: String, podeTentarOutraVez: Boolean) {
        ligar()
        entrar()
        http.rotas["/api/fotos"] = { Resposta(codigo, """{"erro":"$erro"}""") }
        abrirFolha()
        escolher()
        enviar()
        esperarPor(mensagem)
        regra.onNodeWithText(mensagem).assertIsDisplayed()
        // Uma foto recusada não se repete: tem de se escolher outra. Os outros erros deixam tentar com a mesma.
        if (podeTentarOutraVez) regra.onNodeWithText("Tentar outra vez").assertIsEnabled() else regra.onNodeWithText("Enviar").assertIsNotEnabled()
        regra.onNodeWithText("Escolher foto").assertIsEnabled()
        assertEquals(1, http.jpegs.size)
    }

    @Test fun fotoGrandeTemMensagemPropria() = recusa(413, "foto_grande", "A foto é demasiado grande. Escolhe outra.", podeTentarOutraVez = false)

    @Test fun fotoComMetadadosTemMensagemPropria() =
        recusa(400, "foto_com_metadados", "Esta foto traz dados escondidos que não conseguimos tirar. Escolhe outra.", podeTentarOutraVez = false)

    @Test fun limiteDeFotosTemMensagemPropria() =
        recusa(429, "muitos_pedidos", "Hoje já não dá para enviar mais fotos. Tenta outra vez amanhã.", podeTentarOutraVez = true)

    @Test fun errosSemMensagemPropriaCaemNaGeral() = recusa(502, "guardar_falhou", "Não foi enviado. Tenta outra vez.", podeTentarOutraVez = true)

    @Test fun emPausaNaoEFaltaDeRede() {
        ligar()
        entrar()
        http.rotas["/api/fotos"] = { Resposta(503, """{"erro":"em_pausa"}""") }
        abrirFolha()
        escolher()
        enviar()
        esperarPor("Algumas funções estão em pausa até à meia-noite")
        // Segunda vez: a pausa responde sem pedir (Falha(null, true, "em_pausa")) e continua a ser pausa, não falta de rede.
        regra.onNodeWithText("Tentar outra vez").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Algumas funções estão em pausa até à meia-noite").assertIsDisplayed()
        semTexto("Sem ligação")
        assertEquals(1, http.jpegs.size)
    }

    @Test fun semContaPedeEntrarComOTextoDasFotos() {
        ligar()
        abrirFolha()
        escolher()
        enviar()
        esperarPor("Entra com Google ou email para enviar fotos")
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertTrue(http.jpegs.isEmpty())
    }

    @Test fun termosPorAceitarMostramOsTermosEDepoisEnviamAMesmaFoto() {
        ligar()
        entrar()
        var vezes = 0
        http.rotas["/api/fotos"] = {
            if (vezes++ == 0) Resposta(403, """{"erro":"termos_por_aceitar"}""") else Resposta(201, """{"id":"f9","estado":"pendente"}""")
        }
        abrirFolha()
        escolher()
        enviar()
        esperarPor("Termos e regras da comunidade")
        regra.onNodeWithText("Aceito").performClick()
        assertEquals("Foto enviada. Fica em revisão até o autor a aprovar.", toast())
        assertEquals(1, http.a("/api/termos").size)
        assertEquals(2, http.jpegs.size)
        assertArrayEquals(http.jpegs[0], http.jpegs[1])
    }

    @Test fun servidorDesligadoEscondeASecao() {
        ligar()
        entrar()
        http.rotas["/api/fotos"] = { Resposta(403, """{"erro":"desligado"}""") }
        abrirFolha()
        escolher()
        enviar()
        assertEquals("As fotos estão desligadas por agora.", toast())
        esperarQueSaia("Enviar foto")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size) // esconde logo e não volta a ler
    }

    @Test fun imagemEstragadaPedeOutra() {
        ligar()
        abrirFolha()
        registo.escolhida = Uri.fromFile(File(contexto.cacheDir, "escolhida.jpg").apply { writeText("isto não é uma imagem") })
        regra.onNodeWithText("Escolher foto").performClick()
        esperarPor("Não foi possível usar esta foto. Escolhe outra.")
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
    }

    @Test fun cancelarOSeletorNaoFazNada() {
        ligar()
        abrirFolha()
        registo.escolhida = null // a pessoa saiu do seletor sem escolher
        regra.onNodeWithText("Escolher foto").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
        semTexto("Não foi possível")
        semTexto("Foto pronta")
    }

    // ---- a câmara ----

    @Test fun tirarFotoApagaOFicheiroDepoisDePreparado() {
        ligar()
        entrar()
        registo.aoTirar = { uri ->
            // Como a app da câmara: escreve no endereço que lhe demos, com o GPS no Exif.
            File(uri.path!!).writeBytes(comGps(jpegDaFoto))
            true
        }
        abrirFolha()
        regra.onNodeWithText("Tirar foto").performClick()
        esperarPor("Foto pronta para enviar.")
        val captura = capturaPedida!!
        // O ficheiro está na pasta que o FileProvider serve (a do res/xml/caminhos_fotos.xml) e apaga-se depois de preparado.
        val servida = File(contexto.cacheDir, pastasDoFileProvider().single().third!!)
        assertTrue("$captura fora de $servida", captura.toPath().startsWith(servida.toPath()))
        assertFalse("o ficheiro da câmara apaga-se depois de preparado", captura.exists())
        enviar()
        toast()
        assertFalse(String(http.jpegs.single(), Charsets.ISO_8859_1).contains("Exif"))
    }

    @Test fun cameraCanceladaApagaOQueFicouEFicaSemFoto() {
        ligar()
        registo.aoTirar = { uri ->
            File(uri.path!!).writeBytes(byteArrayOf(1, 2, 3))
            false
        }
        abrirFolha()
        regra.onNodeWithText("Tirar foto").performClick()
        regra.waitForIdle()
        assertFalse(capturaPedida!!.exists())
        regra.onNodeWithText("Enviar").assertIsNotEnabled()
        semTexto("Foto pronta")
    }

    @Test fun capturaQueFicouDeOutraVezNaoEntra() {
        ligar()
        entrar()
        // Sobras de uma captura que não chegou ao fim: apagam-se antes de a câmara abrir, senão a foto nova podia ser a velha.
        File(contexto.cacheDir, "fotos").mkdirs()
        File(contexto.cacheDir, "fotos/captura.jpg").writeBytes(byteArrayOf(9, 9, 9))
        registo.aoTirar = { uri ->
            assertFalse("a captura velha devia ter sido apagada", File(uri.path!!).exists())
            File(uri.path!!).writeBytes(jpegDaFoto)
            true
        }
        abrirFolha()
        regra.onNodeWithText("Tirar foto").performClick()
        esperarPor("Foto pronta para enviar.")
    }

    @Test fun semAppDeCameraAvisa() {
        ligar()
        registo.semCamera = true
        abrirFolha()
        regra.onNodeWithText("Tirar foto").performClick()
        assertEquals("Não há nenhuma app de câmara.", toast())
        regra.onNodeWithText("Escolher foto").assertIsEnabled() // a folha continua
    }

    // ---- a foto na paragem ----

    @Test fun fotoAprovadaMostraOCredito() {
        ligar()
        http.fotos = listOf(fotoF1)
        abrir()
        esperarPor("Foto de um passageiro · CC BY-SA 4.0")
        regra.waitUntil(5_000) { regra.onAllNodesWithContentDescription("Foto da paragem, enviada por um passageiro").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("https://fotos.exemplo.pt/f1.jpg"), carregadas)
    }

    @Test fun fotoLeSeForaDaLinhaDoEcra() {
        ligar()
        http.fotos = listOf(fotoF1)
        var hilo: Thread? = null
        carregarFoto = { hilo = Thread.currentThread(); jpegDaFoto }
        abrir()
        esperarPor("Foto de um passageiro")
        regra.waitUntil(5_000) { hilo != null }
        assertNotEquals(Looper.getMainLooper().thread, hilo)
    }

    @Test fun fotoQueNaoCarregaDizQueNaoCarregou() {
        ligar()
        http.fotos = listOf(fotoF1)
        carregarFoto = { null }
        abrir()
        esperarPor("Não foi possível carregar a foto.")
        regra.onNodeWithText("Foto de um passageiro · CC BY-SA 4.0").assertIsDisplayed() // continua a poder-se ocultar e denunciar
    }

    @Test fun bytesQueNaoSaoUmaImagemDizemQueNaoCarregou() {
        ligar()
        http.fotos = listOf(fotoF1)
        carregarFoto = { "<html>não é uma foto</html>".toByteArray() }
        abrir()
        esperarPor("Não foi possível carregar a foto.")
    }

    @Test fun ocultarFotoEsconde() {
        ligar()
        http.fotos = listOf(fotoF1)
        abrir()
        esperarPor("Foto de um passageiro")
        regra.onNodeWithContentDescription("Mais opções da foto").performClick()
        regra.onNodeWithText("Ocultar esta foto").performClick()
        esperarQueSaia("Foto de um passageiro")
        assertEquals(setOf("foto:f1"), runBlocking { preferencias.ocultos.first() })
    }

    @Test fun denunciarFotoUsaOIdDaFoto() {
        ligar()
        entrar()
        http.fotos = listOf(fotoF1)
        abrir()
        esperarPor("Foto de um passageiro")
        regra.onNodeWithContentDescription("Mais opções da foto").performClick()
        regra.onNodeWithText("Denunciar").performClick()
        esperarPor("Denunciar esta foto")
        semTexto("Denunciar este relato")
        regra.onNodeWithText("O teu nome").performTextInput("Rita")
        regra.onNodeWithText("Declaro que faço esta denúncia de boa-fé e que o que digo é verdade.").performClick()
        regra.onNodeWithText("Enviar denúncia").performClick()
        assertEquals("Denúncia enviada.", toast())
        assertEquals("""{"conteudo":"foto:f1","motivo":"falso","nome":"Rita","explicacao":"","boaFe":true}""", http.a("/api/denuncias").single().third)
    }

    // ---- privacidade ----

    @Test fun naoPedePermissoesDeGaleriaNemCamara() {
        val pedidas = contexto.packageManager.getPackageInfo(contexto.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue("o manifesto devia ter sido lido: $pedidas", "android.permission.INTERNET" in pedidas)
        listOf(
            "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.CAMERA",
        ).forEach { assertFalse(it, it in pedidas) }
    }

    @Test fun fileProviderDasFotosNaoEExportadoEServeSoACacheDasFotos() {
        val info = contexto.packageManager.getProviderInfo(ComponentName(contexto, "androidx.core.content.FileProvider"), PackageManager.GET_META_DATA)
        assertEquals("${contexto.packageName}.fotos", info.authority)
        assertFalse("o FileProvider não pode ser exportado", info.exported)
        assertTrue(info.grantUriPermissions)
        assertEquals(listOf(Triple("cache-path", "fotos", "fotos/")), pastasDoFileProvider())
    }

    @Test fun seletorDeFotosAntigoPedeOModuloDoPlayServices() {
        val servico = contexto.packageManager.getServiceInfo(
            ComponentName(contexto, "com.google.android.gms.metadata.ModuleDependencies"), PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertFalse("o serviço só serve para o Play services ler a metainformação", servico.enabled)
        assertFalse(servico.exported)
        assertTrue(servico.metaData.containsKey("photopicker_activity:0:required"))
    }

    // ---- a imagem lida do servidor ----

    @Test fun fotoGrandeLeSeComTamanhoLimitado() {
        val bitmap = decodificarFoto(jpeg(4000, 3000))
        assertNotNull(bitmap)
        val maior = maxOf(bitmap!!.width, bitmap.height)
        assertTrue("lado maior $maior", maior in 640..1280)
        assertEquals(1280 to 960, decodificarFoto(jpeg(1280, 960))!!.let { it.width to it.height }) // as da app lêem-se inteiras
    }

    @Test fun bytesQueNaoSaoUmaImagemNaoDaoBitmap() {
        assertNull(decodificarFoto(ByteArray(0)))
        assertNull(decodificarFoto("isto não é uma imagem".toByteArray()))
        assertNull(decodificarFoto(jpeg(800, 600).copyOf(40)))
    }

    // ---- ajudas ----

    /** As pastas que o FileProvider das fotos serve, lidas do que o manifesto aponta: (tipo, nome, caminho). */
    private fun pastasDoFileProvider(): List<Triple<String, String?, String?>> {
        val info = contexto.packageManager.getProviderInfo(ComponentName(contexto, "androidx.core.content.FileProvider"), PackageManager.GET_META_DATA)
        val caminhos = contexto.resources.getXml(info.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))
        val pastas = mutableListOf<Triple<String, String?, String?>>()
        while (caminhos.next() != XmlPullParser.END_DOCUMENT) {
            if (caminhos.eventType == XmlPullParser.START_TAG && caminhos.name != "paths") {
                pastas += Triple(caminhos.name, caminhos.getAttributeValue(null, "name"), caminhos.getAttributeValue(null, "path"))
            }
        }
        return pastas
    }

    private fun jpeg(largura: Int, altura: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 120, 200)) }
        val saida = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, saida))
        bitmap.recycle()
        return saida.toByteArray()
    }

    /** O mesmo JPEG com o GPS no Exif, como sai de uma câmara com a localização ligada. */
    private fun comGps(jpeg: ByteArray): ByteArray {
        val ficheiro = File.createTempFile("gps", ".jpg", contexto.cacheDir).apply { writeBytes(jpeg) }
        try {
            ExifInterface(ficheiro.path).apply {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "38/1,42/1,20/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                saveAttributes()
            }
            return ficheiro.readBytes()
        } finally {
            ficheiro.delete()
        }
    }
}
