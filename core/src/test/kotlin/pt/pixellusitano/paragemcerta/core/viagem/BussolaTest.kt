package pt.pixellusitano.paragemcerta.core.viagem

import org.junit.Assert.assertEquals
import org.junit.Test

class BussolaTest {
    // Cais do Sodré, e pontos a ~110 m dele em cada direção.
    private val lat = 38.7060
    private val lon = -9.1440
    private val d = 0.001

    private fun rumoPara(dLat: Double, dLon: Double) = rumo(lat, lon, lat + dLat, lon + dLon)

    @Test fun rumoParaNorteEsteSulEOeste() {
        assertEquals(0.0, rumoPara(d, 0.0), 0.01)
        assertEquals(90.0, rumoPara(0.0, d), 0.05)
        assertEquals(180.0, rumoPara(-d, 0.0), 0.01)
        assertEquals(270.0, rumoPara(0.0, -d), 0.05)
    }

    @Test fun rumoNaDiagonalNoEquador() {
        assertEquals(45.0, rumo(0.0, 0.0, 1.0, 1.0), 0.01)
        assertEquals(225.0, rumo(0.0, 0.0, -1.0, -1.0), 0.01)
    }

    @Test fun rumoParaOMesmoSitioNaoFalha() {
        assertEquals(0.0, rumo(lat, lon, lat, lon), 0.0)
    }

    @Test fun setaEmFrenteQuandoOTelemovelApontaParaAParagem() {
        assertEquals(0.0, anguloDaSeta(rumoParagem = 90.0, orientacao = 90.0, declinacao = 0.0), 1e-9)
    }

    @Test fun setaParaADireitaEParaAEsquerda() {
        assertEquals(90.0, anguloDaSeta(90.0, orientacao = 0.0, declinacao = 0.0), 1e-9)
        assertEquals(270.0, anguloDaSeta(0.0, orientacao = 90.0, declinacao = 0.0), 1e-9)
    }

    @Test fun aDeclinacaoSomaSeAoNorteMagnetico() {
        // A bússola dá o norte magnético (80°); com 2° de declinação o telemóvel aponta para 82° verdadeiros.
        assertEquals(8.0, anguloDaSeta(90.0, orientacao = 80.0, declinacao = 2.0), 1e-9)
        assertEquals(12.0, anguloDaSeta(90.0, orientacao = 80.0, declinacao = -2.0), 1e-9)
    }

    @Test fun setaDaAVoltaAosTrezentosESessenta() {
        assertEquals(20.0, anguloDaSeta(10.0, orientacao = 350.0, declinacao = 0.0), 1e-9)
        assertEquals(340.0, anguloDaSeta(350.0, orientacao = 10.0, declinacao = 0.0), 1e-9)
        assertEquals(1.0, anguloDaSeta(0.0, orientacao = 1.0, declinacao = -2.0), 1e-9)
        assertEquals(0.0, anguloDaSeta(0.0, orientacao = 360.0, declinacao = 0.0), 1e-9)
    }

    @Test fun setorDivideACircunferenciaEmFatiasCentradasNoNorte() {
        assertEquals(0, setor(0.0, 8))
        assertEquals(0, setor(22.4, 8))
        assertEquals(1, setor(22.5, 8))
        assertEquals(1, setor(45.0, 8))
        assertEquals(4, setor(180.0, 8))
        assertEquals(7, setor(315.0, 8))
        assertEquals(0, setor(337.5, 8)) // a fatia do norte fecha a volta
        assertEquals(0, setor(359.9, 8))
        assertEquals(1, setor(90.0, 4))
        assertEquals(0, setor(44.9, 4))
        assertEquals(3, setor(270.0, 4))
        assertEquals(0, setor(315.0, 4))
    }
}
