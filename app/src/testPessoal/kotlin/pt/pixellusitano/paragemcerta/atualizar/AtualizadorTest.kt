package pt.pixellusitano.paragemcerta.atualizar

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.core.rede.Resposta

class AtualizadorTest {
    private val release = """
        {"tag_name": "v0.1.1", "body": "Correções no mapa.",
         "assets": [
           {"name": "notas.txt", "browser_download_url": "https://exemplo/notas.txt"},
           {"name": "paragem-certa-v0.1.1.apk", "browser_download_url": "https://github.com/DaewonPT/paragem-certa/releases/download/v0.1.1/paragem-certa-v0.1.1.apk"}
         ]}
    """.trimIndent()

    @Test fun haVersaoNova() = runTest {
        val http = HttpPorUrl("releases/latest" to listOf({ Resposta(200, release) }))
        val nova = VerificadorVersoes(http).verificar(versionCodeAtual = 100)
        assertEquals("v0.1.1", nova?.tag)
        assertEquals("https://github.com/DaewonPT/paragem-certa/releases/download/v0.1.1/paragem-certa-v0.1.1.apk", nova?.urlApk)
        assertEquals("Correções no mapa.", nova?.notas)
        assertEquals("https://api.github.com/repos/DaewonPT/paragem-certa/releases/latest", http.pedidos.single())
    }

    @Test fun jaEstaAtualizada() = runTest {
        val http = HttpPorUrl("releases/latest" to listOf({ Resposta(200, release) }))
        assertNull(VerificadorVersoes(http).verificar(versionCodeAtual = 101))
    }

    @Test fun aindaNaoHaReleases() = runTest {
        val http = HttpPorUrl("releases/latest" to listOf({ Resposta(404, """{"message":"Not Found"}""") }))
        assertNull(VerificadorVersoes(http).verificar(versionCodeAtual = 100))
    }
}
