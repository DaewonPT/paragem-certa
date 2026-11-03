package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetalheDasLinhasTest {
    @get:Rule val pasta = TemporaryFolder()
    private val bd = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()

    // 03/11/2026: o dia em que os grupos de viagens da fixture do percurso correm. A fixture das chegadas da CM é de 26/10 e a do Metro de 03/11 à tarde.
    private val hoje = Instant.parse("2026-11-03T10:00:00Z")
    private val manha = Instant.parse("2026-10-26T05:00:00Z")
    private val tarde = Instant.parse("2026-11-03T18:00:00Z")

    @After fun fechar() = bd.close()

    private val cm3708 = RefLinha(Fonte.CM, "3708")
    private val az = RefLinha(Fonte.TRANSITOUS, "pt-Metro-Lisboa:Az")
    private val l15e = RefLinha(Fonte.TRANSITOUS, "pt-Carris:15E")

    // 3708_0_1 (sentido 0, 2 viagens hoje), 3708_1_1 (também sentido 0, sem viagens hoje, e a horas diferentes) e 3708_0_2 (sentido 1).
    private val p011 = fixture("cm-pattern-completo.json")
    private val p111 = p011.replace("3708_0_1", "3708_1_1").replace("Lisboa (C. Sodré)", "Lisboa (Sete Rios)")
        .replace("\"20261103\",", "\"20261105\",").replace("05:25:00", "04:50:00").replace("25:25:00", "24:50:00")
    private val p021 = p011.replace("3708_0_1", "3708_0_2").replace("\"direction_id\": 0", "\"direction_id\": 1")
        .replace("Lisboa (C. Sodré)", "Costa da Caparica (Terminal)")

    // O HttpPorUrl usa o primeiro prefixo que coincide: o que o teste pede em [antes] tem de ficar à frente dos de omissão.
    private fun httpCm(vararg antes: Pair<String, List<() -> Resposta>>) = HttpPorUrl(
        *antes,
        "patterns/3708_0_1" to listOf({ Resposta(200, p011) }),
        "patterns/3708_1_1" to listOf({ Resposta(200, p111) }),
        "patterns/3708_0_2" to listOf({ Resposta(200, p021) }),
        "shapes/" to listOf({ Resposta(200, fixture("cm-shape.json")) }),
        "arrivals/by_stop" to listOf({ Resposta(200, fixture("cm-arrivals-060419.json")) }),
    )

    private fun paragemCm(id: String, nome: String, lat: Double) =
        ParagemEntidade("CM:$id", "CM", id, nome, normalizar(nome), lat, -9.14, null, "3708")

    private suspend fun guardarLinhaCm() {
        bd.linhas().guardar(listOf(LinhaEntidade("3708", "3708", "Costa da Caparica - Cais do Sodré", "#C61D23", "#FFFFFF", "3708_0_1,3708_1_1,3708_0_2")))
        bd.paragens().guardar(
            listOf(
                paragemCm("020735", "Costa da Caparica (Terminal)", 38.64), paragemCm("020002", "Trafaria", 38.67),
                paragemCm("060419", "Cais do Sodré (Terminal)", 38.7058),
            ),
        )
    }

    private suspend fun catalogoPronto() = catalogoACarregar().also { it.aoAbrir() }

    private fun catalogoACarregar(resposta: () -> Resposta = { Resposta(200, fixture("linhas.json"), "\"v1\"") }) =
        CatalogoLinhas(pasta.newFolder(), HttpComEtag(resposta), "https://exemplo.pt", relogio = { hoje })

    private fun detalhes(http: Http, catalogo: CatalogoLinhas, relogioDoRepositorio: Instant = manha) = DetalheDasLinhas(
        CarrisMetropolitana(http), Transitous(http), catalogo,
        Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { relogioDoRepositorio }),
        relogio = { hoje },
    )

    private fun paragemTransitous(id: String) = Paragem(RefParagem(Fonte.TRANSITOUS, id), id, 38.7, -9.1, null)

    @Test fun detalheCmComSentidosDosPadroes() = runTest {
        guardarLinhaCm()
        val d = (detalhes(httpCm(), catalogoPronto()).detalhe(cm3708) as Resultado.Ok).valor
        assertEquals("3708", d.nome)
        assertEquals("Costa da Caparica - Cais do Sodré", d.nomeLongo)
        assertEquals("Carris Metropolitana", d.operador)
        assertEquals(Modo.AUTOCARRO, d.modo)
        assertEquals("#C61D23", d.cor)
        assertEquals(listOf(0, 1), d.sentidos.map { it.sentido })
        val ida = d.sentidos[0]
        // Dois percursos com direction_id 0: dá o nome e as paragens o que tem mais viagens hoje; as chegadas juntam os dois.
        assertEquals("Lisboa (C. Sodré)", ida.destino)
        assertEquals(setOf("3708_0_1", "3708_1_1"), ida.padroes)
        assertEquals(setOf("Lisboa (C. Sodré)", "Lisboa (Sete Rios)"), ida.destinos)
        assertEquals(listOf("Costa da Caparica (Terminal)", "Trafaria", "Cais do Sodré (Terminal)"), ida.paragens.map { it.nome })
        assertEquals("[2QDAD]3708_0_1", ida.formaId)
        assertEquals(setOf("3708_0_2"), d.sentidos[1].padroes)
        assertEquals("Costa da Caparica (Terminal)", d.sentidos[1].destino)
    }

    @Test fun detalheDoCatalogo() = runTest {
        val http = HttpPorUrl("x" to listOf({ Resposta(200, "") }))
        val d = (detalhes(http, catalogoPronto()).detalhe(l15e) as Resultado.Ok).valor
        assertEquals("15E", d.nome)
        assertEquals("Pç. Figueira - Algés", d.nomeLongo)
        // O nome para mostrar é o do operador, não o id do Transitous.
        assertEquals("Carris", d.operador)
        assertEquals(Modo.ELETRICO, d.modo)
        assertEquals(setOf("pt-Carris_76_0", "pt-Carris_76_1"), d.rotas)
        assertEquals(listOf(0, 1), d.sentidos.map { it.sentido })
        assertEquals("Algés", d.sentidos[0].destino)
        assertEquals(listOf("pt-Carris_920", "pt-Carris_3803", "pt-Carris_4215"), d.sentidos[0].paragens.map { it.ref.id })
        assertTrue(d.sentidos.all { it.padroes.isEmpty() })
        // Do catálogo, sem rede nenhuma.
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun linhaDoCatalogoSemParagensNaoAbre() = runTest {
        // O catálogo vem de fora: uma linha cujos sentidos não têm paragens não tem ecrã (e não rebenta a abrir).
        val semParagens = """{"versao":1,"geradoEm":"2026-11-03","operadores":[{"id":"pt-Carris","nome":"Carris"}],"paragens":{},
            "linhas":[{"operador":"pt-Carris","nome":"99","modo":"AUTOCARRO","rotas":["pt-Carris_99_0"],
            "sentidos":[{"sentido":0,"destino":"X","destinos":["X"],"paragens":[],"forma":""}]}]}"""
        val catalogo = catalogoACarregar { Resposta(200, semParagens, "\"v1\"") }.also { it.aoAbrir() }
        val dl = detalhes(HttpPorUrl("x" to listOf({ Resposta(200, "") })), catalogo)
        assertEquals(Resultado.Falha(null, limite = false), dl.detalhe(RefLinha(Fonte.TRANSITOUS, "pt-Carris:99")))
    }

    @Test fun linhaForaDoCatalogoDa404() = runTest {
        val http = HttpPorUrl("x" to listOf({ Resposta(200, "") }))
        val dl = detalhes(http, catalogoPronto())
        assertEquals(Resultado.Falha(404, limite = false), dl.detalhe(RefLinha(Fonte.TRANSITOUS, "pt-Carris:999")))
        // Com a rede da CM guardada, uma linha que lá não está também já não existe.
        guardarLinhaCm()
        assertEquals(Resultado.Falha(404, limite = false), dl.detalhe(RefLinha(Fonte.CM, "9999")))
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun catalogoACarregarEsperaEmVezDeDizerQueNaoExiste() = runTest {
        val catalogo = catalogoACarregar()
        val dl = detalhes(HttpPorUrl("x" to listOf({ Resposta(200, "") })), catalogo)
        val perdida = async { dl.detalhe(RefLinha(Fonte.TRANSITOUS, "pt-Carris:999")) }
        val achada = async { dl.detalhe(l15e) }
        runCurrent()
        assertFalse(perdida.isCompleted)
        assertFalse(achada.isCompleted)
        catalogo.aoAbrir()
        // Só agora, com o catálogo carregado, se sabe qual existe e qual não.
        assertEquals(Resultado.Falha(404, limite = false), perdida.await())
        assertEquals("15E", (achada.await() as Resultado.Ok).valor.nome)
    }

    @Test fun catalogoQueFalhouNaoDizQueNaoExiste() = runTest {
        val catalogo = catalogoACarregar { throw IOException("sem rede") }.also { it.aoAbrir() }
        assertEquals(EstadoCatalogo.Falhou, catalogo.estado.value)
        assertEquals(Resultado.Falha(null, limite = false), detalhes(HttpPorUrl("x" to listOf({ Resposta(200, "") })), catalogo).detalhe(l15e))
    }

    @Test fun linhaDaCmSemRedeNemRedeGuardadaNaoDizQueNaoExiste() = runTest {
        // Telemóvel novo: a rede da CM não está no Room e o pedido falha. Não se sabe se a linha existe.
        val http = HttpPorUrl("stops" to listOf({ throw IOException("sem rede") }), "lines" to listOf({ throw IOException("sem rede") }))
        assertEquals(Resultado.Falha(null, limite = false), detalhes(http, catalogoACarregar()).detalhe(cm3708))
        // A rede chega a meio do pedido: a linha aparece em vez de "já não existe".
        val comRede = HttpPorUrl(
            "stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
            "lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
            "patterns/" to listOf({ Resposta(200, p011) }),
        )
        assertTrue(detalhes(comRede, catalogoACarregar()).detalhe(cm3708) is Resultado.Ok)
    }

    @Test fun formaQueFalhaNaoTiraAsParagens() = runTest {
        guardarLinhaCm()
        val http = httpCm("shapes/" to listOf({ Resposta(500, "") }, { Resposta(200, fixture("cm-shape.json")) }))
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(cm3708) as Resultado.Ok).valor
        // A forma vem à parte: o detalhe não a leva, e uma falha dela deixa as paragens.
        assertEquals(3, d.sentidos[0].paragens.size)
        assertEquals(emptyList<Pair<Double, Double>>(), dl.forma(d, 0))
        // Uma falha não fica guardada; uma forma que chegou fica e não se pede outra vez.
        val forma = dl.forma(d, 0)
        assertEquals(CmParser.forma(fixture("cm-shape.json")), forma)
        assertEquals(forma, dl.forma(d, 0))
        val pedidosDaForma = http.pedidos.filter { "shapes/" in it }
        assertEquals(2, pedidosDaForma.size)
        assertTrue(pedidosDaForma.last().endsWith("shapes/%5B2QDAD%5D3708_0_1"))
    }

    @Test fun formaDoCatalogoNaoPedeNada() = runTest {
        val http = HttpPorUrl("x" to listOf({ Resposta(200, "") }))
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(l15e) as Resultado.Ok).valor
        assertEquals(3, dl.forma(d, 0).size)
        assertTrue(http.pedidos.isEmpty())
    }

    @Test fun paragemInicialHabitualDepoisPertoDepoisPrimeira() {
        val a = Paragem(RefParagem(Fonte.CM, "1"), "A", 38.7000, -9.1500, null)
        val b = Paragem(RefParagem(Fonte.CM, "2"), "B", 38.7100, -9.1500, null)
        val c = Paragem(RefParagem(Fonte.CM, "3"), "C", 38.7200, -9.1500, null)
        val s = SentidoLinha(0, "C", setOf("C"), listOf(a, b, c), emptySet())
        // A habitual (se a linha a tem nesse sentido) ganha a quem está mais perto.
        assertEquals(c, paragemInicial(s, "CM:3", 38.7001 to -9.1500))
        // Sem habitual (ou uma que não é deste sentido), a mais perto de ti, a menos de 800 m.
        assertEquals(b, paragemInicial(s, null, 38.7102 to -9.1500))
        assertEquals(b, paragemInicial(s, "CM:99", 38.7102 to -9.1500))
        // Longe de todas ou sem posição, a primeira.
        assertEquals(a, paragemInicial(s, null, 40.0 to -8.0))
        assertEquals(a, paragemInicial(s, null, null))
    }

    @Test fun proximasSoNaParagemEscolhida() = runTest {
        guardarLinhaCm()
        val http = httpCm()
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(cm3708) as Resultado.Ok).valor
        val escolhida = d.sentidos[0].paragens.last()
        // Uma só paragem pede chegadas: a escolhida. As outras (Trafaria, o terminal da Caparica) não.
        val e = dl.chegadas(d, 0, escolhida).first()
        assertEquals(listOf("arrivals/by_stop/060419"), http.pedidos.filter { "arrivals" in it }.map { it.substringAfter("v2/") })
        // Do sentido 0 só as chegadas dos percursos dele; a de 3708_0_2 é do sentido 1 (o terminal parte e chega na mesma paragem).
        assertEquals(listOf("3708_0_1", "3708_0_1"), e.chegadas.map { it.padrao })
        val volta = dl.chegadas(d, 1, escolhida).first()
        assertEquals(listOf("3708_0_2"), volta.chegadas.map { it.padrao })
    }

    @Test fun chegadasDoCatalogoSoDaLinhaEDoSentido() = runTest {
        val http = HttpPorUrl("stoptimes" to listOf({ Resposta(200, fixture("transitous-stoptimes-metro-mae.json")) }))
        val dl = detalhes(http, catalogoPronto(), relogioDoRepositorio = tarde)
        val d = (dl.detalhe(az) as Resultado.Ok).valor
        val mp = paragemTransitous("pt-Metro-Lisboa_MP")
        val paraReboleira = dl.chegadas(d, 1, mp).first()
        // A Azul no sentido de Reboleira: a Amarela (outra rota) e o outro sentido ficam de fora.
        assertEquals(2, paraReboleira.chegadas.size)
        assertTrue(paraReboleira.chegadas.all { it.linhaNome == "Az" && it.destino == "Reboleira" })
        assertEquals(1, http.pedidos.size)
        assertTrue(http.pedidos.single().contains("stopId=pt-Metro-Lisboa_MP"))
    }

    @Test fun horarioComPrimeiraEUltima() = runTest {
        val http = HttpPorUrl("window=86400" to listOf({ Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) }))
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(az) as Resultado.Ok).valor
        val colegioMilitar = d.sentidos[0].paragens.first()
        val dia = LocalDate.of(2026, 11, 4)
        val horas = (dl.horario(d, 0, colegioMilitar, dia) as Resultado.Ok).valor
        // O dia de serviço: a meia-noite não volta ao princípio, o último comboio é à 01:20.
        assertEquals(listOf("06:31", "06:38", "23:56", "00:05", "01:10", "01:20"), horas.map { it.toString() })
        assertEquals(LocalTime.of(6, 31), horas.first())
        assertEquals(LocalTime.of(1, 20), horas.last())
        // Guardado até ao fim do dia: o mesmo dia não pede outra vez; outro dia sim.
        assertEquals(horas, (dl.horario(d, 0, colegioMilitar, dia) as Resultado.Ok).valor)
        assertEquals(1, http.pedidos.size)
        dl.horario(d, 0, colegioMilitar, dia.plusDays(1))
        assertEquals(2, http.pedidos.size)
    }

    @Test fun horarioQueFalhaNaoFicaGuardado() = runTest {
        val http = HttpPorUrl("window=86400" to listOf({ Resposta(503, "") }, { Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) }))
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(az) as Resultado.Ok).valor
        val dia = LocalDate.of(2026, 11, 4)
        assertTrue(dl.horario(d, 0, d.sentidos[0].paragens.first(), dia) is Resultado.Falha)
        assertEquals(6, (dl.horario(d, 0, d.sentidos[0].paragens.first(), dia) as Resultado.Ok).valor.size)
    }

    @Test fun semPartidasHojeDaListaVazia() = runTest {
        val http = HttpPorUrl("window=86400" to listOf({ Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) }))
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(az) as Resultado.Ok).valor
        // Uma linha que não passa nesta paragem: nada do que o Transitous devolveu é dela.
        val outra = d.copy(rotas = setOf("pt-Metro-Lisboa_Z"))
        assertEquals(emptyList<LocalTime>(), (dl.horario(outra, 0, d.sentidos[0].paragens.first(), LocalDate.of(2026, 11, 4)) as Resultado.Ok).valor)
    }

    @Test fun horarioCmJuntaOsPercursosDoSentidoSemPedirOutraVez() = runTest {
        guardarLinhaCm()
        val http = httpCm()
        val dl = detalhes(http, catalogoPronto())
        val d = (dl.detalhe(cm3708) as Resultado.Ok).valor
        val cais = d.sentidos[0].paragens.last()
        // Hoje só o 3708_0_1 corre: a 05:25 e, depois da meia-noite, à 01:25 (o "25:25:00" do GTFS).
        assertEquals(
            listOf("05:25", "01:25"),
            (dl.horario(d, 0, cais, LocalDate.of(2026, 11, 3)) as Resultado.Ok).valor.map { it.toString() },
        )
        // A 04/11 correm os dois percursos do sentido 0: os normais por ordem e, no fim do dia de serviço, os da madrugada.
        assertEquals(
            listOf("04:50", "05:25", "00:50", "01:25"),
            (dl.horario(d, 0, cais, LocalDate.of(2026, 11, 4)) as Resultado.Ok).valor.map { it.toString() },
        )
        // O horário sai dos percursos que o detalhe já trouxe: nenhum pedido novo.
        assertEquals(3, http.pedidos.count { "patterns/" in it })
    }
}
