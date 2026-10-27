package pt.pixellusitano.paragemcerta.dados

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** Definições do utilizador (modo claro/escuro e limites de atraso), guardadas no telemóvel. */
class Preferencias(private val loja: DataStore<Preferences>) {
    val modo: Flow<ModoTema> = loja.data.map { p ->
        p[MODO]?.let { nome -> ModoTema.entries.firstOrNull { it.name == nome } } ?: ModoTema.SISTEMA
    }

    val limites: Flow<Limites> = loja.data.map { p ->
        Limites(p[ATRASADO] ?: Limites().atrasado, p[MUITO_ATRASADO] ?: Limites().muitoAtrasado)
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
    }
}
