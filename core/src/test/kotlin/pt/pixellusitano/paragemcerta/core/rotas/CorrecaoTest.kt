package pt.pixellusitano.paragemcerta.core.rotas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import java.time.Instant

class CorrecaoTest {
    private val t = Instant.parse("2026-11-16T08:00:00Z")
    private fun m(min: Long) = t.plusSeconds(min * 60)
    private val l = Lugar("x", 38.70, -9.23, TipoLugar.PARAGEM)
    private fun aPe(de: Long, ate: Long) = Etapa(true, Modo.OUTRO, null, null, null, l, l, m(de), m(ate), m(de), false, 0, emptyList(), null)
    private fun autocarro(linha: String, de: Long, ate: Long, paragem: String? = "pt-Carris-Metropolitana_120321") = Etapa(
        false, Modo.AUTOCARRO, linha, null, "Algés (Estação)", l, l, m(de), m(ate), m(de), false, 1, emptyList(), null,
        paragens = listOfNotNull(paragem?.let { ParagemEtapa(it, "A", 38.70, -9.23, m(de)) }) + ParagemEtapa("p2", "B", 38.70, -9.23, m(ate)))
    private fun itin(vararg e: Etapa) = Itinerario(e.toList(), e.first().partida, e.last().chegada, e.count { !it.aPe } - 1)
    private fun chegada(linha: String, programada: Instant, prevista: Instant?) =
        Chegada(Fonte.CM, linha, linha, "Algés (Estação)", programada, prevista, "T1", null, Modo.AUTOCARRO, null, null)
    private val direta = itin(aPe(2, 7), autocarro("1103", 7, 27), aPe(27, 32))
    private val comTransbordo = itin(aPe(2, 7), autocarro("1103", 7, 20), aPe(20, 23), autocarro("1502", 26, 40, "pt-Carris-Metropolitana_120159"), aPe(40, 45))

    @Test fun chegadaDaEtapaA90sOuMenos() {
        val etapa = autocarro("1103", 7, 27)
        val a90 = chegada("1103", m(7).plusSeconds(90), m(8))
        assertSame(a90, chegadaDaEtapa(listOf(a90), etapa))
        assertNull(chegadaDaEtapa(listOf(chegada("1103", m(7).plusSeconds(91), m(8))), etapa))
        assertNull(chegadaDaEtapa(listOf(chegada("1107", m(7), m(8))), etapa))
        val mais30 = chegada("1103", m(7).plusSeconds(30), m(8))
        assertSame(mais30, chegadaDaEtapa(listOf(chegada("1103", m(7).minusSeconds(60), m(8)), mais30), etapa))
    }

    @Test fun atrasoDeslocaAPartidaAEtapaEOResto() {
        val r = corrigirComTempoReal(direta, listOf(chegada("1103", m(7).plusSeconds(30), m(12).plusSeconds(30))), t)!!
        assertEquals(m(7), r.partida)
        assertEquals(m(37), r.chegada)
        assertEquals(m(7), r.etapas[0].partida)
        assertEquals(m(12), r.etapas[0].chegada)
        val bus = r.etapas[1]
        assertEquals(m(12), bus.partida)
        assertEquals(m(32), bus.chegada)
        assertEquals(m(7), bus.partidaProgramada)
        assertTrue(bus.aoVivo)
        assertEquals(listOf(m(12), m(32)), bus.paragens.map { it.hora })
        assertEquals(m(32), r.etapas[2].partida)
        assertEquals(m(37), r.etapas[2].chegada)
    }

    @Test fun transbordoQueAindaDaFicaComOResto() {
        val r = corrigirComTempoReal(comTransbordo, listOf(chegada("1103", m(7), m(9))), t)!!
        assertEquals(m(22), r.etapas[2].partida)
        assertEquals(m(25), r.etapas[2].chegada)
        assertEquals(m(26), r.etapas[3].partida)
        assertEquals(m(45), r.chegada)
    }

    @Test fun transbordoPerdidoSai() {
        assertNull(corrigirComTempoReal(comTransbordo, listOf(chegada("1103", m(7), m(12))), t))
    }

    @Test fun jaNaoChegasATempoSai() {
        assertNull(corrigirComTempoReal(direta, listOf(chegada("1103", m(7), m(3))), t))
        assertNotNull(corrigirComTempoReal(direta, listOf(chegada("1103", m(7), m(5))), t))
    }

    @Test fun semChegadaCorrespondenteFicaComoVeio() {
        val atrasada = m(12)
        assertSame(direta, corrigirComTempoReal(direta, listOf(chegada("1107", m(7), atrasada)), t))
        assertSame(direta, corrigirComTempoReal(direta, listOf(chegada("1103", m(7).plusSeconds(91), atrasada)), t))
        assertSame(direta, corrigirComTempoReal(direta, listOf(chegada("1103", m(7), null)), t))
        val carris = itin(aPe(2, 7), autocarro("1103", 7, 27, "pt-Carris_3804"), aPe(27, 32))
        assertSame(carris, corrigirComTempoReal(carris, listOf(chegada("1103", m(7), atrasada)), t))
        val semParagens = itin(aPe(2, 7), autocarro("1103", 7, 27).copy(paragens = emptyList()), aPe(27, 32))
        assertSame(semParagens, corrigirComTempoReal(semParagens, listOf(chegada("1103", m(7), atrasada)), t))
    }

    @Test fun estimativaPelaPosicaoTambemCorrige() {
        val pelaPosicao = chegada("1103", m(7), m(10)).copy(paragensEmFalta = 2, pelaPosicao = true)
        assertEquals(m(10), corrigirComTempoReal(direta, listOf(pelaPosicao), t)!!.etapas[1].partida)
    }
}
