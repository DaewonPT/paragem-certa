package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
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
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Faz de trabalho periódico das versões até à 0.3.1: o que interessa é que seja cancelado, não o que fazia. */
class TrabalhoAntigo(contexto: Context, parametros: WorkerParameters) : Worker(contexto, parametros) {
    override fun doWork() = Result.success()
}

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

    @Test fun cancelaOsTrabalhosPeriodicosDeAntes() {
        WorkManagerTestInitHelper.initializeTestWorkManager(contexto) // executores síncronos: o cancelamento é imediato
        val trabalhos = WorkManager.getInstance(contexto)
        val nomes = listOf("rede-cm", "atualizacao-automatica")
        for (nome in nomes) {
            val pedido = PeriodicWorkRequestBuilder<TrabalhoAntigo>(24, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.DAYS).build()
            trabalhos.enqueueUniquePeriodicWork(nome, ExistingPeriodicWorkPolicy.KEEP, pedido).result.get()
        }
        val vivos = WorkQuery.fromStates(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        assertEquals("os dois trabalhos de antes estão agendados", 2, trabalhos.getWorkInfos(vivos).get().size)
        AtualizacaoDiaria.cancelarTrabalhosAntigos(contexto)
        for (nome in nomes) assertEquals(nome, WorkInfo.State.CANCELLED, trabalhos.getWorkInfosForUniqueWork(nome).get().single().state)
        // E nada de agendado no WorkManager: com a app fechada não corre nada.
        assertTrue(trabalhos.getWorkInfos(vivos).get().isEmpty())
    }
}
