package pt.pixellusitano.paragemcerta.dados

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LocaisTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private var agora = Instant.parse("2026-11-01T08:00:00Z")
    private val http = HttpPorUrl("geocode" to listOf({ Resposta(200, "[]") }))
    private val repo = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http), relogio = { agora })
    private fun lugar(n: String, lat: Double = 38.7) = Lugar(n, lat, -9.14, TipoLugar.LOCAL)

    @After fun fechar() = bd.close()

    @Test fun casaSoHaUma() = runTest {
        repo.guardarLocal("Casa", lugar("Rua A"), "CASA")
        repo.guardarLocal("Casa", lugar("Rua B", 38.8), "CASA")
        val casas = repo.locais().first().filter { it.tipo == "CASA" }
        assertEquals(1, casas.size)
        assertEquals(38.8, casas.single().lugar.lat, 1e-9)
    }

    @Test fun recentesGuardaDezSemRepetir() = runTest {
        repeat(12) { i ->
            agora = agora.plusSeconds(60)
            repo.registarRecente(lugar("De $i"), lugar("Para $i"))
        }
        assertEquals(10, repo.recentes().first().size)
        agora = agora.plusSeconds(60)
        repo.registarRecente(lugar("De 5"), lugar("Para 5"))
        val r = repo.recentes().first()
        assertEquals(10, r.size)
        assertEquals("De 5", r.first().de.nome)
        assertEquals(1, r.count { it.de.nome == "De 5" })
    }

    @Test fun pesquisaComecaPelosLocais() = runTest {
        repo.guardarLocal("Trabalho", lugar("Av. da República"), "TRABALHO")
        assertEquals("Trabalho", repo.pesquisarLugares("trab")!!.first().nome)
    }

    private val semRede = Repositorio(
        bd, CarrisMetropolitana(http), Transitous(HttpPorUrl("geocode" to listOf({ Resposta(503, "") }))), GeoApi(http), relogio = { agora },
    )

    @Test fun pesquisaSemRedeSemLocaisDevolveNull() = runTest {
        assertNull(semRede.pesquisarLugares("trab"))
    }

    @Test fun pesquisaSemRedeMostraLocais() = runTest {
        semRede.guardarLocal("Trabalho", lugar("Av. da República"), "TRABALHO")
        assertEquals(listOf("Trabalho"), semRede.pesquisarLugares("trab")!!.map { it.nome })
    }

    @Test fun caisDaEstacaoSaiDoNome() = runTest {
        val resposta = """[
            {"type":"STOP","name":"Algés (Estação) P9","id":"pt-Carris-Metropolitana_120339","lat":38.6988,"lon":-9.2276},
            {"type":"STOP","name":"Algés (Estação) P10","id":"pt-Carris-Metropolitana_120340","lat":38.6989,"lon":-9.2277},
            {"type":"PLACE","name":"Estação Ferroviária de Algés","lat":38.6985,"lon":-9.2296}
        ]"""
        val comCais = Repositorio(
            bd, CarrisMetropolitana(http), Transitous(HttpPorUrl("geocode" to listOf({ Resposta(200, resposta) }))), GeoApi(http), relogio = { agora },
        )
        val lugares = comCais.pesquisarLugares("alges")!!
        assertEquals(listOf("Algés (Estação)"), lugares.filter { it.tipo == TipoLugar.PARAGEM }.map { it.nome })
        assertEquals(listOf("Estação Ferroviária de Algés"), lugares.filter { it.tipo == TipoLugar.LOCAL }.map { it.nome })
    }

    @Test fun ruasComOMesmoNomeEmConcelhosDiferentesFicamSeparadas() = runTest {
        fun rua(lat: Double, concelho: String) =
            """{"type":"ADDRESS","name":"Rua de Goa","lat":$lat,"lon":-8.9,"areas":[{"name":"$concelho","adminLevel":7.0}]}"""
        val resposta = "[${rua(40.9, "São João da Madeira")}, ${rua(40.9005, "São João da Madeira")}, ${rua(38.5, "Setúbal")}]"
        val repoGoa = Repositorio(
            bd, CarrisMetropolitana(http), Transitous(HttpPorUrl("geocode" to listOf({ Resposta(200, resposta) }))), GeoApi(http), relogio = { agora },
        )
        assertEquals(listOf("São João da Madeira", "Setúbal"), repoGoa.pesquisarLugares("rua de goa")!!.map { it.zona })
    }

    private suspend fun pesquisarGoa(vararg ruas: String): List<Lugar> {
        val resposta = ruas.joinToString(prefix = "[", postfix = "]")
        val r = Repositorio(
            bd, CarrisMetropolitana(http), Transitous(HttpPorUrl("geocode" to listOf({ Resposta(200, resposta) }))), GeoApi(http), relogio = { agora },
        )
        return r.pesquisarLugares("rua de goa")!!
    }

    private fun ruaGoa(lat: Double, freguesia: String, concelho: String = "Oeiras") =
        """{"type":"ADDRESS","name":"Rua de Goa","lat":$lat,"lon":-9.3,"areas":[{"name":"$concelho","adminLevel":7.0},{"name":"$freguesia","adminLevel":8.0}]}"""

    @Test fun pedacosDaMesmaRuaNaMesmaZonaFicamUm() = runTest {
        // 0.008 graus de latitude = cerca de 890 m: acima dos 400 m de antes, abaixo dos 2 km dos pedaços da mesma rua.
        val l = pesquisarGoa(ruaGoa(38.70, "Barcarena"), ruaGoa(38.708, "Barcarena"), ruaGoa(38.716, "Barcarena"))
        assertEquals(listOf(38.70), l.map { it.lat })
        assertEquals(listOf("Barcarena · Oeiras"), l.map { it.zona })
    }

    @Test fun ruasComOMesmoNomeEmFreguesiasDiferentesFicamSeparadas() = runTest {
        val l = pesquisarGoa(ruaGoa(38.70, "Barcarena"), ruaGoa(38.708, "Carnaxide e Queijas"))
        assertEquals(listOf("Barcarena · Oeiras", "Carnaxide e Queijas · Oeiras"), l.map { it.zona })
    }

    @Test fun pedacosDaMesmaZonaMuitoLongeFicamSeparados() = runTest {
        // 0.03 graus = cerca de 3,3 km: já passa dos 2 km.
        assertEquals(2, pesquisarGoa(ruaGoa(38.70, "Barcarena"), ruaGoa(38.73, "Barcarena")).size)
    }

    /** Um repositório com a GeoAPI e o geocoder do Transitous falsos (as respostas pela ordem em que são pedidas). */
    private fun comCodigosPostais(geoApi: () -> Resposta, geocode: () -> Resposta = { Resposta(200, "[]") }): Pair<Repositorio, HttpPorUrl> {
        val h = HttpPorUrl("json.geoapi.pt" to listOf(geoApi), "geocode" to listOf(geocode))
        return Repositorio(bd, CarrisMetropolitana(h), Transitous(h), GeoApi(h), relogio = { agora }) to h
    }

    private val daFixture = { Resposta(200, fixture("geoapi-cp-1400-038.json")) }

    @Test fun codigoPostalUsaAGeoApi() = runTest {
        val (r, h) = comCodigosPostais(daFixture)
        val lugares = r.pesquisarLugares("1400-038")!!
        assertEquals(listOf("1400-038 · Doca do Bom Sucesso"), lugares.map { it.nome })
        assertEquals(TipoLugar.MORADA, lugares.single().tipo)
        assertEquals(38.693, lugares.single().lat, 0.001)
        assertEquals(listOf("https://json.geoapi.pt/cp/1400-038"), h.pedidos) // sem Transitous: a GeoAPI já deu o ponto
    }

    @Test fun codigoPostalComEspacoTambem() = runTest {
        val (r, h) = comCodigosPostais(daFixture)
        assertEquals("1400-038 · Doca do Bom Sucesso", r.pesquisarLugares(" 1400 038 ")!!.first().nome)
        assertEquals("1400-038 · Doca do Bom Sucesso", r.pesquisarLugares("1400038")!!.first().nome)
        assertEquals(1, h.pedidos.count { it.startsWith("https://json.geoapi.pt/cp/1400-038") }) // o segundo já estava guardado
    }

    @Test fun codigoPostalInexistenteDaListaVazia() = runTest {
        val (r, h) = comCodigosPostais({ Resposta(404, """{"erro":"Postal Code not found!"}""") })
        assertEquals(emptyList<Lugar>(), r.pesquisarLugares("9999-999"))
        assertEquals(listOf("https://json.geoapi.pt/cp/9999-999"), h.pedidos) // 404 é "não existe": não se tenta a pesquisa normal
    }

    @Test fun codigoPostalInexistenteNaoFicaGuardado() = runTest {
        // Uma falha ou um 404 não pode ficar preso: da próxima vez pergunta-se outra vez.
        val (r, h) = comCodigosPostais({ Resposta(404, "") })
        r.pesquisarLugares("9999-999")
        r.pesquisarLugares("9999-999")
        assertEquals(2, h.pedidos.count { it.startsWith("https://json.geoapi.pt") })
    }

    @Test fun geoApiEmBaixoUsaAPesquisaNormal() = runTest {
        val rua = """[{"type":"ADDRESS","name":"Doca do Bom Sucesso","lat":38.6935,"lon":-9.2106}]"""
        listOf(503, 429, 500).forEach { codigo ->
            val (r, h) = comCodigosPostais({ Resposta(codigo, "") }, { Resposta(200, rua) })
            assertEquals(codigo.toString(), listOf("Doca do Bom Sucesso"), r.pesquisarLugares("1400-038")!!.map { it.nome })
            assertTrue(h.pedidos.any { it.contains("geocode?text=1400-038") })
        }
    }

    @Test fun geoApiSemRedeENormalSemRedeDaNull() = runTest {
        val (r, _) = comCodigosPostais({ throw java.io.IOException("sem rede") }, { throw java.io.IOException("sem rede") })
        assertNull(r.pesquisarLugares("1400-038"))
    }

    @Test fun codigoPostalSemCoordenadasProcuraARuaNoTransitous() = runTest {
        val semPontos = """{"CP":"1169-001","Concelho":"Lisboa","Localidade":"Lisboa","partes":[{"Artéria":"Rua dos Anjos"}],"pontos":[],"ruas":[]}"""
        val achada = """[{"type":"ADDRESS","name":"Rua dos Anjos","lat":38.7228,"lon":-9.1337,"areas":[{"name":"Arroios","adminLevel":8.0},{"name":"Lisboa","adminLevel":7.0}]}]"""
        val (r, h) = comCodigosPostais({ Resposta(200, semPontos) }, { Resposta(200, achada) })
        val l = r.pesquisarLugares("1169-001")!!.single()
        assertEquals("1169-001 · Rua dos Anjos", l.nome) // o nome é o do código; o ponto é o do Transitous
        assertEquals(38.7228, l.lat, 1e-9)
        assertEquals(-9.1337, l.lon, 1e-9)
        assertEquals("Lisboa", l.zona)
        assertTrue(h.pedidos.any { it.endsWith("geocode?text=Rua+dos+Anjos+Lisboa") })
    }

    @Test fun codigoPostalSemCoordenadasENadaNoTransitousDaListaVazia() = runTest {
        val semPontos = """{"CP":"1169-001","Concelho":"Lisboa","Localidade":"Lisboa","partes":[{"Artéria":"Rua dos Anjos"}],"pontos":[],"ruas":[]}"""
        val (r, h) = comCodigosPostais({ Resposta(200, semPontos) })
        assertEquals(emptyList<Lugar>(), r.pesquisarLugares("1169-001"))
        assertEquals(2, h.pedidos.size) // a GeoAPI e a rua no Transitous, nada mais
    }

    @Test fun apagarDadosDoTelemovel() = runTest {
        repo.guardarLocal("Casa", lugar("Rua A"), "CASA")
        repo.registarRecente(lugar("A"), lugar("B"))
        bd.favoritas().guardar(FavoritaEntidade("CM:060419", "Cais do Sodré", 0, 38.7, -9.14))
        repo.apagarDadosDoTelemovel()
        assertTrue(repo.locais().first().isEmpty())
        assertTrue(repo.recentes().first().isEmpty())
        assertTrue(repo.favoritas().first().isEmpty())
    }
}

/** Abre uma base de dados criada com o esquema v1 exportado e confirma que a v2 a migra sem perder favoritas. */
@RunWith(RobolectricTestRunner::class)
class MigracaoTest {
    @Test fun favoritaSobreviveAMigracao() {
        val contexto = RuntimeEnvironment.getApplication()
        val ficheiro = contexto.getDatabasePath("migracao.db").apply { parentFile?.mkdirs(); delete() }
        val esquema = Json.parseToJsonElement(File("schemas/pt.pixellusitano.paragemcerta.dados.BaseDados/1.json").readText())
            .jsonObject["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(ficheiro, null).use { db ->
            esquema["entities"]!!.jsonArray.forEach { e ->
                db.execSQL(e.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", e.jsonObject["tableName"]!!.jsonPrimitive.content))
            }
            esquema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO favoritas (chave, nome, ordem, lat, lon) VALUES ('CM:060419', 'Cais do Sodré', 0, 38.7, -9.14)")
            db.version = 1
        }
        val bd = Room.databaseBuilder(contexto, BaseDados::class.java, ficheiro.absolutePath).allowMainThreadQueries().build()
        val favoritas = kotlinx.coroutines.runBlocking { bd.favoritas().todas().first() }
        assertEquals(listOf("CM:060419"), favoritas.map { it.chave })
        bd.close()
    }
}
