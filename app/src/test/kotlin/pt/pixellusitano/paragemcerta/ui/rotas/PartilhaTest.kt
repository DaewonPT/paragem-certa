package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
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

    @Test fun distanciaArredondada() {
        assertEquals("120 m", textoDistancia(121.0))
        assertEquals("1,3 km", textoDistancia(1260.0))
    }
}
