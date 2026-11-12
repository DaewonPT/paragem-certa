package pt.pixellusitano.paragemcerta.ui.comunidade

import pt.pixellusitano.paragemcerta.ui.comum.textoDoAviso
import pt.pixellusitano.paragemcerta.R
import android.Manifest
import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.flow.first
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
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
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
import pt.pixellusitano.paragemcerta.ui.viagem.Exemplo
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import java.io.File
import java.io.IOException
import java.time.Instant

/** O servidor da Paragem Certa para o ecrã da paragem: [relatos] é o que a leitura devolve; guarda (método, url, corpo) de cada pedido à API. */
private class ServidorDaParagem : Http {
    var config = fixture("servidor-config.json").replace("\"comunidade\": false", "\"comunidade\": true")
    var relatos: List<String> = emptyList()
    var semRede = false
    val pedidos = mutableListOf<Triple<String, String, String?>>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/google" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/comunidade" to { Resposta(200, """{"relatos":[${relatos.joinToString(",")}]}""") },
        "/api/votos" to { Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""") },
        "/api/denuncias" to { Resposta(204, "") },
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
class RelatosNaParagemTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDaParagem()
    private val servidor = ServidorParagemCerta(http)
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val posicaoDoSistema = Perto.posicao
    private val agora = Instant.parse("2026-11-03T12:00:00Z")
    private var abriuConta = 0

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        FicheirosViagem.apagar(contexto)
        shadowOf(contexto).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)))
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias)
        Perto.posicao = { null }
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
            Location("gps").apply {
                latitude = cais.lat + metros / 111_000.0
                longitude = cais.lon
                accuracy = 10f
            }
        }
    }

    /** Um relato como o servidor o devolve: feito há [minutos], com [confirmacoes] (2 ou mais = confirmado). */
    private fun relato(id: String = "r1", tipo: String = "desvio", linha: String = "736", confirmacoes: Int = 1, minutos: Long = 4) =
        """{"id":"$id","tipo":"$tipo","paragem":"CM:060419","linha":"$linha","criadoEm":${agora.minusSeconds(minutos * 60).toEpochMilli()},""" +
            """"expiraEm":${agora.plusSeconds(3600).toEpochMilli()},"confirmacoes":$confirmacoes,"confirmado":${confirmacoes >= 2}}"""

    @Composable
    private fun Ecra() {
        val relatos = rememberRelatosDaParagem(cais.ref.chave, cais, comunidade, configuracoes, preferencias, aoAbrirConta = { abriuConta++ })
        ParagemConteudo(
            nome = "Cais do Sodré", codigo = "060419", acessivel = null, favorita = false, vigiada = false, estado = null, filtro = null,
            linhasPresentes = listOf("736" to "736", "15E" to "15E"), alertas = emptyList(), linhas = emptyMap(), agora = agora,
            aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {},
            relatos = relatos.lista, aoVotar = relatos.aoVotar, aoDenunciar = relatos.aoDenunciar, aoOcultar = relatos.aoOcultar,
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

    private val boaFe = "Declaro que faço esta denúncia de boa-fé e que o que digo é verdade."

    private fun denunciarComNomeEBoaFe(nome: String = "Rita") {
        regra.onNodeWithText("Denunciar").performClick()
        esperarPor("Porquê?")
        regra.onNodeWithText("O teu nome").performTextInput(nome)
        regra.onNodeWithText(boaFe).performClick()
    }

    // ---- o cartão ----

    @Test fun relatoPorConfirmarEDiscreto() {
        ligar()
        http.relatos = listOf(relato(confirmacoes = 1))
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Desvio na 736").assertExists()
        regra.onNodeWithText("Relatado por 1 passageiro · por confirmar · há 4 min").assertExists()
        regra.onNodeWithText("Confirmado", substring = true).assertDoesNotExist()
    }

    @Test fun relatoConfirmadoEmDestaque() {
        ligar()
        http.relatos = listOf(relato(confirmacoes = 2))
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Desvio na 736").assertExists()
        regra.onNodeWithText("Confirmado por 2 passageiros · há 4 min").assertExists()
        regra.onNodeWithText("por confirmar", substring = true).assertDoesNotExist()
    }

    @Test fun quemRelatouSemVotosContinuaASerUmPassageiro() {
        ligar()
        http.relatos = listOf(relato(confirmacoes = 0)) // quem relatou mudou para "Já não está"
        abrir()
        esperarPor("Relatado por 1 passageiro · por confirmar")
    }

    @Test fun tempoEmHorasEDiasUsaPlurais() {
        ligar()
        http.relatos = listOf(
            relato(id = "r1", confirmacoes = 3, minutos = 0), relato(id = "r2", confirmacoes = 3, minutos = 180),
            relato(id = "r3", tipo = "problema_paragem", linha = "", confirmacoes = 3, minutos = 24 * 60),
            relato(id = "r4", tipo = "problema_paragem", linha = "", confirmacoes = 3, minutos = 3 * 24 * 60),
        )
        abrir()
        esperarPor("Problema na paragem")
        regra.onNodeWithText("Confirmado por 3 passageiros · agora mesmo").assertExists()
        regra.onNodeWithText("Confirmado por 3 passageiros · há 3 h").assertExists()
        regra.onNodeWithText("Confirmado por 3 passageiros · há 1 dia").assertExists()
        regra.onNodeWithText("Confirmado por 3 passageiros · há 3 dias").assertExists()
    }

    @Test fun relatoDaParagemNaoTemLinha() {
        ligar()
        http.relatos = listOf(relato(tipo = "elevador", linha = ""))
        abrir()
        esperarPor("Elevador ou escada avariados")
        regra.onNodeWithText("Elevador ou escada avariados").assertExists()
    }

    @Test fun relatoDeUmTipoQueAAppNaoConheceNaoApareceNemRebenta() {
        ligar()
        http.relatos = listOf(relato(id = "r1", tipo = "tipo_novo"), relato(id = "r2", tipo = "evento", linha = "15E"))
        abrir()
        esperarPor("Evento na 15E")
        regra.onNodeWithText("tipo_novo", substring = true).assertDoesNotExist()
    }

    @Test fun semRelatosNaoMostraNada() {
        ligar()
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size)
        listOf("Ainda está", "Já não está", "Denunciar", "passageiro").forEach { regra.onNodeWithText(it, substring = true).assertDoesNotExist() }
    }

    // ---- votos ----

    @Test fun aindaEstaVotaEAtualiza() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato(confirmacoes = 1))
        http.rotas["/api/votos"] = {
            http.relatos = listOf(relato(confirmacoes = 2))
            Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""")
        }
        abrir()
        esperarPor("Relatado por 1 passageiro")
        regra.onNodeWithText("Ainda está").performClick()
        esperarPor("Confirmado por 2 passageiros")
        val voto = http.a("/api/votos").single()
        assertEquals("POST", voto.first)
        assertEquals("""{"relato":"r1","voto":"ainda_esta"}""", voto.third)
        assertEquals(2, http.a("/api/comunidade").size) // ao abrir e depois do voto
        // Só diz que o voto ficou registado: se renova o tempo ou conta para já depende de quem vota, e o ecrã não promete mais.
        assertEquals("Voto registado.", toast())
    }

    @Test fun jaNaoEstaSaiQuandoOServidorDizInativo() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        http.rotas["/api/votos"] = {
            http.relatos = emptyList()
            Resposta(200, """{"confirmacoes":0,"confirmado":false,"ativo":false}""")
        }
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Já não está").performClick()
        esperarQueSaia("Desvio na 736")
        assertEquals("""{"relato":"r1","voto":"ja_nao_esta"}""", http.a("/api/votos").single().third)
    }

    @Test fun votarLongeRecusa() {
        ligar()
        entrar()
        aDistancia(301.0)
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        assertEquals("Tens de estar a menos de 300 m da paragem para relatar.", toast())
        assertTrue(http.a("/api/votos").isEmpty())
    }

    @Test fun aBordoDaLinhaVotaSemPedirAPosicao() {
        ligar()
        entrar()
        var posicoes = 0
        Perto.posicao = { posicoes++; null }
        viagemNa736()
        http.relatos = listOf(relato(linha = "736"), relato(id = "r2", tipo = "evento", linha = "15E"))
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithContentDescription("Ainda está: Desvio na 736").performClick()
        assertEquals("Voto registado.", toast())
        assertEquals(0, posicoes)
        // A bordo de uma linha não vale para outra: o relato da 15E pede a posição.
        ShadowToast.reset()
        regra.onNodeWithContentDescription("Ainda está: Evento na 15E").performClick()
        assertEquals("Não foi possível saber onde estás. Tenta outra vez.", toast())
        assertEquals(1, posicoes)
        assertEquals(1, http.a("/api/votos").size)
    }

    @Test fun votarSemContaPedeEntrar() {
        ligar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        esperarPor("Entra com o Google para votar")
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertTrue(http.a("/api/votos").isEmpty())
    }

    @Test fun votoComSessaoRecusadaPedeEntrar() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        http.rotas["/api/votos"] = { Resposta(401, "") }
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        esperarPor("Entra com o Google para votar")
    }

    @Test fun termosPorAceitarNoVotoMostramOsTermosEDepoisVotam() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        var vezes = 0
        http.rotas["/api/votos"] = {
            if (vezes++ == 0) Resposta(403, """{"erro":"termos_por_aceitar"}""") else Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""")
        }
        http.rotas["/api/termos"] = { Resposta(200, "{}") }
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        esperarPor("Termos e regras da comunidade")
        regra.onNodeWithText("Aceito").performClick()
        assertEquals("Voto registado.", toast())
        assertEquals(2, http.a("/api/votos").size)
    }

    @Test fun relatoQueJaAcabouSaiDaLista() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        http.rotas["/api/votos"] = {
            http.relatos = emptyList()
            Resposta(404, """{"erro":"nao_existe"}""")
        }
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        assertEquals("Este relato já acabou.", toast())
        esperarQueSaia("Desvio na 736")
    }

    @Test fun votoSemRedeAvisaEDeixaOEcraComoEstava() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        http.semRede = true
        regra.onNodeWithText("Ainda está").performClick()
        assertEquals("Sem ligação. Não foi enviado.", toast())
        regra.waitForIdle()
        regra.onNodeWithText("Desvio na 736", substring = true).assertExists() // sem rede não se vai ler outra vez
        assertEquals(1, http.a("/api/comunidade").size)
    }

    @Test fun votoEmPausaDizPausaESemOutraLeitura() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        http.rotas["/api/votos"] = { Resposta(503, """{"erro":"em_pausa"}""") }
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Ainda está").performClick()
        assertFalse(toast().contains("%"))
        assertEquals(textoDoAviso(contexto, R.string.servidor_em_pausa), toast())
        // Segundo toque: a pausa responde sem pedir (Falha(null, true, "em_pausa")) e continua a ser pausa, não falta de rede.
        ShadowToast.reset()
        regra.onNodeWithText("Ainda está").performClick()
        assertFalse(toast().contains("%"))
        assertEquals(textoDoAviso(contexto, R.string.servidor_em_pausa), toast())
        regra.waitForIdle()
        assertEquals(1, http.a("/api/votos").size)
        assertEquals(1, http.a("/api/comunidade").size)
    }

    @Test fun servidorDesligadoEscondeASecao() {
        ligar()
        entrar()
        aDistancia(10.0)
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        // O servidor desligou a comunidade com a app aberta (a config da app é a antiga): o voto dá 403 desligado.
        http.rotas["/api/votos"] = { Resposta(403, """{"erro":"desligado"}""") }
        regra.onNodeWithText("Ainda está").performClick()
        esperarQueSaia("Desvio na 736")
        listOf("Ainda está", "Já não está", "Denunciar", "passageiro").forEach { regra.onNodeWithText(it, substring = true).assertDoesNotExist() }
        regra.waitForIdle()
        assertEquals(1, http.a("/api/comunidade").size) // esconde logo e não volta a ler
    }

    @Test fun denunciaComServidorDesligadoEscondeASecao() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        http.rotas["/api/denuncias"] = { Resposta(403, """{"erro":"desligado"}""") }
        abrir()
        esperarPor("Desvio na 736")
        denunciarComNomeEBoaFe()
        regra.onNodeWithText("Enviar denúncia").performClick()
        esperarQueSaia("Desvio na 736")
        regra.onNodeWithText("Porquê?").assertDoesNotExist()
    }

    @Test fun servidorDesligadoNaAberturaNaoMostraNada() {
        ligar()
        http.relatos = listOf(relato())
        http.rotas["/api/comunidade"] = { Resposta(403, """{"erro":"desligado"}""") }
        abrir()
        esperarPor("Cais do Sodré")
        regra.waitForIdle()
        regra.onNodeWithText("Desvio na 736", substring = true).assertDoesNotExist()
        assertEquals(1, http.a("/api/comunidade").size)
    }

    // ---- ocultar ----

    @Test fun ocultarEscondeEGuardaOId() {
        ligar()
        http.relatos = listOf(relato(id = "r1"), relato(id = "r2", tipo = "evento", linha = "15E"))
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithContentDescription("Mais opções: Desvio na 736").performClick()
        regra.onNodeWithText("Ocultar este relato").performClick()
        esperarQueSaia("Desvio na 736")
        regra.onNodeWithText("Evento na 15E", substring = true).assertExists() // o outro continua
        assertEquals(setOf("relato:r1"), runBlocking { preferencias.ocultos.first() })
    }

    // ---- denunciar ----

    @Test fun denunciarPedeNomeEBoaFe() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Denunciar").performClick()
        esperarPor("Porquê?")
        regra.onNodeWithText("Enviar denúncia").assertIsNotEnabled()
        regra.onNodeWithText("O teu nome").performTextInput("   ") // só espaços não é um nome
        regra.onNodeWithText(boaFe).performClick()
        regra.onNodeWithText("Enviar denúncia").assertIsNotEnabled()
        regra.onNodeWithText("O teu nome").performTextReplacement("Rita")
        regra.onNodeWithText("Enviar denúncia").assertIsEnabled()
        regra.onNodeWithText(boaFe).performClick() // desmarcada
        regra.onNodeWithText("Enviar denúncia").assertIsNotEnabled()
    }

    @Test fun denunciaEnviaOMotivoONomeEAExplicacao() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Denunciar").performClick()
        esperarPor("Porquê?")
        regra.onNodeWithText("Põe alguém em perigo").performClick()
        regra.onNodeWithText("O teu nome").performTextInput("Rita")
        regra.onNodeWithText("Explica, se quiseres (opcional)").performTextInput("Não há desvio nenhum")
        regra.onNodeWithText(boaFe).performClick()
        regra.onNodeWithText("Enviar denúncia").performClick()
        assertEquals("Denúncia enviada.", toast())
        val (metodo, _, corpo) = http.a("/api/denuncias").single()
        assertEquals("POST", metodo)
        assertEquals("""{"conteudo":"relato:r1","motivo":"perigo","nome":"Rita","explicacao":"Não há desvio nenhum","boaFe":true}""", corpo)
        regra.onNodeWithText("Porquê?").assertDoesNotExist()
    }

    @Test fun denunciaUsaOEmailDaConta() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        denunciarComNomeEBoaFe()
        regra.onNodeWithText("Enviamos o recibo e a decisão para ana@exemplo.pt.").assertExists()
        // Nenhum campo para outro endereço, e o pedido não leva nenhum: o servidor usa o email da conta.
        regra.onNodeWithText("Email", substring = true, ignoreCase = true).assertDoesNotExist()
        regra.onNodeWithText("Enviar denúncia").performClick()
        toast()
        val corpo = http.a("/api/denuncias").single().third.orEmpty()
        assertFalse("@" in corpo)
        assertFalse("email" in corpo.lowercase())
    }

    @Test fun denunciaQueFalhaDeixaOsDadosParaTentarOutraVez() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        http.rotas["/api/denuncias"] = { Resposta(429, """{"erro":"muitos_pedidos"}""") }
        abrir()
        esperarPor("Desvio na 736")
        denunciarComNomeEBoaFe()
        regra.onNodeWithText("Enviar denúncia").performClick()
        assertEquals("Chegaste ao limite de hoje.", toast())
        regra.onNodeWithText("Enviar denúncia").assertIsEnabled() // o diálogo fica, com o que a pessoa escreveu
    }

    @Test fun denunciarSemContaPedeEntrar() {
        ligar()
        http.relatos = listOf(relato())
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithText("Denunciar").performClick()
        esperarPor("Entra com o Google para denunciar")
        regra.onNodeWithText("Porquê?").assertDoesNotExist()
        regra.onNodeWithText("Entrar").performClick()
        assertEquals(1, abriuConta)
        assertTrue(http.a("/api/denuncias").isEmpty())
    }

    @Test fun denunciaComSessaoRecusadaPedeEntrar() {
        ligar()
        entrar()
        http.relatos = listOf(relato())
        http.rotas["/api/denuncias"] = { Resposta(401, "") }
        abrir()
        esperarPor("Desvio na 736")
        denunciarComNomeEBoaFe()
        regra.onNodeWithText("Enviar denúncia").performClick()
        esperarPor("Entra com o Google para denunciar")
    }

    // ---- leitor de ecrã e letra grande ----

    @Test fun todosOsBotoesETodosOsItensDoMenuTemEtiqueta() {
        ligar()
        entrar()
        http.relatos = listOf(relato(confirmacoes = 2), relato(id = "r2", tipo = "elevador", linha = ""))
        abrir()
        esperarPor("Desvio na 736")
        regra.onNodeWithContentDescription("Mais opções: Desvio na 736").performClick() // abre o menu
        esperarPor("Ocultar este relato")
        regra.onNodeWithContentDescription("Ainda está: Desvio na 736").assertExists()
        regra.onNodeWithContentDescription("Já não está: Desvio na 736").assertExists()
        regra.onNodeWithContentDescription("Denunciar: Desvio na 736").assertExists()
        val semEtiqueta = regra.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n ->
            n.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() && n.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
        }
        assertTrue("clicáveis sem etiqueta: $semEtiqueta", semEtiqueta.isEmpty())
    }

    @Test fun cartaoNaoCortaOsBotoesComLetra200() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) {
                    Box(Modifier.width(360.dp)) {
                        CartaoRelato(
                            RelatoRemoto("r1", "nao_passou", "CM:060419", "736", agora.minusSeconds(240).toEpochMilli(), agora.plusSeconds(3600).toEpochMilli(), 1, false),
                            "736", agora, aoVotar = {}, aoDenunciar = {}, aoOcultar = {},
                        )
                    }
                }
            }
        }
        listOf("Ainda está", "Já não está", "Denunciar").forEach { regra.onNodeWithText(it).assertIsDisplayed() }
        regra.onNodeWithContentDescription("Mais opções: Não passou / adiantado na 736").assertIsDisplayed()
        // Nada passa da largura do cartão: os botões quebram para a linha de baixo.
        listOf(regra.onNodeWithText("Ainda está"), regra.onNodeWithText("Já não está"), regra.onNodeWithText("Denunciar")).forEach {
            val caixa = it.getUnclippedBoundsInRoot()
            assertTrue("${caixa.left}..${caixa.right}", caixa.left >= 0.dp && caixa.right <= 360.dp)
        }
    }

    @Test fun denunciarComLetra200TemOsBotoesSempreAVista() {
        regra.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                TemaParagemCerta(ModoTema.CLARO) { DenunciarConteudo("ana@exemplo.pt", aoEnviar = { _, _, _ -> }, aoFechar = {}) }
            }
        }
        // O formulário rola; os dois botões ficam fora da parte que rola.
        regra.onNodeWithText("Cancelar").assertIsDisplayed()
        regra.onNodeWithText("Enviar denúncia").assertIsDisplayed()
        regra.onNodeWithText("Denunciar este relato").assertIsDisplayed()
    }

    /** Uma viagem a bordo do 736 (a mesma dos testes de Relatar): só interessa que `aBordoDaLinha("736")` seja verdade. */
    private fun viagemNa736() {
        val ex = Exemplo(Instant.now())
        val plano = ex.plano()
        FicheirosViagem.gravarPlano(contexto, plano)
        FicheirosViagem.gravarEstado(contexto, ex.aBordo(plano, 1, k = 2))
        assertTrue(aBordoDaLinha(contexto, "736"))
    }
}
