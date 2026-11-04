package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.fotos.lerFotosDeRua
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** O `fotos-de-rua.json` pelo mesmo caminho do catálogo (ETag, ficheiro guardado, estragado = nada), com o leitor das fotos. */
class FotosDeRuaTest {
    @get:Rule val pasta = TemporaryFolder()
    private var agora = Instant.parse("2026-11-04T09:00:00Z")
    private val url = "https://exemplo.pt/dados/fotos-de-rua.json"
    private fun ficheiro(http: Http) =
        FicheiroDoServidor(pasta.root, "fotos-de-rua.json", http, url, relogio = { agora }, ler = ::lerFotosDeRua)
    private val bom = { Resposta(200, fixture("fotos-de-rua.json"), "\"f1\"") }

    private fun pronto(f: FicheiroDoServidor<Map<String, FotoDeRua>>) = (f.estado.value as EstadoFicheiro.Pronto).c

    @Test fun fotosDeRuaLeODoServidorEGuarda() = runTest {
        val primeiro = HttpComEtag(bom)
        val f = ficheiro(primeiro)
        assertEquals(EstadoFicheiro.ACarregar, f.estado.value)
        f.aoAbrir()
        assertEquals(listOf(url to null), primeiro.pedidos)
        assertEquals(setOf("CM:060419", "TRANSITOUS:pt-Carris_920"), pronto(f).keys)
        assertEquals("Robot8A", pronto(f).getValue("CM:060419").autor)
        assertEquals(pronto(f), f.atual)
        assertTrue(File(pasta.root, "fotos-de-rua.json").isFile)

        // Na abertura seguinte (outra vez a app) o guardado conta logo, e o 304 deixa-o como estava.
        agora += Duration.ofHours(25)
        val segundo = HttpComEtag({ Resposta(304, "") })
        val g = ficheiro(segundo)
        g.aoAbrir()
        assertEquals(listOf(url to "\"f1\""), segundo.pedidos)
        assertEquals(pronto(f), pronto(g))
    }

    @Test fun fotosDeRuaFicheiroEstragadoFicaComOAntigo() = runTest {
        ficheiro(HttpComEtag(bom)).aoAbrir()
        val bomTexto = fixture("fotos-de-rua.json")
        // Cortado, e de uma versão que esta app não conhece: nenhum dos dois vai para o disco nem tira o que lá está.
        listOf(bomTexto.take(120), bomTexto.replace("\"versao\": 1", "\"versao\": 2")).forEachIndexed { i, mau ->
            agora += Duration.ofHours(25)
            val f = ficheiro(HttpComEtag({ Resposta(200, mau, "\"mau$i\"") }))
            f.aoAbrir()
            assertEquals(setOf("CM:060419", "TRANSITOUS:pt-Carris_920"), pronto(f).keys)
        }
        // O guardado continua o bom: a abertura seguinte, no dia seguinte, pede com o ETag dele.
        agora += Duration.ofHours(25)
        val depois = HttpComEtag({ Resposta(304, "") })
        ficheiro(depois).aoAbrir()
        assertEquals(listOf(url to "\"f1\""), depois.pedidos)
    }

    @Test fun fotosDeRuaSemFicheiroESemRedeFalha() = runTest {
        val f = ficheiro(HttpComEtag({ throw IOException("sem rede") }))
        f.aoAbrir()
        assertEquals(EstadoFicheiro.Falhou, f.estado.value)
        assertEquals(null, f.atual)
    }
}
