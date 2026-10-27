package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.IOException
import java.time.Instant

/** Http falso por URL: cada pedido usa a próxima resposta da fila do primeiro prefixo que coincide. */
class HttpPorUrl(vararg filas: Pair<String, List<() -> Resposta>>) : Http {
    private val filas = filas.map { it.first to it.second.toMutableList() }
    val pedidos = mutableListOf<String>()
    override suspend fun get(url: String): Resposta {
        pedidos += url
        val fila = filas.first { url.contains(it.first) }.second
        return (if (fila.size > 1) fila.removeAt(0) else fila.first())()
    }
}

fun fixture(nome: String): String =
    requireNotNull(HttpPorUrl::class.java.getResource("/fixtures/$nome")) { "fixture $nome em falta" }.readText()

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositorioTest {
    private val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
    private val manha = Instant.parse("2026-10-26T05:00:00Z")
    private val terminal = RefParagem(Fonte.CM, "060419")

    private fun repo(http: Http) = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), relogio = { manha })

    @After fun fechar() = bd.close()

    @Test fun cacheSemRede() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }, { throw IOException("sem rede") }))
        repo(http).chegadas(terminal).test {
            val primeira = awaitItem()
            assertEquals(3, primeira.chegadas.size)
            assertFalse(primeira.semLigacao)
            val segunda = awaitItem()
            assertEquals(primeira.chegadas, segunda.chegadas)
            assertTrue(segunda.semLigacao)
            assertEquals(primeira.atualizadoEm, segunda.atualizadoEm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun limiteDePedidosEsperaAntesDeRepetir() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(429, "") }))
        repo(http).chegadas(terminal).test {
            assertTrue(awaitItem().tempoRealIndisponivel)
            advanceTimeBy(19_000)
            assertEquals(1, http.pedidos.size)
            advanceTimeBy(2_000)
            awaitItem()
            assertEquals(2, http.pedidos.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun paragemSemChegadas() = runTest {
        val http = HttpPorUrl("arrivals" to listOf({ Resposta(200, "[]") }))
        repo(http).chegadas(terminal).test {
            val e = awaitItem()
            assertTrue(e.chegadas.isEmpty())
            assertFalse(e.semLigacao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun pesquisaSemAcentosNemMaiusculas() = runTest {
        val http = HttpPorUrl(
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
            "geocode" to listOf({ Resposta(200, "[]") }),
        )
        val r = repo(http)
        assertTrue(r.atualizarRede())
        assertTrue(r.pesquisar("sodre").any { it.nome == "Cais do Sodré (Terminal)" })
        assertEquals("#C61D23", r.linhaCm("3708")?.cor)
    }

    @Test fun mostraOGuardadoAntesDeAPrimeiraRespostaChegar() = runTest {
        val rapido = HttpPorUrl("arrivals" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }))
        repo(rapido).chegadas(terminal).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        val lento = object : Http {
            override suspend fun get(url: String): Resposta { kotlinx.coroutines.delay(30_000); return Resposta(200, "[]") }
        }
        repo(lento).chegadas(terminal).test {
            val primeira = awaitItem()
            assertEquals(3, primeira.chegadas.size)
            assertFalse(primeira.semLigacao)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun linhaQueFalhaNaoFicaGuardadaVazia() = runTest {
        val http = HttpPorUrl(
            "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
            "patterns" to listOf({ throw IOException("sem rede") }, { Resposta(200, fixture("cm-pattern-3708_0_1.json")) }),
        )
        val r = repo(http)
        assertTrue(r.atualizarRede())
        assertEquals(null, r.paragensDaLinha("3708"))
        assertTrue(r.paragensDaLinha("3708")!!.any { it.ref.id == "060419" })
    }
}
