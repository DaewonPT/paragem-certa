package pt.pixellusitano.paragemcerta.ui.linhas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.CatalogoLinhas
import pt.pixellusitano.paragemcerta.dados.DetalheDasLinhas
import pt.pixellusitano.paragemcerta.dados.HttpComEtag
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.LinhaEntidade
import pt.pixellusitano.paragemcerta.dados.ParagemEntidade
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.dados.normalizar
import java.io.IOException
import java.time.Instant

/**
 * O ecrã da linha com os dados verdadeiros (Room em memória, o catálogo e os pedidos às fontes com respostas falsas). Corre em tempo real
 * (o Main é o Unconfined): o JSON converte-se noutra thread, e um relógio virtual passava por cima dessa espera.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LinhaViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()

    // 03/11/2026: o dia em que os grupos de viagens da fixture do percurso correm; as chegadas da CM são de 26/10 e as do Metro de 03/11 à tarde.
    private val hoje = Instant.parse("2026-11-03T10:00:00Z")
    private val manha = Instant.parse("2026-10-26T05:00:00Z")
    private val tarde = Instant.parse("2026-11-03T18:00:00Z")

    // Os ecrãs deste teste vivem numa loja que se limpa no fim: o que cada um ainda estava a pedir pára com o teste, e não rebenta noutro.
    private val loja = ViewModelStore()

    @Before fun antes() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun depois() {
        loja.clear()
        Dispatchers.resetMain()
        bd.close()
    }

    private val cm3708 = RefLinha(Fonte.CM, "3708")
    private val az = RefLinha(Fonte.TRANSITOUS, "pt-Metro-Lisboa:Az")
    private val l15e = RefLinha(Fonte.TRANSITOUS, "pt-Carris:15E")
    private val p011 = fixture("cm-pattern-completo.json")
    private val p021 = p011.replace("3708_0_1", "3708_0_2").replace("\"direction_id\": 0", "\"direction_id\": 1")
        .replace("Lisboa (C. Sodré)", "Costa da Caparica (Terminal)")

    private fun rodar(bloco: suspend () -> Unit) = runBlocking { withTimeout(20_000) { bloco() } }

    private suspend fun LinhaViewModel.ate(condicao: (EstadoLinha) -> Boolean) = estado.first(condicao)

    private fun paragemCm(id: String, nome: String, lat: Double) =
        ParagemEntidade("CM:$id", "CM", id, nome, normalizar(nome), lat, -9.14, null, "3708")

    private suspend fun guardarLinhaCm() {
        bd.linhas().guardar(listOf(LinhaEntidade("3708", "3708", "Costa da Caparica - Cais do Sodré", "#C61D23", "#FFFFFF", "3708_0_1,3708_0_2")))
        bd.paragens().guardar(
            listOf(
                paragemCm("020735", "Costa da Caparica (Terminal)", 38.64), paragemCm("020002", "Trafaria", 38.67),
                paragemCm("060419", "Cais do Sodré (Terminal)", 38.7058),
            ),
        )
    }

    private fun httpCm() = HttpPorUrl(
        "patterns/3708_0_1" to listOf({ Resposta(200, p011) }),
        "patterns/3708_0_2" to listOf({ Resposta(200, p021) }),
        "shapes/" to listOf({ Resposta(200, fixture("cm-shape.json")) }),
        "arrivals/by_stop" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
    )

    private fun httpMetro() = HttpPorUrl(
        "window=86400" to listOf({ Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) }),
        "stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes-metro-mae.json")) }),
    )

    private suspend fun catalogoPronto() = catalogoACarregar().also { it.aoAbrir() }

    private fun catalogoACarregar(resposta: () -> Resposta = { Resposta(200, fixture("linhas.json"), "\"v1\"") }) =
        CatalogoLinhas(pasta.newFolder(), HttpComEtag(resposta), "https://exemplo.pt", relogio = { hoje })

    private inner class Mundo(val http: HttpPorUrl, catalogo: CatalogoLinhas, relogioDasChegadas: Instant = manha) {
        val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { relogioDasChegadas })
        private val detalhes = DetalheDasLinhas(CarrisMetropolitana(http), Transitous(http), catalogo, repositorio, relogio = { hoje })

        fun vm(
            ref: RefLinha, sentido: Int? = null, paragem: String? = null, posicao: Pair<Double, Double>? = null,
            relatos: suspend (String) -> List<RelatoRemoto> = { emptyList() },
        ): LinhaViewModel = ViewModelProvider(
            loja,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LinhaViewModel(ref, detalhes, repositorio, relatos, sentido, paragem, posicao, relogio = { hoje }) as T
            },
        )[ref.chave, LinhaViewModel::class.java]

        fun paragensComChegadasPedidas() = http.pedidos.filter { "arrivals/by_stop/" in it }.map { it.substringAfterLast('/') }
        fun pedidosDeHorario() = http.pedidos.count { "window=86400" in it }
    }

    @Test fun soAParagemEscolhidaPedeChegadas() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        val vm = m.vm(cm3708)
        // Sem favorita nem posição abre na primeira paragem, e só ela pede chegadas.
        val aberta = vm.ate { it.chegadas != null }
        assertEquals("020735", aberta.escolhida?.ref?.id)
        assertEquals(listOf("020735"), m.paragensComChegadasPedidas())
        // Escolher outra passa o pedido para ela; a lista das paragens nunca pede chegadas (a 060419 também não).
        vm.escolherParagem(aberta.detalhe!!.sentidos[0].paragens[1])
        val outra = vm.ate { it.escolhida?.ref?.id == "020002" && it.chegadas != null }
        assertEquals(listOf("020735", "020002"), m.paragensComChegadasPedidas())
        // As chegadas são as da linha no sentido à vista (os dois percursos do 3708_0_1 na fixture).
        assertEquals(listOf("3708_0_1", "3708_0_1"), outra.chegadas!!.chegadas.map { it.padrao })
    }

    @Test fun mudarDeSentidoMudaAsChegadasEVoltaAParagemDePartida() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        val vm = m.vm(cm3708)
        val e = vm.ate { it.chegadas != null }
        vm.escolherParagem(e.detalhe!!.sentidos[0].paragens[2])
        vm.ate { it.escolhida?.ref?.id == "060419" && it.chegadas != null }
        vm.mudarSentido(1)
        // No outro sentido a escolha volta à paragem de partida, e as chegadas são só as dele.
        val volta = vm.ate { it.sentido == 1 && it.escolhida?.ref?.id == "020735" && it.chegadas != null }
        assertEquals(listOf("3708_0_2"), volta.chegadas!!.chegadas.map { it.padrao })
    }

    @Test fun abreNoSentidoENaParagemDaRota() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        val e = m.vm(cm3708, sentido = 1, paragem = "CM:020002").ate { it.chegadas != null }
        assertEquals(1, e.sentido)
        assertEquals("020002", e.escolhida?.ref?.id)
        assertEquals(listOf("020002"), m.paragensComChegadasPedidas())
    }

    @Test fun semFavoritaAbreNaParagemMaisPertoDeTi() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        val e = m.vm(cm3708, posicao = 38.7060 to -9.1400).ate { it.chegadas != null }
        assertEquals("060419", e.escolhida?.ref?.id)
    }

    @Test fun abreNaParagemHabitualDaFavorita() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        m.repositorio.alternarLinhaFavorita(cm3708, 1, "Costa da Caparica (Terminal)", Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 38.7058, -9.14, null), "3708")
        // A favorita é do sentido 1 e a habitual é a 060419, mesmo com a posição longe dela.
        val e = m.vm(cm3708, posicao = 38.64 to -9.14).ate { it.chegadas != null }
        assertEquals(1, e.sentido)
        assertEquals("060419", e.escolhida?.ref?.id)
        assertEquals(e.escolhida?.ref, e.favorita?.paragem)
    }

    @Test fun horarioSoQuandoASecaoAparece() = rodar {
        val m = Mundo(httpMetro(), catalogoPronto(), tarde)
        val vm = m.vm(az)
        val aberta = vm.ate { it.chegadas != null }
        assertEquals("pt-Metro-Lisboa_CM", aberta.escolhida?.ref?.id)
        // Com a paragem e as chegadas à vista, o horário do dia ainda não foi pedido.
        assertFalse(aberta.horarioAberto)
        assertNull(aberta.horario)
        assertEquals(0, m.pedidosDeHorario())
        vm.verHorario()
        val horas = vm.ate { it.horario != null }
        assertTrue(horas.horarioAberto)
        assertEquals(listOf("06:31", "06:38", "23:56", "00:05", "01:10", "01:20"), horas.horario!!.map { it.toString() })
        assertEquals(1, m.pedidosDeHorario())
        // Com a secção aberta, outra paragem pede o horário dela; a anterior não fica à vista na nova.
        vm.escolherParagem(horas.detalhe!!.sentidos[0].paragens[2])
        val outra = vm.ate { it.escolhida?.ref?.id == "pt-Metro-Lisboa_SP" && it.horario != null }
        assertEquals(emptyList<java.time.LocalTime>(), outra.horario)
        assertEquals(2, m.pedidosDeHorario())
    }

    @Test fun horarioQueFalhaFicaMarcadoEPodePedirseOutraVez() = rodar {
        val http = HttpPorUrl(
            "window=86400" to listOf({ throw IOException("sem rede") }, { Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) }),
            "stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes-metro-mae.json")) }),
        )
        val vm = Mundo(http, catalogoPronto(), tarde).vm(az)
        vm.ate { it.detalhe != null }
        vm.verHorario()
        val falhou = vm.ate { it.horarioFalhou }
        assertNull(falhou.horario)
        // Escolher outra paragem tenta outra vez (a falha não ficou guardada).
        vm.escolherParagem(falhou.detalhe!!.sentidos[0].paragens[1])
        val outra = vm.ate { it.escolhida?.ref?.id == "pt-Metro-Lisboa_MP1" && it.horario != null }
        assertFalse(outra.horarioFalhou)
    }

    @Test fun estrelaGuardaSentidoEParagem() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        val vm = m.vm(cm3708)
        val e = vm.ate { it.detalhe != null }
        vm.mudarSentido(1)
        vm.escolherParagem(e.detalhe!!.sentidos[1].paragens[1])
        val antes = vm.ate { it.sentido == 1 && it.escolhida?.ref?.id == "020002" }
        assertNull(antes.favorita)
        vm.alternarFavorita()
        val f = vm.ate { it.favorita != null }.favorita!!
        // O sentido que estava à vista e a paragem escolhida, e o nome e o destino de agora.
        assertEquals(cm3708, f.ref)
        assertEquals(1, f.sentido)
        assertEquals(RefParagem(Fonte.CM, "020002"), f.paragem)
        assertEquals("3708", f.nome)
        assertEquals("Costa da Caparica (Terminal)", f.destino)
        assertEquals(listOf(f), m.repositorio.linhasFavoritas().first())
        // A ★ outra vez tira-a, e o que estava à vista fica como estava (não salta para a paragem de partida).
        vm.alternarFavorita()
        val depois = vm.ate { it.favorita == null }
        assertEquals(1, depois.sentido)
        assertEquals("020002", depois.escolhida?.ref?.id)
        assertTrue(m.repositorio.linhasFavoritas().first().isEmpty())
    }

    @Test fun outraParagemNumaFavoritaOfereceUsarComoHabitual() = rodar {
        guardarLinhaCm()
        val m = Mundo(httpCm(), catalogoPronto())
        m.repositorio.alternarLinhaFavorita(cm3708, 0, "Lisboa (C. Sodré)", Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré (Terminal)", 38.7058, -9.14, null), "3708")
        val vm = m.vm(cm3708)
        val e = vm.ate { it.chegadas != null }
        assertEquals(e.escolhida?.ref, e.favorita?.paragem)
        vm.escolherParagem(e.detalhe!!.sentidos[0].paragens[1])
        // A escolhida (Trafaria) já não é a habitual (Cais do Sodré): é aqui que aparece "Usar como paragem habitual".
        val outra = vm.ate { it.escolhida?.ref?.id == "020002" }
        assertEquals("060419", outra.favorita?.paragem?.id)
        vm.usarComoHabitual()
        val nova = vm.ate { it.favorita?.paragem?.id == "020002" }
        assertEquals(nova.escolhida?.ref, nova.favorita?.paragem)
        // Continua a ser uma só favorita desta linha e deste sentido.
        assertEquals(listOf("020002"), m.repositorio.linhasFavoritas().first().map { it.paragem.id })
    }

    @Test fun catalogoACarregarNaoDizQueALinhaNaoExiste() = rodar {
        val catalogo = catalogoACarregar()
        val vm = Mundo(httpMetro(), catalogo).vm(l15e)
        // Enquanto o catálogo não chega, nem a linha existe nem deixa de existir.
        delay(300)
        vm.estado.value.let {
            assertNull(it.detalhe)
            assertFalse(it.semLinha)
            assertFalse(it.falhou)
        }
        catalogo.aoAbrir()
        assertEquals("15E", vm.ate { it.detalhe != null }.detalhe?.nome)
    }

    @Test fun catalogoProntoSemALinhaDizQueJaNaoExiste() = rodar {
        val vm = Mundo(httpCm(), catalogoPronto()).vm(RefLinha(Fonte.TRANSITOUS, "pt-Carris:999"))
        val e = vm.ate { it.semLinha }
        assertNull(e.detalhe)
        assertFalse(e.falhou)
    }

    @Test fun catalogoQueFalhouNaoDizQueJaNaoExiste() = rodar {
        val catalogo = catalogoACarregar { throw IOException("sem rede") }.also { it.aoAbrir() }
        val e = Mundo(httpCm(), catalogo).vm(l15e).ate { it.falhou }
        assertFalse(e.semLinha)
    }

    @Test fun linhaDaCmQueNaoEstaNaRedeGuardadaDizQueJaNaoExiste() = rodar {
        guardarLinhaCm()
        val e = Mundo(httpCm(), catalogoPronto()).vm(RefLinha(Fonte.CM, "9999")).ate { it.semLinha || it.falhou }
        assertTrue(e.semLinha)
    }

    @Test fun relatosDaLinhaChegamDepoisDoDetalhe() = rodar {
        guardarLinhaCm()
        val pedidos = mutableListOf<String>()
        val relato = RelatoRemoto("a", "desvio", "CM:060419", "3708", hoje.toEpochMilli(), hoje.toEpochMilli() + 3_600_000, 2, true)
        val vm = Mundo(httpCm(), catalogoPronto()).vm(cm3708, relatos = { pedidos += it; listOf(relato) })
        assertEquals(listOf(relato), vm.ate { it.relatos.isNotEmpty() }.relatos)
        // Pedem-se pelo nome que a pessoa vê, uma só vez.
        assertEquals(listOf("3708"), pedidos)
    }
}
