package pt.pixellusitano.paragemcerta.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import java.time.Duration
import java.time.Instant

internal fun chegada(linha: String, programada: Instant, prevista: Instant? = null) =
    Chegada(Fonte.CM, linha, linha, "Restelo", programada, prevista, null, null, Modo.AUTOCARRO, null, null)

class ConteudoWidgetTest {
    private val agora = Instant.parse("2026-11-04T14:00:00Z")

    @Test fun soMostraAsQueAindaVem() {
        val lista = listOf(chegada("passou", agora.minusSeconds(31)), chegada("a-chegar", agora.minusSeconds(29)), chegada("vem", agora.plusSeconds(60)))
        val c = conteudoDoWidget("Cais", lista to agora, agora, EstadoWidget.Normal)
        assertEquals(listOf("a-chegar", "vem"), c.linhas.map { it.linha })
    }

    @Test fun todasPassadasFicaSemLinhasMasSabeDeQuandoSao() {
        val c = conteudoDoWidget("Cais", listOf(chegada("passou", agora.minusSeconds(60))) to agora.minusSeconds(120), agora, EstadoWidget.Normal)
        assertTrue(c.linhas.isEmpty())
        assertEquals("13:58", c.atualizadoAs)
    }

    @Test fun maximoDeTresLinhas() {
        val lista = (1..5).map { chegada("$it", agora.plusSeconds(it * 60L)) }
        assertEquals(listOf("1", "2", "3"), conteudoDoWidget("Cais", lista to agora, agora, EstadoWidget.Normal).linhas.map { it.linha })
    }

    @Test fun aos15MinutosFicaCinzento() {
        fun cinzento(passou: Duration) = conteudoDoWidget("Cais", emptyList<Chegada>() to agora.minus(passou), agora, EstadoWidget.Normal).cinzento
        assertFalse(cinzento(Duration.ofMinutes(14)))
        assertFalse(cinzento(Duration.ofMinutes(15)))
        assertTrue(cinzento(Duration.ofMinutes(15).plusSeconds(1)))
    }

    @Test fun horaPrevistaGanhaAProgramada() {
        val lista = listOf(
            chegada("com", agora.plusSeconds(60), prevista = Instant.parse("2026-11-04T14:32:00Z")),
            chegada("sem", Instant.parse("2026-11-04T14:40:00Z")),
        )
        val c = conteudoDoWidget("Cais", lista to agora, agora, EstadoWidget.Normal)
        assertEquals(LinhaWidget("com", "Restelo", "14:32", aoVivo = true), c.linhas[0])
        assertEquals(LinhaWidget("sem", "Restelo", "14:40", aoVivo = false), c.linhas[1])
    }

    @Test fun horasEmHoraDeLisboa() {
        val verao = Instant.parse("2026-07-01T13:32:00Z")
        val c = conteudoDoWidget("Cais", listOf(chegada("1", verao)) to verao, verao, EstadoWidget.Normal)
        assertEquals("14:32", c.linhas.single().hora)
        assertEquals("14:32", c.atualizadoAs)
    }

    @Test fun semNadaGuardadoDizParaTocar() {
        val c = conteudoDoWidget("Cais", null, agora, EstadoWidget.Normal)
        assertEquals(ConteudoWidget("Cais", emptyList(), null, cinzento = false, EstadoWidget.Normal), c)
    }

    @Test fun semParagemFicaSemNome() = assertNull(conteudoDoWidget(null, null, agora, EstadoWidget.Normal).paragem)
}
