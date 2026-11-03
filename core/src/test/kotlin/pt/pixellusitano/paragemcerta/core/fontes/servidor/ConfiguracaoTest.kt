package pt.pixellusitano.paragemcerta.core.fontes.servidor

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File

class ConfiguracaoTest {
    // Os testes do :core correm na pasta do módulo.
    private val daApp = File("../app/src/main/assets/config.json")
    private val doServidor = File("../worker/public/dados/config.json")

    private fun objeto(ficheiro: File) = Json.parseToJsonElement(ficheiro.readText()) as JsonObject

    private fun ligados(config: JsonObject) = config.getValue("interruptores").jsonObject.filterValues { it.jsonPrimitive.boolean }.keys

    private suspend fun ler(texto: String) = (ServidorParagemCerta(HttpFalso({ Resposta(200, texto) })).config() as Resultado.Ok).valor

    @Test fun copiaDaAppIgualADoServidor() {
        val app = objeto(daApp)
        val servidor = objeto(doServidor)
        // Só os interruptores podem diferir: o servidor liga primeiro e a app no dia de publicar (docs/servidor.md, secção 9).
        assertEquals(servidor - "interruptores", app - "interruptores")
        assertTrue("ligado na app e desligado no servidor: ${ligados(app) - ligados(servidor)}", ligados(servidor).containsAll(ligados(app)))
    }

    @Test fun osTiposDeRelatoSaoOsDaConfiguracao() {
        val duracoes = objeto(doServidor).getValue("comunidade").jsonObject.getValue("duracaoMinutos").jsonObject
        assertEquals(duracoes.keys, TipoRelato.entries.map { it.chave }.toSet())
    }

    @Test fun leOsMapasOficiaisDoServidor() = runTest {
        val config = ler(doServidor.readText())
        assertTrue(config.mapasOficiais.isNotEmpty())
        assertTrue(config.mapasOficiais.all { it.operador.isNotBlank() && it.url.startsWith("https://") })
    }

    @Test fun leAVersaoDosTermos() = runTest {
        assertEquals("1", ler(doServidor.readText()).versaoTermos)
        val futura = """{"versao":2,"versaoTermos":"2","interruptores":{},"emails":{"codigosPorDia":70},"canaisReclamacao":[]}"""
        assertEquals("2", ler(futura).versaoTermos)
        // Uma configuração antiga, sem o campo, fica na versão 1.
        assertEquals("1", ler(fixture("servidor-config.json")).versaoTermos)
    }
}
