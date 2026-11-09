package pt.pixellusitano.paragemcerta.core.perturbacoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.fontes.servidor.PerturbacaoRemota
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import java.time.Instant

class PerturbacoesTest {
    private fun cm(id: String, vararg linhas: String) = Perturbacao(
        "cm:$id", FontePerturbacao.CM, linhas.toSet(), "Aviso $id", "Texto $id", null, null,
    )

    private fun metro(linha: String) = doMetro(PerturbacaoRemota("metro:$linha:3f2a9c1e", linha, "Circulação interrompida."))

    @Test fun daCmUsaOAlertaDaApp() {
        // Um alerta real da CM: o id é o que a app já usa para não o notificar duas vezes.
        val alerta = CmParser.alertas(fixture("cm-alerts.json"), Instant.parse("2026-10-27T12:00:00Z")).first { "1625" in it.linhaIds }
        val p = daCm(alerta)
        assertEquals("cm:" + alerta.id, p.id)
        assertEquals(FontePerturbacao.CM, p.fonte)
        assertEquals(setOf("1625"), p.linhas)
        assertEquals(alerta.titulo, p.titulo)
        assertEquals(alerta.texto, p.texto)
        assertEquals(alerta.inicio, p.inicio)
        assertEquals(alerta.fim, p.fim)
    }

    @Test fun daCmJuntaAsLinhasPelaChaveDaLinha() {
        val inicio = Instant.parse("2026-11-03T08:00:00Z")
        val alerta = Alerta("Desvio|1", setOf("1009", "15e"), setOf("060419"), inicio, null, "Desvio", "Obras na avenida.", Gravidade.AVISO, null)
        // As linhas ficam como nos relatos ("15e" → "15E") para se compararem com as chaves das linhas do utilizador.
        assertEquals(Perturbacao("cm:Desvio|1", FontePerturbacao.CM, setOf("1009", "15E"), "Desvio", "Obras na avenida.", inicio, null), daCm(alerta))
    }

    @Test fun doMetroDaOTituloDaLinha() {
        val p = doMetro(PerturbacaoRemota("metro:VD:3f2a9c1e", "VD", "Circulação com perturbações. Tempo de espera superior ao normal."))
        assertEquals(
            Perturbacao(
                "metro:VD:3f2a9c1e", FontePerturbacao.METRO, setOf("VD"), "Linha Verde",
                "Circulação com perturbações. Tempo de espera superior ao normal.", null, null,
            ),
            p,
        )
        assertEquals("Linha Azul", metro("AZ").titulo)
        assertEquals("Linha Amarela", metro("AM").titulo)
        assertEquals("Linha Vermelha", metro("VM").titulo)
    }

    @Test fun perturbacoesDasMinhasLinhas() {
        val todas = listOf(cm("a", "1009"), cm("b", "736", "1009"), metro("VD"), cm("c", "15E"), metro("AZ"), cm("sem-linhas"))
        // Só as que tocam numa das minhas linhas, pela ordem em que vieram; um alerta sem linhas (só de paragens) não é de nenhuma.
        assertEquals(listOf("cm:a", "cm:b", "metro:VD:3f2a9c1e"), dasLinhas(todas, setOf("1009", "VD")).map { it.id })
        assertEquals(listOf("cm:b"), dasLinhas(todas, setOf("736")).map { it.id })
        assertTrue(dasLinhas(todas, emptySet()).isEmpty())
        assertTrue(dasLinhas(todas, setOf("999")).isEmpty())
        assertTrue(dasLinhas(emptyList(), setOf("1009")).isEmpty())
    }

    @Test fun soAsNovas() {
        val lista = listOf(cm("a", "1009"), cm("b", "736"), metro("VD"))
        assertEquals(listOf("cm:b"), novas(lista, setOf("cm:a", "metro:VD:3f2a9c1e", "cm:ja-acabou")).map { it.id })
        assertEquals(lista, novas(lista, emptySet()))
        assertTrue(novas(lista, setOf("cm:a", "cm:b", "metro:VD:3f2a9c1e")).isEmpty())
    }
}
