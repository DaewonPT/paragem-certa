package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.dados.fixture

class DescricaoRotaTest {
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    @Test fun descricaoDaPrimeiraRota() {
        assertEquals(
            "Rota de 30 min, das 02:28 às 02:58. A pé 2 min, autocarro 210, a pé 2 min. 4 min a pé, sem transbordos. " +
                "Sai de Cais Sodré às 02:30, hora do horário, sem tempo real. Etiquetas: mais rápida, direta. Aviso numa linha.",
            descricaoRota(rota, listOf("mais rápida", "direta"), comAlerta = true, Limites()),
        )
    }

    @Test fun semEtiquetasNemAviso() {
        val d = descricaoRota(rota, emptyList(), comAlerta = false, Limites())
        assertTrue(d, d.endsWith("hora do horário, sem tempo real."))
        assertFalse(d, d.contains("Etiquetas"))
        assertFalse(d, d.contains("Aviso"))
    }
}
