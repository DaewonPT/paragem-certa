package pt.pixellusitano.paragemcerta.ui.viagem

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.viagem.EstadoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Fase
import pt.pixellusitano.paragemcerta.core.viagem.Origem
import pt.pixellusitano.paragemcerta.core.viagem.Percurso
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.time.Instant

/** Puxa o painel da viagem para cima: por baixo do mapa só se vê o título da etapa e as primeiras paragens; os botões ficam mais abaixo. */
internal fun ComposeTestRule.abrirPainel() {
    // A meio do painel recolhido: mais abaixo, com letra a 200 %, o dedo cai nos botões da barra e não arrasta o painel.
    onRoot().performTouchInput { swipeUp(startY = height * 0.6f, endY = height * 0.1f) }
    waitForIdle()
}

/**
 * A viagem dos testes do ecrã, a começar em t0: a pé até ao Cais do Sodré, o 736 até ao Oriente (seis paragens), a pé até à
 * outra paragem e o 28 até Sacavém (três). Por omissão t0 = 10:00 UTC, 11:00 em Lisboa (verão): as horas dos textos só
 * batem certo com a zona de Lisboa.
 */
internal class Exemplo(private val t0: Instant = Instant.parse("2026-07-01T10:00:00Z")) {
    private fun lugar(nome: String, lat: Double = 38.70) = Lugar(nome, lat, -9.14, TipoLugar.PARAGEM)

    private fun aPe(de: String, para: String, partida: Instant, metros: Double) = Etapa(
        aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null, de = lugar(de), para = lugar(para),
        partida = partida, chegada = partida.plusSeconds(180), partidaProgramada = partida, aoVivo = false,
        paragensIntermedias = 0, desenho = emptyList(), cor = null, distancia = metros,
    )

    /** Paragens a 0,005° (cerca de 550 m) umas das outras e 2 min entre cada. */
    private fun autocarro(linha: String, nomes: List<String>, partida: Instant, aoVivo: Boolean) = nomes.mapIndexed { i, n ->
        ParagemEtapa("$linha-$i", n, 38.70 + i * 0.005, -9.14, partida.plusSeconds(i * 120L))
    }.let { paragens ->
        Etapa(
            aPe = false, modo = Modo.AUTOCARRO, linhaNome = linha, linhaId = "id$linha", destino = nomes.last(),
            de = lugar(nomes.first()), para = lugar(nomes.last(), paragens.last().lat), partida = partida, chegada = paragens.last().hora,
            partidaProgramada = partida, aoVivo = aoVivo, paragensIntermedias = nomes.size - 2, desenho = emptyList(), cor = null,
            idViagem = "viagem$linha", paragens = paragens,
        )
    }

    fun itinerario(aoVivo: Boolean = false): Itinerario {
        val etapas = listOf(
            aPe("Casa", "Cais do Sodré", t0, 140.0),
            autocarro("736", listOf("Cais do Sodré", "Santos", "Alcântara", "Belém", "Algés", "Oriente"), t0.plusSeconds(180), aoVivo),
            aPe("Oriente", "Oriente (Norte)", t0.plusSeconds(1500), 90.0),
            autocarro("28", listOf("Oriente (Norte)", "Moscavide", "Sacavém"), t0.plusSeconds(1800), aoVivo = false),
        )
        return Itinerario(etapas, etapas.first().partida, etapas.last().chegada, 1)
    }

    fun plano(aoVivo: Boolean = false) = PlanoViagem(itinerario(aoVivo), som = true, vibrar = true)

    fun viagem(p: PlanoViagem) = Viagem(p.itinerario)

    /** O estado a pé, na etapa i (0 ou 2), a faltarem tantos metros. */
    fun aPe(p: PlanoViagem, etapa: Int, falta: Double): EstadoViagem =
        viagem(p).inicio(t0).copy(etapa = etapa, fase = Fase.A_PE, progresso = falta)

    /** À espera do veículo da etapa i, no ponto de embarque. */
    fun espera(p: PlanoViagem, etapa: Int, atualizado: Instant = t0): EstadoViagem =
        viagem(p).inicio(t0).copy(etapa = etapa, fase = Fase.ESPERA, progresso = metros(p, etapa)[0], atualizado = atualizado)

    /** A bordo da etapa i, 100 m para lá da paragem k (e antes da k + 1). */
    fun aBordo(p: PlanoViagem, etapa: Int, k: Int, origem: Origem = Origem.GPS): EstadoViagem =
        viagem(p).inicio(t0).copy(etapa = etapa, fase = Fase.A_BORDO, progresso = metros(p, etapa)[k] + 100, origem = origem, atualizado = t0)

    private fun metros(p: PlanoViagem, etapa: Int): DoubleArray {
        val e = p.itinerario.etapas[etapa]
        return Percurso(e.desenho, e.paragens).noPercurso
    }
}
