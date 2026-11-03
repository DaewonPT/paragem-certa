package pt.pixellusitano.paragemcerta.core.linhas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import java.time.Instant
import java.time.LocalDate

class LinhasTest {
    private fun catalogo() = lerCatalogo(fixture("linhas.json"))

    private fun chegada(linhaId: String, destino: String, sentido: Int?) = Chegada(
        Fonte.TRANSITOUS, linhaId, "x", destino, Instant.parse("2026-11-03T18:00:00Z"), null, null, null, Modo.AUTOCARRO, null, null, sentido = sentido,
    )

    private fun linha(nome: String, nomeLongo: String) = LinhaCatalogo(
        RefLinha(Fonte.TRANSITOUS, "pt-X:$nome"), "pt-X", nome, nomeLongo, Modo.AUTOCARRO, null, null, setOf("pt-X_$nome"), emptyList(),
    )

    @Test fun leOCatalogo() {
        val c = catalogo()
        assertEquals(LocalDate.of(2026, 11, 3), c.geradoEm)
        assertEquals(mapOf("pt-Carris" to "Carris", "pt-Comboios-de-Portugal(CP)" to "CP", "pt-Metro-Lisboa" to "Metro de Lisboa"), c.operadores)
        assertEquals(listOf("15E", "Cais do Sodré - Cascais", "Az"), c.linhas.map { it.nome })

        val l = c.linhas[0]
        assertEquals(RefLinha(Fonte.TRANSITOUS, "pt-Carris:15E"), l.ref)
        assertEquals("TRANSITOUS:pt-Carris:15E", l.ref.chave)
        assertEquals("pt-Carris", l.operador)
        assertEquals("Pç. Figueira - Algés", l.nomeLongo)
        assertEquals(Modo.ELETRICO, l.modo)
        assertNull(l.cor)
        assertEquals(setOf("pt-Carris_76_0", "pt-Carris_76_1"), l.rotas)
        assertEquals(listOf(0, 1), l.sentidos.map { it.sentido })

        val s = l.sentidos[0]
        assertEquals("Algés", s.destino)
        assertEquals(setOf("Algés"), s.destinos)
        // O nome vem sem os espaços a mais do GTFS ("Pç.  Figueira").
        assertEquals(listOf("Pç. Figueira", "Cais Sodré", "Algés (Jardim)"), s.paragens.map { it.nome })
        assertEquals(RefParagem(Fonte.TRANSITOUS, "pt-Carris_920"), s.paragens[0].ref)
        assertEquals(38.70625, s.paragens[1].lat, 1e-9)
        // A forma é a polilinha de precisão 5, descodificada com a que a app já tem para as rotas.
        assertEquals(3, s.forma.size)
        assertEquals(38.7131, s.forma[0].first, 1e-6)
        assertEquals(-9.1377, s.forma[0].second, 1e-6)
        assertEquals(-9.23146, s.forma[2].second, 1e-6)

        val metro = c.linhas[2]
        assertEquals(Modo.METRO, metro.modo)
        assertEquals("#6699CC", metro.cor)
        assertEquals("#000000", metro.corTexto)
        assertEquals(listOf("pt-Metro-Lisboa_CM", "pt-Metro-Lisboa_MP1", "pt-Metro-Lisboa_SP"), metro.sentidos[0].paragens.map { it.ref.id })
    }

    @Test fun sentidoSinteticoVemMarcadoPorDestino() {
        val c = catalogo()
        assertFalse(c.linhas[0].sentidos.any { it.porDestino })
        assertTrue(c.linhas[1].sentidos.all { it.porDestino })
        // Uma linha da CP sem nome curto fica com o nome longo e sem cor de texto inventada.
        assertEquals("Cais do Sodré - Cascais", c.linhas[1].nomeLongo)
    }

    @Test fun catalogoEstragadoLanca() {
        val json = fixture("linhas.json")
        assertThrows(Exception::class.java) { lerCatalogo(json.take(json.length / 2)) } // cortado a meio
        assertThrows(Exception::class.java) { lerCatalogo("[]") }
        assertThrows(Exception::class.java) { lerCatalogo("") }
        assertThrows(Exception::class.java) { lerCatalogo(json.replace("\"geradoEm\": \"2026-11-03\"", "\"geradoEm\": \"ontem\"")) }
        // Uma paragem que a linha usa e o ficheiro não tem: também é um ficheiro estragado (o chamador fica com o antigo).
        assertThrows(Exception::class.java) { lerCatalogo(json.replace("\"pt-Carris_3803\": [", "\"pt-Carris_0000\": [")) }
    }

    @Test fun daRotaEncontraALinha() {
        val c = catalogo()
        assertEquals("15E", c.daRota("pt-Carris_76_1")?.nome)
        assertEquals("Az", c.daRota("pt-Metro-Lisboa_A")?.nome)
        assertEquals("Cais do Sodré - Cascais", c.daRota("pt-Comboios-de-Portugal(CP)_25-94_69005-94_69260")?.nome)
        assertNull(c.daRota("pt-Carris_9999_0"))
    }

    @Test fun procurarSemAcentos() {
        val c = catalogo().copy(
            linhas = listOf(linha("728", "Restelo - Portela"), linha("Azul", "São Sebastião - Reboleira"), linha("28E", "Martim Moniz - Prazeres")),
        )
        assertEquals(listOf("Azul"), c.procurar("sao").map { it.nome })
        assertEquals(listOf("Azul"), c.procurar("SÃO seba").map { it.nome })
        // O nome curto vem primeiro (a começar pelo texto, depois a conter) e só depois o nome longo.
        assertEquals(listOf("28E", "728"), c.procurar("28").map { it.nome })
        assertEquals(listOf("728"), c.procurar("portela").map { it.nome })
        assertEquals(listOf("28E"), c.procurar("  moniz  ").map { it.nome })
        assertTrue(c.procurar("").isEmpty())
        assertTrue(c.procurar("   ").isEmpty())
        assertTrue(c.procurar("zzz").isEmpty())
    }

    @Test fun refLinhaIdaEVolta() {
        assertEquals(RefLinha(Fonte.CM, "1009"), RefLinha.daChave("CM:1009"))
        assertEquals("CM:1009", RefLinha(Fonte.CM, "1009").chave)
        // O id do Transitous leva ":" dentro: só o primeiro separa a fonte.
        assertEquals(RefLinha(Fonte.TRANSITOUS, "pt-Carris:728"), RefLinha.daChave("TRANSITOUS:pt-Carris:728"))
        assertEquals("TRANSITOUS:pt-Carris:728", RefLinha.daChave("TRANSITOUS:pt-Carris:728")?.chave)
        // A 1009 da CM e uma 1009 de outro operador são linhas diferentes.
        assertTrue(RefLinha.daChave("CM:1009") != RefLinha.daChave("TRANSITOUS:pt-Carris:1009"))
        assertNull(RefLinha.daChave("OUTRA:1009"))
        assertNull(RefLinha.daChave("1009"))
    }

    @Test fun daLinhaNoSentidoPelaRotaESentido() {
        val s0 = SentidoCatalogo(0, "Algés", setOf("Algés"), emptyList(), emptyList())
        val rotas = setOf("pt-Carris_76_0", "pt-Carris_76_1")
        assertTrue(daLinhaNoSentido(chegada("pt-Carris_76_0", "Algés", 0), rotas, s0))
        // Com sentido, é o sentido que decide (o destino pode vir diferente).
        assertTrue(daLinhaNoSentido(chegada("pt-Carris_76_0", "Outro destino", 0), rotas, s0))
        assertFalse(daLinhaNoSentido(chegada("pt-Carris_76_0", "Algés", 1), rotas, s0))
        // Outra rota nunca é da linha.
        assertFalse(daLinhaNoSentido(chegada("pt-Carris_60_0", "Algés", 0), rotas, s0))
    }

    @Test fun daLinhaNoSentidoSemSentidoPeloDestino() {
        val s0 = SentidoCatalogo(0, "Parede", setOf("Estoril", "Parede"), emptyList(), emptyList())
        val rotas = setOf("pt-X_1")
        assertTrue(daLinhaNoSentido(chegada("pt-X_1", "Estoril", null), rotas, s0))
        assertTrue(daLinhaNoSentido(chegada("pt-X_1", "parede", null), rotas, s0))
        assertFalse(daLinhaNoSentido(chegada("pt-X_1", "Cascais", null), rotas, s0))
    }

    @Test fun sentidoInventadoIgnoraODirectionIdDoTransitous() {
        // A CP não tem direction_id: o gerador inventa os sentidos e o Transitous manda sempre "0" (lido a 03/11/2026).
        val s1 = SentidoCatalogo(1, "Cascais", setOf("Cascais", "Oeiras"), emptyList(), emptyList(), porDestino = true)
        val s0 = SentidoCatalogo(0, "Cais do Sodré", setOf("Cais do Sodré"), emptyList(), emptyList(), porDestino = true)
        val rotas = setOf("pt-CP_1")
        val paraCascais = chegada("pt-CP_1", "Cascais", 0)
        assertTrue(daLinhaNoSentido(paraCascais, rotas, s1))
        assertFalse(daLinhaNoSentido(paraCascais, rotas, s0))
    }
}
