package pt.pixellusitano.paragemcerta.core.viagem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import java.time.Instant

class VigiaTest {
    private val agora = Instant.parse("2026-11-02T10:00:00Z")
    private val ref = RefParagem(Fonte.CM, "010101")

    private fun pedido(vararg linhas: String, minutos: Int = 5) =
        PedidoAviso(ref, "Cais do Sodré", linhas.toSet(), minutos, agora)

    /** Uma chegada da linha daqui a s segundos; com tempoReal = false só tem a hora do horário. */
    private fun chegada(linha: String, s: Long, tempoReal: Boolean = true, destino: String = "Algés") =
        Chegada(
            Fonte.CM, linha, linha, destino, agora.plusSeconds(s), if (tempoReal) agora.plusSeconds(s) else null,
            null, null, Modo.AUTOCARRO, null, null,
        )

    @Test fun linhaAQuatroMinutosComPedidoDeCincoAvisa() {
        val avisos = aDisparar(pedido("736"), listOf(chegada("736", 4 * 60 + 10)), emptySet(), agora)
        assertEquals(listOf(AvisoChegada("736", "Algés", 4, false, Modo.AUTOCARRO)), avisos)
    }

    @Test fun linhaASeisMinutosNaoAvisa() {
        assertTrue(aDisparar(pedido("736"), listOf(chegada("736", 6 * 60)), emptySet(), agora).isEmpty())
    }

    @Test fun oLimiteSegueOQueAListaMostra() {
        // 5 min 59 s mostra "5 min" na lista, por isso conta como 5; 6 min exatos já são 6.
        assertEquals(5L, aDisparar(pedido("736"), listOf(chegada("736", 5 * 60 + 59)), emptySet(), agora).single().minutos)
        assertTrue(aDisparar(pedido("736"), listOf(chegada("736", 6 * 60)), emptySet(), agora).isEmpty())
    }

    @Test fun duasLinhasPedidasSoUmaChegaAvisaEssa() {
        val chegadas = listOf(chegada("736", 3 * 60), chegada("728", 12 * 60))
        val avisos = aDisparar(pedido("736", "728"), chegadas, emptySet(), agora)
        assertEquals(listOf("736"), avisos.map { it.linhaNome })
    }

    @Test fun linhasNaoPedidasSaoIgnoradas() {
        assertTrue(aDisparar(pedido("728"), listOf(chegada("736", 60)), emptySet(), agora).isEmpty())
    }

    @Test fun linhaJaAvisadaNaoVoltaAAvisar() {
        assertTrue(aDisparar(pedido("736"), listOf(chegada("736", 60)), setOf("736"), agora).isEmpty())
    }

    @Test fun duasLinhasPertoAvisamAsDuasPelaOrdemDeChegada() {
        val chegadas = listOf(chegada("728", 4 * 60), chegada("736", 2 * 60))
        assertEquals(listOf("736", "728"), aDisparar(pedido("736", "728"), chegadas, emptySet(), agora).map { it.linhaNome })
    }

    @Test fun semTempoRealUsaOHorarioEDiz() {
        val avisos = aDisparar(pedido("736"), listOf(chegada("736", 3 * 60, tempoReal = false)), emptySet(), agora)
        assertEquals(listOf(AvisoChegada("736", "Algés", 3, true, Modo.AUTOCARRO)), avisos)
    }

    @Test fun oTempoRealMandaOHorarioSoServeSemEle() {
        // Horário a 2 min mas o tempo real diz 8: não avisa; ao contrário (horário a 20, real a 3) avisa sem "pelo horário".
        val atrasada = Chegada(Fonte.CM, "736", "736", "Algés", agora.plusSeconds(120), agora.plusSeconds(480), null, null, Modo.AUTOCARRO, null, null)
        assertTrue(aDisparar(pedido("736"), listOf(atrasada), emptySet(), agora).isEmpty())
        val adiantada = Chegada(Fonte.CM, "736", "736", "Algés", agora.plusSeconds(1200), agora.plusSeconds(180), null, null, Modo.AUTOCARRO, null, null)
        assertEquals(listOf(AvisoChegada("736", "Algés", 3, false, Modo.AUTOCARRO)), aDisparar(pedido("736"), listOf(adiantada), emptySet(), agora))
    }

    @Test fun oAvisoLevaOModoDaChegada() {
        // O texto do aviso precisa dele: "O comboio da Linha de Cascais", mas "O 736".
        val comboio = chegada("Linha de Cascais", 3 * 60).copy(modo = Modo.COMBOIO)
        assertEquals(Modo.COMBOIO, aDisparar(pedido("Linha de Cascais"), listOf(comboio), emptySet(), agora).single().modo)
    }

    @Test fun chegadaJaPassadaIgnoraSeEUsaAProximaDaLinha() {
        // O que passou há mais de 30 s não conta; a seguinte da mesma linha, a 4 min, é a que avisa (com o destino dela).
        val chegadas = listOf(chegada("736", -90, destino = "Odivelas"), chegada("736", 4 * 60, destino = "Algés"))
        assertEquals(listOf(AvisoChegada("736", "Algés", 4, false, Modo.AUTOCARRO)), aDisparar(pedido("736"), chegadas, emptySet(), agora))
        assertTrue(aDisparar(pedido("736"), listOf(chegada("736", -90)), emptySet(), agora).isEmpty())
    }

    @Test fun chegadaQueAcabouDePassarAindaConta() {
        assertEquals(0L, aDisparar(pedido("736"), listOf(chegada("736", -10)), emptySet(), agora).single().minutos)
    }

    @Test fun contaAPrimeiraDaLinhaMesmoForaDeOrdem() {
        val chegadas = listOf(chegada("736", 30 * 60), chegada("736", 3 * 60))
        assertEquals(3L, aDisparar(pedido("736"), chegadas, emptySet(), agora).single().minutos)
    }

    @Test fun primeiraLongeNaoLevaASeguinteAAvisar() {
        // A primeira da linha está a 10 min: nada, e a seguinte (ainda mais longe) não muda isso.
        val chegadas = listOf(chegada("736", 10 * 60), chegada("736", 40 * 60))
        assertTrue(aDisparar(pedido("736"), chegadas, emptySet(), agora).isEmpty())
    }

    @Test fun semChegadasNaoAvisa() {
        assertTrue(aDisparar(pedido("736"), emptyList(), emptySet(), agora).isEmpty())
    }

    @Test fun acabouQuandoTodasAvisadas() {
        assertTrue(vigiaAcabou(pedido("736", "728"), setOf("736", "728"), agora))
        assertTrue(vigiaAcabou(pedido("736", "728"), setOf("736", "728", "711"), agora))
        assertFalse(vigiaAcabou(pedido("736", "728"), setOf("736"), agora))
        assertFalse(vigiaAcabou(pedido("736", "728"), setOf("711"), agora))
    }

    @Test fun acabouAosNoventaMinutos() {
        val p = pedido("736")
        assertFalse(vigiaAcabou(p, emptySet(), agora.plusSeconds(89 * 60)))
        assertFalse(vigiaAcabou(p, emptySet(), agora.plusSeconds(90 * 60)))
        assertTrue(vigiaAcabou(p, emptySet(), agora.plusSeconds(90 * 60 + 1)))
    }
}
