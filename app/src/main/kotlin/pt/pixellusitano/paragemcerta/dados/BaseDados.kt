package pt.pixellusitano.paragemcerta.dados

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.util.UUID

/** Paragens da Carris Metropolitana (rede inteira, atualizada uma vez por dia) e do Transitous já vistas.
 *  linhas = os line_ids da CM separados por vírgulas (vazio nas do Transitous). */
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
    @ColumnInfo(defaultValue = "''") val linhas: String = "",
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

/** pendente = mudou no telemóvel e ainda não foi para a conta (toda a escrita da app marca; o que vem da conta não). */
@Entity(tableName = "favoritas")
data class FavoritaEntidade(
    @PrimaryKey val chave: String,
    val nome: String,
    val ordem: Int,
    val lat: Double,
    val lon: Double,
    @ColumnInfo(defaultValue = "0") val pendente: Boolean = true,
)

/**
 * Uma linha favorita num sentido, com a paragem habitual. chave = "LINHA|<chave da linha>|<sentido>|<chave da paragem>" (a da
 * conta); linha e paragem são as chaves de [pt.pixellusitano.paragemcerta.core.linhas.RefLinha] e de RefParagem; lat e lon, as da paragem.
 */
@Entity(tableName = "linhas_favoritas")
data class LinhaFavoritaEntidade(
    @PrimaryKey val chave: String,
    val linha: String,
    val sentido: Int,
    val paragem: String,
    val nome: String,
    val destino: String,
    val ordem: Int,
    val lat: Double,
    val lon: Double,
    val pendente: Boolean = true,
)

/** Últimas chegadas boas de cada paragem, para mostrar sem rede. */
@Entity(tableName = "chegadas_guardadas")
data class ChegadasGuardadasEntidade(
    @PrimaryKey val chave: String,
    val json: String,
    val guardadoEm: Long,
)

/** Sítios guardados pelo utilizador; tipo = CASA, TRABALHO ou OUTRO. uuid = a chave do sítio na conta (o id só vale neste telemóvel). */
@Entity(tableName = "locais")
data class LocalEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val tipo: String,
    val ordem: Int,
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val pendente: Boolean = true,
)

/** Favoritos e sítios apagados com a sessão iniciada, por apagar também na conta (chave = a da paragem ou o uuid do sítio). */
@Entity(tableName = "apagados")
data class ApagadoEntidade(
    @PrimaryKey val chave: String,
    val tipo: String,
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

    @Query("SELECT * FROM paragens WHERE fonte = 'CM' AND linhas != '' AND lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon LIMIT 400")
    suspend fun cmNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<ParagemEntidade>

    @Query("SELECT * FROM paragens WHERE fonte = 'CM' AND nomeNormalizado LIKE '%' || :texto || '%' LIMIT 30")
    suspend fun cmPorNome(texto: String): List<ParagemEntidade>

    @Query("SELECT * FROM paragens WHERE chave IN (:chaves)")
    suspend fun porChaves(chaves: List<String>): List<ParagemEntidade>

    @Query("SELECT * FROM paragens WHERE nomeNormalizado IN (:nomes)")
    suspend fun porNomes(nomes: List<String>): List<ParagemEntidade>

    @Query("SELECT COUNT(*) FROM paragens WHERE fonte = 'CM'")
    suspend fun contarCm(): Int

    // Conta as que têm linhas, não as que não têm: há paragens da CM sem linhas nenhumas, que nunca deixariam de faltar.
    @Query("SELECT COUNT(*) FROM paragens WHERE fonte = 'CM' AND linhas != ''")
    suspend fun contarCmComLinhas(): Int
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

    @Query("SELECT * FROM linhas")
    fun fluxo(): Flow<List<LinhaEntidade>>
}

@Dao
interface LinhasFavoritasDao {
    @Query("SELECT * FROM linhas_favoritas ORDER BY ordem")
    fun todas(): Flow<List<LinhaFavoritaEntidade>>

    @Query("SELECT * FROM linhas_favoritas WHERE linha = :linha AND sentido = :sentido")
    suspend fun daLinha(linha: String, sentido: Int): List<LinhaFavoritaEntidade>

    @Query("SELECT COALESCE(MAX(ordem), -1) + 1 FROM linhas_favoritas")
    suspend fun proximaOrdem(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(f: LinhaFavoritaEntidade)

    @Query("DELETE FROM linhas_favoritas WHERE chave = :chave")
    suspend fun apagar(chave: String)

    @Query("UPDATE linhas_favoritas SET ordem = :ordem, pendente = 1 WHERE chave = :chave")
    suspend fun reordenar(chave: String, ordem: Int)

    @Query("SELECT * FROM linhas_favoritas WHERE pendente")
    suspend fun pendentes(): List<LinhaFavoritaEntidade>

    @Query("UPDATE linhas_favoritas SET pendente = 0 WHERE chave = :chave")
    suspend fun enviada(chave: String)

    @Query("UPDATE linhas_favoritas SET pendente = :pendente")
    suspend fun marcarTodas(pendente: Boolean)
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

    @Query("SELECT * FROM favoritas WHERE pendente")
    suspend fun pendentes(): List<FavoritaEntidade>

    @Query("UPDATE favoritas SET pendente = 0 WHERE chave = :chave")
    suspend fun enviada(chave: String)

    @Query("UPDATE favoritas SET pendente = :pendente")
    suspend fun marcarTodas(pendente: Boolean)
}

@Dao
interface ApagadosDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(a: ApagadoEntidade)

    @Query("SELECT * FROM apagados")
    suspend fun todos(): List<ApagadoEntidade>

    @Query("DELETE FROM apagados WHERE chave = :chave")
    suspend fun apagar(chave: String)

    @Query("DELETE FROM apagados")
    suspend fun apagarTodos()

    /** Quantas alterações há por enviar à conta; volta a emitir a cada escrita nestas tabelas. Uma linha favorita com a chave
     *  maior do que o servidor aceita (250) nunca vai, por isso não conta. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM favoritas WHERE pendente) + (SELECT COUNT(*) FROM locais WHERE pendente) + " +
            "(SELECT COUNT(*) FROM linhas_favoritas WHERE pendente AND length(chave) <= 250) + (SELECT COUNT(*) FROM apagados)",
    )
    fun porEnviar(): Flow<Int>
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

    /** Com o id de um sítio que já existe, substitui-o (o que vem da conta). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(l: LocalEntidade)

    @Query("SELECT * FROM locais WHERE id = :id")
    suspend fun porId(id: Long): LocalEntidade?

    @Query("SELECT * FROM locais WHERE uuid = :uuid")
    suspend fun porUuid(uuid: String): LocalEntidade?

    @Query("DELETE FROM locais WHERE uuid = :uuid")
    suspend fun apagarUuid(uuid: String)

    @Query("SELECT * FROM locais WHERE pendente")
    suspend fun pendentes(): List<LocalEntidade>

    @Query("UPDATE locais SET pendente = 0 WHERE uuid = :uuid")
    suspend fun enviado(uuid: String)

    @Query("UPDATE locais SET pendente = :pendente")
    suspend fun marcarTodos(pendente: Boolean)

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

    @Query("UPDATE locais SET nome = :nome, pendente = 1 WHERE id = :id")
    suspend fun renomear(id: Long, nome: String)

    @Query("UPDATE locais SET ordem = :ordem, pendente = 1 WHERE id = :id")
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

    @Query("DELETE FROM linhas_favoritas")
    suspend fun apagarLinhasFavoritas()

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
        apagarLinhasFavoritas()
        apagarLocais()
        apagarRecentes()
        apagarChegadas()
    }
}

@Database(
    entities = [
        ParagemEntidade::class, LinhaEntidade::class, FavoritaEntidade::class, ChegadasGuardadasEntidade::class,
        LocalEntidade::class, RecenteEntidade::class, ApagadoEntidade::class, LinhaFavoritaEntidade::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BaseDados : RoomDatabase() {
    abstract fun paragens(): ParagensDao
    abstract fun linhas(): LinhasDao
    abstract fun favoritas(): FavoritasDao
    abstract fun linhasFavoritas(): LinhasFavoritasDao
    abstract fun chegadas(): ChegadasDao
    abstract fun locais(): LocaisDao
    abstract fun apagados(): ApagadosDao
}

/** 0.4: marcas de "por enviar à conta" e um id estável para cada sítio. O que já cá estava fica sem marca: só vai para uma conta se
 *  a pessoa o juntar ao entrar. */
val MIGRACAO_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favoritas ADD COLUMN pendente INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE locais ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE locais ADD COLUMN pendente INTEGER NOT NULL DEFAULT 0")
        // randomblob corre uma vez por linha: cada sítio fica com o seu.
        db.execSQL("UPDATE locais SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS `apagados` (`chave` TEXT NOT NULL, `tipo` TEXT NOT NULL, PRIMARY KEY(`chave`))")
    }
}

/** 0.4: as linhas favoritas e as linhas de cada paragem da CM. As paragens ficam sem linhas até a rede vir outra vez,
 *  o que acontece logo nessa abertura (o [Repositorio.redeEmFalta] dá verdadeiro). O SQL é o do 4.json, tal e qual. */
val MIGRACAO_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paragens ADD COLUMN linhas TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `linhas_favoritas` (`chave` TEXT NOT NULL, `linha` TEXT NOT NULL, `sentido` INTEGER NOT NULL, " +
                "`paragem` TEXT NOT NULL, `nome` TEXT NOT NULL, `destino` TEXT NOT NULL, `ordem` INTEGER NOT NULL, `lat` REAL NOT NULL, " +
                "`lon` REAL NOT NULL, `pendente` INTEGER NOT NULL, PRIMARY KEY(`chave`))",
        )
    }
}

/** "Cais do Sodré" → "cais do sodre": a pesquisa ignora acentos e maiúsculas. */
fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
