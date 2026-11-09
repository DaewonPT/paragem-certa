package pt.pixellusitano.paragemcerta.dados

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.aovivo.paragensEmFalta
import pt.pixellusitano.paragemcerta.core.aovivo.veiculoDaCarris
import pt.pixellusitano.paragemcerta.core.aovivo.viagemCarrisDeTransitous
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.CmParser
import pt.pixellusitano.paragemcerta.core.fontes.cm.cmParagemDeTransitous
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.CodigoPostal
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.ehCodigoPostal
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.normalizarCodigoPostal
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.paraLugar
import pt.pixellusitano.paragemcerta.core.fontes.servidor.AoVivoCarris
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FavoritoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Interruptores
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.fontes.transitous.chavesDosNomes
import pt.pixellusitano.paragemcerta.core.fontes.transitous.comNomesAcentuados
import pt.pixellusitano.paragemcerta.core.linhas.Catalogo
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.regras.intervaloNormal
import pt.pixellusitano.paragemcerta.core.regras.nomeBase
import pt.pixellusitano.paragemcerta.core.regras.pausaDepoisDeFalha
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rotas.proximasPartidas as proximasDaLinha
import java.time.Duration
import java.time.Instant

data class EstadoChegadas(
    val chegadas: List<Chegada>,
    val atualizadoEm: Instant?,
    val semLigacao: Boolean,
    val tempoRealIndisponivel: Boolean,
)

/** O autocarro ao vivo de uma chegada da Carris: [paragensEmFalta] até à paragem (0 = a chegar; nunca negativo: se já passou não há [AoVivo]). */
data class AoVivo(val idViagem: String, val paragensEmFalta: Int)

data class LocalGuardado(val id: Long, val nome: String, val lugar: Lugar, val tipo: String, val ordem: Int)

data class ViagemRecente(val de: Lugar, val para: Lugar, val em: Instant)

/** Uma linha favorita num sentido, com a paragem habitual; [nome] e [destino] são os de quando se guardou (o catálogo pode não ter chegado). */
data class LinhaFavorita(val ref: RefLinha, val sentido: Int, val paragem: RefParagem, val nome: String, val destino: String, val ordem: Int)

private fun chaveLinhaFavorita(ref: RefLinha, sentido: Int, paragem: RefParagem) = "LINHA|${ref.chave}|$sentido|${paragem.chave}"

/** "LINHA|<linha>|<sentido>|<paragem>" pelo fim: o sentido e a paragem não têm "|", o nome de uma linha pode ter. */
private fun daChaveLinhaFavorita(chave: String): Triple<RefLinha, Int, RefParagem>? {
    val resto = chave.removePrefix("LINHA|").takeIf { it != chave } ?: return null
    val semParagem = resto.substringBeforeLast('|', "")
    val paragem = RefParagem.daChave(resto.substringAfterLast('|')) ?: return null
    val sentido = semParagem.substringAfterLast('|').toIntOrNull() ?: return null
    val linha = RefLinha.daChave(semParagem.substringBeforeLast('|', "")) ?: return null
    return Triple(linha, sentido, paragem)
}

private fun LinhaFavoritaEntidade.paraLinhaFavorita(): LinhaFavorita? =
    LinhaFavorita(RefLinha.daChave(linha) ?: return null, sentido, RefParagem.daChave(paragem) ?: return null, nome, destino, ordem)

// Na conta só há um nome: o destino vai nele, depois de um "\n" (que nenhum nome de linha nem destino tem).
private fun LinhaFavoritaEntidade.paraRemoto() = FavoritoRemoto(chave, TIPO_LINHA, "$nome\n$destino", lat, lon, ordem, apagado = false)

private const val TIPO_LINHA = "LINHA"

/** O servidor recusa o pedido inteiro com uma chave de linha maior (worker/src/favoritos.ts): essas ficam só no telemóvel. */
private const val MAX_CHAVE_LINHA = 250

@Serializable
private class ChegadaGuardada(
    val fonte: Fonte, val linhaId: String, val linhaNome: String, val destino: String,
    val programada: Long, val prevista: Long?, val idViagem: String?, val idVeiculo: String?,
    val modo: Modo, val cor: String?, val acessivel: Boolean?,
    // Com omissão: o que a versão anterior guardou não os tem.
    val sentido: Int? = null, val padrao: String? = null,
)

private val JSON = Json { ignoreUnknownKeys = true }

private fun Chegada.paraGuardar() = ChegadaGuardada(
    fonte, linhaId, linhaNome, destino, programada.epochSecond, prevista?.epochSecond, idViagem, idVeiculo, modo, cor, acessivel,
    sentido, padrao,
)

private fun ChegadaGuardada.paraChegada() = Chegada(
    fonte, linhaId, linhaNome, destino, Instant.ofEpochSecond(programada), prevista?.let(Instant::ofEpochSecond),
    idViagem, idVeiculo, modo, cor, acessivel, sentido, padrao,
)

private fun ParagemEntidade.paraParagem() =
    Paragem(RefParagem(Fonte.valueOf(fonte), id), nome, lat, lon, acessivel)

private fun Paragem.paraEntidade() = ParagemEntidade(
    chave = ref.chave, fonte = ref.fonte.name, id = ref.id, nome = nome,
    nomeNormalizado = normalizar(nome), lat = lat, lon = lon, acessivel = acessivel,
)

private fun LocalEntidade.lugar() = Lugar(nome, lat, lon, TipoLugar.LOCAL)

private fun LinhaEntidade.paraLinha() = Linha(Fonte.CM, id, nomeCurto, nomeLongo, cor, corTexto, Modo.AUTOCARRO)

class Repositorio(
    private val bd: BaseDados,
    private val cm: CarrisMetropolitana,
    private val transitous: Transitous,
    private val geoApi: GeoApi,
    private val relogio: () -> Instant = Instant::now,
    // As leituras do servidor (Carris ao vivo) e os interruptores do config.json; com omissão, para os testes que não os usam.
    private val servidor: ServidorParagemCerta? = null,
    private val interruptores: () -> Interruptores = { Interruptores() },
    // O catálogo de linhas (se já chegou): dá o nome da linha aos autocarros da Carris ao vivo.
    private val catalogo: () -> Catalogo? = { null },
) {
    /** Consulta ao ritmo da fonte enquanto alguém recolhe o fluxo; sem rede ou com erro, emite o último resultado bom. */
    fun chegadas(ref: RefParagem): Flow<EstadoChegadas> = flow {
        // Mostra logo o que está guardado, para não ficar em branco enquanto a rede responde (até 30 s com rede fraca).
        chegadasGuardadas(ref, relogio())?.takeIf { it.first.isNotEmpty() }?.let { (lista, em) ->
            emit(EstadoChegadas(lista, em, semLigacao = false, tempoRealIndisponivel = false))
        }
        var tentativa = 0
        while (true) {
            val agora = relogio()
            when (val r = atualizarGuardadas(ref, agora)) {
                is Resultado.Ok -> {
                    tentativa = 0
                    emit(EstadoChegadas(r.valor, agora, semLigacao = false, tempoRealIndisponivel = false))
                    delay(intervaloNormal(ref.fonte).toMillis())
                }
                is Resultado.Falha -> {
                    val (anteriores, em) = chegadasGuardadas(ref, agora) ?: (emptyList<Chegada>() to null)
                    emit(EstadoChegadas(anteriores, em, semLigacao = r.codigo == null, tempoRealIndisponivel = r.codigo != null))
                    delay(pausaDepoisDeFalha(ref.fonte, tentativa++).toMillis())
                }
            }
        }
    }

    /** Uma só consulta à fonte da paragem, sem repetir nem guardar nada (o aviso de chegada tem o seu próprio ritmo). */
    suspend fun chegadasAgora(ref: RefParagem, agora: Instant = relogio()): Resultado<List<Chegada>> = when (ref.fonte) {
        Fonte.CM -> cm.chegadas(ref.id, agora)
        Fonte.TRANSITOUS -> transitous.chegadas(ref.id, agora)
    }

    /** Uma consulta à fonte; se veio, fica guardada para quando não houver rede (o ecrã da paragem e os widgets leem as mesmas). */
    suspend fun atualizarGuardadas(ref: RefParagem, agora: Instant = relogio()): Resultado<List<Chegada>> =
        chegadasAgora(ref, agora).also { r ->
            if (r is Resultado.Ok) {
                val json = JSON.encodeToString(r.valor.map { it.paraGuardar() })
                bd.chegadas().guardar(ChegadasGuardadasEntidade(ref.chave, json, agora.epochSecond))
            }
        }

    /** As que ainda vêm e a hora em que se guardaram; nada guardado, ou o que se guardou não se lê: null. */
    suspend fun chegadasGuardadas(ref: RefParagem, agora: Instant): Pair<List<Chegada>, Instant>? {
        val g = bd.chegadas().porChave(ref.chave) ?: return null
        val lista = runCatching { JSON.decodeFromString<List<ChegadaGuardada>>(g.json).map { it.paraChegada() } }.getOrNull() ?: return null
        return lista.filter { aindaPorChegar(it, agora) } to Instant.ofEpochSecond(g.guardadoEm)
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

    /**
     * As posições da Carris ao vivo (do servidor, que guarda o GTFS-RT 30 s) de 30 em 30 s. Sem servidor ou com o `carrisAoVivo`
     * desligado dá null sem pedir, e volta a ver o interruptor daí a pouco (o config.json pode chegar depois de o ecrã abrir); numa
     * falha dá null e espera como a CM.
     */
    private fun leiturasCarris(): Flow<AoVivoCarris?> = flow {
        var tentativa = 0
        while (true) {
            val doServidor = servidor
            if (doServidor == null || !interruptores().carrisAoVivo) {
                emit(null)
                delay(ESPERA_SEM_CARRIS.toMillis())
                continue
            }
            when (val r = doServidor.carrisAoVivo()) {
                is Resultado.Ok -> {
                    tentativa = 0
                    emit(r.valor)
                    delay(INTERVALO_CARRIS.toMillis())
                }
                is Resultado.Falha -> {
                    emit(null)
                    delay(pausaDepoisDeFalha(Fonte.CM, tentativa++).toMillis())
                }
            }
        }
    }

    /** Os autocarros da Carris ao vivo de [leiturasCarris]; sem leitura, a lista vazia. */
    fun veiculosCarris(): Flow<List<Veiculo>> = leiturasCarris().map { leitura ->
        val catalogo = catalogo()
        leitura?.veiculos?.map { veiculoDaCarris(it, catalogo) }.orEmpty()
    }

    // As paragens de cada viagem lidas ao Transitous, para a sessão: a lista de uma viagem não muda.
    private val paragensDasViagens = mutableMapOf<String, List<String>>()

    /**
     * "ao vivo, a N paragens" das [chegadas] da paragem da Carris [paragemId], pelo `idViagem` de cada uma: só as que têm um autocarro
     * ao vivo que ainda não passou nela. N sai da posição da paragem na viagem (o `/api/v6/trip`, lido uma vez por sessão) menos o
     * `current_stop_sequence` do autocarro; as posições vêm de [leiturasCarris]. Cada chamada (um ecrã) lê no máximo 3 viagens. Outra
     * paragem, sem servidor, sem o `carrisAoVivo` ou numa falha: nada, e o ecrã fica com o horário.
     */
    fun aoVivoCarris(paragemId: String, chegadas: Flow<List<Chegada>>): Flow<Map<String, AoVivo>> {
        if (!paragemId.startsWith(PARAGEM_CARRIS)) return emptyFlow()
        var lidas = 0
        return flow {
            combine(chegadas, leiturasCarris(), ::Pair).collect { (lista, leitura) ->
                val agora = relogio()
                val veiculos = leitura?.veiculos.orEmpty().associateBy { it.viagem }
                val vivos = mutableMapOf<String, AoVivo>()
                for (c in lista) {
                    val idViagem = c.idViagem ?: continue
                    val veiculo = veiculos[viagemCarrisDeTransitous(idViagem)] ?: continue
                    if (!aindaPorChegar(c, agora)) continue
                    val paragens = paragensDasViagens[idViagem] ?: run {
                        if (lidas >= MAX_VIAGENS_POR_ECRA) return@run null
                        lidas++
                        (transitous.paragensDaViagem(idViagem) as? Resultado.Ok)?.valor?.also { paragensDasViagens[idViagem] = it }
                    } ?: continue
                    // Limite: uma paragem que a viagem repete (circulares) conta pela primeira vez, e um autocarro já a meio do percurso fica sem etiqueta nela; contar a primeira depois do seq se pesar.
                    val posicao = paragens.indexOf(paragemId) + 1
                    if (posicao > 0) paragensEmFalta(posicao, veiculo.seq)?.let { vivos[idViagem] = AoVivo(idViagem, it) }
                }
                emit(vivos)
            }
        }.distinctUntilChanged()
    }

    private var alertasEmCache: Pair<Instant, List<Alerta>>? = null

    /** Alertas ativos da paragem ou das linhas que lá passam (Carris Metropolitana; o Transitous ainda não os tem). */
    suspend fun alertasDaParagem(ref: RefParagem, linhas: Set<String>): List<Alerta> {
        if (ref.fonte != Fonte.CM) return emptyList()
        return alertasAtivos().filter { ref.id in it.paragemIds || it.linhaIds.any(linhas::contains) }
    }

    /** Linhas da Carris Metropolitana com algum alerta ativo; vazio se os alertas não chegarem. */
    suspend fun linhasComAlerta(): Set<String> = alertasAtivos().flatMapTo(mutableSetOf()) { it.linhaIds }

    private suspend fun alertasAtivos(): List<Alerta> {
        val agora = relogio()
        alertasEmCache?.takeIf { Duration.between(it.first, agora) < VALIDADE_ALERTAS }?.let { return it.second }
        return when (val r = cm.alertas(agora)) {
            is Resultado.Ok -> r.valor.also { alertasEmCache = agora to it }
            is Resultado.Falha -> emptyList()
        }
    }

    fun favoritas(): Flow<List<Favorita>> = bd.favoritas().todas().map { lista ->
        lista.mapNotNull { f -> RefParagem.daChave(f.chave)?.let { Favorita(it, f.nome, f.ordem, f.lat, f.lon) } }
    }

    suspend fun eFavorita(ref: RefParagem): Boolean = bd.favoritas().porChave(ref.chave) != null

    suspend fun alternarFavorita(p: Paragem) {
        val dao = bd.favoritas()
        if (dao.porChave(p.ref.chave) != null) return removerFavorita(p.ref)
        // Voltou a ser favorita: já não é para apagar na conta.
        bd.apagados().apagar(p.ref.chave)
        dao.guardar(FavoritaEntidade(p.ref.chave, p.nome, dao.proximaOrdem(), p.lat, p.lon))
    }

    suspend fun removerFavorita(ref: RefParagem) {
        apagado(ref.chave, TIPO_PARAGEM)
        bd.favoritas().apagar(ref.chave)
    }

    suspend fun reordenarFavoritas(ordem: List<Favorita>) =
        bd.favoritas().guardarTodas(ordem.mapIndexed { i, f -> FavoritaEntidade(f.ref.chave, f.nome, i, f.lat, f.lon) })

    fun linhasFavoritas(): Flow<List<LinhaFavorita>> = bd.linhasFavoritas().todas().map { lista -> lista.mapNotNull { it.paraLinhaFavorita() } }

    /** Uma por linha e sentido: se a linha já é favorita nesse sentido (em qualquer paragem), deixa de ser; senão fica com [paragem]. */
    suspend fun alternarLinhaFavorita(ref: RefLinha, sentido: Int, destino: String, paragem: Paragem, nome: String) = bd.withTransaction {
        val dao = bd.linhasFavoritas()
        val ja = dao.daLinha(ref.chave, sentido)
        if (ja.isNotEmpty()) {
            ja.forEach { apagado(it.chave, TIPO_LINHA); dao.apagar(it.chave) }
            return@withTransaction
        }
        val chave = chaveLinhaFavorita(ref, sentido, paragem.ref)
        bd.apagados().apagar(chave)
        dao.guardar(LinhaFavoritaEntidade(chave, ref.chave, sentido, paragem.ref.chave, nome, destino, dao.proximaOrdem(), paragem.lat, paragem.lon))
    }

    suspend fun removerLinhaFavorita(f: LinhaFavorita) = bd.withTransaction {
        val chave = chaveLinhaFavorita(f.ref, f.sentido, f.paragem)
        apagado(chave, TIPO_LINHA)
        bd.linhasFavoritas().apagar(chave)
    }

    /** A ordem de [ordem] passa a ser a da lista (e vai toda para a conta). */
    suspend fun reordenarLinhasFavoritas(ordem: List<LinhaFavorita>) = bd.withTransaction {
        ordem.forEachIndexed { i, f -> bd.linhasFavoritas().reordenar(chaveLinhaFavorita(f.ref, f.sentido, f.paragem), i) }
    }

    /** A paragem faz parte da chave: na conta é apagar a antiga e criar a nova, no mesmo lugar da lista. */
    suspend fun mudarParagemHabitual(f: LinhaFavorita, paragem: Paragem) = bd.withTransaction {
        val antiga = chaveLinhaFavorita(f.ref, f.sentido, f.paragem)
        val nova = chaveLinhaFavorita(f.ref, f.sentido, paragem.ref)
        if (antiga == nova) return@withTransaction
        apagado(antiga, TIPO_LINHA)
        bd.linhasFavoritas().apagar(antiga)
        bd.apagados().apagar(nova)
        bd.linhasFavoritas().guardar(
            LinhaFavoritaEntidade(nova, f.ref.chave, f.sentido, paragem.ref.chave, f.nome, f.destino, f.ordem, paragem.lat, paragem.lon),
        )
    }

    /** As chaveLinha das linhas que passam nas paragens favoritas: as da CM pela rede guardada, as outras pelas últimas chegadas guardadas. */
    suspend fun linhasDasParagensFavoritas(): Set<String> {
        val chaves = bd.favoritas().todas().first().map { it.chave }
        val cm = bd.paragens().porChaves(chaves).filter { it.fonte == Fonte.CM.name }.flatMap { it.linhas.split(',') }
        val outras = chaves.filter { RefParagem.daChave(it)?.fonte == Fonte.TRANSITOUS }.flatMap { c ->
            // Uma linha guardada que não se lê fica de fora: a faixa das perturbações chama isto em cada abertura e não pode deitar a app abaixo.
            val guardadas = bd.chegadas().porChave(c)?.let { g -> runCatching { JSON.decodeFromString<List<ChegadaGuardada>>(g.json) }.getOrNull() }
            guardadas.orEmpty().map { it.linhaNome }
        }
        return (cm + outras).filter { it.isNotBlank() }.mapTo(HashSet(), ::chaveLinha)
    }

    suspend fun paragem(ref: RefParagem): Paragem? = bd.paragens().porChave(ref.chave)?.paraParagem()

    /** Guarda [p] (de uma linha do catálogo) para o ecrã da paragem a ler; a que já está guardada fica como está (a da CM leva as linhas dela). */
    suspend fun guardarParagemSeFalta(p: Paragem) {
        if (bd.paragens().porChave(p.ref.chave) == null) bd.paragens().guardar(listOf(p.paraEntidade()))
    }

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

    /** Os ids dos percursos (patterns) de uma linha da Carris Metropolitana, como a rede guardada os tem. */
    suspend fun padroesDaLinhaCm(linhaId: String): List<String> =
        bd.linhas().porId(linhaId)?.padroes?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    /** As paragens da CM pelo id (as que a rede guardada não tem ficam de fora). */
    suspend fun paragensCm(ids: List<String>): Map<String, Paragem> =
        bd.paragens().porChaves(ids.map { RefParagem(Fonte.CM, it).chave }).associate { it.id to it.paraParagem() }

    suspend fun linhasCmComecadasPor(prefixo: String): List<Linha> = bd.linhas().comecadasPor(prefixo).map { it.paraLinha() }

    suspend fun linhaCm(id: String): Linha? = bd.linhas().porId(id)?.paraLinha()

    suspend fun linhasCm(): Map<String, Linha> = bd.linhas().todas().associate { it.id to it.paraLinha() }

    /** Volta a emitir quando a rede chega ou muda (o separador Linhas mostra-as assim que há). */
    fun linhasCmFlow(): Flow<List<Linha>> = bd.linhas().fluxo().map { lista -> lista.map { it.paraLinha() } }

    suspend fun coresDasLinhasCm(): Map<String, String> =
        bd.linhas().todas().mapNotNull { l -> l.cor?.let { l.id to it } }.toMap()

    /** Sem paragens da CM, ou só com as que a versão anterior guardou sem as linhas de cada uma (depois da migração para a v4). */
    suspend fun redeEmFalta(): Boolean = bd.paragens().contarCmComLinhas() == 0

    /** Paragens e linhas da Carris Metropolitana (uma vez por dia). Devolve falso se alguma falhar. */
    suspend fun atualizarRede(): Boolean {
        val paragensJson = cm.paragensJson() as? Resultado.Ok ?: return false
        val linhasJson = cm.linhasJson() as? Resultado.Ok ?: return false
        // Milhares de paragens: converter fora da linha do ecrã.
        val (linhas, entidades) = try {
            withContext(Dispatchers.Default) {
                val padroes = CmParser.padroesDasLinhas(linhasJson.valor)
                val linhasDe = CmParser.linhasDasParagens(paragensJson.valor)
                CmParser.linhas(linhasJson.valor).map {
                    LinhaEntidade(it.id, it.nomeCurto, it.nomeLongo, it.cor, it.corTexto, padroes[it.id].orEmpty().joinToString(","))
                } to CmParser.paragens(paragensJson.valor).map { it.paraEntidade().copy(linhas = linhasDe[it.ref.id].orEmpty().joinToString(",")) }
            }
        } catch (e: IllegalArgumentException) {
            return false // JSON estragado (o SerializationException é um IllegalArgumentException): fica a rede que havia
        }
        bd.paragens().substituirFonte(Fonte.CM.name, entidades)
        bd.linhas().substituir(linhas)
        return true
    }

    /**
     * As paragens vêm com os acentos que o Transitous às vezes tira: a base de paragens e os sítios pedidos sabem como se escrevem.
     * [cursor] = a página "mais cedo" ou "mais tarde" de uma resposta anterior (o resto do pedido tem de ser igual).
     */
    suspend fun planear(
        de: Lugar, para: Lugar, hora: Instant, chegarAs: Boolean, f: Filtros, cursor: String? = null,
    ): Resultado<PlanoRotas> =
        when (val r = transitous.planear(de, para, hora, chegarAs, f, cursor)) {
            is Resultado.Ok -> {
                val itinerarios = r.valor.itinerarios
                // Aos bocados: o SQLite aceita poucas variáveis por consulta (999 no Android 8).
                val daBase = itinerarios.chavesDosNomes().chunked(500).flatMap { bd.paragens().porNomes(it) }
                    .map { Lugar(it.nome, it.lat, it.lon, TipoLugar.PARAGEM) }
                Resultado.Ok(r.valor.copy(itinerarios = itinerarios.comNomesAcentuados(daBase + de + para)))
            }
            is Resultado.Falha -> r
        }

    private val chegadasParaPartidas = mutableMapOf<RefParagem, Pair<Instant, List<Chegada>>>()

    /**
     * As próximas partidas da linha da primeira etapa em veículo, a partir das chegadas da paragem onde ela começa (as da
     * Carris Metropolitana pela CM, as dos outros operadores pelo Transitous). null = a etapa não tem id de paragem ou a fonte falhou.
     * Várias rotas a sair da mesma paragem fazem um só pedido: as chegadas ficam 20 s em memória.
     */
    suspend fun proximasPartidas(etapa: Etapa, agora: Instant = relogio()): List<Chegada>? {
        val id = etapa.paragens.firstOrNull()?.id ?: return null
        val ref = cmParagemDeTransitous(id)?.let { RefParagem(Fonte.CM, it) } ?: RefParagem(Fonte.TRANSITOUS, id)
        val chegadas = chegadasParaPartidas[ref]?.takeIf { Duration.between(it.first, agora) < VALIDADE_PARTIDAS }?.second
            ?: (chegadasAgora(ref, agora) as? Resultado.Ok)?.valor?.also { chegadasParaPartidas[ref] = agora to it }
            ?: return null
        return proximasDaLinha(chegadas, etapa, agora)
    }

    /** Primeiro os locais guardados cujo nome tem o texto (sem ligar a acentos), depois moradas e sítios do Transitous
     *  (as paragens sem o cais no nome, e sem repetidas).
     *  perto = a posição do utilizador: o Transitous põe os resultados mais próximos primeiro.
     *  Um código postal completo ("1400-038", "1400 038") vai primeiro à GeoAPI.pt: devolve só esse sítio, ou lista vazia se não existe;
     *  se a GeoAPI falhar, pesquisa-se como texto normal.
     *  null = o Transitous falhou e não há nenhum local guardado que sirva (sem ligação, não "sem resultados"). */
    suspend fun pesquisarLugares(texto: String, perto: Pair<Double, Double>? = null): List<Lugar>? {
        if (ehCodigoPostal(texto)) lugaresDoCodigoPostal(texto)?.let { return it }
        val procura = normalizar(texto)
        val meus = bd.locais().lista().filter { normalizar(it.nome).contains(procura) }.map { it.lugar() }
        val outros = (transitous.pesquisarLugares(texto, perto) as? Resultado.Ok)?.valor
            ?.map { if (it.tipo == TipoLugar.PARAGEM) it.copy(nome = nomeBase(it.nome)) else it }
            ?.fold(emptyList<Lugar>()) { sem, l -> if (sem.any { it.repetidoDe(l) }) sem else sem + l }
        return if (outros == null && meus.isEmpty()) null else meus + outros.orEmpty()
    }

    private val codigosPostais = mutableMapOf<String, CodigoPostal>()

    /** Só se guarda (nesta sessão) um código que existe: uma falha ou um 404 não ficam presos. */
    private suspend fun codigoPostal(cp: String): Resultado<CodigoPostal?> {
        codigosPostais[cp]?.let { return Resultado.Ok(it) }
        return geoApi.codigoPostal(cp).also { r ->
            val achado = (r as? Resultado.Ok)?.valor
            if (achado != null) codigosPostais[cp] = achado
        }
    }

    /** O código postal como sítio. Lista vazia = não existe (ou não se consegue localizar); null = a GeoAPI ou o Transitous falharam. */
    private suspend fun lugaresDoCodigoPostal(texto: String): List<Lugar>? {
        val achado = (codigoPostal(normalizarCodigoPostal(texto)) as? Resultado.Ok ?: return null).valor ?: return emptyList()
        achado.paraLugar()?.let { return listOf(it) }
        // Sem ponto na GeoAPI: o 1.º resultado do Transitous para "rua localidade" dá o ponto; o nome continua a ser o do código.
        val procura = listOfNotNull(achado.rua, achado.localidade).joinToString(" ")
        val ponto = (transitous.pesquisarLugares(procura) as? Resultado.Ok ?: return null).valor.firstOrNull() ?: return emptyList()
        return listOfNotNull(achado.copy(lat = ponto.lat, lon = ponto.lon).paraLugar())
    }

    /** Mesmo tipo e nome e muito perto; ou, na mesma zona, até 2 km (a mesma rua aos bocados no OpenStreetMap). */
    private fun Lugar.repetidoDe(o: Lugar): Boolean {
        if (tipo != o.tipo || !nome.equals(o.nome, ignoreCase = true)) return false
        val metros = distanciaMetros(lat, lon, o.lat, o.lon)
        return metros < RAIO_REPETIDOS_METROS || (zona != null && zona == o.zona && metros < RAIO_PEDACOS_METROS)
    }

    suspend fun lugarEm(lat: Double, lon: Double): Lugar? = (transitous.lugarEm(lat, lon) as? Resultado.Ok)?.valor

    fun locais(): Flow<List<LocalGuardado>> = bd.locais().todos().map { lista ->
        lista.map { LocalGuardado(it.id, it.nome, it.lugar(), it.tipo, it.ordem) }
    }

    suspend fun guardarLocal(nome: String, lugar: Lugar, tipo: String) = bd.locais().guardar(nome, lugar.lat, lugar.lon, tipo)

    suspend fun renomearLocal(id: Long, nome: String) = bd.locais().renomear(id, nome)

    suspend fun reordenarLocais(ids: List<Long>) = bd.locais().reordenar(ids)

    suspend fun apagarLocal(id: Long) {
        bd.locais().porId(id)?.let { apagado(it.uuid, it.tipo) }
        bd.locais().apagar(id)
    }

    /** Com sessão iniciada (quem muda isto é a [Conta]), apagar guarda a chave para o apagar também na conta; sem sessão apaga só. */
    @Volatile var comConta = false

    // Guarda-se antes de apagar: se a app morrer a meio, a conta apaga-o na mesma e a resposta apaga-o cá.
    // Uma linha com a chave comprida nunca foi para a conta: não há lá nada a apagar.
    private suspend fun apagado(chave: String, tipo: String) {
        if (comConta && !(tipo == TIPO_LINHA && chave.length > MAX_CHAVE_LINHA)) bd.apagados().guardar(ApagadoEntidade(chave, tipo))
    }

    /** Muda sempre que há uma escrita nos favoritos ou nos sítios; > 0 = há alterações por enviar à conta. */
    fun porEnviar(): Flow<Int> = bd.apagados().porEnviar()

    /** O que mudou no telemóvel e ainda não foi para a conta, como o servidor o quer (sem as chaves [soNoTelemovel]). */
    suspend fun porSincronizar(soNoTelemovel: Set<String> = emptySet()): List<FavoritoRemoto> =
        (bd.apagados().todos().map { FavoritoRemoto(it.chave, it.tipo, "", 0.0, 0.0, 0, apagado = true) } +
            bd.favoritas().pendentes().map { FavoritoRemoto(it.chave, TIPO_PARAGEM, it.nome, it.lat, it.lon, it.ordem, apagado = false) } +
            bd.linhasFavoritas().pendentes().filter { it.chave.length <= MAX_CHAVE_LINHA }.map { it.paraRemoto() } +
            bd.locais().pendentes().map { FavoritoRemoto(it.uuid, it.tipo, it.nome, it.lat, it.lon, it.ordem, apagado = false) })
            .filter { it.chave !in soNoTelemovel }
            // O servidor recusa o pedido inteiro com um nome maior; a resposta traz o nome cortado e fica assim cá também.
            .map { it.copy(nome = it.nome.take(120)) }

    /**
     * Aplica o que veio da conta (sem o marcar como por enviar) e tira as marcas do que foi [enviadas]. O que mudou no telemóvel
     * enquanto o pedido ia e vinha não é pisado e fica por enviar. Um apagado na conta nunca apaga o que a pessoa deixou só
     * neste telemóvel ([soNoTelemovel]).
     */
    suspend fun aplicarSincronizacao(enviadas: List<FavoritoRemoto>, recebidas: List<FavoritoRemoto>, soNoTelemovel: Set<String>) =
        bd.withTransaction {
            val enviada = enviadas.associateBy { it.chave }
            val mudou = porSincronizar(soNoTelemovel).filter { enviada[it.chave] != it }.mapTo(HashSet()) { it.chave }
            recebidas.filter { it.chave !in mudou && !(it.apagado && it.chave in soNoTelemovel) }.forEach { aplicar(it) }
            enviadas.filter { it.chave !in mudou }.forEach {
                when {
                    it.apagado -> bd.apagados().apagar(it.chave)
                    it.tipo == TIPO_PARAGEM -> bd.favoritas().enviada(it.chave)
                    it.tipo == TIPO_LINHA -> bd.linhasFavoritas().enviada(it.chave)
                    else -> bd.locais().enviado(it.chave)
                }
            }
        }

    private suspend fun aplicar(f: FavoritoRemoto) {
        when (f.tipo) {
            TIPO_PARAGEM ->
                if (f.apagado) bd.favoritas().apagar(f.chave)
                else bd.favoritas().guardar(FavoritaEntidade(f.chave, f.nome, f.ordem, f.lat, f.lon, pendente = false))
            "CASA", "TRABALHO", "OUTRO" ->
                if (f.apagado) bd.locais().apagarUuid(f.chave)
                else bd.locais().inserir(
                    LocalEntidade(bd.locais().porUuid(f.chave)?.id ?: 0, f.nome, f.lat, f.lon, f.tipo, f.ordem, f.chave, pendente = false),
                )
            TIPO_LINHA ->
                if (f.apagado) bd.linhasFavoritas().apagar(f.chave)
                else daChaveLinhaFavorita(f.chave)?.let { (linha, sentido, paragem) ->
                    bd.linhasFavoritas().guardar(
                        LinhaFavoritaEntidade(
                            f.chave, linha.chave, sentido, paragem.chave, f.nome.substringBefore('\n'), f.nome.substringAfter('\n', ""),
                            f.ordem, f.lat, f.lon, pendente = false,
                        ),
                    )
                }
            // Um tipo novo de uma versão futura do servidor: fica para essa versão da app.
        }
    }

    /** Ao entrar e juntar: tudo o que está no telemóvel vai para a conta (fica marcado até ir mesmo). */
    suspend fun marcarTudoPorEnviar() = bd.withTransaction {
        bd.favoritas().marcarTodas(true)
        bd.linhasFavoritas().marcarTodas(true)
        bd.locais().marcarTodos(true)
    }

    /** As chaves que a conta conheceria de tudo o que está no telemóvel. */
    suspend fun chavesNoTelemovel(): Set<String> =
        (bd.favoritas().todas().first().map { it.chave } + bd.linhasFavoritas().todas().first().map { it.chave } +
            bd.locais().lista().map { it.uuid }).toSet()

    /** Ao sair da conta: os favoritos ficam, mas nada fica por enviar. */
    suspend fun limparMarcas() = bd.withTransaction {
        bd.favoritas().marcarTodas(false)
        bd.linhasFavoritas().marcarTodas(false)
        bd.locais().marcarTodos(false)
        bd.apagados().apagarTodos()
    }

    fun recentes(): Flow<List<ViagemRecente>> = bd.locais().recentes().map { lista ->
        lista.map {
            ViagemRecente(
                Lugar(it.deNome, it.deLat, it.deLon, TipoLugar.LOCAL),
                Lugar(it.paraNome, it.paraLat, it.paraLon, TipoLugar.LOCAL),
                Instant.ofEpochSecond(it.em),
            )
        }
    }

    suspend fun registarRecente(de: Lugar, para: Lugar) = bd.locais().registarRecente(
        RecenteEntidade(
            deNome = de.nome, deLat = de.lat, deLon = de.lon,
            paraNome = para.nome, paraLat = para.lat, paraLon = para.lon, em = relogio().epochSecond,
        ),
    )

    /** Favoritas (paragens e linhas), locais, viagens recentes e chegadas guardadas; também esquece o que tem em memória. */
    suspend fun apagarDadosDoTelemovel() {
        bd.locais().apagarDadosPessoais()
        alertasEmCache = null
        chegadasParaPartidas.clear()
        codigosPostais.clear()
    }

    private companion object {
        val INTERVALO_VEICULOS: Duration = Duration.ofSeconds(15)
        val INTERVALO_CARRIS: Duration = Duration.ofSeconds(30)
        val ESPERA_SEM_CARRIS: Duration = Duration.ofSeconds(5)
        const val PARAGEM_CARRIS = "pt-Carris_"
        const val MAX_VIAGENS_POR_ECRA = 3
        val VALIDADE_ALERTAS: Duration = Duration.ofMinutes(5)
        val VALIDADE_PARTIDAS: Duration = Duration.ofSeconds(20)
        const val RAIO_REPETIDOS_METROS = 400.0
        const val RAIO_PEDACOS_METROS = 2000.0
        const val TIPO_PARAGEM = "PARAGEM"
    }
}
