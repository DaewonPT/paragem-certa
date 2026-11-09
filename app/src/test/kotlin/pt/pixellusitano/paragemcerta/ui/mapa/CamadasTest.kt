package pt.pixellusitano.paragemcerta.ui.mapa

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.emel.EstacaoGira
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Interruptores
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.EstacoesGira
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.inicio.CartaoGira
import pt.pixellusitano.paragemcerta.ui.inicio.PACOTE_GIRA
import pt.pixellusitano.paragemcerta.ui.inicio.abrirGira
import pt.pixellusitano.paragemcerta.ui.inicio.rememberGiraDoMapa
import pt.pixellusitano.paragemcerta.ui.inicio.rememberVeiculosDoMapa
import pt.pixellusitano.paragemcerta.ui.linhas.DETALHE_15E
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

private const val CARRIS_JSON = """{"atualizado":1793699990,"veiculos":[{"id":"5247","rota":"76_0","viagem":"4502_20261103_76_0_8","sentido":1,"lat":38.7068,"lon":-9.1754,"seq":19,"paragem":"12105","ts":1793699980}]}"""

/** O que o mapa pede: a CM e a EMEL (GET) e o servidor da Paragem Certa (a Carris ao vivo); guarda o URL de cada pedido. */
private class RedeDoMapa : Http {
    var carris = Resposta(200, CARRIS_JSON)
    val pedidos = mutableListOf<String>()

    fun a(parte: String) = pedidos.filter { parte in it }

    override suspend fun get(url: String): Resposta {
        pedidos += url
        return Resposta(200, if ("girastations" in url) fixture("emel-gira.json") else "[]")
    }

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        return carris
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp")
class CamadasTest {
    @get:Rule val regra = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val rede = RedeDoMapa()
    private val catalogo = lerCatalogo(fixture("linhas.json"))
    private var carrisLigado = true
    private val repositorio = Repositorio(
        bd, CarrisMetropolitana(rede), Transitous(rede), GeoApi(rede),
        servidor = ServidorParagemCerta(rede), interruptores = { Interruptores(carrisAoVivo = carrisLigado) }, catalogo = { catalogo },
    )
    private val preferencias = Preferencias(LojaEmMemoria())
    private val estacoes by lazy { EstacoesGira(pasta.root, Gira(rede)) { Instant.parse("2026-11-03T09:00:00Z") } }
    private var dono: Dono? = null

    @After fun fechar() = bd.close()

    // ---- o que o mapa do Início pede: só com o mapa à vista (STARTED), a camada ligada e o interruptor ligado ----

    // Como o InicioEcra, sem o MapView (o Robolectric não o desenha): as mesmas fontes, a mesma regra.
    @Composable
    private fun MapaAoVivo() {
        val cm = remember { repositorio.veiculos() }
        val carris = remember { repositorio.veiculosCarris() }
        val veiculos = rememberVeiculosDoMapa(cm, carris, preferencias)
        val gira = rememberGiraDoMapa(estacoes, preferencias)
        Text(veiculos.joinToString { it.id } + " / " + gira.size)
    }

    private fun abrir() = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            val d = dono
            if (d == null) MapaAoVivo() else CompositionLocalProvider(LocalLifecycleOwner provides d) { MapaAoVivo() }
        }
    }

    // O relógio do teste anda à risca (sem arredondar aos 16 ms de um quadro).
    private fun passar(ms: Long) = regra.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)

    // Deixa a composição ver o que mudou: a notificação de um estado novo vai pela fila do ecrã, que só anda com o teste parado, e depois um quadro.
    private fun quadro() {
        regra.waitForIdle()
        regra.mainClock.advanceTimeByFrame()
    }

    private fun irPara(estado: Lifecycle.State) {
        regra.runOnUiThread { dono!!.registo.currentState = estado }
        regra.waitForIdle()
    }

    // Ler o ficheiro das estações e converter uma resposta passam por fios de verdade (Dispatchers.IO e Default): o relógio do teste
    // não os espera, por isso espera-se por eles em tempo real, um quadro de cada vez.
    private fun esperarPor(condicao: () -> Boolean) {
        repeat(300) {
            if (condicao()) return
            Thread.sleep(10)
            quadro()
        }
        fail("não chegou o que se esperava; pedidos: ${rede.pedidos}")
    }

    private fun veiculosNoEcra() = regra.onAllNodesWithText("carris:5247", substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun carrisSoPedeComOMapaAVista() {
        regra.mainClock.autoAdvance = false
        dono = Dono().also { it.registo.currentState = Lifecycle.State.CREATED } // a app não está à vista
        abrir()
        passar(60_000)
        assertTrue(rede.a("/api/rt/carris").isEmpty())
        assertTrue(rede.a("/vehicles").isEmpty())

        // À vista, o primeiro pedido sai logo e o seguinte 30 s depois de a resposta chegar (os 30 s contam-se do que se vê no ecrã).
        irPara(Lifecycle.State.STARTED)
        esperarPor { veiculosNoEcra() }
        assertEquals(1, rede.a("/api/rt/carris").size)
        passar(29_000)
        assertEquals(1, rede.a("/api/rt/carris").size)
        passar(2_000)
        assertEquals(2, rede.a("/api/rt/carris").size)

        // Em segundo plano pára: nem a Carris nem a CM voltam a pedir.
        val carris = rede.a("/api/rt/carris").size
        val cm = rede.a("/vehicles").size
        irPara(Lifecycle.State.CREATED)
        passar(120_000)
        assertEquals(carris, rede.a("/api/rt/carris").size)
        assertEquals(cm, rede.a("/vehicles").size)
    }

    @Test fun camadaDesligadaNaoPede() {
        runBlocking {
            preferencias.definirCamadaAoVivo(false)
            preferencias.definirCamadaGira(false)
        }
        regra.mainClock.autoAdvance = false
        abrir()
        passar(60_000)
        // Nem a Carris, nem a CM, nem a EMEL.
        assertEquals(emptyList<String>(), rede.pedidos)

        // Ligadas outra vez nas Definições, o mapa pede.
        runBlocking {
            preferencias.definirCamadaAoVivo(true)
            preferencias.definirCamadaGira(true)
        }
        quadro()
        passar(1_000)
        assertEquals(1, rede.a("/api/rt/carris").size)
        assertEquals(1, rede.a("/vehicles").size)
        esperarPor { rede.a("girastations").size == 1 }
    }

    @Test fun semInterruptorNaoPedeCarris() {
        carrisLigado = false
        regra.mainClock.autoAdvance = false
        abrir()
        passar(60_000)
        assertTrue(rede.a("/api/rt/carris").isEmpty())
        assertTrue(rede.a("/vehicles").isNotEmpty()) // a camada está ligada: a CM não depende do interruptor

        // O config.json chega depois de o mapa abrir: a Carris passa a pedir sem ser preciso reabrir o mapa.
        carrisLigado = true
        passar(10_000)
        assertEquals(1, rede.a("/api/rt/carris").size)
    }

    @Test fun veiculosDaCarrisTemPrefixoENomeDoCatalogo() {
        val v = runBlocking { repositorio.veiculosCarris().first() }
        assertEquals(
            listOf(
                Veiculo(
                    id = "carris:5247", linhaId = "15E", lat = 38.7068, lon = -9.1754, rumo = null, ultimoSinal = Instant.ofEpochSecond(1793699980),
                    destino = null, viagem = "4502_20261103_76_0_8", paragem = "12105", aChegar = false,
                ),
            ),
            v,
        )
    }

    @Test fun carrisEmBaixoDaListaVazia() {
        rede.carris = Resposta(502, """{"erro":"fonte_falhou"}""")
        assertEquals(emptyList<Veiculo>(), runBlocking { repositorio.veiculosCarris().first() })
    }

    @Test fun veiculosTemCorPeloOperador() {
        fun v(id: String, linha: String) = Veiculo(id, linha, 38.7, -9.1, null, Instant.EPOCH)
        val cores = mapOf("1009" to "#E4002B")
        assertEquals("#F2B705" to "#1A1A1A", coresDoVeiculo(v("carris:5247", "15E"), cores, escuro = false))
        assertEquals("#F2B705" to "#1A1A1A", coresDoVeiculo(v("carris:5248", ""), cores, escuro = false)) // sem nome também é da Carris
        assertEquals("#E4002B" to "#FFFFFF", coresDoVeiculo(v("[YA15B]2261", "1009"), cores, escuro = false))
        assertEquals("#1F4FA3" to "#FFFFFF", coresDoVeiculo(v("[YA15B]2262", "1010"), cores, escuro = false)) // sem cor: o azul da app
        assertEquals("#132A4F" to "#FFFFFF", coresDoVeiculo(v("[YA15B]2263", "1011"), mapOf("1011" to "#132A4F"), escuro = false))
    }

    @Test fun corNeutraNoMapaEscuro() {
        // O cobalto (a cor das linhas sem cor publicada) não se vê no mapa escuro: lá passa ao azul-céu, como o primary do tema.
        assertEquals("#7FB0FF", corDaLinhaNoMapa("#1F4FA3", escuro = true))
        assertEquals("#1F4FA3", corDaLinhaNoMapa("#1F4FA3", escuro = false))
        assertEquals("#E4002B", corDaLinhaNoMapa("#E4002B", escuro = true))
        // O número por cima do círculo segue a regra do selo da linha: Tinta sobre o amarelo do elétrico, branco sobre cores escuras.
        assertEquals("#132A4F", corDoNumeroDoVeiculo("#E2A400"))
        assertEquals("#FFFFFF", corDoNumeroDoVeiculo("#E4002B"))
        assertEquals("#FFFFFF", corDoNumeroDoVeiculo("#132A4F"))
        // Um veículo da CM sem cor, no escuro: o azul-céu com o número em Tinta.
        fun v(id: String, linha: String) = Veiculo(id, linha, 38.7, -9.1, null, Instant.EPOCH)
        assertEquals("#7FB0FF" to "#132A4F", coresDoVeiculo(v("[YA15B]2262", "1010"), emptyMap(), escuro = true))
        assertEquals("#F2B705" to "#1A1A1A", coresDoVeiculo(v("carris:5247", "15E"), emptyMap(), escuro = true))
    }

    @Test fun linhaMostraOsVeiculosDela() {
        fun v(id: String, linha: String) = Veiculo(id, linha, 38.7, -9.1, null, Instant.EPOCH)
        val cm1009 = v("[YA15B]2261", "1009")
        val carris15E = v("carris:5247", "15E")
        val todos = listOf(cm1009, v("[YA15B]2262", "736"), carris15E, v("carris:1", "728"), v("carris:2", ""))
        // A Carris pelo nome da linha no catálogo (a rota do veículo já foi traduzida para ele), a CM pelo id da linha.
        assertEquals(listOf(carris15E), veiculosDaLinha(todos, DETALHE_15E))
        assertEquals(listOf(cm1009), veiculosDaLinha(todos, DETALHE_15E.copy(ref = RefLinha(Fonte.CM, "1009"), nome = "1009")))
        // A mesma "1009" noutro operador não apanha os autocarros da CM; e a "15E" da CM não apanha os da Carris.
        assertEquals(emptyList<Veiculo>(), veiculosDaLinha(todos, DETALHE_15E.copy(ref = RefLinha(Fonte.TRANSITOUS, "pt-Metro-Lisboa:1009"), nome = "1009")))
        assertEquals(emptyList<Veiculo>(), veiculosDaLinha(todos, DETALHE_15E.copy(ref = RefLinha(Fonte.CM, "15E"))))
    }

    @Test fun veiculosDoMapaJuntaAsFontesSoComACamadaLigada() {
        val cm = Veiculo("[YA15B]2261", "1009", 38.7, -9.1, null, Instant.EPOCH)
        val carris = Veiculo("carris:5247", "15E", 38.7, -9.1, null, Instant.EPOCH)
        assertEquals(listOf(cm, carris), veiculosDoMapa(listOf(cm), listOf(carris), aoVivo = true))
        assertEquals(emptyList<Veiculo>(), veiculosDoMapa(listOf(cm), listOf(carris), aoVivo = false))
    }

    @Test fun giraSoAbreComZoom13ENomeCom15() {
        assertFalse(giraVisivel(12.9))
        assertTrue(giraVisivel(13.0))
        assertFalse(nomeGiraVisivel(14.9))
        assertTrue(nomeGiraVisivel(15.0))
        assertTrue(giraVisivel(16.5) && nomeGiraVisivel(16.5))
    }

    @Test fun giraDrawableExiste() {
        assertNotNull(contexto.getDrawable(R.drawable.gira_mapa))
    }

    // ---- o cartão da estação GIRA ----

    private val vascoDaGama = EstacaoGira("105", "CC Vasco da Gama", 38.768548, -9.096222)

    @Test fun cartaoGiraMostraONomeOCreditoEAbreAApp() {
        var abriu = 0
        var fechou = 0
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { CartaoGira(vascoDaGama, aoAbrirApp = { abriu++ }, aoFechar = { fechou++ }) } }
        regra.onNodeWithText("CC Vasco da Gama").assertExists()
        regra.onNodeWithText("Estações GIRA: EMEL (dados.emel.pt), CC BY-SA 4.0").assertExists()
        regra.onNodeWithText("Ver disponibilidade na app GIRA").assertHasClickAction().performClick()
        regra.onNodeWithText("Fechar").performClick()
        assertEquals(1, abriu)
        assertEquals(1, fechou)
    }

    @Test fun cartaoGiraAbreAApp() {
        val pm = contexto.packageManager
        val loja = "market://details?id=com.vaimoo.emelsharing"
        val navegador = "https://play.google.com/store/apps/details?id=com.vaimoo.emelsharing"

        // Sem a app GIRA: a loja.
        val tentadas = mutableListOf<String>()
        assertTrue(abrirGira(pm) { tentadas += it.dataString.orEmpty() })
        assertEquals(listOf(loja), tentadas)

        // Sem a app da loja (telemóveis sem Google) o market:// não abre nada: o endereço da loja vai para o navegador.
        tentadas.clear()
        assertTrue(abrirGira(pm) { tentadas += it.dataString.orEmpty(); if (it.scheme == "market") throw ActivityNotFoundException() })
        assertEquals(listOf(loja, navegador), tentadas)

        // Com a app GIRA instalada abre-se ela (o launch intent), sem passar pela loja.
        val principal = ComponentName(PACOTE_GIRA, "$PACOTE_GIRA.Principal")
        shadowOf(pm).addActivityIfNotPresent(principal)
        shadowOf(pm).addIntentFilterForActivity(principal, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        val abertas = mutableListOf<Intent>()
        assertTrue(abrirGira(pm) { abertas += it })
        assertEquals(listOf(PACOTE_GIRA), abertas.map { it.`package` ?: it.component?.packageName })
        assertEquals(1, abertas.size)

        // Nada abre: não rebenta, diz que não abriu.
        assertFalse(abrirGira(pm) { throw ActivityNotFoundException() })
    }
}
