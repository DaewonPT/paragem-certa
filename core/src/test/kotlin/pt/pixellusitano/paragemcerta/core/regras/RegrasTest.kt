package pt.pixellusitano.paragemcerta.core.regras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import java.time.Duration

class RegrasTest {
    private val paragens = CmParser.paragens(fixture("cm-stops.json"))
    private val terminal = paragens.first { it.ref.id == "060419" }
    private val cais = paragens.first { it.ref.id == "062000" }

    @Test fun distancia() {
        val d = distanciaMetros(terminal.lat, terminal.lon, cais.lat, cais.lon)
        assertTrue("distância $d", d in 80.0..100.0)
    }

    @Test fun maisProximasDentroDoRaio() {
        assertEquals(listOf("060419"), maisProximas(paragens, terminal.lat, terminal.lon, raioMetros = 50.0).map { it.ref.id })
        assertEquals("060419", maisProximas(paragens, terminal.lat, terminal.lon).first().ref.id)
    }

    @Test fun favoritaPorOmissao() {
        val favs = listOf(
            Favorita(RefParagem(Fonte.CM, "060419"), "Terminal", ordem = 0, lat = terminal.lat, lon = terminal.lon),
            Favorita(RefParagem(Fonte.CM, "062000"), "Cais", ordem = 1, lat = cais.lat, lon = cais.lon),
        )
        assertEquals("062000", favoritaPorOmissao(favs, cais.lat to cais.lon)?.ref?.id)
        assertEquals("060419", favoritaPorOmissao(favs, null)?.ref?.id)
        assertNull(favoritaPorOmissao(emptyList(), null))
    }

    @Test fun pausas() {
        assertEquals(Duration.ofSeconds(20), intervaloNormal(Fonte.CM))
        assertEquals(Duration.ofSeconds(60), intervaloNormal(Fonte.TRANSITOUS))
        assertEquals(Duration.ofSeconds(20), pausaDepoisDeFalha(Fonte.CM, 0))
        assertEquals(Duration.ofSeconds(160), pausaDepoisDeFalha(Fonte.CM, 3))
        assertEquals(Duration.ofMinutes(5), pausaDepoisDeFalha(Fonte.TRANSITOUS, 4))
        assertEquals(Duration.ofMinutes(5), pausaDepoisDeFalha(Fonte.CM, 40))
    }
}
