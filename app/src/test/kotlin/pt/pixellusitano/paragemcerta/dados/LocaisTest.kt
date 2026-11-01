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
    private val repo = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), relogio = { agora })
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
        bd, CarrisMetropolitana(http), Transitous(HttpPorUrl("geocode" to listOf({ Resposta(503, "") }))), relogio = { agora },
    )

    @Test fun pesquisaSemRedeSemLocaisDevolveNull() = runTest {
        assertNull(semRede.pesquisarLugares("trab"))
    }

    @Test fun pesquisaSemRedeMostraLocais() = runTest {
        semRede.guardarLocal("Trabalho", lugar("Av. da República"), "TRABALHO")
        assertEquals(listOf("Trabalho"), semRede.pesquisarLugares("trab")!!.map { it.nome })
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
