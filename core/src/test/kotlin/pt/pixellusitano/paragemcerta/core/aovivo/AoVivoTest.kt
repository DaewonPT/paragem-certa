package pt.pixellusitano.paragemcerta.core.aovivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.servidor.VeiculoCarrisRemoto
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import java.time.Instant

class AoVivoTest {
    private val catalogo = lerCatalogo(fixture("linhas.json"))

    // O servidor manda a rota sem o prefixo do Transitous ("76_0"); o catálogo tem "pt-Carris_76_0".
    private fun remoto(rota: String) = VeiculoCarrisRemoto(
        id = "5247", rota = rota, viagem = "4502_20261103_76_0_8", sentido = 1, lat = 38.7068, lon = -9.1754, seq = 19, paragem = "12105", ts = 1793699980,
    )

    @Test fun veiculoDaCarrisComONomeDaLinha() {
        val v = veiculoDaCarris(remoto("76_0"), catalogo)
        assertEquals(
            Veiculo(
                id = "carris:5247", linhaId = "15E", lat = 38.7068, lon = -9.1754, rumo = null, ultimoSinal = Instant.ofEpochSecond(1793699980),
                destino = null, viagem = "4502_20261103_76_0_8", paragem = "12105", aChegar = false,
            ),
            v,
        )
    }

    @Test fun veiculoSemLinhaNoCatalogoFicaSemNome() {
        // Uma rota que o catálogo não conhece (ou nenhum catálogo ainda) desenha-se na mesma, só sem nome.
        assertEquals("", veiculoDaCarris(remoto("999_0"), catalogo).linhaId)
        assertEquals("", veiculoDaCarris(remoto("76_0"), null).linhaId)
        assertEquals("carris:5247", veiculoDaCarris(remoto("999_0"), catalogo).id)
    }

    @Test fun jaPassouNaoTemEtiqueta() {
        assertNull(paragensEmFalta(posicao = 4, seq = 6))
    }

    @Test fun aChegarComZero() {
        assertEquals(0, paragensEmFalta(posicao = 9, seq = 9))
    }

    @Test fun contaAsParagensQueFaltam() {
        assertEquals(1, paragensEmFalta(posicao = 20, seq = 19))
        assertEquals(12, paragensEmFalta(posicao = 31, seq = 19))
    }

    @Test fun viagemDaCarrisDoTripIdDoTransitous() {
        assertEquals("5166_20260606_76_0_33", viagemCarrisDeTransitous("20261103_15:56_pt-Carris_5166_20260606_76_0_33"))
        assertEquals("11280_20260606_76_0_16", viagemCarrisDeTransitous("20261103_17:31_pt-Carris_11280_20260606_76_0_16"))
        // A Carris Metropolitana ("pt-Carris-Metropolitana_"), outros operadores e ids que já vêm sem prefixo não são da Carris.
        assertNull(viagemCarrisDeTransitous("20261101_10:35_pt-Carris-Metropolitana_3708_0_1|1|0525"))
        assertNull(viagemCarrisDeTransitous("20261103_10:00_pt-Metro-Lisboa_A_1"))
        assertNull(viagemCarrisDeTransitous("5166_20260606_76_0_33"))
        assertNull(viagemCarrisDeTransitous("20261103_15:56_pt-Carris_"))
    }

    @Test fun chaveDoMetroPorQualquerNome() {
        mapOf("AZ" to listOf("Az", "AZ", "Azul", "Linha Azul", " linha  azul "), "AM" to listOf("Am", "Amarela", "Linha Amarela"),
            "VD" to listOf("Vd", "Verde", "Linha Verde"), "VM" to listOf("Vm", "Vermelha", "Linha Vermelha"),
        ).forEach { (chave, nomes) -> nomes.forEach { assertEquals(it, chave, chaveMetro(it)) } }
        listOf("", "15E", "Linha", "Linha Cinzenta", "Azulejo", "Cais do Sodré - Cascais").forEach { assertNull(it, chaveMetro(it)) }
    }

    @Test fun nomeDaLinhaDoMetroPelaChave() {
        assertEquals(listOf("Azul", "Amarela", "Verde", "Vermelha"), listOf("AZ", "AM", "VD", "VM").map { nomeLinhaMetro(it) })
        assertNull(nomeLinhaMetro("XX"))
    }

    private fun norte(k: Double) = 38.70 + k * 0.0009 to -9.14 // ~100 m por passo
    private val reta = (0..10).map { norte(it.toDouble()) }

    @Test fun trocoNumaReta() = assertEquals(listOf(norte(2.1)) + reta.subList(3, 8) + listOf(norte(7.9)), troco(reta, norte(2.1), norte(7.9)))

    @Test fun trocoNumCircularVaiAteAoFim() { // quadrado que volta ao início; a paragem é o terminal
        val q = listOf(38.70 to -9.14, 38.71 to -9.14, 38.71 to -9.13, 38.70 to -9.13, 38.70 to -9.14)
        assertEquals(listOf(38.71 to -9.13, 38.70 to -9.13, 38.70 to -9.14), troco(q, 38.71 to -9.13, 38.70 to -9.14))
    }

    @Test fun autocarroNaParagemDaSoAsPontas() = assertEquals(listOf(norte(5.0), norte(5.0)), troco(reta, norte(5.0), norte(5.0)))

    @Test fun autocarroDepoisDaParagemDaVazio() = assertEquals(emptyList<Pair<Double, Double>>(), troco(reta, norte(8.0), norte(3.0)))
}
