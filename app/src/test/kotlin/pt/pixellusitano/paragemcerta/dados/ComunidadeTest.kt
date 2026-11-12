package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant

/** O servidor da Paragem Certa por rota: guarda os pedidos que lhe chegam e responde com o que o teste puser em [rotas]. */
private class ServidorDeComunidade : Http {
    class Pedido(val metodo: String, val url: String, val corpo: String?, val autorizacao: String?)

    var config = fixture("servidor-config.json")
    var semRede = false

    /** Enquanto não for completada, a API não responde: o pedido está "em voo" (a rota só corre depois). */
    var porta: CompletableDeferred<Unit>? = null
    val pedidos = mutableListOf<Pedido>()
    val rotas = mutableMapOf<String, () -> Resposta>(
        "/api/auth/google" to { Resposta(200, """{"token":"tok-ana","email":"ana@exemplo.pt"}""") },
        "/api/sincronizar" to { Resposta(200, """{"revisao":0,"alteracoes":[]}""") },
        "/api/comunidade" to { Resposta(200, fixture("servidor-comunidade.json")) },
        "/api/avisos" to { Resposta(200, """{"relatos":[]}""") },
        "/api/relatos" to { Resposta(201, """{"id":"r9","novo":true}""") },
        "/api/votos" to { Resposta(200, """{"confirmacoes":2,"confirmado":true,"ativo":true}""") },
        "/api/correcoes" to { Resposta(201, "{}") },
        "/api/denuncias" to { Resposta(201, "{}") },
        "/api/termos" to { Resposta(200, "{}") },
    )

    fun pedidosA(rota: String) = pedidos.filter { rota in it.url }

    // O tamanho de cada lista de chaves pedida ("paragens=a,b,c" → 3).
    fun tamanhos(rota: String, parametro: String) = pedidosA(rota).map { it.url.substringAfter("$parametro=").split(",").size }

    override suspend fun get(url: String): Resposta = if (semRede) throw IOException("sem rede") else Resposta(200, config)

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta =
        responder(metodo, url, corpo, cabecalhos)

    private suspend fun responder(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += Pedido(metodo, url, corpo, cabecalhos["Authorization"])
        // Como o OkHttp, que não pára a meio: quem espera aqui não é cancelado pelo pedido de quem chamou.
        porta?.let { withContext(NonCancellable) { it.await() } }
        if (semRede) throw IOException("sem rede")
        return rotas.entries.first { it.key in url }.value()
    }
}

// Os ids do servidor-comunidade.json.
private const val RELATO_DESVIO = "6f1c2a9e-3b7d-4c58-9a10-5d2e8b7f4a11"
private const val RELATO_ELEVADOR = "0a4d9b3c-8e21-47f6-b5c9-1e7a6d3f2b90"

@OptIn(ExperimentalCoroutinesApi::class)
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
        assertEquals(Resultado.Ok(Unit), conta.entrarComGoogle("token-de-teste", juntarFavoritos = true))
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
        assertEquals(Resultado.Ok(Unit), comunidade.corrigir("CM:060419", Correcao.JaNaoExiste))
        assertEquals(Resultado.Ok(Unit), comunidade.denunciar("relato:$RELATO_DESVIO", MotivoDenuncia.FALSO, "Ana", ""))
        assertEquals(Resultado.Ok(Unit), comunidade.aceitarTermos())
        assertEquals(5, http.pedidos.size)
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
        ligar("comunidade", "correcoes")
        val d = daParagem("CM:060419")
        assertEquals(listOf(RELATO_DESVIO, RELATO_ELEVADOR), d.relatos.map { it.id })
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
        ligar("comunidade")
        preferencias.ocultar("relato:$RELATO_DESVIO")
        val d = daParagem("CM:060419")
        assertEquals(listOf(RELATO_ELEVADOR), d.relatos.map { it.id })
        // E o que se ocultou continua só no telemóvel: nenhum pedido leva os ids.
        assertTrue(http.pedidos.none { RELATO_DESVIO in it.url })
    }

    @Test fun ocultosTambemSaemDoMapaEDasRotas() = runTest {
        ligar("comunidade")
        http.rotas["/api/avisos"] = { Resposta(200, """{"relatos":[${relato("r1", "736")},${relato("r2", "736")}]}""") }
        preferencias.ocultar("relato:$RELATO_DESVIO")
        preferencias.ocultar("relato:r1")
        assertEquals(listOf(RELATO_ELEVADOR), comunidade.dasParagens(listOf("CM:060419")).relatos.map { it.id })
        assertEquals(listOf("r2"), comunidade.confirmadosNasLinhas(listOf("736")).map { it.id })
    }

    @Test fun relatosDaLinhaSoComAComunidadeLigada() = runTest {
        // Desligada: lista vazia e nem um pedido (o ecrã da linha não mostra a secção).
        assertEquals(emptyList<RelatoRemoto>(), comunidade.daLinha("736"))
        assertTrue(http.pedidos.isEmpty())
        ligar("comunidade")
        preferencias.ocultar("relato:$RELATO_ELEVADOR")
        // Ligada: os relatos ativos da linha pelo nome que a pessoa vê, sem os que ela ocultou.
        assertEquals(listOf(RELATO_DESVIO), comunidade.daLinha(" 736 ").map { it.id })
        assertEquals(listOf(listOf("736")), chavesPedidas("/api/comunidade", "linhas"))
    }

    @Test fun relatosDaLinhaQueFalhamFicamVazios() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(500, "") }
        assertEquals(emptyList<RelatoRemoto>(), comunidade.daLinha("736"))
        // Um nome que o servidor recusaria (dava 400) nem se pede.
        http.pedidos.clear()
        assertEquals(emptyList<RelatoRemoto>(), comunidade.daLinha("1/2"))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun mapaGuarda2Minutos() = runTest {
        ligar("comunidade", "correcoes")
        val chaves = listOf("CM:060419", "CM:070001")
        val d = comunidade.dasParagens(chaves)
        assertEquals(listOf(RELATO_DESVIO, RELATO_ELEVADOR), d.relatos.map { it.id })
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

    // ---- chaves que o servidor aceita: uma só fora do formato dava 400 ao pedido inteiro, em cada chamada ----

    // As chaves de um pedido, já sem o URL-encoding e sem a vírgula que as separa.
    private fun chavesPedidas(rota: String, parametro: String) =
        http.pedidosA(rota).map { p -> URLDecoder.decode(p.url.substringAfter("$parametro="), "UTF-8").split(",") }

    @Test fun mapaSoMandaChavesQueOServidorAceita() = runTest {
        ligar("comunidade")
        comunidade.dasParagens(listOf("CM:060419", "TRANSITOUS:com espaco", "lixo", "", "CM:", "TRANSITOUS:pt-Carris_(1)", "CM:a,b", "CM:060419"))
        // As chaves sem sentido ficam de fora; a vírgula de "CM:a,b" não pode partir a chave em duas "boas".
        assertEquals(listOf(listOf("CM:060419", "TRANSITOUS:pt-Carris_(1)")), chavesPedidas("/api/comunidade", "paragens"))
    }

    @Test fun mapaSemChavesValidasNaoPede() = runTest {
        ligar("comunidade")
        assertEquals(DadosComunidade(), comunidade.dasParagens(listOf("lixo", "", "TRANSITOUS:com espaco", "CM:" + "x".repeat(121))))
        assertEquals(DadosComunidade(), comunidade.dasParagens(emptyList()))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun chaveInvalidaNaoEsvaziaOsGruposDoMapa() = runTest {
        ligar("comunidade")
        http.rotas["/api/comunidade"] = { Resposta(200, "{}") }
        val invalidas = listOf("lixo", "TRANSITOUS:com espaco", "CM:")
        comunidade.dasParagens(invalidas + (0 until 150).map { "CM:%06d".format(it) })
        // Antes, as 3 más iam no 1.º grupo: o servidor dava 400, o pedido falhava e os grupos seguintes nem se tentavam.
        assertEquals(listOf(100, 50), http.tamanhos("/api/comunidade", "paragens"))
        assertTrue(chavesPedidas("/api/comunidade", "paragens").flatten().none { it in invalidas })
    }

    @Test fun rotasSoMandamLinhasQueOServidorAceita() = runTest {
        ligar("comunidade")
        comunidade.confirmadosNasLinhas(listOf("736", " 15e ", "LINHA MUITO COMPRIDA 1", "1/2", "A(B)", "L+", "", "  ", "A,B"))
        assertEquals(listOf(listOf("736", "15E")), chavesPedidas("/api/avisos", "linhas"))
    }

    @Test fun rotasSemLinhasValidasNaoPedem() = runTest {
        ligar("comunidade")
        assertEquals(emptyList<RelatoRemoto>(), comunidade.confirmadosNasLinhas(listOf("1/2", "A(B)", "L+", "", "LINHA MUITO COMPRIDA 1")))
        assertEquals(emptyList<RelatoRemoto>(), comunidade.confirmadosNasLinhas(emptyList()))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun linhaInvalidaNaoEsvaziaOsGruposDasRotas() = runTest {
        ligar("comunidade")
        val invalidas = listOf("1/2", "L+", "LINHA MUITO COMPRIDA 1")
        comunidade.confirmadosNasLinhas(invalidas + (1..25).map { "L$it" })
        assertEquals(listOf(20, 5), http.tamanhos("/api/avisos", "linhas"))
        assertTrue(chavesPedidas("/api/avisos", "linhas").flatten().none { it in invalidas })
    }

    // ---- um só pedido do mapa de cada vez ----

    // Com a API "em voo" (sem responder até [corpo] a soltar); solta-a no fim, passe o que passar: um teste que falha não deixa pedidos presos.
    private suspend fun comApiEmVoo(corpo: suspend (soltar: () -> Unit) -> Unit) {
        val porta = CompletableDeferred<Unit>().also { http.porta = it }
        try {
            corpo { porta.complete(Unit) }
        } finally {
            porta.complete(Unit)
        }
    }

    @Test fun mapaNaoRepeteUmPedidoEmVoo() = runTest {
        ligar("comunidade")
        comApiEmVoo { soltar ->
            val chaves = listOf("CM:060419")
            val a = async { comunidade.dasParagens(chaves) }
            runCurrent()
            val b = async { comunidade.dasParagens(chaves) } // outra volta ao mapa, com o 1.º pedido ainda à espera da resposta
            runCurrent()
            assertEquals(1, http.pedidosA("/api/comunidade").size)
            soltar()
            assertEquals(2, a.await().relatos.size)
            assertEquals(a.await(), b.await())
            assertEquals(1, http.pedidosA("/api/comunidade").size) // a resposta do 1.º ficou guardada e o 2.º usou-a
        }
    }

    @Test fun quemFoiCanceladoNaoDeitaForaAResposta() = runTest {
        ligar("comunidade")
        comApiEmVoo { soltar ->
            val chaves = listOf("CM:060419")
            val a = async { comunidade.dasParagens(chaves) }
            runCurrent()
            // A pessoa mexe no mapa: o ecrã cancela a leitura a meio e pede a seguinte. O pedido já foi, e a resposta não se perde.
            a.cancel()
            val b = async { comunidade.dasParagens(chaves) }
            runCurrent()
            soltar()
            assertEquals(2, b.await().relatos.size)
            assertEquals(1, http.pedidosA("/api/comunidade").size)
        }
    }

    @Test fun mapaNaoComecaGruposDepoisDeCancelado() = runTest {
        ligar("comunidade")
        comApiEmVoo { soltar ->
            val a = async { comunidade.dasParagens((0 until 150).map { "CM:%06d".format(it) }) }
            runCurrent()
            a.cancel()
            soltar()
            runCurrent()
            assertEquals(1, http.pedidosA("/api/comunidade").size) // o grupo em voo acaba; o 2.º grupo já ninguém o quer
        }
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

    @Test fun correcoesDesligadasLimpamEssaParte() = runTest {
        ligar("comunidade")
        val d = daParagem("CM:060419")
        assertEquals(2, d.relatos.size)
        assertTrue(d.correcoes.isEmpty())
        val mapa = comunidade.dasParagens(listOf("CM:060419", "CM:070001"))
        assertEquals(2, mapa.relatos.size)
        assertTrue(mapa.correcoes.isEmpty())
        // Ligadas na config, as mesmas leituras (já guardadas) passam a trazer as correções.
        ligar("comunidade", "correcoes")
        val ligado = comunidade.dasParagens(listOf("CM:060419", "CM:070001"))
        assertEquals(2, http.pedidosA("/api/comunidade").size) // só a daParagem e o primeiro mapa pediram
        assertEquals(1, ligado.correcoes.size)
    }
}
