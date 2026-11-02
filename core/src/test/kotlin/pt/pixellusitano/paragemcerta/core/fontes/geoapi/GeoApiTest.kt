package pt.pixellusitano.paragemcerta.core.fontes.geoapi

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.HttpFalso
import pt.pixellusitano.paragemcerta.core.fixture
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado

class GeoApiTest {
    // Um código real sem pontos nem centro: a GeoAPI só tem as artérias dos CTT.
    private val semCoordenadas = """{"CP":"1169-001","Concelho":"Lisboa","Localidade":"Lisboa","partes":[{"Artéria":"Rua dos Anjos","Porta":"68"}],"pontos":[],"ruas":[]}"""

    @Test fun reconheceCodigosPostais() {
        listOf("1400-038", "1400038", "1400 038", " 1400-038 ").forEach { assertTrue(it, ehCodigoPostal(it)) }
        listOf("1400-03", "14000-038", "Belém", "1400-0388", "1400--038", "1400  038", "").forEach { assertFalse(it, ehCodigoPostal(it)) }
    }

    @Test fun normalizaParaOFormatoDaGeoApi() {
        listOf("1400 038", "1400038", " 1400-038 ").forEach { assertEquals(it, "1400-038", normalizarCodigoPostal(it)) }
    }

    @Test fun leOCodigoPostal() {
        val cp = GeoApiParser.codigoPostal(fixture("geoapi-cp-1400-038.json"))
        assertEquals("1400-038", cp.cp)
        assertEquals("Doca do Bom Sucesso", cp.rua)
        assertEquals("Lisboa", cp.localidade)
        assertEquals("Lisboa", cp.concelho)
        assertNotNull(cp.lat)
        assertNotNull(cp.lon)
        // [lat, lon], como vem: a latitude de Lisboa é 38,7 e a longitude -9,2.
        assertEquals(38.693, cp.lat!!, 0.001)
        assertEquals(-9.2106, cp.lon!!, 0.001)
    }

    @Test fun semCentroideUsaOCentroDeMassa() {
        val cp = GeoApiParser.codigoPostal("""{"CP":"2735-001","Localidade":"São Marcos","centroDeMassa":[38.7478,-9.294]}""")
        assertEquals(38.7478, cp.lat!!, 1e-9)
        assertEquals(-9.294, cp.lon!!, 1e-9)
    }

    @Test fun semCoordenadasFicamNulas() {
        val cp = GeoApiParser.codigoPostal(semCoordenadas)
        assertNull(cp.lat)
        assertNull(cp.lon)
        assertNull(cp.paraLugar())
        // Sem "ruas" (é o que acontece sem coordenadas), a artéria dos CTT serve para procurar no Transitous.
        assertEquals("Rua dos Anjos", cp.rua)
    }

    @Test fun ruaFicaComMaiusculasSoNoInicioDasPalavras() {
        fun rua(nome: String) = GeoApiParser.codigoPostal("""{"CP":"1000-000","Localidade":"Lisboa","ruas":["$nome"]}""").rua
        assertEquals("Doca do Bom Sucesso", rua("DOCA DO BOM SUCESSO"))
        assertEquals("Rua Dr. António de Oliveira e Silva", rua("RUA DR. ANTÓNIO DE OLIVEIRA E SILVA"))
        assertEquals("Avenida das Descobertas", rua("AVENIDA DAS DESCOBERTAS"))
        assertEquals("Largo dos Pescadores da Madalena", rua("LARGO DOS PESCADORES DA MADALENA"))
    }

    @Test fun respostaSemCodigoNaoServe() = runTest {
        // Um 200 sem o código não é um código postal: conta como falha, não como resultado vazio.
        val r = GeoApi(HttpFalso({ Resposta(200, """{"erro":"x"}""") })).codigoPostal("1400-038")
        assertEquals(Resultado.Falha(200, limite = false), r)
    }

    @Test fun inexistenteDaNulo() = runTest {
        val r = GeoApi(HttpFalso({ Resposta(404, """{"erro":"Postal Code not found!"}""") })).codigoPostal("9999-999")
        assertEquals(Resultado.Ok(null), r)
    }

    @Test fun limiteDaFalha() = runTest {
        val r = GeoApi(HttpFalso({ Resposta(429, "") })).codigoPostal("1400-038")
        assertEquals(Resultado.Falha(429, limite = true), r)
    }

    @Test fun erroDoServidorDaFalha() = runTest {
        assertEquals(Resultado.Falha(500, limite = false), GeoApi(HttpFalso({ Resposta(500, "") })).codigoPostal("1400-038"))
    }

    @Test fun pedeOEnderecoCerto() = runTest {
        val http = HttpFalso({ Resposta(200, fixture("geoapi-cp-1400-038.json")) })
        val r = GeoApi(http).codigoPostal("1400-038")
        assertEquals("https://json.geoapi.pt/cp/1400-038", http.pedidos.single())
        assertEquals("1400-038", (r as Resultado.Ok).valor!!.cp)
    }

    @Test fun pedeOCodigoNormalizado() = runTest {
        val http = HttpFalso({ Resposta(404, "") })
        GeoApi(http).codigoPostal("1400 038")
        assertEquals("https://json.geoapi.pt/cp/1400-038", http.pedidos.single())
    }

    @Test fun lugarComONomeDoCodigoEAZona() {
        val l = GeoApiParser.codigoPostal(fixture("geoapi-cp-1400-038.json")).paraLugar()!!
        assertEquals("1400-038 · Doca do Bom Sucesso", l.nome)
        assertEquals(TipoLugar.MORADA, l.tipo)
        assertEquals("Lisboa", l.zona)
        assertEquals(38.693, l.lat, 0.001)
    }

    @Test fun lugarSemRuaFicaSoComOCodigo() {
        val l = GeoApiParser.codigoPostal("""{"CP":"1400-038","Localidade":"Lisboa","centroide":[38.69,-9.21]}""").paraLugar()!!
        assertEquals("1400-038", l.nome)
    }

    @Test fun reconheceOsLugaresDeCodigoPostal() {
        assertTrue(ehLugarDeCodigoPostal(Lugar("1400-038 · Doca do Bom Sucesso", 38.7, -9.2, TipoLugar.MORADA)))
        assertTrue(ehLugarDeCodigoPostal(Lugar("1400-038", 38.7, -9.2, TipoLugar.MORADA)))
        assertFalse(ehLugarDeCodigoPostal(Lugar("Doca do Bom Sucesso", 38.7, -9.2, TipoLugar.MORADA)))
        assertFalse(ehLugarDeCodigoPostal(Lugar("Rua 1400-038", 38.7, -9.2, TipoLugar.MORADA)))
    }
}
