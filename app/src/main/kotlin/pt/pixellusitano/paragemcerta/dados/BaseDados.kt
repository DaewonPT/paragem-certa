package pt.pixellusitano.paragemcerta.dados

import androidx.room.AutoMigration
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

/** Sítios guardados pelo utilizador; tipo = CASA, TRABALHO ou OUTRO. */
@Entity(tableName = "locais")
data class LocalEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val tipo: String,
    val ordem: Int,
)

@Entity(tableName = "recentes")
data class RecenteEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deNome: String,
    val deLat: Double,
    val deLon: Double,
    val paraNome: String,
    val paraLat: Double,
    val paraLon: Double,
    val em: Long,
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

    @Query("SELECT * FROM paragens WHERE chave IN (:chaves)")
    suspend fun porChaves(chaves: List<String>): List<ParagemEntidade>

    @Query("SELECT * FROM paragens WHERE nomeNormalizado IN (:nomes)")
    suspend fun porNomes(nomes: List<String>): List<ParagemEntidade>

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

@Dao
interface LocaisDao {
    @Query("SELECT * FROM locais ORDER BY ordem")
    fun todos(): Flow<List<LocalEntidade>>

    @Query("SELECT * FROM locais ORDER BY ordem")
    suspend fun lista(): List<LocalEntidade>

    @Insert
    suspend fun inserir(l: LocalEntidade)

    @Query("DELETE FROM locais WHERE tipo = :tipo")
    suspend fun apagarTipo(tipo: String)

    @Query("SELECT COALESCE(MAX(ordem), -1) + 1 FROM locais")
    suspend fun proximaOrdem(): Int

    /** Casa e Trabalho só há um de cada: o novo substitui o anterior. */
    @Transaction
    suspend fun guardar(nome: String, lat: Double, lon: Double, tipo: String) {
        if (tipo != "OUTRO") apagarTipo(tipo)
        inserir(LocalEntidade(nome = nome, lat = lat, lon = lon, tipo = tipo, ordem = proximaOrdem()))
    }

    @Query("UPDATE locais SET nome = :nome WHERE id = :id")
    suspend fun renomear(id: Long, nome: String)

    @Query("UPDATE locais SET ordem = :ordem WHERE id = :id")
    suspend fun definirOrdem(id: Long, ordem: Int)

    @Transaction
    suspend fun reordenar(ids: List<Long>) = ids.forEachIndexed { i, id -> definirOrdem(id, i) }

    @Query("DELETE FROM locais WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("SELECT * FROM recentes ORDER BY em DESC, id DESC LIMIT 10")
    fun recentes(): Flow<List<RecenteEntidade>>

    @Insert
    suspend fun inserirRecente(r: RecenteEntidade)

    @Query(
        "DELETE FROM recentes WHERE deNome = :deNome AND deLat = :deLat AND deLon = :deLon " +
            "AND paraNome = :paraNome AND paraLat = :paraLat AND paraLon = :paraLon",
    )
    suspend fun apagarPar(deNome: String, deLat: Double, deLon: Double, paraNome: String, paraLat: Double, paraLon: Double)

    @Query("DELETE FROM recentes WHERE id NOT IN (SELECT id FROM recentes ORDER BY em DESC, id DESC LIMIT 10)")
    suspend fun cortarRecentes()

    /** O mesmo par de origem e destino passa para o topo em vez de se repetir. */
    @Transaction
    suspend fun registarRecente(r: RecenteEntidade) {
        apagarPar(r.deNome, r.deLat, r.deLon, r.paraNome, r.paraLat, r.paraLon)
        inserirRecente(r)
        cortarRecentes()
    }

    @Query("DELETE FROM favoritas")
    suspend fun apagarFavoritas()

    @Query("DELETE FROM locais")
    suspend fun apagarLocais()

    @Query("DELETE FROM recentes")
    suspend fun apagarRecentes()

    @Query("DELETE FROM chegadas_guardadas")
    suspend fun apagarChegadas()

    /** Tudo o que é do utilizador; a rede (paragens e linhas) fica, para não ter de a descarregar outra vez. */
    @Transaction
    suspend fun apagarDadosPessoais() {
        apagarFavoritas()
        apagarLocais()
        apagarRecentes()
        apagarChegadas()
    }
}

@Database(
    entities = [
        ParagemEntidade::class, LinhaEntidade::class, FavoritaEntidade::class, ChegadasGuardadasEntidade::class,
        LocalEntidade::class, RecenteEntidade::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BaseDados : RoomDatabase() {
    abstract fun paragens(): ParagensDao
    abstract fun linhas(): LinhasDao
    abstract fun favoritas(): FavoritasDao
    abstract fun chegadas(): ChegadasDao
    abstract fun locais(): LocaisDao
}

/** "Cais do Sodré" → "cais do sodre": a pesquisa ignora acentos e maiúsculas. */
fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
