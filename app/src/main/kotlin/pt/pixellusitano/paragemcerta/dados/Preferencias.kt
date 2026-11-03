package pt.pixellusitano.paragemcerta.dados

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** O ecrã em que a app abre; FAVORITOS é o Início com o painel dos favoritos aberto. As Linhas entram na etapa 4. */
enum class EcraInicial { MAPA, ROTAS, FAVORITOS }

/** Definições do utilizador (modo claro/escuro, limites de atraso, filtros das rotas, ecrã inicial e avisos da viagem), guardadas no telemóvel. */
class Preferencias(private val loja: DataStore<Preferences>) {
    val modo: Flow<ModoTema> = loja.data.map { p ->
        p[MODO]?.let { nome -> ModoTema.entries.firstOrNull { it.name == nome } } ?: ModoTema.SISTEMA
    }

    val limites: Flow<Limites> = loja.data.map { p ->
        Limites(p[ATRASADO] ?: Limites().atrasado, p[MUITO_ATRASADO] ?: Limites().muitoAtrasado)
    }

    /** O que falta (a 0.3.2 não guardava o tempo a pé, o ritmo nem a ordem) ou não se conhece fica como em [Filtros]. */
    val filtros: Flow<Filtros> = loja.data.map { p ->
        val omissao = Filtros()
        Filtros(
            menosAPe = p[MENOS_A_PE] ?: false,
            menosTransbordos = p[MENOS_TRANSBORDOS] ?: false,
            acessivel = p[ACESSIVEL] ?: false,
            modos = p[MODOS].orEmpty().mapNotNull { nome -> Modo.entries.firstOrNull { it.name == nome } }.toSet(),
            maxAPeMin = p[MAX_A_PE_MIN]?.takeIf { it in A_PE_MIN_POSSIVEIS } ?: omissao.maxAPeMin,
            ritmo = p[RITMO]?.let { nome -> Ritmo.entries.firstOrNull { it.name == nome } } ?: omissao.ritmo,
            ordem = p[ORDEM]?.let { nome -> Ordem.entries.firstOrNull { it.name == nome } } ?: omissao.ordem,
        )
    }

    /** Sem escolha, ou com um valor que esta versão não conhece, abre no mapa. */
    val ecraInicial: Flow<EcraInicial> = loja.data.map { p ->
        p[ECRA_INICIAL]?.let { nome -> EcraInicial.entries.firstOrNull { it.name == nome } } ?: EcraInicial.MAPA
    }

    /** Se os avisos "Prepara-te" e "Sai agora" tocam e vibram (vão no plano da viagem: o serviço não lê as definições). */
    val somAviso: Flow<Boolean> = loja.data.map { it[SOM_AVISO] ?: true }
    val vibracaoAviso: Flow<Boolean> = loja.data.map { it[VIBRACAO_AVISO] ?: true }

    /** Depois da primeira viagem já não se explica a poupança de bateria. */
    val primeiraViagemFeita: Flow<Boolean> = loja.data.map { it[PRIMEIRA_VIAGEM_FEITA] ?: false }

    /** Se a viagem e o "Avisa-me" avisam dos relatos confirmados da comunidade. */
    val avisosComunidade: Flow<Boolean> = loja.data.map { it[AVISOS_COMUNIDADE] ?: true }

    /** Se o mapa marca as paragens com relatos confirmados. */
    val camadaRelatos: Flow<Boolean> = loja.data.map { it[CAMADA_RELATOS] ?: true }

    /** Os relatos e fotos que a pessoa escondeu ("relato:<id>", "foto:<id>"), do mais antigo para o mais novo. Ficam só no telemóvel. */
    val ocultos: Flow<Set<String>> = loja.data.map { lerOcultos(it) }

    suspend fun definirAvisosComunidade(ligado: Boolean) {
        loja.edit { it[AVISOS_COMUNIDADE] = ligado }
    }

    suspend fun definirCamadaRelatos(ligada: Boolean) {
        loja.edit { it[CAMADA_RELATOS] = ligada }
    }

    /** Junta no fim (um id repetido passa para o fim) e, passando de [MAX_OCULTOS], tira os mais antigos. */
    suspend fun ocultar(id: String) {
        loja.edit { it[OCULTOS] = Json.encodeToString((lerOcultos(it) - id + id).toList().takeLast(MAX_OCULTOS)) }
    }

    suspend fun esquecerOcultos() {
        loja.edit { it.remove(OCULTOS) }
    }

    // Uma lista como texto numa só chave (um stringSet não guarda a ordem); um valor estragado é o mesmo que não ter nenhum.
    private fun lerOcultos(p: Preferences): Set<String> =
        p[OCULTOS]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty().toSet()

    suspend fun definirSomAviso(ligado: Boolean) {
        loja.edit { it[SOM_AVISO] = ligado }
    }

    suspend fun definirVibracaoAviso(ligado: Boolean) {
        loja.edit { it[VIBRACAO_AVISO] = ligado }
    }

    suspend fun marcarPrimeiraViagemFeita() {
        loja.edit { it[PRIMEIRA_VIAGEM_FEITA] = true }
    }

    suspend fun definirFiltros(f: Filtros) {
        loja.edit {
            it[MENOS_A_PE] = f.menosAPe
            it[MENOS_TRANSBORDOS] = f.menosTransbordos
            it[ACESSIVEL] = f.acessivel
            it[MODOS] = f.modos.map { m -> m.name }.toSet()
            it[MAX_A_PE_MIN] = f.maxAPeMin
            it[RITMO] = f.ritmo.name
            it[ORDEM] = f.ordem.name
        }
    }

    suspend fun definirEcraInicial(e: EcraInicial) {
        loja.edit { it[ECRA_INICIAL] = e.name }
    }

    suspend fun definirModo(m: ModoTema) {
        loja.edit { it[MODO] = m.name }
    }

    suspend fun definirLimites(l: Limites) {
        require(l.atrasado >= 1) { "o limite de atrasado tem de ser pelo menos 1 minuto" }
        require(l.muitoAtrasado > l.atrasado) { "muito atrasado tem de ser maior do que atrasado" }
        loja.edit {
            it[ATRASADO] = l.atrasado
            it[MUITO_ATRASADO] = l.muitoAtrasado
        }
    }

    private companion object {
        val MODO = stringPreferencesKey("modo")
        val ATRASADO = intPreferencesKey("limite_atrasado")
        val MUITO_ATRASADO = intPreferencesKey("limite_muito_atrasado")
        val MENOS_A_PE = booleanPreferencesKey("filtro_menos_a_pe")
        val MENOS_TRANSBORDOS = booleanPreferencesKey("filtro_menos_transbordos")
        val ACESSIVEL = booleanPreferencesKey("filtro_acessivel")
        val MODOS = stringSetPreferencesKey("filtro_modos")
        val MAX_A_PE_MIN = intPreferencesKey("filtro_max_a_pe_min")
        val RITMO = stringPreferencesKey("filtro_ritmo")
        val ORDEM = stringPreferencesKey("filtro_ordem")
        val ECRA_INICIAL = stringPreferencesKey("ecra_inicial")
        val A_PE_MIN_POSSIVEIS = setOf(5, 10, 15, 20)
        val SOM_AVISO = booleanPreferencesKey("som_aviso")
        val VIBRACAO_AVISO = booleanPreferencesKey("vibracao_aviso")
        val PRIMEIRA_VIAGEM_FEITA = booleanPreferencesKey("primeira_viagem_feita")
        val AVISOS_COMUNIDADE = booleanPreferencesKey("avisos_comunidade")
        val CAMADA_RELATOS = booleanPreferencesKey("camada_relatos")
        val OCULTOS = stringPreferencesKey("ocultos")
        const val MAX_OCULTOS = 500
    }
}
