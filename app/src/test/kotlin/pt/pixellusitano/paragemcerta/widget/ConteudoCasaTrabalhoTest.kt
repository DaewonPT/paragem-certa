package pt.pixellusitano.paragemcerta.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ConteudoCasaTrabalhoTest {
    // Em novembro Lisboa está em UTC: 18:40Z lê-se 18:40.
    private val agora = Instant.parse("2026-11-09T18:40:00Z")
    private val casa = LocalGuardado(1, "Casa", Lugar("Casa", 38.7369, -9.1427, TipoLugar.MORADA), "CASA", 0)
    private val trabalho = LocalGuardado(2, "Trabalho", Lugar("Trabalho", 38.7058, -9.1431, TipoLugar.MORADA), "TRABALHO", 1)
    private val belem = PosicaoComHora(38.6979, -9.2064, hora = agora.minusSeconds(1_200).toEpochMilli())
    private val ok = LigacaoGuardada(
        "210", 0xFFE2001A.toInt(), Modo.AUTOCARRO, soAPe = false, partida = agora.plusSeconds(120).toEpochMilli(), duracaoMin = 30,
        em = agora.toEpochMilli(), estado = EstadoLigacao.OK,
    )

    private fun conteudo(
        casa: LocalGuardado? = this.casa,
        trabalho: LocalGuardado? = this.trabalho,
        ligacoes: Map<String, LigacaoGuardada> = emptyMap(),
        posicao: PosicaoComHora? = belem,
        posicaoGuardada: Boolean = false,
        aCalcular: Boolean = false,
    ) = conteudoCasaTrabalho(casa, trabalho, ligacoes, posicao, posicaoGuardada, agora, aCalcular)

    @Test fun ligacaoCalculada() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok, "TRABALHO" to ok.copy(linha = "728")))
        assertEquals(Destino.Ligacao("Casa", ok), c.casa)
        assertEquals(Destino.Ligacao("Trabalho", ok.copy(linha = "728")), c.trabalho)
        assertEquals("o toque abre as Rotas até à morada", "Casa|38.7369|-9.1427", c.rotaCasa)
        assertEquals("Trabalho|38.7058|-9.1431", c.rotaTrabalho)
    }

    @Test fun estasPertoA500Metros() {
        // 0,0044° de latitude são cerca de 489 m; 0,0046° são cerca de 511 m.
        val aoLado = PosicaoComHora(casa.lugar.lat + 0.0044, casa.lugar.lon, belem.hora)
        assertEquals(Destino.Perto("Casa"), conteudo(posicao = aoLado, ligacoes = mapOf("CASA" to ok)).casa)
        val maisLonge = aoLado.copy(lat = casa.lugar.lat + 0.0046)
        assertEquals(Destino.Ligacao("Casa", ok), conteudo(posicao = maisLonge, ligacoes = mapOf("CASA" to ok)).casa)
    }

    @Test fun semMorada() {
        val c = conteudo(casa = null, trabalho = null)
        assertEquals(Destino.SemMorada, c.casa)
        assertEquals(Destino.SemMorada, c.trabalho)
        assertEquals(null, c.rotaCasa)
        assertEquals(null, c.rotaTrabalho)
    }

    @Test fun moradaEPosicaoSemLigacaoPedeOToque() {
        assertEquals(Destino.TocaAtualizar("Casa"), conteudo().casa)
    }

    @Test fun aCalcularSoDuranteOToque() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok), aCalcular = true)
        assertEquals(Destino.ACalcular("Casa"), c.casa)
        assertEquals(Destino.ACalcular("Trabalho"), c.trabalho)
        assertEquals("sem morada não há nada a calcular", Destino.SemMorada, conteudo(casa = null, aCalcular = true).casa)
    }

    @Test fun semTransportesAgora() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok.copy(estado = EstadoLigacao.SEM_TRANSPORTES)))
        assertEquals(Destino.SemTransportes("Casa"), c.casa)
    }

    @Test fun falhouFicaSemLigacao() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok.copy(estado = EstadoLigacao.FALHOU)))
        assertEquals(Destino.SemLigacao("Casa"), c.casa)
    }

    @Test fun nuncaHouvePosicao() {
        val c = conteudo(posicao = null)
        assertEquals(Destino.SemPosicao("Casa"), c.casa)
        assertEquals(Destino.SemPosicao("Trabalho"), c.trabalho)
        assertNull(c.rodape)
    }

    @Test fun ligacaoQueJaSaiuFicaACinzento() {
        val saiu = ok.copy(partida = agora.minusSeconds(60).toEpochMilli())
        assertEquals(Destino.Passada("Casa", saiu), conteudo(ligacoes = mapOf("CASA" to saiu)).casa)
        val saiAgora = ok.copy(partida = agora.toEpochMilli())
        assertEquals("na hora exata ainda não passou", Destino.Ligacao("Casa", saiAgora), conteudo(ligacoes = mapOf("CASA" to saiAgora)).casa)
    }

    @Test fun rodapeDizQuandoSeAtualizou() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok.copy(em = agora.minusSeconds(600).toEpochMilli()), "TRABALHO" to ok))
        assertEquals(Rodape("18:40", daPosicaoGuardada = false), c.rodape)
        val texto = RuntimeEnvironment.getApplication().getString(R.string.widget_atualizado, c.rodape!!.hora)
        assertEquals("atualizado às 18:40", texto)
    }

    @Test fun rodapeComAPosicaoGuardadaDizDeQuandoEla() {
        val c = conteudo(ligacoes = mapOf("CASA" to ok), posicaoGuardada = true)
        assertEquals(Rodape("18:20", daPosicaoGuardada = true), c.rodape)
        val texto = RuntimeEnvironment.getApplication().getString(R.string.widget_posicao_das, c.rodape!!.hora)
        assertEquals("posição das 18:20", texto)
        val semHora = conteudo(ligacoes = mapOf("CASA" to ok), posicao = belem.copy(hora = 0), posicaoGuardada = true)
        assertEquals("posição sem hora: fica a hora do cálculo", Rodape("18:40", daPosicaoGuardada = false), semHora.rodape)
    }

    @Test fun rodapeNaoContaALigacaoDeUmDestinoPerto() {
        val aoLado = PosicaoComHora(casa.lugar.lat + 0.0044, casa.lugar.lon, belem.hora)
        val c = conteudo(ligacoes = mapOf("CASA" to ok, "TRABALHO" to ok.copy(em = agora.minusSeconds(600).toEpochMilli())), posicao = aoLado)
        assertEquals(Rodape("18:30", daPosicaoGuardada = false), c.rodape)
    }

    @Test fun semNadaCalculadoNaoHaRodape() {
        assertNull(conteudo().rodape)
    }
}
