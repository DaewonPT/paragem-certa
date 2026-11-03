package pt.pixellusitano.paragemcerta.core.fontes.emel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.IOException

class GiraTest {
    private val v1 = "\"v1\""

    @Test fun leAsEstacoesGira() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("emel-gira.json"), v1) })
        val r = (Gira(http, "https://gira.teste/girastations.json").estacoes(null) as Resultado.Ok).valor
        assertEquals("https://gira.teste/girastations.json", http.pedidos.single())
        assertEquals(v1, r.etag)
        val e = r.estacoes!!
        assertEquals(listOf("105", "107", "261", "454", "483"), e.map { it.id })
        // O nome vem sem o "NNN - " e sem os espaços nem os tabs que o ficheiro deixa no fim.
        assertEquals(
            listOf(
                "CC Vasco da Gama", "Rotunda dos Vice-Reis", "Calçada da Estrela / Assembleia da República",
                "Av. Álvaro Pais / Rua da Cruz Vermelha", "Rua Professor Francisco Lucas Pires",
            ),
            e.map { it.nome },
        )
        assertEquals(EstacaoGira("105", "CC Vasco da Gama", 38.768548, -9.096222), e[0])
        assertEquals(38.7114685, e[2].lat, 1e-9)
        assertEquals(-9.1538708, e[2].lon, 1e-9)
    }

    @Test fun soPedeSeHouverEtagGuardado() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("emel-gira.json")) })
        Gira(http).estacoes(null)
        Gira(http).estacoes(v1)
        assertEquals(listOf(emptyMap(), mapOf("If-None-Match" to v1)), http.cabecalhosDoGet)
        assertEquals(URL_GIRA, http.pedidos[0])
    }

    @Test fun giraNaoMudouDa304() = runTest {
        // O 304 não leva corpo: fica o ETag que já se tinha e nenhuma lista, para o chamador não mexer no que guardou.
        val http = HttpFalso({ Resposta(304, "") })
        assertEquals(Resultado.Ok(LeituraGira(null, v1)), Gira(http).estacoes(v1))
    }

    @Test fun giraCortadoFicaComOAntigo() = runTest {
        val cortado = fixture("emel-gira.json").let { it.substring(0, it.length / 2) }
        assertEquals(Resultado.Falha(200, limite = false), Gira(HttpFalso({ Resposta(200, cortado, v1) })).estacoes(null))
    }

    @Test fun giraVazioOuQueNaoELista() = runTest {
        // Um ficheiro que não é uma lista de estações (uma página de erro com 200, um objeto, uma lista vazia) não apaga as que já se tinham.
        listOf("<html>Manutenção</html>", "{}", "[]").forEach {
            assertEquals(it, Resultado.Falha(200, limite = false), Gira(HttpFalso({ Resposta(200, it) })).estacoes(null))
        }
    }

    @Test fun giraEmBaixoDaFalha() = runTest {
        assertEquals(Resultado.Falha(500, limite = false), Gira(HttpFalso({ Resposta(500, "") })).estacoes(v1))
        assertEquals(Resultado.Falha(503, limite = true), Gira(HttpFalso({ Resposta(503, "") })).estacoes(null))
        assertEquals(Resultado.Falha(null, limite = false), Gira(HttpFalso({ throw IOException("sem rede") })).estacoes(null))
    }

    @Test fun estacaoSemCoordenadasSai() = runTest {
        val json = """[{"id_expl":"1","estacaolocalizacao":"1 - Sem sítio","latitude":null,"longitude":null},
            {"id_expl":"2","estacaolocalizacao":"2 - Com sítio","latitude":38.7,"longitude":-9.1}]"""
        val r = (Gira(HttpFalso({ Resposta(200, json) })).estacoes(null) as Resultado.Ok).valor
        assertEquals(listOf(EstacaoGira("2", "Com sítio", 38.7, -9.1)), r.estacoes)
    }

    @Test fun nomeSemONumeroNemEspacosAMais() {
        assertEquals("CC Vasco da Gama", nomeGira("105 - CC Vasco da Gama "))
        assertEquals("Av. Álvaro Pais / Rua da Cruz Vermelha", nomeGira("454 - Av. Álvaro Pais / Rua da Cruz Vermelha\t"))
        assertEquals("Av. República / Interface Entrecampos", nomeGira("446 -  Av. República  /  Interface Entrecampos "))
        // Só o número do início sai: um nome com algarismos no meio, ou sem número, fica como está.
        assertEquals("Rua 25 de Abril - Norte", nomeGira("12 - Rua 25 de Abril - Norte"))
        assertEquals("Jardim da Estrela", nomeGira("Jardim da Estrela"))
        assertTrue(nomeGira("7 - ").isEmpty())
    }
}
