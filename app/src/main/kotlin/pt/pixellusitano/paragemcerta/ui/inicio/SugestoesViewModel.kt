package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.rotas.MINHA_POSICAO
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/**
 * As sugestões do momento dos sítios favoritos, com a entrada do Início na pilha: sobrevivem a trocar de separador, abrir uma paragem
 * e rodar o ecrã, e um pedido a meio acaba e fica guardado mesmo que o Início saia entretanto. [planear] dá as rotas como vieram e
 * [corrigir] acerta-as com os atrasos ao vivo (e tira as que já partiram).
 */
internal class SugestoesViewModel(
    private val planear: suspend (Lugar, Lugar, Instant) -> Resultado<PlanoRotas>,
    private val corrigir: suspend (List<Itinerario>, Instant) -> List<Itinerario>,
    private val relogio: () -> Instant = Instant::now,
) : ViewModel() {
    val sugestoes: SnapshotStateMap<Long, Sugestao> = mutableStateMapOf()
    val calculando: SnapshotStateSet<Long> = mutableStateSetOf()

    private class EmCurso(val origem: Pair<Double, Double>, val pedido: Deferred<Itinerario?>)
    private val emCurso = mutableMapOf<Long, EmCurso>()

    /**
     * Reaproveita a guardada se serve; junta-se ao pedido em curso se partiu a 200 m ou menos; senão pede.
     * Um pedido que falhou também fica guardado (itinerário null), mas nunca é reaproveitado: pede-se de novo.
     * Uma sugestão nova da Casa ou do Trabalho vai também para [aoSugerir] (o widget), uma só vez, sem pedidos a mais.
     */
    suspend fun sugerir(
        local: LocalGuardado, posicao: Pair<Double, Double>, validade: Duration,
        aoSugerir: suspend (LocalGuardado, Itinerario, Pair<Double, Double>) -> Unit,
    ): Itinerario? {
        val agora = relogio()
        sugestoes[local.id]?.takeIf { it.itinerario != null && guardadoValido(it.em, it.origem, agora, posicao, validade) }
            ?.let { return it.itinerario }
        emCurso[local.id]?.let { e ->
            if (distanciaMetros(e.origem.first, e.origem.second, posicao.first, posicao.second) <= 200) return e.pedido.await()
            // De mais longe: a rota antiga já não serve, e chegar depois da nova não a pode substituir.
            e.pedido.cancel()
        }
        // Na linha de quem pede (a do ecrã, como antes), mas com a vida do ViewModel: sair do Início não o cancela.
        val pedido = viewModelScope.async(currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext) {
            val partida = Lugar(MINHA_POSICAO, posicao.first, posicao.second, TipoLugar.POSICAO)
            val plano = (planear(partida, local.lugar, agora) as? Resultado.Ok)?.valor?.itinerarios
            val i = plano?.let { corrigir(it, agora).minByOrNull { c -> c.chegada } }
            sugestoes[local.id] = Sugestao(agora, posicao, i, plano)
            if (i != null && (local.tipo == "CASA" || local.tipo == "TRABALHO")) avisarWidget(aoSugerir, local, i, posicao)
            i
        }
        emCurso[local.id] = EmCurso(posicao, pedido)
        calculando += local.id
        pedido.invokeOnCompletion {
            if (emCurso[local.id]?.pedido === pedido) {
                emCurso -= local.id
                calculando -= local.id
            }
        }
        return pedido.await()
    }

    /**
     * Volta a corrigir as sugestões guardadas com os atrasos de agora e fica com a que chega primeiro, sem novo pedido de rotas.
     * Corrige-se sempre o plano como veio: um atraso que passa tem de poder devolver a rota que tinha ficado para trás.
     * Limite: uma correção que muda a linha não vai para o widget; o widget fica com a próxima sugestão nova.
     */
    suspend fun recorrigir() {
        for ((id, s) in sugestoes.toMap()) {
            val plano = s.plano ?: continue
            if (id in emCurso) continue // o pedido novo vai substituí-la
            val i = corrigir(plano, relogio()).minByOrNull { it.chegada }
            // Só se ninguém a trocou entretanto: um pedido acabado durante a correção é mais novo.
            if (sugestoes[id] === s) sugestoes[id] = Sugestao(s.em, s.origem, i, plano)
        }
    }

    // À parte e sem deixar sair erros: um widget lento não atrasa o pedido seguinte, e um que falha não deita o Início abaixo.
    private fun avisarWidget(
        aoSugerir: suspend (LocalGuardado, Itinerario, Pair<Double, Double>) -> Unit, l: LocalGuardado, i: Itinerario, de: Pair<Double, Double>,
    ) {
        viewModelScope.launch {
            try {
                aoSugerir(l, i, de)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
    }
}
