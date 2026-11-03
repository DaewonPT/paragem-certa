package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.IOException
import java.time.Duration
import java.time.Instant

class EstacoesGiraTest {
    @get:Rule val pasta = TemporaryFolder()
    private var agora = Instant.parse("2026-11-03T09:00:00Z")
    private fun estacoes(http: HttpComEtag) = EstacoesGira(pasta.root, Gira(http, "https://exemplo.pt/gira.json")) { agora }

    @Test fun giraSoDe24Em24Horas() = runTest {
        val http = HttpComEtag({ Resposta(200, fixture("emel-gira.json"), "\"g1\"") }, { Resposta(304, "") })
        val e = estacoes(http)
        assertTrue(e.atual.value.isEmpty())
        e.seVelhas()
        val lidas = e.atual.value
        assertEquals(5, lidas.size)
        assertEquals("CC Vasco da Gama", lidas.first { it.id == "105" }.nome)

        agora += Duration.ofHours(23)
        e.seVelhas()
        assertEquals(1, http.pedidos.size) // ainda frescas: não se pede

        // Noutra abertura, mais de 24 h depois: o guardado aparece e o 304 deixa-o igual.
        agora += Duration.ofHours(2)
        val outra = estacoes(http)
        outra.seVelhas()
        assertEquals(listOf(null, "\"g1\""), http.pedidos.map { it.second })
        assertEquals(lidas, outra.atual.value)
    }

    @Test fun falhaFicaComAsAntigas() = runTest {
        estacoes(HttpComEtag({ Resposta(200, fixture("emel-gira.json"), "\"g1\"") })).seVelhas()
        agora += Duration.ofHours(25)
        val e = estacoes(HttpComEtag({ throw IOException("sem rede") }, { Resposta(200, fixture("emel-gira.json").take(300)) }))
        e.seVelhas()
        assertEquals(5, e.atual.value.size)
        e.seVelhas() // a falha não gastou o dia; o cortado também fica com as antigas
        assertEquals(5, e.atual.value.size)
    }
}
