package pt.pixellusitano.paragemcerta.core.aovivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.GRAVADA_120321
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.fontes.cm.PadraoAoVivo
import pt.pixellusitano.paragemcerta.core.fontes.cm.diaDeServicoCm
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import java.time.Instant

class SituacaoCmTest {
    private val agora = Instant.parse("2026-11-16T08:12:00Z")
    private fun h(hhmm: String) = Instant.parse("2026-11-16T$hhmm:00Z")
    private val p = PadraoAoVivo("1103_0_1", listOf("A", "B", "C", "D", "E", "120321", "G"), "S", listOf(0, 120, 240, 360, 480, 780, 900))
    private fun chegada(programada: String, prevista: String? = null, viagem: String? = null, veiculo: String? = null) = Chegada(
        Fonte.CM, "1103", "1103", "Algés (Estação)", h(programada), prevista?.let(::h), viagem, veiculo, Modo.AUTOCARRO, null, null, 0, "1103_0_1")
    private fun veiculo(id: String, viagem: String?, paragem: String?, sinalHa: Long = 20) =
        Veiculo(id, "1103", 38.7, -9.2, null, agora.minusSeconds(sinalHa), viagem = viagem, paragem = paragem, padrao = "1103_0_1")
    private fun situar(c: List<Chegada>, v: List<Veiculo>, pp: PadraoAoVivo = p) = situacaoCm(c, v, mapOf(pp.id to pp), "120321", agora)
    // Em C (índice 2) o autocarro está a 3 paragens e a 780 − 240 = 540 s: a posição dá 08:21.
    private val presa = chegada("08:05", "08:08", "T1")
    private val emC = veiculo("[OP1]1388", "T1", "C")

    @Test fun estimativaNoFuturoSemAutocarroFicaComoEsta() {
        val c = chegada("08:15", "08:16", "T9")
        assertEquals(listOf(c), situar(listOf(c), emptyList()))
    }

    @Test fun estimativaNoFuturoComAutocarroGanhaAsParagens() {
        val r = situar(listOf(chegada("08:14", "08:15", "T1")), listOf(emC)).single()
        assertEquals(h("08:15"), r.prevista)
        assertEquals(3, r.paragensEmFalta)
        assertFalse(r.pelaPosicao)
    }

    @Test fun estimativaPresaComOAutocarroAntesPassaAPosicao() {
        val r = situar(listOf(presa), listOf(emC)).single()
        assertEquals(h("08:21"), r.prevista)
        assertEquals(3, r.paragensEmFalta)
        assertTrue(r.pelaPosicao)
    }

    @Test fun estimativaPresaSemAutocarroSai() {
        assertTrue(situar(listOf(presa), emptyList()).isEmpty())
    }

    @Test fun semEstimativaNemAutocarroSaiAos30s() {
        val c = chegada("08:12")
        assertEquals(1, situar(listOf(c.copy(programada = agora.minusSeconds(29))), emptyList()).size)
        assertTrue(situar(listOf(c.copy(programada = agora.minusSeconds(31))), emptyList()).isEmpty())
    }

    @Test fun atrasoDeMaisDe30MinSai() {
        val v = veiculo("[OP1]7", "T7", "C")
        assertTrue(situar(listOf(chegada("07:41")), listOf(v)).isEmpty())
        val r = situar(listOf(chegada("07:43")), listOf(v)).single()
        assertEquals(3, r.paragensEmFalta)
        assertEquals(h("08:21"), r.prevista)
        assertEquals("T7", r.idViagem)
    }

    @Test fun autocarroNoTerminalFicaComAViagemSeguinte() {
        val r = situar(listOf(chegada("08:10"), chegada("08:25")), listOf(veiculo("[OP1]5", "T3", "A"))).single()
        assertEquals(h("08:25"), r.programada)
        assertEquals(5, r.paragensEmFalta)
        assertEquals(h("08:25"), r.prevista)
        assertEquals("T3", r.idViagem)
    }

    @Test fun veiculoDoPadraoQueJaPassouNaoConta() {
        assertTrue(situar(listOf(chegada("08:11")), listOf(veiculo("[OP1]5", "T3", "G"))).isEmpty())
    }

    @Test fun semParagemConhecidaNaoConta() {
        assertTrue(situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "UNAVAILABLE_STOP_ID"))).isEmpty())
    }

    @Test fun paragemRepetidaNoPathNaoConta() {
        val q = PadraoAoVivo("1103_0_1", listOf("A", "B", "C", "B", "120321"), "S", listOf(0, 60, 120, 180, 240))
        assertTrue(situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "B")), q).isEmpty())
        assertEquals(2, situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "C")), q).single().paragensEmFalta)
    }

    @Test fun sinalCom119sContaE121sNao() {
        assertEquals(1, situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "C", sinalHa = 119))).size)
        assertTrue(situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "C", sinalHa = 121))).isEmpty())
    }

    @Test fun sinalAte120sNoFuturoConta() {
        assertEquals(3, situar(listOf(presa), listOf(veiculo("[OP1]1388", "T1", "C", sinalHa = -60))).single().paragensEmFalta)
    }

    @Test fun numeroDoVeiculoRepetidoNoutroOperadorNaoLiga() {
        val c = chegada("08:05", "08:08", "T1", veiculo = "1388")
        assertTrue(situar(listOf(c), listOf(veiculo("[OP2]1388", "T5", "C"))).isEmpty())
    }

    @Test fun veiculoComChegadaNaoFicaComOutra() {
        val r = situar(listOf(chegada("08:14", "08:15", "T1"), chegada("08:25")), listOf(emC))
        val outra = r.single { it.programada == h("08:25") }
        assertNull(outra.paragensEmFalta)
        assertNull(outra.prevista)
    }

    @Test fun viagemCircularLigaAPassagemQueVem() {
        val q = PadraoAoVivo("1103_0_1", listOf("120321", "A", "B", "C", "120321"), "S", listOf(0, 120, 240, 360, 480))
        val r = situar(listOf(chegada("08:00", "08:01", "T1"), chegada("08:20", "08:21", "T1")), listOf(veiculo("[OP1]1388", "T1", "B")), q).single()
        assertEquals(h("08:20"), r.programada)
        assertEquals(h("08:21"), r.prevista)
        assertEquals(2, r.paragensEmFalta)
    }

    @Test fun voltaCurtaAtrasadaLigaAPrimeiraPassagem() {
        val q = PadraoAoVivo("1103_0_1", listOf("A", "120321", "B", "C", "D", "120321", "E"), "S", listOf(0, 60, 120, 180, 240, 300, 360))
        val r = situar(listOf(chegada("08:05", "08:06", "T1"), chegada("08:09", "08:17", "T1")), listOf(veiculo("[OP1]1388", "T1", "A")), q)
        val primeira = r.single { it.programada == h("08:05") }
        assertEquals(1, primeira.paragensEmFalta)
        assertEquals(h("08:13"), primeira.prevista)
        assertTrue(primeira.pelaPosicao)
        // agora + Δ = 08:13 fica mais perto das 08:09: a ligação vai pela passagem, não pela hora.
        val segunda = r.single { it.programada == h("08:09") }
        assertNull(segunda.paragensEmFalta)
        assertEquals(h("08:17"), segunda.prevista)
    }

    @Test fun ordemPelaHoraEmUsoComAsEstimativasPelaPosicao() {
        val a = chegada("08:05", "08:08", "T1")
        val b = chegada("08:18", "08:18", "T2")
        val c = chegada("08:30")
        assertEquals(listOf(h("08:18"), h("08:21"), h("08:30")), situar(listOf(a, b, c), listOf(emC)).map { it.prevista ?: it.programada })
    }

    // A gravação de 120321: a chegada da viagem com veículo e o veículo dela ([LA77N]1239) a caminho de 120292.
    private val VIAGEM_GRAVADA = "[7DQC0][LA77N]1107_0_2_0500_0529_0_1"
    // O pattern_id limpo do veículo e da chegada gravados (a linha 1107 que passa em 120292 e depois em 120321).
    private val PADRAO_GRAVADO = "1107_0_2"
    // Do 120292 (índice 21 do path) ao 120321 (índice 40): 19 paragens.
    private val EM_FALTA_GRAVADA = 19
    // Do 120292 (720 s no horário) ao 120321 (1380 s): 660 s.
    private val DELTA_GRAVADO_S = 660L

    private val chegadasGravadas = CmParser.chegadas(fixture("cm-arrivals-120321.json"), GRAVADA_120321)
    private val veiculosGravados = CmParser.veiculos(fixture("cm-vehicles-120321.json"))
    private val padraoGravado = CmParser.padraoAoVivo(fixture("cm-pattern-120321.json"), diaDeServicoCm(GRAVADA_120321))
    private fun situarGravadas(c: List<Chegada>, v: List<Veiculo>) = situacaoCm(c, v, mapOf(PADRAO_GRAVADO to padraoGravado), "120321", GRAVADA_120321)
    private val comEstimativaPresa = chegadasGravadas.map {
        if (it.idViagem == VIAGEM_GRAVADA) it.copy(prevista = GRAVADA_120321.minusSeconds(300)) else it
    }

    @Test fun gravadasLigamPeloTripId() {
        val r = situarGravadas(chegadasGravadas, veiculosGravados).single { it.idViagem == VIAGEM_GRAVADA }
        assertEquals(EM_FALTA_GRAVADA, r.paragensEmFalta)
    }

    @Test fun gravadaComEstimativaPresaPassaAPosicao() {
        val r = situarGravadas(comEstimativaPresa, veiculosGravados).single { it.idViagem == VIAGEM_GRAVADA }
        assertEquals(GRAVADA_120321.plusSeconds(DELTA_GRAVADO_S), r.prevista)
        assertTrue(r.pelaPosicao)
    }

    @Test fun gravadaComEstimativaPresaSemVeiculoSai() {
        assertTrue(situarGravadas(comEstimativaPresa, emptyList()).none { it.idViagem == VIAGEM_GRAVADA })
    }
}
