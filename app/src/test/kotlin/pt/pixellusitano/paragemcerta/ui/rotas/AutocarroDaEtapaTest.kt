package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import java.time.Instant

class AutocarroDaEtapaTest {
    private val partida = Instant.parse("2026-11-11T10:00:00Z")
    private val agora = partida.minusSeconds(300)

    private fun etapa(primeira: String? = "pt-Carris-Metropolitana_320047", linha: String? = "3708", aPe: Boolean = false) = Etapa(
        aPe = aPe, modo = Modo.AUTOCARRO, linhaNome = linha, linhaId = linha, destino = "Oriente",
        de = Lugar("Cais do Sodré", 38.70, -9.14, TipoLugar.PARAGEM), para = Lugar("Oriente", 38.76, -9.10, TipoLugar.PARAGEM),
        partida = partida, chegada = partida.plusSeconds(900), partidaProgramada = partida, aoVivo = false,
        paragensIntermedias = 5, desenho = emptyList(), cor = null,
        paragens = listOf(ParagemEtapa(primeira, "Cais do Sodré", 38.70, -9.14, partida)),
    )

    private fun chegada(linha: String, programada: Instant, viagem: String?, prevista: Instant? = programada) =
        Chegada(Fonte.CM, linha, linha, "Oriente", programada, prevista, viagem, null, Modo.AUTOCARRO, null, null)

    private fun veiculo(viagem: String, sinal: Instant) = Veiculo("[YA15B]2261", "3708", 38.71, -9.13, null, sinal, viagem = viagem)

    @Test fun oAutocarroEODaViagemAoVivoComPosicaoRecente() {
        val v = veiculo("T1", agora.minusSeconds(20))
        assertEquals(v, autocarroDaEtapa(listOf(chegada("3708", partida.plusSeconds(30), "T1")), listOf(veiculo("T2", agora), v), etapa(), agora))
    }

    @Test fun maisDe60sDeDiferencaNaHoraProgramadaEOutroAutocarro() {
        assertNull(autocarroDaEtapa(listOf(chegada("3708", partida.plusSeconds(61), "T1")), listOf(veiculo("T1", agora)), etapa(), agora))
    }

    @Test fun outraLinhaAMesmaHoraNaoConta() {
        assertNull(autocarroDaEtapa(listOf(chegada("3709", partida, "T1")), listOf(veiculo("T1", agora)), etapa(), agora))
    }

    @Test fun posicaoComMaisDe60sNaoConta() {
        val chegadas = listOf(chegada("3708", partida, "T1"))
        assertNull(autocarroDaEtapa(chegadas, listOf(veiculo("T1", agora.minusSeconds(61))), etapa(), agora))
        // Como no modo viagem, um relógio adiantado até 60 s ainda conta.
        assertEquals(veiculo("T1", agora.plusSeconds(60)), autocarroDaEtapa(chegadas, listOf(veiculo("T1", agora.plusSeconds(60))), etapa(), agora))
    }

    @Test fun semChegadaAoVivoNaoHaAutocarro() {
        assertNull(autocarroDaEtapa(listOf(chegada("3708", partida, null, prevista = null)), listOf(veiculo("T1", agora)), etapa(), agora))
        assertNull(autocarroDaEtapa(emptyList(), listOf(veiculo("T1", agora)), etapa(), agora))
    }

    @Test fun soAsEtapasDaCmComLinhaTemPosicaoAoVivo() {
        assertEquals("020047", etapa().embarqueCm())
        assertNull(etapa(primeira = "pt-Carris_3804").embarqueCm())
        assertNull(etapa(primeira = null).embarqueCm())
        assertNull(etapa(linha = null).embarqueCm())
        assertNull(etapa(aPe = true).embarqueCm())
    }

    @Test fun aProcurarSoAteHaverRespostaOuAteASegundaConsulta() {
        val v = veiculo("T1", agora)
        assertEquals(EstadoAutocarro.A_PROCURAR, estadoAutocarro(null, voltas = 0, temVeiculos = true))
        assertEquals(EstadoAutocarro.A_PROCURAR, estadoAutocarro(null, voltas = 1, temVeiculos = false))
        assertEquals(EstadoAutocarro.SEM_POSICAO, estadoAutocarro(null, voltas = 1, temVeiculos = true))
        // Os veículos nunca responderam: à segunda consulta às chegadas deixa de procurar.
        assertEquals(EstadoAutocarro.SEM_POSICAO, estadoAutocarro(null, voltas = 2, temVeiculos = false))
        assertEquals(EstadoAutocarro.NO_MAPA, estadoAutocarro(v, voltas = 1, temVeiculos = true))
    }
}
