package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.mapa.Dono
import java.time.Instant

/** O que o detalhe pede à CM: as chegadas da paragem de embarque e os veículos; guarda o URL de cada pedido. 404 nas chegadas = nenhuma. */
private class RedeDaCm : Http {
    var chegadas = { Resposta(404, "") }
    var veiculos = { Resposta(200, "[]") }
    val pedidos = mutableListOf<String>()

    fun a(parte: String) = pedidos.filter { parte in it }

    override suspend fun get(url: String): Resposta {
        pedidos += url
        return when {
            "arrivals/by_stop/" in url -> chegadas()
            url.endsWith("vehicles") -> veiculos()
            else -> Resposta(404, "")
        }
    }

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        return Resposta(404, "")
    }
}

@RunWith(RobolectricTestRunner::class)
class AutocarroNoDetalheTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val rede = RedeDaCm()
    private val repositorio = Repositorio(bd, CarrisMetropolitana(rede), Transitous(rede), GeoApi(rede))
    private val dono = Dono()

    // A rota do detalhe com uma etapa da 3708 da Carris Metropolitana (a 3.ª), que entra na paragem 020047.
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0].let { r ->
        val b = r.etapas[1]
        val cm = b.copy(linhaNome = "3708", paragens = listOf(ParagemEtapa("pt-Carris-Metropolitana_320047", b.de.nome, b.de.lat, b.de.lon, b.partida)))
        r.copy(etapas = r.etapas.take(2) + cm + r.etapas.drop(2))
    }
    private val partidaDaEtapa = rota.etapas[2].partidaProgramada

    private var seguida by mutableStateOf<Int?>(null)
    private var visto: Pair<Veiculo?, EstadoAutocarro>? = null

    @After fun fechar() = bd.close()

    // Como o DetalheRotaEcra, sem o MapView (o Robolectric não o desenha).
    private fun abrir(estado: Lifecycle.State) {
        dono.registo.currentState = estado
        regra.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides dono) {
                visto = rememberAutocarro(repositorio, seguida, seguida?.let(rota.etapas::getOrNull))
            }
        }
    }

    // A chegada da 3708 à hora do horário da etapa, com tempo real; e o veículo dessa viagem com a posição de agora.
    private fun chegadaJson(viagem: String) =
        """[{"line_id":"3708","headsign":"Oriente","scheduled_arrival_unix":${partidaDaEtapa.epochSecond},""" +
            """"estimated_arrival_unix":${Instant.now().plusSeconds(300).epochSecond},"trip_id":"$viagem"}]"""

    private fun veiculoJson(viagem: String) =
        """[{"id":"41|1234","line_id":"3708","lat":38.7,"lon":-9.1,"timestamp":${System.currentTimeMillis()},"trip_id":"$viagem"}]"""

    private fun passar(ms: Long) = regra.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)

    private fun quadro() {
        regra.waitForIdle()
        regra.mainClock.advanceTimeByFrame()
    }

    private fun irPara(estado: Lifecycle.State) {
        regra.runOnUiThread { dono.registo.currentState = estado }
        regra.waitForIdle()
    }

    // Converter uma resposta corre noutro fio (Dispatchers.Default), que o relógio do teste não espera.
    private fun esperarPor(condicao: () -> Boolean) {
        repeat(300) {
            if (condicao()) return
            Thread.sleep(10)
            quadro()
        }
        fail("não chegou o que se esperava; pedidos: ${rede.pedidos}, visto: $visto")
    }

    private fun chegadas() = rede.a("arrivals/by_stop/020047").size
    private fun veiculos() = rede.a("vehicles").size

    @Test fun soPedeDepoisDoToqueEComOEcraAVista() {
        regra.mainClock.autoAdvance = false
        abrir(Lifecycle.State.STARTED)
        passar(60_000)
        assertEquals(0, chegadas())
        assertEquals(0, veiculos())

        // Com a app em segundo plano, a etapa escolhida espera pelo ecrã.
        irPara(Lifecycle.State.CREATED)
        seguida = 2
        quadro()
        passar(120_000)
        assertEquals(0, chegadas())
        assertEquals(0, veiculos())

        // À vista: as chegadas logo e outra vez 30 s depois; os veículos também.
        irPara(Lifecycle.State.STARTED)
        esperarPor { chegadas() == 1 && veiculos() >= 1 }
        passar(29_000)
        assertEquals(1, chegadas())
        passar(2_000)
        assertEquals(2, chegadas())

        // Em segundo plano deixa de pedir.
        val c = chegadas()
        val v = veiculos()
        irPara(Lifecycle.State.CREATED)
        passar(120_000)
        assertEquals(c, chegadas())
        assertEquals(v, veiculos())
    }

    @Test fun semRespostaNenhumaDeixaDeProcurar() {
        rede.chegadas = { Resposta(500, "") }
        rede.veiculos = { Resposta(500, "") }
        regra.mainClock.autoAdvance = false
        seguida = 2
        abrir(Lifecycle.State.STARTED)
        esperarPor { chegadas() == 1 }
        quadro()
        assertEquals(EstadoAutocarro.A_PROCURAR, visto?.second)
        // A 2.ª consulta também falha: em vez de procurar para sempre, diz que não há posição.
        passar(31_000)
        esperarPor { visto?.second == EstadoAutocarro.SEM_POSICAO }
        assertEquals(2, chegadas())
    }

    @Test fun depoisDePassarNaParagemOAutocarroContinuaNoMapa() {
        rede.chegadas = { Resposta(200, chegadaJson("T1")) }
        rede.veiculos = { Resposta(200, veiculoJson("T1")) }
        regra.mainClock.autoAdvance = false
        seguida = 2
        abrir(Lifecycle.State.STARTED)
        esperarPor { visto?.first?.id == "41|1234" }
        quadro()

        // O autocarro passou na paragem: as chegadas já não o trazem, mas é o mesmo que estava no mapa.
        rede.chegadas = { Resposta(404, "") }
        passar(31_000)
        quadro()
        assertEquals(2, chegadas())
        assertEquals("41|1234", visto?.first?.id)
        assertEquals(EstadoAutocarro.NO_MAPA, visto?.second)
        assertTrue(veiculos() >= 2)
    }
}
