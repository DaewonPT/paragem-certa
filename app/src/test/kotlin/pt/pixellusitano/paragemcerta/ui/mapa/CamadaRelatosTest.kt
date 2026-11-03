package pt.pixellusitano.paragemcerta.ui.mapa

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.CorrecaoRemota
import pt.pixellusitano.paragemcerta.core.fontes.servidor.DadosComunidade
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
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
import pt.pixellusitano.paragemcerta.ui.inicio.ESPERA_RELATOS_MS
import pt.pixellusitano.paragemcerta.ui.inicio.InicioViewModel
import pt.pixellusitano.paragemcerta.ui.inicio.rememberRelatosDoMapa
import pt.pixellusitano.paragemcerta.ui.menu.DefinicoesEcra
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.net.URLDecoder

/** O servidor da Paragem Certa para o mapa e as rotas: [relatos] é o que a leitura devolve; guarda o URL de cada pedido à API. */
private class ServidorDoMapa : Http {
    var config = fixture("servidor-config.json") // a que vem na app: tudo desligado
    var relatos: List<String> = emptyList()
    val pedidos = mutableListOf<String>()

    fun a(rota: String) = pedidos.filter { rota in it }

    // As chaves pedidas à rota, por pedido, sem o URL-encoding.
    fun chaves(rota: String, parametro: String) = a(rota).map { URLDecoder.decode(it.substringAfter("$parametro="), "UTF-8").split(",") }

    override suspend fun get(url: String): Resposta = Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        return Resposta(200, """{"relatos":[${relatos.joinToString(",")}]}""")
    }
}

/** Um dono do ciclo de vida que o teste põe em segundo plano (CREATED) e à vista (STARTED). */
internal class Dono : LifecycleOwner {
    val registo = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registo
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp")
class CamadaRelatosTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDoMapa()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade

    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val oriente = Paragem(RefParagem(Fonte.CM, "070001"), "Oriente", 38.7678, -9.0990, null)

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)))
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
    }

    @After fun fechar() = bd.close()

    private fun ligar() = runBlocking {
        http.config = http.config.replace("\"comunidade\": false", "\"comunidade\": true")
        configuracoes.aoAbrir()
    }

    private fun relato(paragem: String, confirmado: Boolean, id: String = "r-$paragem", tipo: String = "desvio") =
        RelatoRemoto(id, tipo, paragem, "736", 1, 2, if (confirmado) 2 else 1, confirmado)

    private fun json(r: RelatoRemoto) =
        """{"id":"${r.id}","tipo":"${r.tipo}","paragem":"${r.paragem}","linha":"${r.linha}","criadoEm":1,"expiraEm":2,"confirmacoes":${r.confirmacoes},"confirmado":${r.confirmado}}"""

    // ---- o que se desenha: as paragens à vista, corrigidas, e quais têm um relato confirmado ----

    @Test fun mapaMarcaSoOsConfirmados() {
        val dados = DadosComunidade(relatos = listOf(relato("CM:060419", confirmado = false), relato("CM:070001", confirmado = true)))
        assertEquals(listOf(cais to false, oriente to true), paragensDoMapa(listOf(cais, oriente), dados))
    }

    @Test fun relatoPorConfirmarNaMesmaParagemNaoTiraOConfirmado() {
        val dados = DadosComunidade(relatos = listOf(relato("CM:060419", confirmado = false, id = "a"), relato("CM:060419", confirmado = true, id = "b")))
        assertEquals(listOf(cais to true), paragensDoMapa(listOf(cais), dados))
    }

    @Test fun relatoDeOutraParagemNaoMarcaEstaParagem() {
        val dados = DadosComunidade(relatos = listOf(relato("CM:999999", confirmado = true)))
        assertEquals(listOf(cais to false, oriente to false), paragensDoMapa(listOf(cais, oriente), dados))
    }

    @Test fun paragemQueJaNaoExisteSaiDoMapa() {
        // Também sai com um relato confirmado: não há nada no sítio onde o pôr.
        val dados = DadosComunidade(
            relatos = listOf(relato("CM:060419", confirmado = true)),
            correcoes = listOf(CorrecaoRemota("CM:060419", "ja_nao_existe")),
        )
        assertEquals(listOf(oriente to false), paragensDoMapa(listOf(cais, oriente), dados))
    }

    @Test fun posicaoCorrigidaMoveOPonto() {
        val dados = DadosComunidade(
            relatos = listOf(relato("CM:060419", confirmado = true)),
            correcoes = listOf(CorrecaoRemota("CM:060419", "posicao", lat = 38.71, lon = -9.14), CorrecaoRemota("CM:070001", "nome", nome = "Gare do Oriente")),
        )
        val (movida, marcada) = paragensDoMapa(listOf(cais), dados).single()
        assertEquals(cais.copy(lat = 38.71, lon = -9.14), movida)
        assertTrue(marcada) // o relato é da paragem, não do ponto: vai com ela
        // O nome corrigido de outra paragem não mexe nesta, e a que não tem correções fica como está.
        assertEquals(listOf(oriente.copy(nome = "Gare do Oriente") to false), paragensDoMapa(listOf(oriente), dados))
    }

    @Test fun semDadosAsParagensFicamComoEstao() {
        assertEquals(listOf(cais to false, oriente to false), paragensDoMapa(listOf(cais, oriente), DadosComunidade()))
    }

    @Test fun oIconeDoRelatoEUmVetorDoMapa() {
        // Um "⚠" em texto não sai no mapa (as letras do OpenFreeMap não o têm): o ícone é um desenho.
        assertNotNull(contexto.getDrawable(R.drawable.relato_mapa))
    }

    // ---- o pedido do Início: com a comunidade e a camada ligadas, 1 s depois de a câmara parar, nunca em segundo plano ----

    private var paragens by mutableStateOf(listOf(cais))
    private var marcadas = ""
    private var dono: Dono? = null

    @Composable
    private fun Inicio() {
        val relatos = rememberRelatosDoMapa(paragens, comunidade, configuracoes, preferencias)
        marcadas = paragensDoMapa(paragens, relatos).joinToString { (p, relato) -> "${p.ref.chave}:$relato" }
        Text(marcadas)
    }

    private fun abrir() = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            val d = dono
            if (d == null) Inicio() else CompositionLocalProvider(LocalLifecycleOwner provides d) { Inicio() }
        }
    }

    // O relógio do teste anda à risca (sem arredondar aos 16 ms de um quadro).
    private fun passar(ms: Long) = regra.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)

    // Deixa a composição ver o que mudou: a notificação de um estado novo vai pela fila do ecrã, que só anda com o teste parado, e depois um quadro.
    private fun quadro() {
        regra.waitForIdle()
        regra.mainClock.advanceTimeByFrame()
    }

    // Muda o ciclo de vida da app (à vista = STARTED, em segundo plano = CREATED) e deixa os observadores tratarem disso.
    private fun irPara(estado: Lifecycle.State) {
        regra.runOnUiThread { dono!!.registo.currentState = estado }
        regra.waitForIdle()
    }

    @Test fun pedeUmSegundoDepoisDeACamaraParar() {
        ligar()
        http.relatos = listOf(json(relato("CM:060419", confirmado = true)))
        regra.mainClock.autoAdvance = false
        abrir()
        // O Início só entrega a lista 0,5 s depois de a câmara parar; com a espera daqui são 1 s.
        assertEquals(1_000L, ESPERA_RELATOS_MS + InicioViewModel.ESPERA_CAMARA_MS)
        passar(ESPERA_RELATOS_MS - 1)
        assertTrue(http.pedidos.isEmpty())
        passar(1)
        assertEquals(listOf(listOf("CM:060419")), http.chaves("/api/comunidade", "paragens"))
        quadro()
        assertEquals("CM:060419:true", marcadas)
    }

    @Test fun camadaDesligadaNaoPede() {
        ligar()
        runBlocking { preferencias.definirCamadaRelatos(false) }
        regra.mainClock.autoAdvance = false
        abrir()
        passar(5_000)
        assertTrue(http.a("/api/comunidade").isEmpty())
        assertEquals("CM:060419:false", marcadas) // as paragens desenham-se na mesma, sem relatos
        // Ligada outra vez nas Definições, o mapa pede.
        runBlocking { preferencias.definirCamadaRelatos(true) }
        quadro()
        passar(ESPERA_RELATOS_MS)
        assertEquals(1, http.a("/api/comunidade").size)
    }

    @Test fun comunidadeDesligadaNaoPedeNada() {
        // A config que vem na app traz tudo desligado: o mapa não pede, as rotas também não.
        regra.mainClock.autoAdvance = false
        abrir()
        passar(5_000)
        assertEquals(emptyList<RelatoRemoto>(), runBlocking { comunidade.confirmadosNasLinhas(listOf("736")) })
        assertEquals(DadosComunidade(), runBlocking { comunidade.dasParagens(listOf("CM:060419")) })
        assertTrue(http.pedidos.isEmpty())
        assertEquals("CM:060419:false", marcadas)
    }

    @Test fun semParagensNaoPede() {
        ligar()
        paragens = emptyList()
        regra.mainClock.autoAdvance = false
        abrir()
        passar(5_000)
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun mexerNoMapaRecomecaAEspera() {
        ligar()
        regra.mainClock.autoAdvance = false
        abrir()
        passar(ESPERA_RELATOS_MS - 200)
        paragens = listOf(cais, oriente) // a câmara parou noutro sítio antes de o pedido sair
        quadro()
        passar(ESPERA_RELATOS_MS - 100)
        assertTrue(http.pedidos.isEmpty()) // o pedido da lista antiga já não sai, e a espera da nova ainda não acabou
        passar(200)
        assertEquals(listOf(listOf("CM:060419", "CM:070001")), http.chaves("/api/comunidade", "paragens"))
    }

    @Test fun emSegundoPlanoNaoPede() {
        ligar()
        regra.mainClock.autoAdvance = false
        dono = Dono().also { it.registo.currentState = Lifecycle.State.CREATED } // a app não está à vista
        abrir()
        passar(10_000)
        assertTrue(http.pedidos.isEmpty())
        // Volta à frente: 1 s depois, pede.
        irPara(Lifecycle.State.STARTED)
        passar(ESPERA_RELATOS_MS - 1)
        assertTrue(http.pedidos.isEmpty())
        passar(1)
        assertEquals(1, http.a("/api/comunidade").size)
    }

    @Test fun irParaSegundoPlanoANeioDaEsperaCancelaOPedido() {
        ligar()
        regra.mainClock.autoAdvance = false
        dono = Dono().also { it.registo.currentState = Lifecycle.State.STARTED }
        abrir()
        passar(ESPERA_RELATOS_MS - 100)
        irPara(Lifecycle.State.CREATED)
        passar(10_000)
        assertTrue(http.pedidos.isEmpty())
    }

    // ---- as Definições: "Camadas do mapa" está sempre lá; os relatos só com a comunidade ligada ----

    private fun definicoes(comunidadeLigada: Boolean) = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) { DefinicoesEcra(preferencias, conta, aoVoltar = {}, comunidadeLigada = comunidadeLigada) }
    }

    @Test fun camadasSemComunidadeMostramAoVivoEGira() {
        definicoes(comunidadeLigada = false)
        regra.onNodeWithText(contexto.getString(R.string.eu_camadas_mapa)).performScrollTo().assertExists()
        regra.onNodeWithText(contexto.getString(R.string.eu_camada_relatos)).assertDoesNotExist()
        // Ligadas por omissão; cada interruptor muda só a sua camada.
        assertTrue(runBlocking { preferencias.camadaAoVivo.first() && preferencias.camadaGira.first() })
        regra.onNodeWithText(contexto.getString(R.string.eu_camada_ao_vivo)).performScrollTo().performClick()
        regra.waitForIdle()
        assertFalse(runBlocking { preferencias.camadaAoVivo.first() })
        assertTrue(runBlocking { preferencias.camadaGira.first() })
        regra.onNodeWithText(contexto.getString(R.string.eu_camada_gira)).performScrollTo().performClick()
        regra.waitForIdle()
        assertFalse(runBlocking { preferencias.camadaGira.first() })
    }

    @Test fun definicoesComComunidadeMostramEDesligamACamada() {
        definicoes(comunidadeLigada = true)
        regra.onNodeWithText(contexto.getString(R.string.eu_camadas_mapa)).performScrollTo().assertExists()
        assertTrue(runBlocking { preferencias.camadaRelatos.first() }) // ligada por omissão
        regra.onNodeWithText(contexto.getString(R.string.eu_camada_relatos)).performScrollTo().performClick()
        regra.waitForIdle()
        assertFalse(runBlocking { preferencias.camadaRelatos.first() })
    }
}
