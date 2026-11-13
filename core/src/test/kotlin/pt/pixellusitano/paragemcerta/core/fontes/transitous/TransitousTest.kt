package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.linhas.SentidoCatalogo
import pt.pixellusitano.paragemcerta.core.linhas.daLinhaNoSentido
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TransitousTest {
    private val agora = Instant.parse("2026-10-27T01:50:00Z")

    @Test fun chegadasDaParagemPedida() {
        val c = TransitousParser.chegadas(fixture("transitous-stoptimes.json"), "pt-Carris_3803", agora)
        assertEquals(1, c.size)
        assertEquals("201", c[0].linhaNome)
        assertEquals(Instant.parse("2026-10-27T01:54:00Z"), c[0].programada)
        assertNull(c[0].prevista)
        assertEquals(Modo.AUTOCARRO, c[0].modo)
        assertEquals(Fonte.TRANSITOUS, c[0].fonte)
        assertEquals(false, c[0].acessivel)
    }

    @Test fun outraParagemNaoAparece() {
        assertTrue(TransitousParser.chegadas(fixture("transitous-stoptimes.json"), "pt-Comboios-de-Portugal(CP)_94_69005", agora).isEmpty())
    }

    @Test fun canceladasSaoDescartadas() {
        val cancelada = fixture("transitous-stoptimes.json").replaceFirst("\"cancelled\": false", "\"cancelled\": true")
        assertTrue(TransitousParser.chegadas(cancelada, "pt-Carris_3803", agora).isEmpty())
    }

    /** Muda o campo [campo] do 1.º item de stopTimes (não o do place) para true. */
    private fun cancelarNoItem(campo: String): String {
        val json = fixture("transitous-stoptimes.json")
        val inicioItem = json.indexOf("\"pickupDropoffType\"")
        val i = json.indexOf("\"$campo\": false", inicioItem)
        return json.substring(0, i) + "\"$campo\": true" + json.substring(i + "\"$campo\": false".length)
    }

    @Test fun partidaCanceladaEDescartada() {
        assertTrue(TransitousParser.chegadas(cancelarNoItem("cancelled"), "pt-Carris_3803", agora).isEmpty())
    }

    @Test fun viagemCanceladaEDescartada() {
        assertTrue(TransitousParser.chegadas(cancelarNoItem("tripCancelled"), "pt-Carris_3803", agora).isEmpty())
    }

    @Test fun nomeDoOperador() {
        assertEquals("CP", operadorDe("pt-Comboios-de-Portugal(CP)_94_69005"))
        assertEquals("Transtejo", operadorDe("pt-Transtejo-Soflusa_5"))
        assertEquals("Metro de Lisboa", operadorDe("pt-Metro-Lisboa_CS"))
        assertEquals("Carris", operadorDe("pt-Carris_3803"))
        assertNull(operadorDe("eu-FlixBus_123"))
    }

    @Test fun lugaresParaRotas() {
        val l = TransitousParser.lugares(fixture("transitous-geocode-morada.json"))
        assertEquals(6, l.size)
        assertTrue(l.take(4).all { it.tipo == TipoLugar.PARAGEM })
        assertEquals(Lugar("Avenida da Liberdade", l[4].lat, l[4].lon, TipoLugar.LOCAL, "Santo António · Lisboa"), l[4])
        assertEquals(TipoLugar.LOCAL, l[5].tipo)
    }

    @Test fun pesquisaDeLugaresSemTipo() = runTest {
        val http = HttpFalso({ Resposta(200, "[]") })
        Transitous(http).pesquisarLugares("Avenida da Liberdade")
        assertEquals("https://api.transitous.org/api/v1/geocode?text=Avenida+da+Liberdade", http.pedidos.single())
    }

    @Test fun lugaresTrazemAFreguesiaEOConcelho() {
        val l = TransitousParser.lugares(fixture("transitous-geocode-goa.json"))
        assertEquals("São João da Madeira", l[0].zona)
        assertEquals("Setúbal (São Julião, Nossa Senhora da Anunciada e Santa Maria da Graça)", l[2].zona)
        assertEquals("Pinhal Novo · Palmela", l[6].zona)
        assertEquals(
            listOf(
                "São João da Madeira", "Vila Real", "Setúbal (São Julião, Nossa Senhora da Anunciada e Santa Maria da Graça)",
                "Reguengos de Monsaraz", "Estremoz (Santa Maria e Santo André)", "Marinha Grande", "Pinhal Novo · Palmela",
                "Coruche", "São João Baptista · Entroncamento", "Abrantes (São Vicente e São João) e Alferrarede",
            ),
            l.map { it.zona },
        )
        // Só concelho (adminLevel 7) ou só freguesia (8): fica a que houver.
        assertEquals("Lisboa", TransitousParser.lugares("""[{"type":"ADDRESS","name":"Rua A","lat":38.7,"lon":-9.1,"areas":[{"name":"Lisboa","adminLevel":7.0}]}]""").single().zona)
        assertEquals("Alvalade", TransitousParser.lugares("""[{"type":"ADDRESS","name":"Rua A","lat":38.7,"lon":-9.1,"areas":[{"name":"Alvalade","adminLevel":8.0}]}]""").single().zona)
        // Sem área de concelho nem de freguesia ficam sem zona.
        assertNull(TransitousParser.lugares("""[{"type":"ADDRESS","name":"Rua A","lat":38.7,"lon":-9.1,"areas":[{"name":"Portugal","adminLevel":2}]}]""").single().zona)
        assertNull(TransitousParser.lugares("""[{"type":"ADDRESS","name":"Rua A","lat":38.7,"lon":-9.1}]""").single().zona)
    }

    @Test fun zonaJuntaFreguesiaEConcelho() {
        assertEquals("Pinhal Novo · Palmela", zonaDe("Pinhal Novo", "Palmela"))
        assertEquals("Santo António · Lisboa", zonaDe("Santo António", "Lisboa"))
    }

    @Test fun zonaNaoRepeteOConcelho() {
        assertEquals("São João da Madeira", zonaDe("São João da Madeira", "São João da Madeira"))
        assertEquals("lisboa", zonaDe("lisboa", "Lisboa"))
        val longa = "Setúbal (São Julião, Nossa Senhora da Anunciada e Santa Maria da Graça)"
        assertEquals(longa, zonaDe(longa, "Setúbal"))
        assertEquals(longa, zonaDe(longa, "SETÚBAL"))
        // Só conta como "já diz o concelho" quando o nome dele acaba numa palavra inteira.
        assertEquals("Moravia · Mora", zonaDe("Moravia", "Mora"))
    }

    @Test fun zonaComUmaPeca() {
        assertEquals("Palmela", zonaDe(null, "Palmela"))
        assertEquals("Pinhal Novo", zonaDe("Pinhal Novo", null))
        assertEquals("Palmela", zonaDe("", "Palmela"))
        assertEquals("Pinhal Novo", zonaDe("Pinhal Novo", " "))
    }

    @Test fun zonaSemNadaEhNula() {
        assertNull(zonaDe(null, null))
        assertNull(zonaDe("", " "))
    }

    @Test fun pesquisaPertoUsaAPosicao() = runTest {
        val http = HttpFalso({ Resposta(200, "[]") })
        Transitous(http).pesquisarLugares("Rua de Goa", 38.7058 to -9.1446)
        assertTrue(http.pedidos.single().endsWith("&place=38.7058,-9.1446"))
    }

    @Test fun lugarMaisProximoPrefereMoradaDepoisParagem() {
        assertEquals("Campo Pequeno", TransitousParser.lugarMaisProximo(fixture("transitous-reverse.json"))?.nome)
        val comMorada = fixture("transitous-reverse.json").replaceFirst("\"type\":\"PLACE\"", "\"type\":\"ADDRESS\"")
        assertEquals(TipoLugar.MORADA, TransitousParser.lugarMaisProximo(comMorada)?.tipo)
        // Só sítios soltos (um esgoto, um contentor): não dão um nome que se reconheça.
        assertNull(TransitousParser.lugarMaisProximo(fixture("transitous-reverse-sem-morada.json")))
    }

    @Test fun lugarNumPonto() = runTest {
        val http = HttpFalso({ Resposta(200, "[]") })
        Transitous(http).lugarEm(38.7, -9.14)
        assertEquals("https://api.transitous.org/api/v1/reverse-geocode?place=38.7,-9.14", http.pedidos.single())
    }

    @Test fun tempoRealUsaAHoraPrevista() {
        val aoVivo = fixture("transitous-stoptimes.json")
            .replaceFirst("\"realTime\": false", "\"realTime\": true")
            .replaceFirst("\"departure\": \"2026-10-27T01:54:00Z\"", "\"departure\": \"2026-10-27T01:57:00Z\"")
        val c = TransitousParser.chegadas(aoVivo, "pt-Carris_3803", agora).single()
        assertEquals(Instant.parse("2026-10-27T01:57:00Z"), c.prevista)
    }

    @Test fun mapaEscondeACarrisMetropolitana() {
        val p = TransitousParser.paragensNoMapa(fixture("transitous-map-stops.json"))
        assertEquals(3, p.size)
        assertTrue(p.none { it.ref.id.startsWith("pt-Carris-Metropolitana_") })
    }

    @Test fun pesquisaSoEmPortugalESemCarrisMetropolitana() {
        val p = TransitousParser.pesquisa(fixture("transitous-geocode.json"))
        assertEquals(listOf("pt-Comboios-de-Portugal(CP)_94_69005", "pt-Transtejo-Soflusa_5"), p.map { it.ref.id })
    }

    // Os dados reais da Linha de Cascais (pedido de 02/11/2026): a CP escreve "Cais do Sodre", "Alges" e "Alcantara -  Mar".
    private fun cascais() = TransitousParser.rotas(fixture("transitous-plan-cascais.json"))

    private fun nomesDasParagens(it: pt.pixellusitano.paragemcerta.core.modelo.Itinerario) =
        it.etapas.first { e -> !e.aPe }.paragens.map { p -> p.nome }

    @Test fun espacosRepetidosNosNomesPassamAUm() {
        assertEquals(
            listOf("Cais do Sodre", "Santos", "Alcantara - Mar", "Belem", "Alges"),
            nomesDasParagens(cascais()[0]),
        )
    }

    @Test fun nomeSemAcentosUsaOdeUmaParagemConhecidaPerto() {
        // O Transtejo escreve "Cais do Sodré" a uns 100 m da estação da CP.
        val conhecidas = listOf(Lugar("Cais do Sodré", 38.705, -9.1455, TipoLugar.PARAGEM))
        val rota = cascais().comNomesAcentuados(conhecidas)[0]
        val comboio = rota.etapas.first { !it.aPe }
        assertEquals("Cais do Sodré", comboio.de.nome)
        assertEquals("Cais do Sodré", comboio.paragens.first().nome)
        assertEquals("Cais do Sodré", rota.etapas[0].para.nome) // a etapa a pé até lá
        // O que não tem correspondência fica como vem.
        assertEquals("Santos", comboio.paragens[1].nome)
    }

    @Test fun deUmaParagemConhecidaLongeNaoSeAproveitaOnome() {
        val longe = listOf(Lugar("Cais do Sodré", 38.7050, -9.1250, TipoLugar.PARAGEM)) // a uns 1,7 km
        assertEquals("Cais do Sodre", cascais().comNomesAcentuados(longe)[0].etapas.first { !it.aPe }.de.nome)
    }

    @Test fun soTrocaSeOutroNomeFoiSoDeAcentos() {
        val outros = listOf(
            Lugar("Cais do Sodré (Terminal)", 38.7062, -9.1454, TipoLugar.PARAGEM), // nome diferente, não só acentos
            Lugar("CAIS DO SODRE", 38.7062, -9.1454, TipoLugar.PARAGEM), // não tem mais acentos
        )
        assertEquals("Cais do Sodre", cascais().comNomesAcentuados(outros)[0].etapas.first { !it.aPe }.de.nome)
    }

    @Test fun naoTiraAcentosAUmNomeQueOsTem() {
        val rota = cascais()[1].let { r ->
            r.copy(etapas = r.etapas.map { e -> e.copy(para = e.para.copy(nome = if (e.para.nome == "Alges") "Algés" else e.para.nome)) })
        }
        val sem = listOf(Lugar("Alges", 38.6983, -9.2295, TipoLugar.PARAGEM))
        assertEquals("Algés", listOf(rota).comNomesAcentuados(sem)[0].etapas[1].para.nome)
    }

    @Test fun asParagensDaPropriaRotaServemDeConhecidas() {
        // A rota 2 vai de comboio até "Alges" e depois a pé até uma paragem de autocarro chamada "Algés", a uns 190 m.
        val rotas = cascais()
        assertEquals("Alges", rotas[1].etapas[1].para.nome)
        assertEquals("Algés", rotas[1].etapas[2].para.nome)
        // Sem mais nada conhecido, o "Algés" da rota 2 chega para corrigir o "Alges" das duas.
        val certas = rotas.comNomesAcentuados()
        assertEquals(listOf("Algés", "Algés"), certas.map { it.etapas[1].para.nome })
        assertEquals("Algés", certas[0].etapas[1].paragens.last().nome)
        assertEquals("Cais do Sodre", certas[0].etapas[1].de.nome) // nada conhecido com acentos perto
    }

    @Test fun osNomesDaBaseProcuramSePelaChave() {
        val chaves = cascais().chavesDosNomes()
        assertTrue("cais do sodre" in chaves && "alcantara - mar" in chaves && "alges" in chaves && "belem" in chaves)
        assertEquals("alcantara - mar", chaveDoNome("Alcântara -  Mar"))
    }

    @Test fun osResultadosDaPesquisaCorrigemSeEntreSi() {
        // Na pesquisa "Cais do Sodre" vem da CP e "Cais do Sodré" do Transtejo, a 100 m um do outro.
        val paragens = TransitousParser.pesquisa(fixture("transitous-geocode.json"))
        assertEquals(listOf("Cais do Sodré", "Cais do Sodré"), paragens.map { it.nome })
        val lugares = TransitousParser.lugares(fixture("transitous-geocode.json")).filter { it.tipo == TipoLugar.PARAGEM }
        assertTrue("Cais do Sodre" !in lugares.map { it.nome })
    }

    @Test fun asParagensDoMapaTambemPerdemOsEspacosAMaisEGanhamOsAcentos() {
        val json = """[{"stopId":"pt-CP_1","name":"Alcantara -  Mar","lat":38.7,"lon":-9.17},{"stopId":"pt-X_2","name":"Alcântara - Mar","lat":38.7002,"lon":-9.1702}]"""
        assertEquals(listOf("Alcântara - Mar", "Alcântara - Mar"), TransitousParser.paragensNoMapa(json).map { it.nome })
    }

    @Test fun pedeOsEnderecosCertos() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-stoptimes.json")) })
        Transitous(http).chegadas("pt-Comboios-de-Portugal(CP)_94_69005", agora)
        assertEquals(
            // radius=0 + exactRadius=true: só a própria paragem (sem isto o Metro do Cais do Sodré trazia 1 em 10 partidas).
            "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Comboios-de-Portugal%28CP%29_94_69005&n=30&radius=0&exactRadius=true",
            http.pedidos.single(),
        )
    }

    // Respostas reais de 03/11/2026 (cortadas): o Metro (estação-mãe MP e o dia inteiro de CM), uma viagem da Carris e a CP.
    private val tarde = Instant.parse("2026-11-03T18:00:00Z")
    private val urlMetroMae = "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Metro-Lisboa_MP&n=30&radius=80&mode=SUBWAY"
    private val cp = "pt-Comboios-de-Portugal(CP)"

    private fun sentido(n: Int, vararg destinos: String, porDestino: Boolean = false) =
        SentidoCatalogo(n, destinos.first(), destinos.toSet(), emptyList(), emptyList(), porDestino)

    @Test fun estacaoDeCorrespondenciaTemChegadas() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-stoptimes-metro-mae.json")) })
        val c = (Transitous(http).chegadas("pt-Metro-Lisboa_MP", tarde) as Resultado.Ok).valor
        // radius=0 dá 0 numa estação-mãe: pede-se o raio de 80 m, só com o Metro, e ficam as dos cais dela (MP1 a MP4).
        assertEquals(urlMetroMae, http.pedidos.single())
        assertEquals(8, c.size)
        assertEquals(setOf("Santa Apolónia", "Reboleira", "Rato", "Odivelas"), c.map { it.destino }.toSet())
        assertEquals(c.sortedBy { it.programada }, c)
        assertTrue(c.all { it.modo == Modo.METRO })
    }

    @Test fun estacaoMaeSoFicaComOsCaisDela() {
        val json = fixture("transitous-stoptimes-metro-mae.json")
        assertEquals(8, TransitousParser.chegadas(json, "pt-Metro-Lisboa_MP", tarde).size)
        // O raio de 80 m também apanha os cais de outras estações: esses não são desta.
        assertTrue(TransitousParser.chegadas(json, "pt-Metro-Lisboa_SA", tarde).isEmpty())
        // Fora das estações do Metro o parentId não conta: o que não é "pt-Metro-Lisboa_XX" só fica com o stopId.
        assertTrue(TransitousParser.chegadas(json.replace("\"parentId\": \"pt-Metro-Lisboa_MP\"", "\"parentId\": \"pt-Carris_805\""), "pt-Carris_805", tarde).isEmpty())
    }

    @Test fun caisDoMetroPedeORaioEFicaSoComOSeu() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-stoptimes-metro-mae.json")) })
        val c = (Transitous(http).chegadas("pt-Metro-Lisboa_MP1", tarde) as Resultado.Ok).valor
        // Um cais também dá 0 com radius=0 (MP1 real a 03/11/2026: 0 assim, 8 com o raio): pede como a mãe e fica só com as dele.
        assertEquals(
            "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Metro-Lisboa_MP1&n=30&radius=80&mode=SUBWAY",
            http.pedidos.single(),
        )
        assertEquals(listOf("Santa Apolónia", "Santa Apolónia"), c.map { it.destino })
    }

    @Test fun paragemNormalContinuaComRadius0() = runTest {
        val http = HttpFalso({ Resposta(200, "{}") })
        val t = Transitous(http)
        t.chegadas("pt-Carris_805", tarde)
        t.chegadas("pt-Metro-Lisboa_CS12", tarde) // dois algarismos já não é um cais do Metro
        t.chegadas("pt-Metro-Do-Porto_CS", tarde)
        assertEquals(
            listOf("pt-Carris_805", "pt-Metro-Lisboa_CS12", "pt-Metro-Do-Porto_CS").map {
                "https://api.transitous.org/api/v6/stoptimes?stopId=$it&n=30&radius=0&exactRadius=true"
            },
            http.pedidos,
        )
    }

    @Test fun nomesDoMetroDaFixtura() {
        val c = TransitousParser.chegadas(fixture("transitous-stoptimes-metro-mae.json"), "pt-Metro-Lisboa_MP", tarde)
        assertEquals(setOf("Az", "Am"), c.map { it.linhaNome }.toSet())
        assertEquals(setOf("AZ", "AM"), c.map { chaveLinha(it.linhaNome) }.toSet())
    }

    @Test fun chegadasLeemODirectionId() {
        val c = TransitousParser.chegadas(fixture("transitous-stoptimes.json"), "pt-Carris_3803", agora).single()
        assertEquals(1, c.sentido)
        assertNull(c.padrao)
        // Sem directionId na resposta, a chegada fica sem sentido (a linha decide pelo destino).
        val sem = fixture("transitous-stoptimes.json").replace(Regex("\"directionId\": \"\\d\",\\s*"), "")
        assertNull(TransitousParser.chegadas(sem, "pt-Carris_3803", agora).single().sentido)
    }

    @Test fun idsComAcentoEParentesesVaoCodificados() = runTest {
        val http = HttpFalso({ Resposta(200, "{}") })
        val t = Transitous(http)
        t.chegadas("pt-Cascais-Próxima_20127", tarde)
        t.chegadas("pt-Comboios-de-Portugal(CP)_94_31039", tarde)
        t.paragensDaViagem("20261103_18:05_pt-Cascais-Próxima_20127_1")
        t.horarioDoDia("pt-Comboios-de-Portugal(CP)_94_31039", Modo.COMBOIO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 11, 4))
        val base = "https://api.transitous.org/api/v6"
        assertEquals(
            listOf(
                "$base/stoptimes?stopId=pt-Cascais-Pr%C3%B3xima_20127&n=30&radius=0&exactRadius=true",
                "$base/stoptimes?stopId=pt-Comboios-de-Portugal%28CP%29_94_31039&n=30&radius=0&exactRadius=true",
                "$base/trip?tripId=20261103_18%3A05_pt-Cascais-Pr%C3%B3xima_20127_1",
                "$base/stoptimes?stopId=pt-Comboios-de-Portugal%28CP%29_94_31039&time=2026-11-04T03:00:00Z&window=86400&n=1&realtimeMode=OFF" +
                    "&radius=0&exactRadius=true&mode=HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_FAST_RAIL,REGIONAL_RAIL,SUBURBAN",
            ),
            http.pedidos,
        )
    }

    @Test fun operadorDeFergatusBarreiroCascais() {
        // O id real da Fertagus no Transitous tem a gralha "Fergatus".
        assertEquals("Fertagus", operadorDe("pt-Fergatus_1234"))
        assertEquals("Fertagus", operadorDe("pt-Fertagus_1234"))
        assertEquals("TCB Barreiro", operadorDe("pt-Barreiro_55"))
        assertEquals("MobiCascais", operadorDe("pt-Cascais-Próxima_20127"))
    }

    @Test fun cpComDirectionIdPorOmissaoVaiPeloDestino() {
        // Lido a 03/11/2026: a CP não tem direction_id e o Transitous manda "directionId": "0" em todas as partidas.
        val json = fixture("transitous-stoptimes-cp.json")
        assertEquals(6, Regex("\"directionId\": \"0\"").findAll(json).count())
        val c = TransitousParser.chegadas(json, "${cp}_94_69005", tarde)
        val rotas = setOf("${cp}_25-94_69005-94_69179", "${cp}_25-94_69005-94_69260")
        // Os sentidos 0 e 1 são os que o gerador inventou: aqui o 1 é o de Cascais, e tem de ficar com as 6 mesmo vindo "0".
        val paraCascais = c.filter { daLinhaNoSentido(it, rotas, sentido(1, "Cascais", "Oeiras", porDestino = true)) }
        assertEquals(6, paraCascais.size)
        assertTrue(paraCascais.all { it.sentido == 0 })
        assertTrue(c.none { daLinhaNoSentido(it, rotas, sentido(0, "Cais do Sodré", porDestino = true)) })
    }

    @Test fun horarioDoDiaOrdenadoComPrimeiraEUltima() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-stoptimes-dia-metro.json")) })
        val r = Transitous(http).horarioDoDia("pt-Metro-Lisboa_CM", Modo.METRO, setOf("pt-Metro-Lisboa_A"), sentido(0, "Santa Apolónia"), LocalDate.of(2026, 11, 4))
        // O dia de serviço começa às 03:00 de Lisboa e dura 24 h; uma estação-mãe do Metro leva a regra do raio de 80 m.
        assertEquals(
            "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Metro-Lisboa_CM&time=2026-11-04T03:00:00Z&window=86400&n=1&realtimeMode=OFF&radius=80&mode=SUBWAY",
            http.pedidos.single(),
        )
        val horas = (r as Resultado.Ok).valor
        // A meia-noite não volta ao princípio: as 00:05 e as 01:20 são do fim do dia de serviço.
        assertEquals(listOf("06:31", "06:38", "23:56", "00:05", "01:10", "01:20"), horas.map { it.toString() })
        assertEquals(LocalTime.of(6, 31), horas.first())
        assertEquals(LocalTime.of(1, 20), horas.last())
    }

    @Test fun horarioDoDiaPedeOModoDaLinhaEAHoraDeLisboa() = runTest {
        val http = HttpFalso({ Resposta(200, "{}") })
        val t = Transitous(http)
        // Em julho Lisboa está uma hora à frente: as 03:00 locais são as 02:00 UTC. Os elétricos da Carris vêm como BUS no GTFS.
        t.horarioDoDia("pt-Carris_3803", Modo.ELETRICO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 7, 15))
        t.horarioDoDia("pt-Carris_3803", Modo.AUTOCARRO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 7, 15))
        t.horarioDoDia("pt-Transtejo-Soflusa_5", Modo.OUTRO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 7, 15))
        val dia = "&time=2026-07-15T02:00:00Z&window=86400&n=1&realtimeMode=OFF&radius=0&exactRadius=true"
        assertEquals(
            listOf(
                "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Carris_3803$dia&mode=TRAM,BUS",
                "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Carris_3803$dia&mode=BUS",
                "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Transtejo-Soflusa_5$dia",
            ),
            http.pedidos,
        )
    }

    @Test fun horarioDoDiaNaNoiteDaMudancaDaHoraVaiAteAsTresDeLisboa() = runTest {
        val http = HttpFalso({ Resposta(200, "{}") })
        val t = Transitous(http)
        // Sábado 24/10/2026: das 03:00 de verão (02:00Z) às 03:00 de inverno (03:00Z) são 25 h. Sábado 27/03/2027: são 23 h.
        t.horarioDoDia("pt-Carris_3803", Modo.AUTOCARRO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 10, 24))
        t.horarioDoDia("pt-Carris_3803", Modo.AUTOCARRO, emptySet(), sentido(0, "X"), LocalDate.of(2027, 3, 27))
        val resto = "&n=1&realtimeMode=OFF&radius=0&exactRadius=true&mode=BUS"
        assertEquals(
            listOf(
                "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Carris_3803&time=2026-10-24T02:00:00Z&window=90000$resto",
                "https://api.transitous.org/api/v6/stoptimes?stopId=pt-Carris_3803&time=2027-03-27T03:00:00Z&window=82800$resto",
            ),
            http.pedidos,
        )
    }

    @Test fun horarioDoDiaComFalhaDaFonte() = runTest {
        val r = Transitous(HttpFalso({ Resposta(503, "") })).horarioDoDia("pt-Carris_3803", Modo.AUTOCARRO, emptySet(), sentido(0, "X"), LocalDate.of(2026, 11, 4))
        assertEquals(Resultado.Falha(503, limite = true), r)
    }

    @Test fun paragensDaViagemContamDoInicio() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("transitous-trip.json")) })
        val paragens = (Transitous(http).paragensDaViagem("20261103_17:31_pt-Carris_11280_20260606_76_0_16") as Resultado.Ok).valor
        assertEquals("https://api.transitous.org/api/v6/trip?tripId=20261103_17%3A31_pt-Carris_11280_20260606_76_0_16", http.pedidos.single())
        // A origem, as 22 intermédias e o destino, pela ordem da viagem.
        assertEquals(24, paragens.size)
        assertEquals("pt-Carris_4215", paragens.first())
        assertEquals("pt-Carris_920", paragens.last())
        // Cais Sodré é a 20.ª paragem (a contar de 1).
        assertEquals(20, paragens.indexOf("pt-Carris_3803") + 1)
    }

    @Test fun paragensDaViagemSemViagemDaFalha() = runTest {
        assertEquals(Resultado.Falha(404, limite = false), Transitous(HttpFalso({ Resposta(404, "{}") })).paragensDaViagem("x"))
    }
}
