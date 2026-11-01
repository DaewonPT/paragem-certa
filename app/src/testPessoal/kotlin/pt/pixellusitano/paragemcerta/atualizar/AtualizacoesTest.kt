package pt.pixellusitano.paragemcerta.atualizar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.BuildConfig

@RunWith(RobolectricTestRunner::class)
class AtualizacoesTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private val estaVersao = "v" + BuildConfig.VERSION_NAME

    @Test fun porOmissaoAtualizaSozinhaSoComWifi() {
        assertTrue(Atualizacoes.atualizarSozinha(contexto))
        assertFalse(Atualizacoes.usarDadosMoveis(contexto))
    }

    @Test fun definicoesGuardam() {
        Atualizacoes.definirAtualizarSozinha(contexto, false)
        Atualizacoes.definirUsarDadosMoveis(contexto, true)
        assertFalse(Atualizacoes.atualizarSozinha(contexto))
        assertTrue(Atualizacoes.usarDadosMoveis(contexto))
        Atualizacoes.definirAtualizarSozinha(contexto, true)
        Atualizacoes.definirUsarDadosMoveis(contexto, false)
        assertTrue(Atualizacoes.atualizarSozinha(contexto))
        assertFalse(Atualizacoes.usarDadosMoveis(contexto))
    }

    @Test fun semNadaGuardadoNaoHaNovidades() = assertNull(Atualizacoes.novidadesPorMostrar(contexto))

    @Test fun novidadesDaVersaoEmExecucaoSaemUmaVez() {
        Atualizacoes.guardarNovidades(contexto, NovaVersao(estaVersao, "https://exemplo/a.apk", "Rotas mais rápidas."))
        val novidades = Atualizacoes.novidadesPorMostrar(contexto)
        assertEquals(estaVersao, novidades?.tag)
        assertEquals("Rotas mais rápidas.", novidades?.notas)
        Atualizacoes.marcarNovidadesVistas(contexto)
        assertNull(Atualizacoes.novidadesPorMostrar(contexto))
    }

    @Test fun novidadesDeOutraVersaoNaoSaem() {
        Atualizacoes.guardarNovidades(contexto, NovaVersao("v99.0.0", "https://exemplo/a.apk", "Ainda não instalada."))
        assertNull(Atualizacoes.novidadesPorMostrar(contexto))
    }
}
