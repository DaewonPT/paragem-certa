package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Http falso que responde pela ordem da fila e guarda o If-None-Match de cada pedido. */
class HttpComEtag(vararg respostas: () -> Resposta) : Http {
    private val fila = respostas.toMutableList()
    val pedidos = mutableListOf<Pair<String, String?>>()
    override suspend fun get(url: String): Resposta = getCom(url, emptyMap())
    override suspend fun getCom(url: String, cabecalhos: Map<String, String>): Resposta {
        pedidos += url to cabecalhos["If-None-Match"]
        return fila.removeAt(0)()
    }
}

class CatalogoLinhasTest {
    @get:Rule val pasta = TemporaryFolder()
    private var agora = Instant.parse("2026-11-03T09:00:00Z")
    private val base = "https://exemplo.pt"
    private fun catalogo(http: Http) = CatalogoLinhas(pasta.root, http, base, relogio = { agora })
    private val bom = { Resposta(200, fixture("linhas.json"), "\"v1\"") }
    private val semRede = { throw IOException("sem rede") }

    private fun pronto(c: CatalogoLinhas) = (c.estado.value as EstadoCatalogo.Pronto).c

    @Test fun catalogoComEtagNaoDescarregaDeNovo() = runTest {
        val primeiro = HttpComEtag(bom)
        val c = catalogo(primeiro)
        assertEquals(EstadoCatalogo.ACarregar, c.estado.value)
        c.aoAbrir()
        assertEquals(listOf("$base/dados/linhas.json" to null), primeiro.pedidos)
        val lido = pronto(c)
        assertSame(lido, c.atual)

        // Menos de 24 h depois não pede nada.
        agora += Duration.ofHours(23)
        c.aoAbrir()
        assertEquals(1, primeiro.pedidos.size)

        // Na abertura seguinte (outra vez a app) o guardado conta logo, e o 304 deixa-o como estava.
        agora += Duration.ofHours(2)
        val segundo = HttpComEtag({ Resposta(304, "") })
        val d = catalogo(segundo)
        d.aoAbrir()
        assertEquals(listOf("$base/dados/linhas.json" to "\"v1\""), segundo.pedidos)
        assertEquals(lido, pronto(d))
    }

    @Test fun catalogoEstragadoFicaComOAntigo() = runTest {
        catalogo(HttpComEtag(bom)).aoAbrir()
        agora += Duration.ofHours(25)
        val c = catalogo(HttpComEtag({ Resposta(200, fixture("linhas.json").take(500), "\"v2\"") }))
        c.aoAbrir()
        assertEquals(LocalDate.parse("2026-11-03"), pronto(c).geradoEm)
        // O cortado nunca foi para o disco: a abertura seguinte continua com o bom, sem descarregar outra vez nesse dia.
        agora += Duration.ofMinutes(1)
        val depois = HttpComEtag({ Resposta(304, "") })
        val d = catalogo(depois)
        d.aoAbrir()
        assertTrue(depois.pedidos.isEmpty())
        assertTrue(pronto(d).linhas.isNotEmpty())
        // No dia seguinte pede, com o ETag do bom.
        agora += Duration.ofHours(24)
        d.aoAbrir()
        assertEquals(listOf("$base/dados/linhas.json" to "\"v1\""), depois.pedidos)
    }

    @Test fun catalogoVelhoContinuaAServir() = runTest {
        catalogo(HttpComEtag(bom)).aoAbrir()
        agora += Duration.ofDays(90)
        val c = catalogo(HttpComEtag(semRede))
        c.aoAbrir()
        assertEquals(LocalDate.parse("2026-11-03"), pronto(c).geradoEm) // de há 90 dias, mas é melhor do que nada
    }

    @Test fun semCatalogoESemRedeFalha() = runTest {
        val c = catalogo(HttpComEtag(semRede, bom))
        c.aoAbrir()
        assertEquals(EstadoCatalogo.Falhou, c.estado.value)
        // Uma falha não gasta o dia: a abertura seguinte tenta outra vez.
        c.aoAbrir()
        assertTrue(pronto(c).linhas.isNotEmpty())
    }
}
