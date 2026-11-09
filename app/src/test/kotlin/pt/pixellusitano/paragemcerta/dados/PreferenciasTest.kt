package pt.pixellusitano.paragemcerta.dados

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

/** DataStore em memória: o teste é sobre as regras das preferências, não sobre o ficheiro
 * (o DataStore de ficheiros não consegue substituir o ficheiro no Windows; no Android funciona). */
internal class LojaEmMemoria(inicial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val estado = MutableStateFlow(inicial)
    override val data = estado
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(estado.value).also { estado.value = it }
}

class PreferenciasTest {
    private fun prefs(inicial: Preferences = emptyPreferences()) = Preferencias(LojaEmMemoria(inicial))

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

    @Test fun guardaFiltros() = runTest {
        val p = prefs()
        assertEquals(Filtros(), p.filtros.first())
        p.definirFiltros(Filtros(acessivel = true, modos = setOf(Modo.METRO)))
        assertEquals(Filtros(acessivel = true, modos = setOf(Modo.METRO)), p.filtros.first())
    }

    @Test fun guardaPreferenciasDeRotas() = runTest {
        val p = prefs()
        val f = Filtros(menosAPe = true, maxAPeMin = 10, ritmo = Ritmo.LENTO, ordem = Ordem.MENOS_TRANSBORDOS)
        p.definirFiltros(f)
        assertEquals(f, p.filtros.first())
        p.definirFiltros(Filtros(maxAPeMin = 20, ritmo = Ritmo.RAPIDO, ordem = Ordem.CHEGADA_MAIS_CEDO))
        assertEquals(Filtros(maxAPeMin = 20, ritmo = Ritmo.RAPIDO, ordem = Ordem.CHEGADA_MAIS_CEDO), p.filtros.first())
    }

    @Test fun preferenciasAntigasUsamOmissoes() = runTest {
        // O que a 0.3.2 guardou: só as chaves dos filtros de antes, sem as das rotas.
        val p = prefs(
            preferencesOf(
                booleanPreferencesKey("filtro_acessivel") to true,
                stringSetPreferencesKey("filtro_modos") to setOf("METRO"),
            ),
        )
        val f = p.filtros.first()
        assertEquals(15, f.maxAPeMin)
        assertEquals(Ritmo.NORMAL, f.ritmo)
        assertEquals(Ordem.MAIS_RAPIDA, f.ordem)
        assertEquals(Filtros(acessivel = true, modos = setOf(Modo.METRO)), f)
    }

    @Test fun valoresDesconhecidosDasRotasUsamOmissoes() = runTest {
        val p = prefs(
            preferencesOf(
                intPreferencesKey("filtro_max_a_pe_min") to 7,
                stringPreferencesKey("filtro_ritmo") to "VELOZ",
                stringPreferencesKey("filtro_ordem") to "A_MAIS_BONITA",
            ),
        )
        assertEquals(Filtros(), p.filtros.first())
    }

    @Test fun ecraInicialPorOmissaoEMapa() = runTest {
        assertEquals(EcraInicial.MAPA, prefs().ecraInicial.first())
    }

    @Test fun guardaEcraInicial() = runTest {
        val p = prefs()
        p.definirEcraInicial(EcraInicial.FAVORITOS)
        assertEquals(EcraInicial.FAVORITOS, p.ecraInicial.first())
        p.definirEcraInicial(EcraInicial.ROTAS)
        assertEquals(EcraInicial.ROTAS, p.ecraInicial.first())
        p.definirEcraInicial(EcraInicial.LINHAS)
        assertEquals(EcraInicial.LINHAS, p.ecraInicial.first())
        p.definirEcraInicial(EcraInicial.MAPA)
        assertEquals(EcraInicial.MAPA, p.ecraInicial.first())
    }

    @Test fun ecraInicialDesconhecidoUsaMapa() = runTest {
        val p = prefs(preferencesOf(stringPreferencesKey("ecra_inicial") to "WIDGET")) // um valor de uma versão futura
        assertEquals(EcraInicial.MAPA, p.ecraInicial.first())
    }

    @Test fun camadasDoMapaLigadasPorOmissao() = runTest {
        val p = prefs()
        assertTrue(p.camadaAoVivo.first())
        assertTrue(p.camadaGira.first())
        p.definirCamadaAoVivo(false)
        assertFalse(p.camadaAoVivo.first())
        assertTrue(p.camadaGira.first()) // cada camada é independente
        p.definirCamadaGira(false)
        assertFalse(p.camadaGira.first())
    }

    @Test fun perturbacoesVistasGuardamAs500MaisRecentes() = runTest {
        val p = prefs()
        assertTrue(p.perturbacoesVistas.first().isEmpty())
        p.marcarVistas((0..499).map { "cm:$it" }.toSet())
        p.marcarVistas(setOf("cm:0", "metro:VD:0a1b2c3d")) // uma vista outra vez passa para o fim
        val vistas = p.perturbacoesVistas.first()
        assertEquals(500, vistas.size)
        assertFalse("cm:1" in vistas) // sai a mais antiga
        assertTrue("cm:0" in vistas && "metro:VD:0a1b2c3d" in vistas)
        p.esquecerVistas()
        assertTrue(p.perturbacoesVistas.first().isEmpty())
    }

    @Test fun avisoComSomEVibracaoPorOmissao() = runTest {
        val p = prefs()
        assertTrue(p.somAviso.first())
        assertTrue(p.vibracaoAviso.first())
        assertFalse(p.primeiraViagemFeita.first())
    }

    @Test fun dicaBussolaComecaPorVerEFicaVista() = runTest {
        val p = prefs()
        assertFalse(p.dicaBussolaVista.first())
        p.marcarDicaBussolaVista()
        assertTrue(p.dicaBussolaVista.first())
    }

    @Test fun guardaSomVibracaoEPrimeiraViagem() = runTest {
        val p = prefs()
        p.definirSomAviso(false)
        assertFalse(p.somAviso.first())
        assertTrue(p.vibracaoAviso.first()) // cada interruptor é independente
        p.definirVibracaoAviso(false)
        p.marcarPrimeiraViagemFeita()
        assertFalse(p.vibracaoAviso.first())
        assertTrue(p.primeiraViagemFeita.first())
        p.definirSomAviso(true)
        assertTrue(p.somAviso.first())
    }

    @Test fun avisosECamadaDaComunidadeLigadosPorOmissao() = runTest {
        val p = prefs()
        assertTrue(p.avisosComunidade.first())
        assertTrue(p.camadaRelatos.first())
        p.definirAvisosComunidade(false)
        assertFalse(p.avisosComunidade.first())
        assertTrue(p.camadaRelatos.first()) // cada interruptor é independente
        p.definirCamadaRelatos(false)
        assertFalse(p.camadaRelatos.first())
        p.definirAvisosComunidade(true)
        assertTrue(p.avisosComunidade.first())
    }

    @Test fun ocultosGuardamNoMaximo500() = runTest {
        val p = prefs()
        assertTrue(p.ocultos.first().isEmpty())
        (0..500).forEach { p.ocultar("relato:$it") }
        val guardados = p.ocultos.first()
        assertEquals(500, guardados.size)
        assertFalse("relato:0" in guardados) // sai o primeiro, o mais antigo
        assertTrue("relato:1" in guardados)
        assertTrue("relato:500" in guardados)
    }

    @Test fun ocultarOMesmoIdDuasVezesNaoDuplica() = runTest {
        val p = prefs()
        p.ocultar("relato:r1")
        p.ocultar("foto:f1")
        p.ocultar("relato:r1")
        assertEquals(listOf("foto:f1", "relato:r1"), p.ocultos.first().toList()) // o repetido passa para o fim
    }

    @Test fun ocultosEstragadosViramVazio() = runTest {
        val p = prefs(preferencesOf(stringPreferencesKey("ocultos") to "isto não é uma lista"))
        assertTrue(p.ocultos.first().isEmpty())
        p.ocultar("relato:r1") // e a seguinte escrita recomeça limpa
        assertEquals(setOf("relato:r1"), p.ocultos.first())
    }

    @Test fun esquecerOcultosApagaTudo() = runTest {
        val p = prefs()
        p.ocultar("relato:r1")
        p.ocultar("foto:f1")
        p.esquecerOcultos()
        assertTrue(p.ocultos.first().isEmpty())
    }

    @Test fun recusaLimitesImpossiveis() {
        val p = prefs()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.definirLimites(Limites(5, 5)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.definirLimites(Limites(0, 5)) } }
    }
}
