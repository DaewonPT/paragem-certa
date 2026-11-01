package pt.pixellusitano.paragemcerta.core.viagem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot

class PercursoTest {
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")

    // Metros a leste (x) e a norte (y) de um ponto de Lisboa, como (lat, lon).
    private fun ponto(x: Double, y: Double): Pair<Double, Double> =
        (38.7 + y / 111_194.93) to (-9.1 + x / (111_194.93 * cos(Math.toRadians(38.7))))

    private fun paragem(nome: String, p: Pair<Double, Double>, segundos: Long) =
        ParagemEtapa("id-$nome", nome, p.first, p.second, t0.plusSeconds(segundos))

    // Três paragens numa recta de 1500 m para leste (com vértices pelo meio), a 0, 600 e 1500 m.
    private val recta = listOf(0.0, 500.0, 900.0, 1500.0).map { ponto(it, 0.0) }
    private val tres = listOf(
        paragem("A", ponto(0.0, 0.0), 0), paragem("B", ponto(600.0, 0.0), 60), paragem("C", ponto(1500.0, 0.0), 180),
    )
    private val percurso = Percurso(recta, tres)

    @Test fun paragensAoLongoDaRecta() {
        val m = percurso.noPercurso
        assertEquals(3, m.size)
        assertEquals(0.0, m[0], 1.0)
        assertEquals(distanciaMetros(tres[0].lat, tres[0].lon, tres[1].lat, tres[1].lon), m[1], 1.0)
        assertEquals(distanciaMetros(tres[0].lat, tres[0].lon, tres[2].lat, tres[2].lon), m[2], 1.0)
        assertTrue(m[0] < m[1] && m[1] < m[2])
    }

    @Test fun pontoAoLadoDaLinha() {
        val (lat, lon) = ponto(700.0, 20.0)
        val p = percurso.projetar(lat, lon, 0.0, 1500.0)!!
        assertEquals(700.0, p.ao, 1.0)
        assertEquals(20.0, p.afastamento, 1.0)
    }

    @Test fun janelaCortaAProjecao() {
        val (lat, lon) = ponto(700.0, 20.0)
        // O ponto fica antes da janela: prende-se ao início dela e o afastamento é o dessa posição.
        val p = percurso.projetar(lat, lon, 800.0, 1500.0)!!
        assertEquals(800.0, p.ao, 1.0)
        assertEquals(hypot(100.0, 20.0), p.afastamento, 1.0)
        assertNull(percurso.projetar(lat, lon, 2000.0, 3000.0))
        assertNull(percurso.projetar(lat, lon, 900.0, 800.0))
    }

    // O percurso passa a 80 m do destino (0, 400) aos 400 m e só acaba nele, aos 1480 m.
    private val lacoDesenho = listOf(
        ponto(80.0, 0.0), ponto(80.0, 600.0), ponto(-300.0, 600.0), ponto(-300.0, 400.0), ponto(0.0, 400.0),
    )
    private val destino = ponto(0.0, 400.0)

    @Test fun lacoNaoSaltaParaOFim() {
        val laco = Percurso(lacoDesenho, listOf(paragem("S", lacoDesenho.first(), 0), paragem("D", destino, 300)))
        val (lat, lon) = destino
        val cedo = laco.projetar(lat, lon, 0.0, 500.0)!!
        assertEquals(400.0, cedo.ao, 1.0)
        assertEquals(80.0, cedo.afastamento, 1.0)
        val fim = laco.projetar(lat, lon, 0.0, 3000.0)!!
        assertEquals(1480.0, fim.ao, 1.0)
        assertEquals(0.0, fim.afastamento, 1.0)
    }

    @Test fun lacoComParagens() {
        val paragens = listOf(
            paragem("S", lacoDesenho[0], 0), paragem("N", lacoDesenho[1], 100),
            paragem("O", lacoDesenho[2], 200), paragem("D", destino, 300),
        )
        val m = Percurso(lacoDesenho, paragens).noPercurso
        listOf(0.0, 600.0, 980.0, 1480.0).forEachIndexed { i, esperado -> assertEquals(esperado, m[i], 1.0) }
    }

    @Test fun idaEVoltaPelaMesmaRua() {
        // A volta passa nos mesmos sítios da ida: cada paragem projeta-se à frente da anterior.
        val ida = listOf(ponto(0.0, 0.0), ponto(1000.0, 0.0), ponto(0.0, 0.0))
        val paragens = listOf(
            paragem("A", ponto(0.0, 0.0), 0), paragem("M", ponto(500.0, 0.0), 60), paragem("B", ponto(1000.0, 0.0), 120),
            paragem("M2", ponto(500.0, 0.0), 180), paragem("A2", ponto(0.0, 0.0), 240),
        )
        val m = Percurso(ida, paragens).noPercurso
        listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0).forEachIndexed { i, esperado -> assertEquals(esperado, m[i], 1.0) }
    }

    @Test fun semDesenhoUsaAsParagens() {
        val metro = Percurso(emptyList(), tres)
        assertEquals(distanciaMetros(tres[0].lat, tres[0].lon, tres[2].lat, tres[2].lon), metro.noPercurso[2], 1.0)
        val (lat, lon) = ponto(300.0, 15.0)
        val p = metro.projetar(lat, lon, 0.0, 1500.0)!!
        assertEquals(300.0, p.ao, 1.0)
        assertEquals(15.0, p.afastamento, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun semLinhaNaoHaPercurso() {
        Percurso(emptyList(), tres.take(1))
    }

    @Test fun horaEProgressoSaoInversos() {
        assertEquals(t0, percurso.horaEm(0.0))
        assertEquals(t0.plusSeconds(60), percurso.horaEm(percurso.noPercurso[1]))
        // A meio do troço B-C (900 m, 120 s): 60 s depois de B.
        val meio = (percurso.noPercurso[1] + percurso.noPercurso[2]) / 2
        assertEquals(t0.plusSeconds(120), percurso.horaEm(meio))
        listOf(0.0, 150.0, 600.0, 1000.0, 1500.0).forEach { m ->
            assertEquals(m, percurso.progressoEm(percurso.horaEm(m)), 1.0)
        }
        listOf(0L, 30L, 60L, 100L, 180L).forEach { s ->
            assertEquals(t0.plusSeconds(s), percurso.horaEm(percurso.progressoEm(t0.plusSeconds(s))))
        }
    }

    @Test fun foraDaEtapaFicaNosExtremos() {
        assertEquals(t0, percurso.horaEm(-50.0))
        assertEquals(t0.plusSeconds(180), percurso.horaEm(5000.0))
        assertEquals(0.0, percurso.progressoEm(t0.minusSeconds(60)), 1e-9)
        assertEquals(percurso.noPercurso[2], percurso.progressoEm(t0.plusSeconds(600)), 1e-9)
    }
}
