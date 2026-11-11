package pt.pixellusitano.paragemcerta.core.fotos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fixture

class FotosDeRuaTest {
    private fun foto(data: String = "2022-03-13", id: String = "x") = FotoDeRua(id, "https://m.example/t.jpg", "Ana", "CC0", data)

    private fun entrada(
        id: String = "\"a1\"", miniatura: String = "\"https://m.example/t.jpg\"", autor: String = "\"Ana\"",
        licenca: String = "\"CC0\"", data: String = "\"2022-03-13\"",
    ) = """{"id":$id,"miniatura":$miniatura,"autor":$autor,"licenca":$licenca,"data":$data}"""

    private fun ficheiro(fotos: String, versao: String = "1") = """{"versao":$versao,"geradoEm":"2026-11-04","fotos":{$fotos}}"""

    @Test fun lerFotosDeRuaDaFixtura() {
        val fotos = lerFotosDeRua(fixture("fotos-de-rua.json"))
        assertEquals(setOf("CM:060419", "TRANSITOUS:pt-Carris_920"), fotos.keys)
        assertEquals(
            FotoDeRua(
                "7d9a3f10-5c2e-4b86-a1d4-0e8f6b2c9a37", "https://panoramax.example/api/pictures/7d9a3f10-5c2e-4b86-a1d4-0e8f6b2c9a37/thumb.jpg",
                "Robot8A", "CC BY-SA 4.0", "2022-03-13",
            ),
            fotos.getValue("CM:060419"),
        )
        assertEquals("Licence Ouverte 2.0", fotos.getValue("TRANSITOUS:pt-Carris_920").licenca)
    }

    @Test fun ficheiroVazioLeSemFotos() {
        assertEquals(emptyMap<String, FotoDeRua>(), lerFotosDeRua(ficheiro("")))
    }

    @Test fun ficheiroComVersaoDesconhecidaFalha() {
        assertThrows(Exception::class.java) { lerFotosDeRua(ficheiro(""""CM:1":${entrada()}""", versao = "2")) }
        assertThrows(Exception::class.java) { lerFotosDeRua("""{"geradoEm":"2026-11-04","fotos":{}}""") }
    }

    @Test fun ficheiroEstragadoFalha() {
        assertThrows(Exception::class.java) { lerFotosDeRua("""{"versao":1,"fotos":{"CM:1":""") }
        assertThrows(Exception::class.java) { lerFotosDeRua("""{"versao":1,"fotos":[]}""") }
        assertThrows(Exception::class.java) { lerFotosDeRua("") }
    }

    @Test fun entradaEstragadaNaoEstragaAsOutras() {
        val fotos = lerFotosDeRua(
            ficheiro(
                listOf(
                    "\"CM:boa\":${entrada()}",
                    "\"CM:semAutor\":{\"id\":\"a\",\"miniatura\":\"https://m.example/t.jpg\",\"licenca\":\"CC0\",\"data\":\"2022-03-13\"}",
                    "\"CM:numero\":5",
                    "\"CM:nulo\":null",
                    "\"CM:idVazio\":${entrada(id = "\"\"")}",
                    "\"CM:autorVazio\":${entrada(autor = "\" \"")}",
                    "\"CM:semLicenca\":${entrada(licenca = "\"\"")}",
                    "\"CM:dataMa\":${entrada(data = "\"ontem\"")}",
                    "\"CM:idNumero\":${entrada(id = "7")}",
                ).joinToString(","),
            ),
        )
        assertEquals(listOf("CM:boa"), fotos.keys.toList())
        assertEquals("a1", fotos.getValue("CM:boa").id)
    }

    @Test fun miniaturaSemHttpsSalta() {
        val fotos = lerFotosDeRua(
            ficheiro(
                listOf(
                    "\"CM:http\":${entrada(miniatura = "\"http://m.example/t.jpg\"")}",
                    "\"CM:outro\":${entrada(miniatura = "\"file:///data/t.jpg\"")}",
                    "\"CM:vazia\":${entrada(miniatura = "\"\"")}",
                    "\"CM:boa\":${entrada()}",
                ).joinToString(","),
            ),
        )
        assertEquals(listOf("CM:boa"), fotos.keys.toList())
    }

    @Test fun camposNovosNaoPartem() {
        val fotos = lerFotosDeRua("""{"versao":1,"novidade":true,"fotos":{"CM:1":{"id":"a","miniatura":"https://m.example/t.jpg","autor":"Ana","licenca":"CC0","data":"2022-03-13","extra":1}}}""")
        assertEquals(foto(id = "a"), fotos.getValue("CM:1"))
    }

    @Test fun anoDaFotoSaiDaData() {
        assertEquals("2022", anoDaFoto(foto("2022-03-13")))
    }

    @Test fun paginaDaFotoLevaOId() {
        assertEquals(
            "https://api.panoramax.xyz/#focus=pic&pic=7d9a3f10-5c2e-4b86-a1d4-0e8f6b2c9a37",
            paginaDaFoto(foto(id = "7d9a3f10-5c2e-4b86-a1d4-0e8f6b2c9a37")),
        )
    }
}
