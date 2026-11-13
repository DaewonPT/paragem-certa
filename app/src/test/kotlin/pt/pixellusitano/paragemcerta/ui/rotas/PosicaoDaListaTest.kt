package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rotas.etiquetas
import pt.pixellusitano.paragemcerta.dados.fixture
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp")
class PosicaoDaListaTest {
    @get:Rule val regra = createComposeRule()

    private val rotas = TransitousParser.rotas(fixture("transitous-plan.json")).let { it + it + it } // 9 cartões: a lista desliza
    private fun prontas(r: List<Itinerario>) = EstadoRotas.Prontas(r, etiquetas(r))
    private var estado by mutableStateOf<EstadoRotas>(prontas(rotas))
    private var aberto by mutableStateOf(false)
    private var noEcra by mutableStateOf(true)
    private lateinit var lista: LazyListState

    private fun ecra() = regra.setContent {
        val guardado = rememberSaveableStateHolder()
        TemaParagemCerta(ModoTema.CLARO) {
            // Como o NavHost: noutro separador, na viagem ou ao rodar, o ecrã sai com o estado guardado e volta com ele.
            if (noEcra) guardado.SaveableStateProvider("rotas") {
                lista = rememberListaDasRotas(estado)
                // Como o when do RotasEcra, com um texto no lugar do detalhe (o mapa não corre no Robolectric).
                if (aberto) Text("detalhe")
                else RotasConteudo(
                    pedido = PedidoRotas(destino = Lugar("Oriente", 38.7678, -9.0990, TipoLugar.LOCAL)), estado = estado, filtros = Filtros(),
                    alertas = emptySet(), agora = Instant.parse("2026-11-01T11:10:00Z"),
                    aoEscolherOrigem = {}, aoEscolherDestino = {}, aoMudarHora = { _, _ -> }, aoMudarFiltros = {},
                    aoAbrirRota = {}, aoTentarOutraVez = {}, aoMaisCedo = {}, aoMaisTarde = {},
                    lista = lista,
                )
            } else Text("outro separador")
        }
    }

    private fun indice() = regra.runOnIdle { lista.firstVisibleItemIndex }

    private fun descerAoFim(): Int {
        // Só a lista tem "deslizar até ao item": as filas de filtros deslizam na horizontal.
        regra.onNode(hasScrollToIndexAction()).performScrollToIndex(rotas.size + 1) // 2 filas de filtros + 9 cartões
        return indice().also { assertTrue("a lista devia ter descido", it > 0) }
    }

    // Cada mudança espera pela anterior, para o efeito correr nesse passo e não junto com o seguinte.
    private fun passo(mudar: () -> Unit) {
        mudar()
        regra.waitForIdle()
    }

    @Test fun voltarDoDetalheDeixaAListaOndeEstava() {
        ecra()
        val antes = descerAoFim()
        passo { aberto = true }
        passo { aberto = false }
        assertEquals(antes, indice())
    }

    @Test fun voltarAoEcraComAMesmaListaDeixaAOndeEstava() {
        ecra()
        val antes = descerAoFim()
        passo { noEcra = false }
        passo { noEcra = true }
        assertEquals(antes, indice())
    }

    @Test fun listaNovaComODetalheAbertoComecaDeCima() {
        ecra()
        descerAoFim()
        passo { aberto = true }
        passo { estado = prontas(rotas.drop(1)) }
        passo { aberto = false }
        assertEquals(0, indice())
    }

    @Test fun listaNovaComOEcraForaComecaDeCima() {
        ecra()
        descerAoFim()
        passo { noEcra = false }
        passo { estado = prontas(rotas.drop(1)) }
        passo { noEcra = true }
        assertEquals(0, indice())
    }
}
