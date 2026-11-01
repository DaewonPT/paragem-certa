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
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** Definições do utilizador (modo claro/escuro, limites de atraso, filtros das rotas e avisos da viagem), guardadas no telemóvel. */
class Preferencias(private val loja: DataStore<Preferences>) {
    val modo: Flow<ModoTema> = loja.data.map { p ->
        p[MODO]?.let { nome -> ModoTema.entries.firstOrNull { it.name == nome } } ?: ModoTema.SISTEMA
    }

    val limites: Flow<Limites> = loja.data.map { p ->
        Limites(p[ATRASADO] ?: Limites().atrasado, p[MUITO_ATRASADO] ?: Limites().muitoAtrasado)
    }

    val filtros: Flow<Filtros> = loja.data.map { p ->
        Filtros(
            menosAPe = p[MENOS_A_PE] ?: false,
            menosTransbordos = p[MENOS_TRANSBORDOS] ?: false,
            acessivel = p[ACESSIVEL] ?: false,
            modos = p[MODOS].orEmpty().mapNotNull { nome -> Modo.entries.firstOrNull { it.name == nome } }.toSet(),
        )
    }

    /** Se os avisos "Prepara-te" e "Sai agora" tocam e vibram (vão no plano da viagem: o serviço não lê as definições). */
    val somAviso: Flow<Boolean> = loja.data.map { it[SOM_AVISO] ?: true }
    val vibracaoAviso: Flow<Boolean> = loja.data.map { it[VIBRACAO_AVISO] ?: true }

    /** Depois da primeira viagem já não se explica a poupança de bateria. */
    val primeiraViagemFeita: Flow<Boolean> = loja.data.map { it[PRIMEIRA_VIAGEM_FEITA] ?: false }

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
        }
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
        val SOM_AVISO = booleanPreferencesKey("som_aviso")
        val VIBRACAO_AVISO = booleanPreferencesKey("vibracao_aviso")
        val PRIMEIRA_VIAGEM_FEITA = booleanPreferencesKey("primeira_viagem_feita")
    }
}
