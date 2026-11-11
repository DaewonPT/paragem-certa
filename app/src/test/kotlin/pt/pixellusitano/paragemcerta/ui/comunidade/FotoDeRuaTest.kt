package pt.pixellusitano.paragemcerta.ui.comunidade

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.bussola.BussolaConteudo
import pt.pixellusitano.paragemcerta.ui.bussola.EstadoBussola
import pt.pixellusitano.paragemcerta.ui.paragem.ParagemEcra
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** O servidor da Paragem Certa só com o que as fotos precisam: a config e a leitura da paragem. O resto da rede dá 404. */
private class ServidorDeFotosDeRua : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true")
    var semRede = false
    val pedidos = CopyOnWriteArrayList<String>()

    override suspend fun get(url: String): Resposta = if (url.endsWith("/dados/config.json")) Resposta(200, config) else Resposta(404, "")

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        if (semRede) throw IOException("sem rede")
        return if ("/api/comunidade" in url) {
            Resposta(200, "{}")
        } else {
            Resposta(404, "")
        }
    }

    fun leituras() = pedidos.count { "/api/comunidade" in it }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class FotoDeRuaTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDeFotosDeRua()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private val ref = RefParagem(Fonte.CM, "060419")

    private val rua = FotoDeRua("pan-1", "https://panoramax.exemplo/pan-1/thumb.jpg", "Robot8A", "CC BY-SA 4.0", "2022-03-13")
    private val creditoRua = "Foto: Robot8A · CC BY-SA 4.0 · Panoramax · 2022"
    private val paginaDaRua = "https://api.panoramax.xyz/#focus=pic&pic=pan-1"

    private val carregadas = CopyOnWriteArrayList<String>()
    private var carregarFoto: suspend (String) -> ByteArray? = { url -> carregadas += url; jpeg() }
    private val abertas = mutableListOf<String>()

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, Conta(contexto, servidor, repositorio), configuracoes, preferencias)
    }

    @After fun fechar() = bd.close()

    private fun ligar() = runBlocking { configuracoes.aoAbrir() }

    private fun jpeg(largura: Int = 960, altura: Int = 720): ByteArray {
        val bitmap = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 120, 200)) }
        val saida = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, saida))
        bitmap.recycle()
        return saida.toByteArray()
    }

    private fun esperarPor(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun esperarQueSaia(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun semTexto(t: String) = regra.onAllNodesWithText(t, substring = true).assertCountEquals(0)

    /** O ecrã da paragem verdadeiro, ligado à config, à comunidade e às preferências; [rua] é o que o ficheiro das fotos de rua tem para a paragem. */
    private fun abrir(rua: () -> FotoDeRua? = { this.rua }) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            ParagemEcra(
                ref, repositorio, comunidade, configuracoes, preferencias, carregarFoto = carregarFoto, aoEncontrar = {},
                aoAbrirConta = {}, aoVoltar = {}, fotoDeRua = rua(), aoAbrirPagina = { abertas += it },
            )
        }
    }

    // ---- a foto no ecrã da paragem ----

    @Test fun ocultarEscondeAFotoDeRua() {
        ligar()
        abrir()
        esperarPor(creditoRua)
        regra.onNodeWithContentDescription("Mais opções da foto").performClick()
        regra.onNodeWithText("Denunciar").assertDoesNotExist() // a foto de rua só se oculta: não existe no nosso servidor
        regra.onNodeWithText("Ocultar esta foto").performClick()
        esperarQueSaia(creditoRua)
        assertEquals(setOf("foto:pan-1"), runBlocking { preferencias.ocultos.first() })
    }

    @Test fun comunidadeDesligadaAindaMostraARua() {
        http.config = http.config.replace("\"comunidade\": true", "\"comunidade\": false")
        ligar()
        abrir()
        esperarPor(creditoRua)
        regra.waitForIdle()
        assertEquals(0, http.leituras()) // com a comunidade desligada a leitura nem é pedida
    }

    @Test fun fotoDeRuaVeSeSemALeituraDaComunidade() {
        ligar()
        http.semRede = true
        abrir()
        esperarPor(creditoRua)
    }

    // ---- o crédito ----

    @Test fun creditoDizAutorLicencaEAno() {
        ligar()
        abrir()
        esperarPor(creditoRua)
        regra.waitUntil(5_000) { regra.onAllNodesWithContentDescription("Foto de rua da paragem, do Panoramax").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("https://panoramax.exemplo/pan-1/thumb.jpg"), carregadas.toList())
    }

    @Test fun creditoAbreAPaginaDaFoto() {
        ligar()
        abrir()
        esperarPor(creditoRua)
        regra.onNodeWithText(creditoRua).assertHasClickAction().performClick()
        assertEquals(listOf(paginaDaRua), abertas)
    }

    @Test fun fotoQueNaoCarregaDizQueNaoCarregou() {
        ligar()
        carregarFoto = { null }
        abrir()
        esperarPor("Não foi possível carregar a foto.")
        regra.onNodeWithText(creditoRua).assertIsDisplayed() // o crédito e a ligação à página continuam
    }

    // ---- a bússola ----

    private fun bussola(rua: FotoDeRua? = this.rua) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            BussolaConteudo(
                "Cais do Sodré", EstadoBussola.Ativa(120.0, rumo = 73.0, angulo = 80.0, semSensor = false, calibrar = false),
                aoPermitir = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
                foto = { FotoNaBussola(rua, preferencias, carregarFoto, aoAbrirPagina = { abertas += it }) },
            )
        }
    }

    @Test fun bussolaMostraAFotoDaParagem() {
        ligar()
        bussola()
        esperarPor(creditoRua)
        regra.onNodeWithText("120 m", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithContentDescription("Mais opções da foto").assertDoesNotExist() // por baixo do alvo e sem menu
        regra.onNodeWithText(creditoRua).performClick()
        assertEquals(listOf(paginaDaRua), abertas)
        assertEquals(0, http.leituras())
    }

    @Test fun bussolaSemChaveFuncionaSemFoto() {
        ligar()
        bussola(rua = null)
        regra.onNodeWithText("120 m", useUnmergedTree = true).assertIsDisplayed()
        regra.waitForIdle()
        semTexto("Foto")
        assertEquals(0, http.leituras())
        assertTrue(carregadas.isEmpty())
    }

    @Test fun bussolaNaoMostraAFotoOcultada() {
        ligar()
        runBlocking { preferencias.ocultar("foto:pan-1") }
        bussola()
        regra.waitForIdle()
        semTexto(creditoRua)
        assertTrue(carregadas.isEmpty())
    }

    @Test fun bussolaNaoPedeNadaAoServidor() {
        bussola(rua = rua)
        regra.waitForIdle()
        assertEquals(0, http.leituras())
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

    // ---- a imagem lida do servidor ----

    @Test fun fotoGrandeLeSeComTamanhoLimitado() {
        val bitmap = decodificarFoto(jpeg(4000, 3000))
        assertNotNull(bitmap)
        val maior = maxOf(bitmap!!.width, bitmap.height)
        assertTrue("lado maior $maior", maior in 640..1280)
        assertEquals(1280 to 960, decodificarFoto(jpeg(1280, 960))!!.let { it.width to it.height }) // as que já cabem lêem-se inteiras
    }

    @Test fun bytesQueNaoSaoUmaImagemNaoDaoBitmap() {
        assertNull(decodificarFoto(ByteArray(0)))
        assertNull(decodificarFoto("isto não é uma imagem".toByteArray()))
        assertNull(decodificarFoto(jpeg(800, 600).copyOf(40)))
    }
}
