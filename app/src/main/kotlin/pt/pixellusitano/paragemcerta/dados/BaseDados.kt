package pt.pixellusitano.paragemcerta.dados

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

/** Paragens da Carris Metropolitana (rede inteira, atualizada uma vez por dia) e do Transitous já vistas. */
@Entity(tableName = "paragens")
data class ParagemEntidade(
    @PrimaryKey val chave: String,
    val fonte: String,
    val id: String,
    val nome: String,
    val nomeNormalizado: String,
    val lat: Double,
    val lon: Double,
    val acessivel: Boolean?,
)

/** Linhas da Carris Metropolitana; padroes = ids dos percursos separados por vírgulas. */
@Entity(tableName = "linhas")
data class LinhaEntidade(
    @PrimaryKey val id: String,
    val nomeCurto: String,
    val nomeLongo: String,
    val cor: String?,
    val corTexto: String?,
    val padroes: String,
)

@Entity(tableName = "favoritas")
data class FavoritaEntidade(
    @PrimaryKey val chave: String,
    val nome: String,
    val ordem: Int,
    val lat: Double,
    val lon: Double,
)

/** Últimas chegadas boas de cada paragem, para mostrar sem rede. */
@Entity(tableName = "chegadas_guardadas")
data class ChegadasGuardadasEntidade(
    @PrimaryKey val chave: String,
    val json: String,
    val guardadoEm: Long,
)

@Dao
interface ParagensDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(paragens: List<ParagemEntidade>)

    @Query("DELETE FROM paragens WHERE fonte = :fonte")
    suspend fun apagarFonte(fonte: String)

    @Transaction
    suspend fun substituirFonte(fonte: String, paragens: List<ParagemEntidade>) {
        apagarFonte(fonte)
        guardar(paragens)
    }

    @Query("SELECT * FROM paragens WHERE chave = :chave")
    suspend fun porChave(chave: String): ParagemEntidade?

    @Query("SELECT * FROM paragens WHERE fonte = 'CM' AND lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon LIMIT 400")
    suspend fun cmNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<ParagemEntidade>

    @Query("SELECT * FROM paragens WHERE fonte = 'CM' AND nomeNormalizado LIKE '%' || :texto || '%' LIMIT 30")
    suspend fun cmPorNome(texto: String): List<ParagemEntidade>

    @Query("SELECT COUNT(*) FROM paragens WHERE fonte = 'CM'")
    suspend fun contarCm(): Int
}

@Dao
interface LinhasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(linhas: List<LinhaEntidade>)

    @Query("DELETE FROM linhas")
    suspend fun apagarTodas()

    @Transaction
    suspend fun substituir(linhas: List<LinhaEntidade>) {
        apagarTodas()
        guardar(linhas)
    }

    @Query("SELECT * FROM linhas WHERE id = :id")
    suspend fun porId(id: String): LinhaEntidade?

    @Query("SELECT * FROM linhas WHERE nomeCurto LIKE :prefixo || '%' ORDER BY nomeCurto LIMIT 20")
    suspend fun comecadasPor(prefixo: String): List<LinhaEntidade>

    @Query("SELECT * FROM linhas")
    suspend fun todas(): List<LinhaEntidade>
}

@Dao
interface FavoritasDao {
    @Query("SELECT * FROM favoritas ORDER BY ordem")
    fun todas(): Flow<List<FavoritaEntidade>>

    @Query("SELECT * FROM favoritas WHERE chave = :chave")
    suspend fun porChave(chave: String): FavoritaEntidade?

    @Query("SELECT COALESCE(MAX(ordem), -1) + 1 FROM favoritas")
    suspend fun proximaOrdem(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(f: FavoritaEntidade)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodas(f: List<FavoritaEntidade>)

    @Query("DELETE FROM favoritas WHERE chave = :chave")
    suspend fun apagar(chave: String)
}

@Dao
interface ChegadasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(c: ChegadasGuardadasEntidade)

    @Query("SELECT * FROM chegadas_guardadas WHERE chave = :chave")
    suspend fun porChave(chave: String): ChegadasGuardadasEntidade?
}

@Database(
    entities = [ParagemEntidade::class, LinhaEntidade::class, FavoritaEntidade::class, ChegadasGuardadasEntidade::class],
    version = 1,
)
abstract class BaseDados : RoomDatabase() {
    abstract fun paragens(): ParagensDao
    abstract fun linhas(): LinhasDao
    abstract fun favoritas(): FavoritasDao
    abstract fun chegadas(): ChegadasDao
}

/** "Cais do Sodré" → "cais do sodre": a pesquisa ignora acentos e maiúsculas. */
fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
