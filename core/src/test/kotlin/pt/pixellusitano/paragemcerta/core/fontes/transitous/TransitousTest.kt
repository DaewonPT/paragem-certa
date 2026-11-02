package pt.pixellusitano.paragemcerta.core.fontes.transitous

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.time.Instant

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
}
