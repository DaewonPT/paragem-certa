package pt.pixellusitano.paragemcerta.ui.comunidade

import pt.pixellusitano.paragemcerta.ui.comum.textoDoAviso
import pt.pixellusitano.paragemcerta.R
import android.Manifest
import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
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
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstadoConta
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import pt.pixellusitano.paragemcerta.ui.viagem.Exemplo
import pt.pixellusitano.paragemcerta.ui.viagem.ViagemEcra
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import java.io.File
import java.io.IOException
import java.time.Instant

/** O servidor da Paragem Certa: o config.json e a API, com as respostas em [rotas]; guarda (método, url, corpo) de cada pedido à API. */
private class ServidorDeRelatos : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true")
    var semRede = false
    val pedidos = mutableListOf<Triple<String, String, String?>>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/google" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/relatos" to { Resposta(201, """{"id":"r9","novo":true}""") },
        "/api/termos" to { Resposta(200, "{}") },
    )

    fun a(rota: String) = pedidos.filter { rota in it.second }

    override suspend fun get(url: String): Resposta = Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += Triple(metodo, url, corpo)
        if (semRede) throw IOException("sem rede")
        return rotas.entries.first { it.key in url }.value()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class RelatarTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDeRelatos()
    private val servidor = ServidorParagemCerta(http)
    private val loja = LojaEmMemoria()
    private val preferencias = Preferencias(loja)
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val posicaoDoSistema = Perto.posicao
    private var posicoesPedidas = 0
    private var abriuConta = 0

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        FicheirosViagem.apagar(contexto)
        shadowOf(contexto).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)))
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
        Perto.posicao = { posicoesPedidas++; null }
    }

    @After fun fechar() {
        Perto.posicao = posicaoDoSistema
        FicheirosViagem.apagar(contexto)
        bd.close()
    }

    /** A config do servidor de teste traz a comunidade ligada. */
    private fun ligar() = runBlocking { configuracoes.aoAbrir() }

    private fun entrar() = runBlocking {
        assertEquals(Resultado.Ok(Unit), conta.entrarComGoogle("token-de-teste", juntarFavoritos = true))
        http.pedidos.clear() // contam-se só os pedidos da comunidade
    }

    /** O GPS a dizer que estás [metros] a norte da paragem, com 10 m de precisão. */
    private fun aDistancia(metros: Double) {
        Perto.posicao = {
            posicoesPedidas++
            Location("gps").apply {
                latitude = cais.lat + metros / 111_000.0
                longitude = cais.lon
                accuracy = 10f
            }
        }
    }

    private fun abrirParagem() = regra.setContent {
        TemaParagemCerta(ModoTema.CLARO) {
            RelatarNaParagem(cais, listOf("736", "15E"), comunidade, configuracoes, aoAbrirConta = { abriuConta++ })
        }
    }

    private fun relatarDesvio() {
        regra.onNodeWithText("Relatar").performClick()
        regra.onNodeWithText("Desvio").performClick()
        regra.onNodeWithText("736").performClick()
    }

    private fun toast(): String {
        regra.waitUntil(5_000) { regra.waitForIdle(); ShadowToast.getTextOfLatestToast() != null }
        return ShadowToast.getTextOfLatestToast()
    }

    private fun esperarPor(t: String) = regra.waitUntil(5_000) { regra.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private val corpoDesvio = """{"tipo":"desvio","paragem":"CM:060419","linha":"736"}"""

    // ---- a folha ----

    @Test fun grelhaTemOsSeteTipos() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { FolhaRelatar(listOf("736"), null, aoEscolher = { _, _ -> }, aoFechar = {}) } }
        listOf(
            "Acidente", "Desvio", "Evento", "Não passou / adiantado", "Vai cheio / não parou",
            "Elevador ou escada avariados", "Problema na paragem",
        ).forEach { regra.onNodeWithText(it).assertExists() }
    }

    @Test fun tipoDeLinhaPedeALinha() {
        val escolhas = mutableListOf<Pair<TipoRelato, String?>>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) { FolhaRelatar(listOf("736", "15E"), null, aoEscolher = { t, l -> escolhas += t to l }, aoFechar = {}) }
        }
        regra.onNodeWithText("Elevador ou escada avariados").performClick()
        assertEquals(listOf<Pair<TipoRelato, String?>>(TipoRelato.ELEVADOR to null), escolhas) // da paragem: vai logo, sem linha
        regra.onNodeWithText("Desvio").performClick()
        regra.onNodeWithText("15E").assertExists()
        regra.onNodeWithText("736").performClick()
        assertEquals(TipoRelato.DESVIO to "736", escolhas.last())
        assertEquals(2, escolhas.size)
    }

    @Test fun cadaTipoEUmBotao() {
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { FolhaRelatar(listOf("736"), null, aoEscolher = { _, _ -> }, aoFechar = {}) } }
        TipoRelato.entries.forEach { regra.onNodeWithText(contexto.getString(nomeDoTipo(it))).assertHasClickAction() }
    }

    @Test fun linhaNaoAnunciaSelecionado() {
        val escolhas = mutableListOf<Pair<TipoRelato, String?>>()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                FolhaRelatarConteudo(listOf("736"), null, aoEscolher = { t, l -> escolhas += t to l }, aoFechar = {}, tipoInicial = TipoRelato.DESVIO)
            }
        }
        regra.onNodeWithText("736").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)).assertHasClickAction().performClick()
        assertEquals(listOf<Pair<TipoRelato, String?>>(TipoRelato.DESVIO to "736"), escolhas)
    }

    // ---- a proximidade, só no telemóvel ----

    @Test fun longeDaParagemRecusaSemEnviar() {
        ligar()
        entrar()
        aDistancia(301.0)
        abrirParagem()
        relatarDesvio()
        assertEquals("Tens de estar a menos de 300 m da paragem para relatar.", toast())
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun pertoEnvia() {
        ligar()
        entrar()
        aDistancia(299.0)
        abrirParagem()
        relatarDesvio()
        assertEquals("Relato enviado", toast())
        assertEquals("POST", http.a("/api/relatos").single().first)
    }

    @Test fun relatarNaoMandaAPosicao() {
        ligar()
        entrar()
        aDistancia(299.0)
        abrirParagem()
        relatarDesvio()
        toast()
        val (_, url, corpo) = http.a("/api/relatos").single()
        assertEquals(corpoDesvio, corpo)
        assertTrue(url.endsWith("/api/relatos"))
        // Nem a posição nem a precisão vão em pedido nenhum.
        val lat = (cais.lat + 299.0 / 111_000.0).toString().take(6)
        assertTrue(http.pedidos.none { (_, u, c) -> lat in u || lat in c.orEmpty() || "lat" in c.orEmpty() || "precis" in c.orEmpty() })
    }

    @Test fun imprecisaPedeALocalizacaoPrecisa() {
        ligar()
        entrar()
        shadowOf(contexto).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION) // só a aproximada
        abrirParagem()
        relatarDesvio()
        assertEquals("Liga a localização precisa para relatar.", toast())
        assertEquals(0, posicoesPedidas)
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun semPermissaoExplicaAntesDePedir() {
        ligar()
        entrar()
        shadowOf(contexto).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        abrirParagem()
        relatarDesvio()
        esperarPor("A tua posição não sai do telemóvel.")
        assertEquals(0, posicoesPedidas)
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun aBordoDaLinhaNaoPedeAPosicao() {
        ligar()
        entrar()
        aDistancia(5_000.0)
        viagemNa736()
        abrirParagem()
        relatarDesvio()
        assertEquals("Relato enviado", toast())
        assertEquals(0, posicoesPedidas)
        assertEquals(corpoDesvio, http.a("/api/relatos").single().third)
    }

    // ---- conta, termos e falhas ----

    @Test fun semContaPedeEntrar() {
        ligar()
        aDistancia(10.0)
        abrirParagem()
        relatarDesvio()
        esperarPor("Entra com o Google para relatar")
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun sessaoRecusadaPedeEntrar() {
        ligar()
        entrar()
        aDistancia(10.0)
        // O token já não vale: a Conta sai sozinha e um 401 é o mesmo que não ter sessão, com ou sem "erro" no corpo.
        http.rotas["/api/relatos"] = { Resposta(401, "") }
        abrirParagem()
        relatarDesvio()
        esperarPor("Entra com o Google para relatar")
        assertEquals(EstadoConta.SemConta, conta.estado.value)
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertEquals(1, http.a("/api/relatos").size)
    }

    @Test fun termosPorAceitarMostraOsTermosEDepoisEnvia() {
        ligar()
        entrar()
        aDistancia(10.0)
        var vezes = 0
        http.rotas["/api/relatos"] = {
            if (vezes++ == 0) Resposta(403, """{"erro":"termos_por_aceitar"}""") else Resposta(201, """{"id":"r9","novo":true}""")
        }
        abrirParagem()
        relatarDesvio()
        esperarPor("Termos e regras da comunidade")
        regra.onNodeWithText("Aceito").performClick()
        assertEquals("Relato enviado", toast())
        assertEquals("""{"versao":"1"}""", http.a("/api/termos").single().third)
        assertEquals(2, http.a("/api/relatos").size)
        assertEquals(listOf(corpoDesvio, corpoDesvio), http.a("/api/relatos").map { it.third })
    }

    @Test fun termosSoSeRepetemUmaVez() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.rotas["/api/relatos"] = { Resposta(403, """{"erro":"termos_por_aceitar"}""") }
        abrirParagem()
        relatarDesvio()
        esperarPor("Termos e regras da comunidade")
        regra.onNodeWithText("Aceito").performClick()
        toast()
        regra.onNodeWithText("Termos e regras da comunidade").assertDoesNotExist()
        assertEquals(1, http.a("/api/termos").size)
        assertEquals(2, http.a("/api/relatos").size)
    }

    @Test fun agoraNaoNosTermosNaoEnvia() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.rotas["/api/relatos"] = { Resposta(403, """{"erro":"termos_por_aceitar"}""") }
        abrirParagem()
        relatarDesvio()
        esperarPor("Termos e regras da comunidade")
        regra.onNodeWithText("Agora não").performClick()
        regra.waitForIdle()
        assertTrue(http.a("/api/termos").isEmpty())
        assertEquals(1, http.a("/api/relatos").size)
    }

    @Test fun semRedeAvisaENaoGuardaNada() {
        ligar()
        entrar()
        aDistancia(10.0)
        val ficheiros = contexto.filesDir.parentFile!!.walk().map { it.path to it.length() }.toSet()
        http.semRede = true
        abrirParagem()
        relatarDesvio()
        assertEquals("Sem ligação. Não foi enviado.", toast())
        // Nada fica para reenviar mais tarde: nem ficheiros, nem preferências, nem outro pedido.
        regra.mainClock.advanceTimeBy(60_000)
        regra.waitForIdle()
        assertEquals(1, http.pedidos.size)
        assertEquals(ficheiros, contexto.filesDir.parentFile!!.walk().map { it.path to it.length() }.toSet())
        assertEquals(emptyPreferences(), loja.data.value)
    }

    @Test fun emPausaMostraAMensagem() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.rotas["/api/relatos"] = { Resposta(503, """{"erro":"em_pausa"}""") }
        abrirParagem()
        relatarDesvio()
        assertFalse(toast().contains("%"))
        assertEquals(textoDoAviso(contexto, R.string.servidor_em_pausa), toast())
    }

    @Test fun servidorDesligadoEscondeOBotao() {
        // A config da app ainda diz ligada, mas o servidor já desligou os relatos: "Tenta outra vez" era mentira.
        ligar()
        entrar()
        aDistancia(10.0)
        http.rotas["/api/relatos"] = { Resposta(403, """{"erro":"desligado"}""") }
        abrirParagem()
        relatarDesvio()
        assertEquals("Os relatos estão desligados por agora.", toast())
        regra.onNodeWithText("Relatar").assertDoesNotExist()
    }

    @Test fun linhaQueOServidorRecusaNaoAparece() {
        // O servidor recusa sempre esta chave (parênteses e mais de 16 caracteres): escolhê-la dava sempre "Tenta outra vez".
        ligar()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                RelatarNaParagem(cais, listOf("736", "Lisboa (Rossio) - Sintra"), comunidade, configuracoes, aoAbrirConta = {})
            }
        }
        regra.onNodeWithText("Relatar").performClick()
        regra.onNodeWithText("Desvio").performClick()
        regra.onNodeWithText("736").assertExists()
        regra.onNodeWithText("Lisboa (Rossio) - Sintra").assertDoesNotExist()
    }

    @Test fun pedidoEmPausaNaoDizSemLigacao() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.rotas["/api/relatos"] = { Resposta(503, """{"erro":"em_pausa"}""") }
        abrirParagem()
        relatarDesvio()
        assertFalse(toast().contains("%"))
        assertEquals(textoDoAviso(contexto, R.string.servidor_em_pausa), toast())
        // Durante a pausa o pedido nem sai: a falha é Falha(null, true, "em_pausa"), que sem código parece falta de rede mas não é.
        ShadowToast.reset()
        relatarDesvio()
        assertFalse(toast().contains("%"))
        assertEquals(textoDoAviso(contexto, R.string.servidor_em_pausa), toast())
        assertEquals(1, http.a("/api/relatos").size)
    }

    // ---- a bordo, no ecrã da viagem ----

    /** Uma viagem de agora, a bordo do 736 logo depois da terceira paragem, que na Carris Metropolitana é a 020047. */
    private fun viagemNa736(linhaNome: String? = null): PlanoViagem {
        val ex = Exemplo(Instant.now())
        val base = ex.plano()
        val etapas = base.itinerario.etapas.toMutableList()
        etapas[1] = etapas[1].copy(paragens = etapas[1].paragens.mapIndexed { i, p -> p.copy(id = "pt-Carris-Metropolitana_32004${5 + i}") })
        if (linhaNome != null) etapas[1] = etapas[1].copy(linhaNome = linhaNome)
        val plano = base.copy(itinerario = base.itinerario.copy(etapas = etapas))
        val estado = ex.aBordo(plano, 1, k = 2)
        assertEquals(2, Viagem(plano.itinerario).paragemPassada(estado))
        FicheirosViagem.gravarPlano(contexto, plano)
        FicheirosViagem.gravarEstado(contexto, estado)
        return plano
    }

    private val semMapa: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> Box(Modifier.fillMaxSize()) }

    @Test fun aBordoRelataNaUltimaParagemPassada() {
        ligar()
        entrar()
        viagemNa736()
        assertEquals("CM:020047" to "736", ultimaParagemDaViagem(contexto))
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ViagemEcra(aoVoltar = {}, mapa = semMapa, comunidade = comunidade, comunidadeLigada = true, aoAbrirConta = {})
            }
        }
        esperarPor("Relatar")
        regra.onNodeWithText("Relatar").performClick()
        regra.onNodeWithText("Desvio").performClick() // a linha é a da etapa: não se escolhe
        assertEquals("Relato enviado", toast())
        assertEquals("""{"tipo":"desvio","paragem":"CM:020047","linha":"736"}""", http.a("/api/relatos").single().third)
        assertEquals(0, posicoesPedidas)
    }

    @Test fun aBordoServidorDesligadoEscondeOBotao() {
        ligar()
        entrar()
        viagemNa736()
        http.rotas["/api/relatos"] = { Resposta(403, """{"erro":"desligado"}""") }
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ViagemEcra(aoVoltar = {}, mapa = semMapa, comunidade = comunidade, comunidadeLigada = true, aoAbrirConta = {})
            }
        }
        esperarPor("Relatar")
        regra.onNodeWithText("Relatar").performClick()
        regra.onNodeWithText("Desvio").performClick()
        assertEquals("Os relatos estão desligados por agora.", toast())
        regra.onNodeWithText("Relatar").assertDoesNotExist()
    }

    @Test fun aBordoDeUmaLinhaQueOServidorRecusaNaoHaRelatar() {
        ligar()
        viagemNa736(linhaNome = "Lisboa (Rossio) - Sintra")
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                ViagemEcra(aoVoltar = {}, mapa = semMapa, comunidade = comunidade, comunidadeLigada = true, aoAbrirConta = {})
            }
        }
        esperarPor("Faltam 3 paragens · sais em Oriente")
        regra.onNodeWithText("Relatar").assertDoesNotExist()
    }

    @Test fun comunidadeDesligadaEscondeOBotao() {
        // A config que vem na app traz a comunidade desligada (e não se pede a do servidor).
        viagemNa736()
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                Column {
                    RelatarNaParagem(cais, listOf("736"), comunidade, configuracoes, aoAbrirConta = {})
                    ViagemEcra(aoVoltar = {}, mapa = semMapa, comunidade = comunidade, comunidadeLigada = false, aoAbrirConta = {})
                }
            }
        }
        esperarPor("Faltam 3 paragens · sais em Oriente") // o ecrã da viagem já está lá
        regra.onNodeWithText("Relatar").assertDoesNotExist()
    }
}
