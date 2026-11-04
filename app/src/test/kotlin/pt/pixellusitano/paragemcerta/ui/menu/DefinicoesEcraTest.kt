package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstadoConta
import pt.pixellusitano.paragemcerta.dados.HttpPorUrl
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.dados.ServidorDeTeste
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class DefinicoesEcraTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val http = HttpPorUrl("geocode" to listOf({ Resposta(200, "[]") }))
    private val repo = Repositorio(bd, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
    private val servidor = ServidorDeTeste()
    private val preferencias = Preferencias(LojaEmMemoria())
    private val conta = Conta(contexto, ServidorParagemCerta(servidor), repo)
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)

    @After fun fechar() = bd.close()

    @Test fun apagarDadosSoAvisaNoFimESobreviveASairDoEcra() {
        val naRede = CompletableDeferred<Unit>()
        runBlocking {
            assertEquals(Resultado.Ok(Unit), conta.confirmarCodigo("ana@exemplo.pt", "123456", juntarFavoritos = true))
            repo.alternarFavorita(cais)
        }
        servidor.duranteASincronizacao = { naRede.await() }
        val noEcra = mutableStateOf(true)
        regra.setContent { TemaParagemCerta(ModoTema.CLARO) { if (noEcra.value) DefinicoesEcra(preferencias, conta, aoVoltar = {}) } }

        regra.onNodeWithText("Apagar dados deste telemóvel").performScrollTo().performClick()
        regra.onNodeWithText("Apagar").performClick()
        regra.waitUntil(5_000) { servidor.sincronizacoes.size == 2 } // a sincronização antes de sair já está na rede
        assertNull("a rede ainda não respondeu", ShadowToast.getTextOfLatestToast())

        noEcra.value = false
        regra.waitForIdle()
        naRede.complete(Unit)
        regra.waitUntil(5_000) { ShadowToast.getTextOfLatestToast() != null }

        assertEquals("Dados apagados", ShadowToast.getTextOfLatestToast())
        assertTrue(runBlocking { repo.favoritas().first() }.isEmpty())
        assertEquals(EstadoConta.SemConta, conta.estado.value)
        assertFalse(File(contexto.noBackupFilesDir, "conta.json").exists())
    }
}
