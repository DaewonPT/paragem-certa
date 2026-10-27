package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.core.regras.intervaloNormal
import pt.pixellusitano.paragemcerta.core.regras.pausaDepoisDeFalha
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant

data class EstadoChegadas(
    val chegadas: List<Chegada>,
    val atualizadoEm: Instant?,
    val semLigacao: Boolean,
    val tempoRealIndisponivel: Boolean,
)

@Serializable
private class ChegadaGuardada(
    val fonte: Fonte, val linhaId: String, val linhaNome: String, val destino: String,
    val programada: Long, val prevista: Long?, val idViagem: String?, val idVeiculo: String?,
    val modo: Modo, val cor: String?, val acessivel: Boolean?,
)

private val JSON = Json { ignoreUnknownKeys = true }

private fun Chegada.paraGuardar() = ChegadaGuardada(
    fonte, linhaId, linhaNome, destino, programada.epochSecond, prevista?.epochSecond, idViagem, idVeiculo, modo, cor, acessivel,
)

private fun ChegadaGuardada.paraChegada() = Chegada(
    fonte, linhaId, linhaNome, destino, Instant.ofEpochSecond(programada), prevista?.let(Instant::ofEpochSecond),
    idViagem, idVeiculo, modo, cor, acessivel,
)

private fun ParagemEntidade.paraParagem() =
    Paragem(RefParagem(Fonte.valueOf(fonte), id), nome, lat, lon, acessivel)

private fun Paragem.paraEntidade() = ParagemEntidade(
    chave = ref.chave, fonte = ref.fonte.name, id = ref.id, nome = nome,
    nomeNormalizado = normalizar(nome), lat = lat, lon = lon, acessivel = acessivel,
)

private fun LinhaEntidade.paraLinha() = Linha(Fonte.CM, id, nomeCurto, nomeLongo, cor, corTexto, Modo.AUTOCARRO)

class Repositorio(
    private val bd: BaseDados,
    private val cm: CarrisMetropolitana,
    private val transitous: Transitous,
    private val relogio: () -> Instant = Instant::now,
) {
    /** Consulta ao ritmo da fonte enquanto alguém recolhe o fluxo; sem rede ou com erro, emite o último resultado bom. */
    fun chegadas(ref: RefParagem): Flow<EstadoChegadas> = flow {
        var tentativa = 0
        while (true) {
            val agora = relogio()
            val r = when (ref.fonte) {
                Fonte.CM -> cm.chegadas(ref.id, agora)
                Fonte.TRANSITOUS -> transitous.chegadas(ref.id, agora)
            }
            when (r) {
                is Resultado.Ok -> {
                    tentativa = 0
                    val json = JSON.encodeToString(r.valor.map { it.paraGuardar() })
                    bd.chegadas().guardar(ChegadasGuardadasEntidade(ref.chave, json, agora.epochSecond))
                    emit(EstadoChegadas(r.valor, agora, semLigacao = false, tempoRealIndisponivel = false))
                    delay(intervaloNormal(ref.fonte).toMillis())
                }
                is Resultado.Falha -> {
                    val guardado = bd.chegadas().porChave(ref.chave)
                    val anteriores = guardado?.let { g ->
                        JSON.decodeFromString<List<ChegadaGuardada>>(g.json).map { it.paraChegada() }.filter { aindaPorChegar(it, agora) }
                    }.orEmpty()
                    emit(
                        EstadoChegadas(
                            anteriores, guardado?.guardadoEm?.let(Instant::ofEpochSecond),
                            semLigacao = r.codigo == null, tempoRealIndisponivel = r.codigo != null,
                        ),
                    )
                    delay(pausaDepoisDeFalha(ref.fonte, tentativa++).toMillis())
                }
            }
        }
    }

    /** Veículos da Carris Metropolitana a cada 15 s; numa falha mantém a última lista e espera mais. */
    fun veiculos(): Flow<List<Veiculo>> = flow {
        var tentativa = 0
        while (true) {
            when (val r = cm.veiculos()) {
                is Resultado.Ok -> {
                    tentativa = 0
                    emit(r.valor)
                    delay(INTERVALO_VEICULOS.toMillis())
                }
                is Resultado.Falha -> delay(pausaDepoisDeFalha(Fonte.CM, tentativa++).toMillis())
            }
        }
    }

    private var alertasEmCache: Pair<Instant, List<Alerta>>? = null

    /** Alertas ativos da paragem ou das linhas que lá passam (Carris Metropolitana; o Transitous ainda não os tem). */
    suspend fun alertasDaParagem(ref: RefParagem, linhas: Set<String>): List<Alerta> {
        if (ref.fonte != Fonte.CM) return emptyList()
        val agora = relogio()
        val cache = alertasEmCache?.takeIf { Duration.between(it.first, agora) < VALIDADE_ALERTAS }
        val todos = cache?.second ?: when (val r = cm.alertas(agora)) {
            is Resultado.Ok -> r.valor.also { alertasEmCache = agora to it }
            is Resultado.Falha -> return emptyList()
        }
        return todos.filter { ref.id in it.paragemIds || it.linhaIds.any(linhas::contains) }
    }

    fun favoritas(): Flow<List<Favorita>> = bd.favoritas().todas().map { lista ->
        lista.mapNotNull { f -> RefParagem.daChave(f.chave)?.let { Favorita(it, f.nome, f.ordem, f.lat, f.lon) } }
    }

    suspend fun eFavorita(ref: RefParagem): Boolean = bd.favoritas().porChave(ref.chave) != null

    suspend fun alternarFavorita(p: Paragem) {
        val dao = bd.favoritas()
        if (dao.porChave(p.ref.chave) != null) dao.apagar(p.ref.chave)
        else dao.guardar(FavoritaEntidade(p.ref.chave, p.nome, dao.proximaOrdem(), p.lat, p.lon))
    }

    suspend fun reordenarFavoritas(ordem: List<Favorita>) =
        bd.favoritas().guardarTodas(ordem.mapIndexed { i, f -> FavoritaEntidade(f.ref.chave, f.nome, i, f.lat, f.lon) })

    suspend fun paragem(ref: RefParagem): Paragem? = bd.paragens().porChave(ref.chave)?.paraParagem()

    /** Carris Metropolitana da base local mais as do Transitous pela rede (que ficam guardadas para o ecrã da paragem). */
    suspend fun paragensNaZona(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Paragem> {
        val locais = bd.paragens().cmNaZona(minLat, minLon, maxLat, maxLon).map { it.paraParagem() }
        val outras = (transitous.paragensNaZona(minLat, minLon, maxLat, maxLon) as? Resultado.Ok)?.valor.orEmpty()
        if (outras.isNotEmpty()) bd.paragens().guardar(outras.map { it.paraEntidade() })
        return locais + outras
    }

    suspend fun pesquisar(texto: String): List<Paragem> {
        val locais = bd.paragens().cmPorNome(normalizar(texto)).map { it.paraParagem() }
        val outras = (transitous.pesquisar(texto) as? Resultado.Ok)?.valor.orEmpty()
        if (outras.isNotEmpty()) bd.paragens().guardar(outras.map { it.paraEntidade() })
        return locais + outras
    }

    suspend fun linhasCmComecadasPor(prefixo: String): List<Linha> = bd.linhas().comecadasPor(prefixo).map { it.paraLinha() }

    suspend fun linhaCm(id: String): Linha? = bd.linhas().porId(id)?.paraLinha()

    suspend fun linhasCm(): Map<String, Linha> = bd.linhas().todas().associate { it.id to it.paraLinha() }

    suspend fun coresDasLinhasCm(): Map<String, String> =
        bd.linhas().todas().mapNotNull { l -> l.cor?.let { l.id to it } }.toMap()

    suspend fun redeEmFalta(): Boolean = bd.paragens().contarCm() == 0

    /** Paragens e linhas da Carris Metropolitana (uma vez por dia). Devolve falso se alguma falhar. */
    suspend fun atualizarRede(): Boolean {
        val paragens = cm.paragens() as? Resultado.Ok ?: return false
        val linhasJson = cm.linhasJson() as? Resultado.Ok ?: return false
        val padroes = CmParser.padroesDasLinhas(linhasJson.valor)
        val linhas = CmParser.linhas(linhasJson.valor).map {
            LinhaEntidade(it.id, it.nomeCurto, it.nomeLongo, it.cor, it.corTexto, padroes[it.id].orEmpty().joinToString(","))
        }
        bd.paragens().substituirFonte(Fonte.CM.name, paragens.valor.map { it.paraEntidade() })
        bd.linhas().substituir(linhas)
        return true
    }

    private companion object {
        val INTERVALO_VEICULOS: Duration = Duration.ofSeconds(15)
        val VALIDADE_ALERTAS: Duration = Duration.ofMinutes(5)
    }
}
