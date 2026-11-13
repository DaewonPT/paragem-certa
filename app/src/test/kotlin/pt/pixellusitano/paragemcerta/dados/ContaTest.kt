package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FavoritoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Sincronizacao
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File
import java.io.IOException
import java.time.Instant

/** O servidor da Paragem Certa em memória: o idToken e o token são o email, cada email é uma conta, e as revisões sobem como no verdadeiro. */
internal class ServidorDeTeste : Http {
    var semRede = false
    var recusarSessao = false
    /** Corre enquanto o pedido de sincronização "está na rede" (a pessoa mexe nos favoritos entretanto). */
    var duranteASincronizacao: suspend () -> Unit = {}
    /** Corre enquanto o pedido de entrada "está na rede" (a pessoa sai do ecrã entretanto). */
    var duranteAEntrada: suspend () -> Unit = {}
    /** O token e as alterações de cada sincronização recebida. */
    val sincronizacoes = mutableListOf<Pair<String, List<FavoritoRemoto>>>()
    val desdes = mutableListOf<Long>()
    val contas = mutableMapOf<String, MutableMap<String, FavoritoRemoto>>()
    val sessoesTerminadas = mutableListOf<String>()
    private var revisao = 0L
    private val json = Json { ignoreUnknownKeys = true }

    fun naConta(email: String, vararg favoritos: FavoritoRemoto) =
        favoritos.forEach { contas.getOrPut(email) { mutableMapOf() }[it.chave] = it.copy(revisao = ++revisao) }

    override suspend fun get(url: String): Resposta = error("só pedidos à API")

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        if (semRede) throw IOException("sem rede")
        val token = cabecalhos["Authorization"]?.removePrefix("Bearer ")
        val pedido = corpo?.takeIf { it.isNotEmpty() }?.let { json.parseToJsonElement(it).jsonObject }
        return when (url.substringAfter("/api/")) {
            "auth/google" -> {
                val email = pedido!!["idToken"]!!.jsonPrimitive.content
                duranteAEntrada()
                Resposta(200, """{"token":"$email","email":"$email"}""")
            }
            "auth/sair" -> Resposta(204, "").also { sessoesTerminadas += token!! }
            "conta" -> Resposta(204, "").also { contas.remove(token) }
            "sincronizar" -> {
                if (recusarSessao) return Resposta(401, """{"erro":"sem_sessao"}""")
                val desde = pedido!!["desde"]!!.jsonPrimitive.long
                val alteracoes = json.decodeFromJsonElement<List<FavoritoRemoto>>(pedido["alteracoes"]!!)
                // Como worker/src/favoritos.ts: um nome comprido recusa o pedido inteiro.
                if (alteracoes.any { it.nome.length > 120 }) return Resposta(400, """{"erro":"pedido_invalido"}""")
                sincronizacoes += token!! to alteracoes
                desdes += desde
                duranteASincronizacao()
                naConta(token, *alteracoes.toTypedArray())
                val favoritos = contas[token].orEmpty().values.filter { it.revisao!! > desde }
                Resposta(200, json.encodeToString(Sincronizacao(revisao, favoritos)))
            }
            else -> Resposta(404, "")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class ContaTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = HttpPorUrl("geocode" to listOf({ Resposta(200, "[]") }))
    private val repo = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
    private val servidor = ServidorDeTeste()
    private var agora = Instant.parse("2026-11-02T08:00:00Z")
    private var widgetsEsquecidos = 0
    private fun conta() = Conta(contexto, ServidorParagemCerta(servidor), repo, relogio = { agora }, esquecerWidgets = { widgetsEsquecidos++ })
    private val ficheiro get() = File(contexto.noBackupFilesDir, "conta.json")

    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val oriente = Paragem(RefParagem(Fonte.CM, "070001"), "Oriente", 38.7678, -9.0990, null)
    private fun lugar(nome: String) = Lugar(nome, 38.71, -9.15, TipoLugar.LOCAL)
    private fun remoto(chave: String, tipo: String = "PARAGEM", nome: String = "Remota", apagado: Boolean = false) =
        FavoritoRemoto(chave, tipo, nome, 38.6, -9.1, 0, apagado)

    private suspend fun entrar(c: Conta, email: String = "ana@exemplo.pt", juntar: Boolean = true) =
        assertEquals(Resultado.Ok(Unit), c.entrarComGoogle(email, juntarFavoritos = juntar))

    private suspend fun chavesFavoritas() = repo.favoritas().first().map { it.ref.chave }

    @Before fun semSessao() {
        ficheiro.delete()
    }

    @After fun fechar() = bd.close()

    @Test fun entrarGuardaASessaoForaDasCopias() = runTest {
        val c = conta()
        entrar(c)
        assertTrue(ficheiro.readText().contains("ana@exemplo.pt"))
        assertFalse(File(contexto.filesDir, "conta.json").exists())
        assertEquals(EstadoConta.Com("ana@exemplo.pt", agora), c.estado.value)
        assertEquals(EstadoConta.Com("ana@exemplo.pt", agora), conta().estado.value) // a app abre outra vez e a sessão continua
    }

    @Test fun sairDoEcraAMeioDaEntradaGuardaASessao() = runTest {
        val c = conta()
        lateinit var ecra: Job
        // A pessoa volta atrás com o pedido na rede: o OkHttp acaba-o e o servidor já criou a sessão.
        servidor.duranteAEntrada = { ecra.cancel() }
        ecra = launch { c.entrarComGoogle("ana@exemplo.pt", juntarFavoritos = false) }
        ecra.join()
        assertEquals(EstadoConta.Com("ana@exemplo.pt", agora), c.estado.value)
        assertTrue(ficheiro.readText().contains("ana@exemplo.pt"))
    }

    @Test fun entrarSincronizaDesdeZero() = runTest {
        servidor.naConta("ana@exemplo.pt", remoto("CM:000001"))
        repo.alternarFavorita(cais)
        repo.guardarLocal("Casa", lugar("Rua A"), "CASA")
        entrar(conta())
        val uuid = bd.locais().lista().single().uuid
        assertEquals(listOf(0L), servidor.desdes)
        assertEquals(setOf("CM:060419" to "PARAGEM", uuid to "CASA"), servidor.sincronizacoes.single().second.map { it.chave to it.tipo }.toSet())
        assertEquals(setOf("CM:000001", "CM:060419"), chavesFavoritas().toSet())
        assertTrue(repo.porSincronizar().isEmpty())
    }

    @Test fun alteracaoSemRedeFicaPendente() = runTest {
        val c = conta()
        entrar(c)
        servidor.semRede = true
        repo.alternarFavorita(cais)
        assertEquals(Resultado.Falha(null, limite = false), c.sincronizar())
        assertEquals(listOf("CM:060419"), repo.porSincronizar().map { it.chave })
        servidor.semRede = false
        assertEquals(Resultado.Ok(Unit), c.sincronizar())
        assertEquals(listOf("CM:060419"), servidor.sincronizacoes.last().second.map { it.chave })
        assertEquals(setOf("CM:060419"), servidor.contas["ana@exemplo.pt"]!!.keys)
        assertTrue(repo.porSincronizar().isEmpty())
    }

    @Test fun apagadoVaiNaProxima() = runTest {
        repo.alternarFavorita(cais)
        repo.guardarLocal("Ginásio", lugar("Rua B"), "OUTRO")
        val c = conta()
        entrar(c)
        val sitio = bd.locais().lista().single()
        repo.removerFavorita(cais.ref)
        repo.apagarLocal(sitio.id)
        assertEquals(Resultado.Ok(Unit), c.sincronizar())
        val enviadas = servidor.sincronizacoes.last().second
        assertEquals(setOf("CM:060419" to "PARAGEM", sitio.uuid to "OUTRO"), enviadas.map { it.chave to it.tipo }.toSet())
        assertTrue(enviadas.all { it.apagado })
        assertTrue(servidor.contas["ana@exemplo.pt"]!!.values.all { it.apagado })
        assertTrue(repo.porSincronizar().isEmpty())
    }

    @Test fun remotoNaoFicaPendente() = runTest {
        servidor.naConta("ana@exemplo.pt", remoto("CM:060419", nome = "Cais"), remoto("u-casa", tipo = "CASA", nome = "Casa"))
        val c = conta()
        entrar(c)
        assertEquals(listOf("CM:060419"), chavesFavoritas())
        assertEquals(listOf("u-casa" to "CASA"), bd.locais().lista().map { it.uuid to it.tipo })
        assertTrue(repo.porSincronizar().isEmpty())
        c.sincronizar()
        assertEquals(emptyList<FavoritoRemoto>(), servidor.sincronizacoes.last().second) // não volta a subir o que desceu
        // Um apagado noutro telemóvel desaparece também deste.
        servidor.naConta("ana@exemplo.pt", remoto("CM:060419", apagado = true))
        c.sincronizar()
        assertTrue(chavesFavoritas().isEmpty())
    }

    @Test fun sessaoRecusadaSaiSozinha() = runTest {
        repo.alternarFavorita(cais)
        val c = conta()
        entrar(c)
        servidor.recusarSessao = true
        repo.alternarFavorita(oriente)
        assertEquals(Resultado.Falha(401, limite = false, erro = "sem_sessao"), c.sincronizar())
        assertEquals(EstadoConta.SemConta, c.estado.value)
        assertFalse(ficheiro.exists())
        assertEquals(setOf("CM:060419", "CM:070001"), chavesFavoritas().toSet())
    }

    @Test fun comSessaoNaoPrendeAVezDuranteOPedido() = runTest {
        val c = conta()
        entrar(c)
        val r = c.comSessao { token ->
            // Com a vez presa, esta sincronização esperava por este pedido e o pedido por ela. O tempo é o verdadeiro:
            // o virtual do runTest salta à frente enquanto a rede de teste (Dispatchers.Default) ainda responde.
            withContext(Dispatchers.Default) { withTimeout(5_000) { c.sincronizar() } }
            Resultado.Ok(token)
        }
        assertEquals(Resultado.Ok("ana@exemplo.pt"), r) // o token do ServidorDeTeste é o email
    }

    @Test fun sessaoRecusadaTardeNaoSaiDaContaNova() = runTest {
        val c = conta()
        entrar(c)
        val recusada = Resultado.Falha(401, limite = false, erro = "sem_sessao")
        // A pessoa saiu e entrou noutra conta enquanto o pedido da primeira ainda andava: o 401 é do token antigo.
        val r = c.comSessao {
            c.sair()
            entrar(c, "rui@exemplo.pt", juntar = false)
            recusada
        }
        assertEquals(recusada, r)
        assertEquals(EstadoConta.Com("rui@exemplo.pt", agora), c.estado.value)
    }

    @Test fun sairNaoApagaFavoritos() = runTest {
        repo.alternarFavorita(cais)
        repo.guardarLocal("Casa", lugar("Rua A"), "CASA")
        val c = conta()
        entrar(c)
        servidor.semRede = true
        repo.alternarFavorita(oriente) // ficou por enviar quando a pessoa sai sem rede
        c.sair()
        assertEquals(EstadoConta.SemConta, c.estado.value)
        assertFalse(ficheiro.exists())
        assertEquals(setOf("CM:060419", "CM:070001"), chavesFavoritas().toSet())
        assertEquals(listOf("Casa"), bd.locais().lista().map { it.nome })
        // Sem sessão apaga-se como antes: nada fica guardado para enviar a uma conta.
        repo.removerFavorita(cais.ref)
        assertTrue(bd.apagados().todos().isEmpty())
    }

    @Test fun nomeCompridoNaoBloqueiaASincronizacao() = runTest {
        repo.guardarLocal("x".repeat(121), lugar("Rua A"), "OUTRO")
        val c = conta()
        entrar(c)
        repo.alternarFavorita(cais)
        assertEquals(Resultado.Ok(Unit), c.sincronizar())
        assertEquals(setOf("CM:060419"), servidor.contas["ana@exemplo.pt"]!!.values.filter { it.tipo == "PARAGEM" }.map { it.chave }.toSet())
        assertEquals(listOf(120), bd.locais().lista().map { it.nome.length })
        assertTrue(repo.porSincronizar().isEmpty())
    }

    // As apps já instaladas ficam com estes valores para sempre: o servidor só os pode subir, e a app muda com ele.
    @Test fun limitesIguaisAosDoServidor() {
        val servidor = File("../worker/src/favoritos.ts").readText()
        assertTrue(servidor.contains("const MAX_ALTERACOES = ${Conta.MAX_ALTERACOES};"))
        assertTrue(servidor.contains("const MAX_TEXTO = $MAX_TEXTO;"))
        assertTrue(servidor.contains("const MAX_CHAVE_LINHA = $MAX_CHAVE_LINHA;"))
    }

    @Test fun apagarDadosDoTelemovelSaiDaConta() = runTest {
        val c = conta()
        entrar(c)
        repo.alternarFavorita(cais)
        c.apagarDadosDoTelemovel()
        assertEquals(EstadoConta.SemConta, c.estado.value)
        assertFalse(ficheiro.exists())
        assertEquals(listOf("ana@exemplo.pt"), servidor.sessoesTerminadas)
        assertEquals(setOf("CM:060419"), servidor.contas["ana@exemplo.pt"]!!.keys) // o que faltava foi antes de sair
        assertTrue(chavesFavoritas().isEmpty())
    }

    @Test fun apagarDadosDoTelemovelEsqueceOsWidgets() = runTest {
        val c = conta()
        entrar(c)
        assertEquals("entrar e sair da conta não mexem nos widgets", 0, widgetsEsquecidos)
        c.apagarDadosDoTelemovel()
        assertEquals(1, widgetsEsquecidos)
    }

    @Test fun apagarAContaNaoEsqueceOsWidgets() = runTest {
        // Os favoritos ficam no telemóvel (conta_apagar_confirmar), por isso as paragens escolhidas nos widgets continuam a existir.
        val c = conta()
        entrar(c)
        repo.alternarFavorita(cais)
        assertEquals(Resultado.Ok(Unit), c.apagarConta())
        assertEquals(listOf("CM:060419"), chavesFavoritas())
        assertEquals(0, widgetsEsquecidos)
    }

    @Test fun sairEnviaPrimeiroOQueFaltava() = runTest {
        val c = conta()
        entrar(c)
        repo.alternarFavorita(cais)
        c.sair()
        assertEquals(setOf("CM:060419"), servidor.contas["ana@exemplo.pt"]!!.keys)
        assertEquals(listOf("ana@exemplo.pt"), servidor.sessoesTerminadas)
    }

    @Test fun entrarNoutraContaSemJuntarNaoEnvia() = runTest {
        repo.alternarFavorita(cais)
        repo.alternarFavorita(oriente)
        repo.guardarLocal("Casa", lugar("Rua A"), "CASA")
        val c = conta()
        entrar(c, "ana@exemplo.pt")
        c.sair()
        entrar(c, "rui@exemplo.pt", juntar = false)
        // O Rui mexe nos favoritos da Ana que ficaram no telemóvel: nada disso pode ir para a conta dele.
        repo.reordenarFavoritas(repo.favoritas().first().reversed())
        repo.renomearLocal(bd.locais().lista().single().id, "Casa da Ana")
        repo.removerFavorita(oriente.ref)
        c.sincronizar()
        assertTrue(servidor.sincronizacoes.filter { it.first == "rui@exemplo.pt" }.all { it.second.isEmpty() })
        assertTrue(servidor.contas["rui@exemplo.pt"].orEmpty().isEmpty())
        // Os da Ana continuam no telemóvel.
        assertEquals(listOf("CM:060419"), chavesFavoritas())
        assertEquals(listOf("Casa da Ana"), bd.locais().lista().map { it.nome })
        // O que o Rui junta depois de entrar vai para a conta dele, e só isso.
        val nova = Paragem(RefParagem(Fonte.CM, "080002"), "Rossio", 38.714, -9.139, null)
        repo.alternarFavorita(nova)
        c.sincronizar()
        assertEquals(setOf("CM:080002"), servidor.contas["rui@exemplo.pt"]!!.keys)
    }

    @Test fun apagadoNaContaNaoApagaOQueFicouSoNoTelemovel() = runTest {
        // O Rui já teve o Cais do Sodré e apagou-o; a Ana, no telemóvel, tem-no e não o juntou à conta do Rui.
        servidor.naConta("rui@exemplo.pt", remoto("CM:060419", apagado = true))
        repo.alternarFavorita(cais)
        entrar(conta(), "rui@exemplo.pt", juntar = false)
        assertEquals(listOf("CM:060419"), chavesFavoritas())
    }

    @Test fun alteracaoDuranteOPedidoNaoSePerde() = runTest {
        repo.guardarLocal("Ginásio", lugar("Rua B"), "OUTRO")
        val c = conta()
        entrar(c)
        val id = bd.locais().lista().single().id
        repo.renomearLocal(id, "Piscina")
        servidor.duranteASincronizacao = { repo.renomearLocal(id, "Piscina municipal"); servidor.duranteASincronizacao = {} }
        c.sincronizar()
        // A resposta trouxe "Piscina", mas a pessoa já tinha escrito outro nome: fica o dela, por enviar.
        assertEquals(listOf("Piscina municipal"), bd.locais().lista().map { it.nome })
        c.sincronizar()
        assertEquals(listOf("Piscina municipal"), servidor.contas["ana@exemplo.pt"]!!.values.map { it.nome })
        assertTrue(repo.porSincronizar().isEmpty())
    }

    @Test fun sincronizaAoAbrirEDoisSegundosDepoisDeUmaAlteracao() = runTest {
        val c = conta()
        entrar(c)
        agora = agora.plusSeconds(16 * 60)
        backgroundScope.launch { c.aoAbrir() }
        runCurrent()
        // A resposta converte-se em Dispatchers.Default, fora do tempo virtual: espera que a sincronização de abrir largue a vez.
        c.comSessao { Resultado.Ok(it) }
        assertEquals(2, servidor.sincronizacoes.size) // a de entrar e a de abrir
        repo.alternarFavorita(cais)
        advanceTimeBy(1_500)
        assertEquals(2, servidor.sincronizacoes.size)
        advanceTimeBy(1_000)
        assertEquals(listOf("CM:060419"), servidor.sincronizacoes.last().second.map { it.chave })
    }

    private suspend fun TestScope.abrir(c: Conta) {
        backgroundScope.launch { c.aoAbrir() }
        runCurrent()
        c.comSessao { Resultado.Ok(it) } // espera que a sincronização de abrir largue a vez
    }

    @Test fun naoSincronizaAoAbrirSeSincronizouHaMenosDe15Min() = runTest {
        val c = conta()
        entrar(c)
        agora = agora.plusSeconds(14 * 60)
        abrir(c)
        advanceTimeBy(3_000)
        assertEquals(1, servidor.sincronizacoes.size)
    }

    @Test fun sincronizaAoAbrirComAlteracoesPorEnviar() = runTest {
        val c = conta()
        entrar(c)
        repo.alternarFavorita(cais)
        abrir(c)
        assertEquals(2, servidor.sincronizacoes.size)
        assertEquals(listOf("CM:060419"), servidor.sincronizacoes.last().second.map { it.chave })
    }

    @Test fun sincronizaAoAbrirPassados15Min() = runTest {
        val c = conta()
        entrar(c)
        agora = agora.plusSeconds(15 * 60)
        abrir(c)
        assertEquals(2, servidor.sincronizacoes.size)
    }

    @Test fun semJuntarNaoSincronizaAoAbrir() = runTest {
        repo.alternarFavorita(cais)
        val c = conta()
        entrar(c, "rui@exemplo.pt", juntar = false)
        val antes = servidor.sincronizacoes.size
        agora = agora.plusSeconds(5 * 60)
        abrir(c)
        advanceTimeBy(3_000)
        assertEquals(antes, servidor.sincronizacoes.size)
        // Mexer nos favoritos que ficaram só no telemóvel também não sincroniza.
        repo.removerFavorita(cais.ref)
        advanceTimeBy(3_000)
        assertEquals(antes, servidor.sincronizacoes.size)
    }

    @Test fun alteracaoDuranteASincronizacaoDeAbrirVai() = runTest {
        val c = conta()
        entrar(c)
        agora = agora.plusSeconds(16 * 60)
        servidor.duranteASincronizacao = { repo.alternarFavorita(cais); servidor.duranteASincronizacao = {} }
        abrir(c)
        assertEquals(2, servidor.sincronizacoes.size)
        advanceTimeBy(2_500)
        assertEquals(listOf("CM:060419"), servidor.sincronizacoes.last().second.map { it.chave })
    }

    @Test fun apagarContaMantemOsFavoritos() = runTest {
        repo.alternarFavorita(cais)
        val c = conta()
        entrar(c)
        assertEquals(Resultado.Ok(Unit), c.apagarConta())
        assertEquals(EstadoConta.SemConta, c.estado.value)
        assertFalse(ficheiro.exists())
        assertFalse("ana@exemplo.pt" in servidor.contas)
        assertEquals(listOf("CM:060419"), chavesFavoritas())
    }
}
