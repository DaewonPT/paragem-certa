package pt.pixellusitano.paragemcerta.dados

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** DataStore em memória: o teste é sobre as regras das preferências, não sobre o ficheiro
 * (o DataStore de ficheiros não consegue substituir o ficheiro no Windows; no Android funciona). */
private class LojaEmMemoria : DataStore<Preferences> {
    private val estado = MutableStateFlow(emptyPreferences())
    override val data = estado
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(estado.value).also { estado.value = it }
}

class PreferenciasTest {
    private fun prefs() = Preferencias(LojaEmMemoria())

    @Test fun valoresPorOmissao() = runTest {
        val p = prefs()
        assertEquals(ModoTema.SISTEMA, p.modo.first())
        assertEquals(Limites(2, 5), p.limites.first())
    }

    @Test fun guardaLimitesEModo() = runTest {
        val p = prefs()
        p.definirLimites(Limites(3, 8))
        p.definirModo(ModoTema.ESCURO)
        assertEquals(Limites(3, 8), p.limites.first())
        assertEquals(ModoTema.ESCURO, p.modo.first())
    }

    @Test fun recusaLimitesImpossiveis() {
        val p = prefs()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.definirLimites(Limites(5, 5)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.definirLimites(Limites(0, 5)) } }
    }
}
