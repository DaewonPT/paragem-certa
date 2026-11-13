package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.IOException
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AtualizacaoDiariaTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
    private val agora = Instant.parse("2026-11-02T10:00:00Z")

    @After fun fechar() = bd.close()

    private fun comRede() = HttpPorUrl(
        "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
        "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
    )

    private fun repo(http: Http) = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { agora })

    private fun semRede() = object : Http {
        override suspend fun get(url: String): Resposta = throw IOException("sem rede")
    }

    @Test fun primeiraAberturaDescarregaARede() = runTest {
        val http = comRede()
        assertTrue(AtualizacaoDiaria.aoAbrir(contexto, repo(http), agora))
        assertTrue(http.pedidos.any { it.contains("/stops") })
        assertFalse(bd.paragens().contarCm() == 0)
    }

    @Test fun redeComMenosDe24HorasNaoVoltaAAtualizar() = runTest {
        AtualizacaoDiaria.aoAbrir(contexto, repo(comRede()), agora)
        val http = comRede()
        assertFalse(AtualizacaoDiaria.aoAbrir(contexto, repo(http), agora + Duration.ofHours(23)))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun redeComMaisDe24HorasAtualizaOutraVez() = runTest {
        AtualizacaoDiaria.aoAbrir(contexto, repo(comRede()), agora)
        val http = comRede()
        assertTrue(AtualizacaoDiaria.aoAbrir(contexto, repo(http), agora + Duration.ofHours(25)))
        assertTrue(http.pedidos.any { it.contains("/stops") })
    }

    // Sem paragens na base (a primeira utilização, ou uma base refeita) atualiza sempre, mesmo com a hora guardada recente.
    @Test fun redeVaziaAtualizaMesmoComAHoraRecente() = runTest {
        AtualizacaoDiaria.aoAbrir(contexto, repo(comRede()), agora)
        bd.paragens().substituirFonte("CM", emptyList())
        val http = comRede()
        assertTrue(AtualizacaoDiaria.aoAbrir(contexto, repo(http), agora + Duration.ofMinutes(5)))
        assertTrue(http.pedidos.isNotEmpty())
    }

    // Sem rede não se gasta o dia: a abertura seguinte tenta outra vez.
    @Test fun semRedeNaoGastaATentativa() = runTest {
        assertFalse(AtualizacaoDiaria.aoAbrir(contexto, repo(semRede()), agora))
        assertTrue(AtualizacaoDiaria.aoAbrir(contexto, repo(comRede()), agora + Duration.ofMinutes(1)))
    }
}
