package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.comunidade.Correcao
import pt.pixellusitano.paragemcerta.core.comunidade.MotivoDenuncia
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.DadosComunidade
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoCriado
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Voto
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** O servidor da Paragem Certa por rota: guarda os pedidos que lhe chegam e responde com o que o teste puser em [rotas]. */
private class ServidorDeComunidade : Http {
    class Pedido(val metodo: String, val url: String, val corpo: String?, val autorizacao: String?)

    var config = fixture("servidor-config.json")
    var semRede = false
    val pedidos = mutableListOf<Pedido>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/email/confirmar" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/comunidade" to { Resposta(200, fixture("servidor-comunidade.json")) },
        "/api/avisos" to { Resposta(200, """{"relatos":[]}""") },
        "/api/relatos" to { Resposta(201, """{"id":"r9","novo":true}""") },
        "/api/votos" to { Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""") },
        "/api/fotos" to { Resposta(201, """{"id":"f9"}""") },
        "/api/correcoes" to { Resposta(201, "{}") },
        "/api/denuncias" to { Resposta(201, "{}") },
        "/api/termos" to { Resposta(200, "{}") },
    )

    fun pedidosA(rota: String) = pedidos.filter { rota in it.url }

    // O tamanho de cada lista de chaves pedida ("paragens=a,b,c" → 3).
    fun tamanhos(rota: String, parametro: String) = pedidosA(rota).map { it.url.substringAfter("$parametro=").split(",").size }

    // Só o config.json é um GET; a API toda passa por enviar e enviarBytes.
    override suspend fun get(url: String): Resposta = if (semRede) throw IOException("sem rede") else Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta =
        responder(metodo, url, corpo, cabecalhos)

    override suspend fun enviarBytes(metodo: String, url: String, corpo: ByteArray, tipo: String, cabecalhos: Map<String, String>): Resposta =
        responder(metodo, url, "${corpo.size} bytes de $tipo", cabecalhos)

    private fun responder(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += Pedido(metodo, url, corpo, cabecalhos["Authorization"])
        if (semRede) throw IOException("sem rede")
        return rotas.entries.first { it.key in url }.value()
    }
}

// Os ids do servidor-comunidade.json.
private const val RELATO_DESVIO = "6f1c2a9e-3b7d-4c58-9a10-5d2e8b7f4a11"
private const val RELATO_ELEVADOR = "0a4d9b3c-8e21-47f6-b5c9-1e7a6d3f2b90"
private const val FOTO = "c7e5a1b2-4d96-4f03-8a2b-9c0d1e2f3a45"

@RunWith(RobolectricTestRunner::class)
class ComunidadeTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = ServidorDeComunidade()
    private val servidor = ServidorParagemCerta(http)
    private val inicio = Instant.parse("2026-11-03T10:00:00Z")
    private var agora = inicio
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var conta: Conta
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        conta = Conta(contexto, servidor, Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http)), relogio = { agora })
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, conta, configuracoes, preferencias, relogio = { agora })
    }

    @After fun fechar() = bd.close()

    // A config da app traz tudo desligado; isto liga o que o teste pede, como o servidor faria.
    private suspend fun ligar(vararg interruptores: String) {
        http.config = interruptores.fold(fixture("servidor-config.json")) { json, nome -> json.replace("\"$nome\": false", "\"$nome\": true") }
        configuracoes.aoAbrir()
    }

    private suspend fun entrar() {
        assertEquals(Resultado.Ok(Unit), conta.confirmarCodigo("ana@exemplo.pt", "123456", juntarFavoritos = true))
        http.pedidos.clear() // contam-se só os pedidos da comunidade
    }

    private suspend fun daParagem(chave: String) = (comunidade.daParagem(chave) as Resultado.Ok).valor

    private fun relato(id: String, linha: String) =
        """{"id":"$id","tipo":"desvio","paragem":"CM:060419","linha":"$linha","criadoEm":1,"expiraEm":2,"confirmacoes":2,"confirmado":true}"""

    private val semSessao = Resultado.Falha(401, limite = false, erro = "sem_sessao")

    @Test fun semSessaoNaoPede() = runTest {
        ligar("comunidade")
        assertEquals(semSessao, comunidade.relatar(TipoRelato.DESVIO, "CM:060419", "736"))
        assertEquals(semSessao, comunidade.votar(RELATO_DESVIO, aindaEsta = true))
        assertEquals(semSessao, comunidade.enviarFoto("CM:060419", byteArrayOf(1, 2, 3)))
        assertEquals(semSessao, comunidade.corrigir("CM:060419", Correcao.JaNaoExiste))
        assertEquals(semSessao, comunidade.denunciar("relato:$RELATO_DESVIO", MotivoDenuncia.FALSO, "Ana", ""))
        assertEquals(semSessao, comunidade.aceitarTermos())
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun sessaoRecusadaSaiDaConta() = runTest {
        ligar("comunidade")
        entrar()
        http.rotas["/api/relatos"] = { Resposta(401, """{"erro":"sem_sessao"}""") }
        assertEquals(semSessao, comunidade.relatar(TipoRelato.DESVIO, "CM:060419", "736"))
        assertEquals(EstadoConta.SemConta, conta.estado.value)
    }

    @Test fun outraFalhaNaoSaiDaConta() = runTest {
        ligar("comunidade")
        entrar()
        http.rotas["/api/relatos"] = { Resposta(403, """{"erro":"termos_por_aceitar"}""") }
        assertEquals(Resultado.Falha(403, limite = false, erro = "termos_por_aceitar"), comunidade.relatar(TipoRelato.DESVIO, "CM:060419", "736"))
        assertTrue(conta.estado.value is EstadoConta.Com)
    }

    @Test fun escritasUsamOTokenDaSessao() = runTest {
        ligar("comunidade")
        entrar()
        assertEquals(Resultado.Ok(RelatoCriado("r9", novo = true)), comunidade.relatar(TipoRelato.DESVIO, "CM:060419", "736"))
        assertEquals(Resultado.Ok(Voto(2, confirmado = true, ativo = true)), comunidade.votar(RELATO_DESVIO, aindaEsta = false))
        assertEquals(Resultado.Ok("f9"), comunidade.enviarFoto("CM:060419", byteArrayOf(1, 2, 3)))
        assertEquals(Resultado.Ok(Unit), comunidade.corrigir("CM:060419", Correcao.JaNaoExiste))
        assertEquals(Resultado.Ok(Unit), comunidade.denunciar("relato:$RELATO_DESVIO", MotivoDenuncia.FALSO, "Ana", ""))
        assertEquals(Resultado.Ok(Unit), comunidade.aceitarTermos())
        assertEquals(6, http.pedidos.size)
        assertEquals(setOf("Bearer tok-ana"), http.pedidos.map { it.autorizacao }.toSet())
    }

    @Test fun termosUsamAVersaoDaConfig() = runTest {
        entrar()
        comunidade.aceitarTermos() // a config que vem na app
        assertEquals("""{"versao":"1"}""", http.pedidosA("/api/termos").single().corpo)
        http.config = fixture("servidor-config.json").replace("\"versao\": 1,", "\"versao\": 1, \"versaoTermos\": \"2\",")
        configuracoes.aoAbrir()
        comunidade.aceitarTermos()
        assertEquals("""{"versao":"2"}""", http.pedidosA("/api/termos").last().corpo)
    }

    @Test fun daParagemNaoGuardaNada() = runTest {
        ligar("comunidade", "fotos", "correcoes")
        val d = daParagem("CM:060419")
        assertEquals(listOf(RELATO_DESVIO, RELATO_ELEVADOR), d.relatos.map { it.id })
        assertEquals(listOf(FOTO), d.fotos.map { it.id })
        assertEquals(listOf("CM:070001"), d.emRevisao)
        assertEquals(1, d.correcoes.size)
        daParagem("CM:060419")
        assertEquals(2, http.pedidosA("/api/comunidade").size)
        assertTrue(http.pedidosA("/api/comunidade").all { it.url.endsWith("paragens=CM%3A060419") })
    }

    @Test fun desligadoNumaLeituraVemNaFalha() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(403, """{"erro":"desligado"}""") }
        assertEquals(Resultado.Falha(403, limite = false, erro = "desligado"), comunidade.daParagem("CM:060419"))
    }

    @Test fun ocultosNaoAparecem() = runTest {
        ligar("comunidade", "fotos")
        preferencias.ocultar("relato:$RELATO_DESVIO")
        preferencias.ocultar("foto:$FOTO")
        val d = daParagem("CM:060419")
        assertEquals(listOf(RELATO_ELEVADOR), d.relatos.map { it.id })
        assertTrue(d.fotos.isEmpty())
        // E o que se ocultou continua só no telemóvel: nenhum pedido leva os ids.
        assertTrue(http.pedidos.none { RELATO_DESVIO in it.url || FOTO in it.url })
    }

    @Test fun ocultosTambemSaemDoMapaEDasRotas() = runTest {
        ligar("comunidade")
        http.rotas["/api/avisos"] = { Resposta(200, """{"relatos":[${relato("r1", "736")},${relato("r2", "736")}]}""") }
        preferencias.ocultar("relato:$RELATO_DESVIO")
        preferencias.ocultar("relato:r1")
        assertEquals(listOf(RELATO_ELEVADOR), comunidade.dasParagens(listOf("CM:060419")).relatos.map { it.id })
        assertEquals(listOf("r2"), comunidade.confirmadosNasLinhas(listOf("736")).map { it.id })
    }

    @Test fun mapaGuarda2Minutos() = runTest {
        ligar("comunidade", "fotos", "correcoes")
        val chaves = listOf("CM:060419", "CM:070001")
        val d = comunidade.dasParagens(chaves)
        assertEquals(listOf(RELATO_DESVIO, RELATO_ELEVADOR), d.relatos.map { it.id })
        assertEquals(listOf(FOTO), d.fotos.map { it.id })
        assertEquals(listOf("CM:070001"), d.emRevisao)
        assertEquals(1, d.correcoes.size)
        assertEquals(1, http.pedidosA("/api/comunidade").size)
        agora = inicio + Duration.ofMinutes(1)
        assertEquals(d, comunidade.dasParagens(chaves))
        agora = inicio + Duration.ofSeconds(119)
        comunidade.dasParagens(chaves)
        assertEquals(1, http.pedidosA("/api/comunidade").size)
        agora = inicio + Duration.ofMinutes(2)
        comunidade.dasParagens(chaves)
        assertEquals(2, http.pedidosA("/api/comunidade").size)
    }

    @Test fun mapaPedeSoAsChavesEmFalta() = runTest {
        ligar("comunidade")
        comunidade.dasParagens(listOf("CM:060419", "CM:070001"))
        val d = comunidade.dasParagens(listOf("CM:070001", "CM:999999"))
        assertTrue(http.pedidosA("/api/comunidade").last().url.endsWith("paragens=CM%3A999999"))
        assertEquals(2, http.pedidosA("/api/comunidade").size)
        assertTrue(d.relatos.isEmpty()) // os relatos são da 060419, que não foi pedida desta vez
    }

    @Test fun mapaComFalhaFicaComOQueEstavaGuardado() = runTest {
        ligar("comunidade")
        val chaves = listOf("CM:060419")
        val guardado = comunidade.dasParagens(chaves)
        assertEquals(2, guardado.relatos.size)
        agora = inicio + Duration.ofMinutes(5)
        http.rotas["/api/comunidade"] = { Resposta(500, "") }
        assertEquals(guardado, comunidade.dasParagens(chaves))
        http.semRede = true
        assertEquals(guardado, comunidade.dasParagens(chaves))
        assertEquals(3, http.pedidosA("/api/comunidade").size) // a falha não conta como leitura recente: tenta outra vez
    }

    @Test fun maisDe100ParagensVaoEmDoisPedidos() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(200, "{}") }
        comunidade.dasParagens((0 until 150).map { "CM:%06d".format(it) })
        assertEquals(listOf(100, 50), http.tamanhos("/api/comunidade", "paragens"))
    }

    @Test fun cemParagensVaoNumSoPedido() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(200, "{}") }
        comunidade.dasParagens((0 until 100).map { "CM:%06d".format(it) })
        assertEquals(listOf(100), http.tamanhos("/api/comunidade", "paragens"))
    }

    @Test fun maisDe20LinhasVaoEmDoisPedidos() = runTest {
        ligar("comunidade")
        comunidade.confirmadosNasLinhas((1..25).map { "L$it" })
        assertEquals(listOf(20, 5), http.tamanhos("/api/avisos", "linhas"))
        comunidade.confirmadosNasLinhas((101..120).map { "L$it" })
        assertEquals(listOf(20, 5, 20), http.tamanhos("/api/avisos", "linhas"))
    }

    @Test fun falhaNoMapaNaoInsisteNosGruposSeguintes() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(500, "") }
        comunidade.dasParagens((0 until 150).map { "CM:%06d".format(it) })
        assertEquals(1, http.pedidosA("/api/comunidade").size)
    }

    @Test fun rotasGuardam2MinutosPorLinha() = runTest {
        ligar("comunidade")
        http.rotas["/api/avisos"] = { Resposta(200, """{"relatos":[${relato("r1", "736")},${relato("r2", "15E")}]}""") }
        val r = comunidade.confirmadosNasLinhas(listOf(" 15e ", "736", "736")) // maiúsculas, sem repetidas
        assertEquals(listOf("r2", "r1"), r.map { it.id })
        assertTrue(http.pedidosA("/api/avisos").single().url.endsWith("linhas=15E,736"))
        agora = inicio + Duration.ofMinutes(1)
        assertEquals(r, comunidade.confirmadosNasLinhas(listOf("15E", "736")))
        assertEquals(1, http.pedidosA("/api/avisos").size)
        // Uma linha nova pede só a nova; uma sem relatos também fica guardada.
        comunidade.confirmadosNasLinhas(listOf("736", "1"))
        comunidade.confirmadosNasLinhas(listOf("1"))
        assertEquals(2, http.pedidosA("/api/avisos").size)
        assertTrue(http.pedidosA("/api/avisos").last().url.endsWith("linhas=1"))
        agora = inicio + Duration.ofMinutes(2)
        comunidade.confirmadosNasLinhas(listOf("15E"))
        assertEquals(3, http.pedidosA("/api/avisos").size)
    }

    @Test fun rotasSoDevolvemOsConfirmados() = runTest {
        ligar("comunidade")
        http.rotas["/api/avisos"] = {
            Resposta(200, """{"relatos":[${relato("r1", "736").replace("\"confirmado\":true", "\"confirmado\":false")}]}""")
        }
        assertEquals(emptyList<RelatoRemoto>(), comunidade.confirmadosNasLinhas(listOf("736")))
    }

    @Test fun comunidadeDesligadaNaoPede() = runTest {
        // A config que vem na app traz tudo desligado.
        entrar()
        assertEquals(Resultado.Ok(DadosComunidade()), comunidade.daParagem("CM:060419"))
        assertEquals(DadosComunidade(), comunidade.dasParagens(listOf("CM:060419")))
        assertEquals(emptyList<RelatoRemoto>(), comunidade.confirmadosNasLinhas(listOf("736")))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun fotosECorrecoesDesligadasLimpamEssasPartes() = runTest {
        ligar("comunidade")
        val d = daParagem("CM:060419")
        assertEquals(2, d.relatos.size)
        assertTrue(d.fotos.isEmpty() && d.emRevisao.isEmpty() && d.correcoes.isEmpty())
        val mapa = comunidade.dasParagens(listOf("CM:060419", "CM:070001"))
        assertEquals(2, mapa.relatos.size)
        assertTrue(mapa.fotos.isEmpty() && mapa.emRevisao.isEmpty() && mapa.correcoes.isEmpty())
        // Ligadas na config, as mesmas leituras (já guardadas) passam a trazê-las.
        ligar("comunidade", "fotos", "correcoes")
        val ligado = comunidade.dasParagens(listOf("CM:060419", "CM:070001"))
        assertEquals(2, http.pedidosA("/api/comunidade").size) // só a daParagem e o primeiro mapa pediram
        assertEquals(listOf(FOTO), ligado.fotos.map { it.id })
        assertEquals(listOf("CM:070001"), ligado.emRevisao)
        assertEquals(1, ligado.correcoes.size)
    }
}
