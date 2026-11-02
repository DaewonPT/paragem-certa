package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.fixture
import java.time.ZoneId

class PartilhaTest {
    @Test fun textoParaPartilhar() {
        val it = TransitousParser.rotas(fixture("transitous-plan.json"))[0]
        assertEquals(
            """
            Paragem Certa — Cais do Sodré → Oriente
            Sai às 02:28 · chega às 02:58 (30 min)
            • A pé 2 min até Cais Sodré
            • Autocarro 210 até Estação Oriente (02:30 → 02:56)
            • A pé 2 min até ao destino
            """.trimIndent(),
            textoPartilha(it, "Cais do Sodré", "Oriente", ZoneId.of("Europe/Lisbon")),
        )
    }

    @Test fun nomeDaLinhaPoeOModoAntesDeUmNomeProprio() {
        // Depois de "o": o número chega; um nome próprio precisa do modo; sem nome, só o modo.
        assertEquals("736", nomeDaLinha(Modo.AUTOCARRO, "736"))
        assertEquals("15E", nomeDaLinha(Modo.ELETRICO, "15E"))
        assertEquals("comboio da Linha de Cascais", nomeDaLinha(Modo.COMBOIO, "Linha de Cascais"))
        assertEquals("metro da Linha 1", nomeDaLinha(Modo.METRO, "Linha 1"))
        assertEquals("metro Azul", nomeDaLinha(Modo.METRO, "Azul"))
        assertEquals("comboio Intercidades", nomeDaLinha(Modo.COMBOIO, "Intercidades"))
        assertEquals("comboio", nomeDaLinha(Modo.COMBOIO, null))
        assertEquals("barco", nomeDaLinha(Modo.BARCO, "  "))
    }

    @Test fun distanciaArredondada() {
        assertEquals("120 m", textoDistancia(121.0))
        assertEquals("1,3 km", textoDistancia(1260.0))
    }
}
