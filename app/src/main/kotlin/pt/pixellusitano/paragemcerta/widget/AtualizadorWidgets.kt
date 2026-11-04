package pt.pixellusitano.paragemcerta.widget

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant

private val ENTRE_TOQUES: Duration = Duration.ofSeconds(30)
private const val MAX_PEDIDO_MS = 8_000L

/** [despachoDoPedido]: os testes trocam-no para o pedido correr no tempo virtual deles. */
class AtualizadorWidgets(
    private val dados: DadosWidget,
    private val lerGuardadas: suspend (RefParagem, Instant) -> Pair<List<Chegada>, Instant>?,
    private val pedir: suspend (RefParagem, Instant) -> Resultado<List<Chegada>>,
    private val desenhar: (Int, ConteudoWidget) -> Unit,
    private val relogio: () -> Instant = Instant::now,
    private val despachoDoPedido: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Só o que está guardado: é o que corre quando o Android acorda o widget, por isso nunca vai à rede. */
    suspend fun redesenhar(ids: Collection<Int>) = ids.forEach { desenharGuardadas(it, EstadoWidget.Normal) }

    suspend fun aoTocar(id: Int) {
        val (ref, nome) = paragemDe(id) ?: return desenharGuardadas(id, EstadoWidget.Normal)
        val agora = relogio()
        // Negativo: o relógio andou para trás, e o botão não pode ficar preso até ele voltar à hora antiga.
        val passou = dados.ultimaTentativa(id)?.let { Duration.between(it, agora) }
        if (passou != null && !passou.isNegative && passou < ENTRE_TOQUES) return
        dados.marcarTentativa(id, agora)
        desenharGuardadas(id, EstadoWidget.AAtualizar)

        // Num âmbito à parte: o OkHttp não pára um pedido a meio, e como filho desta corrotina prendia-a até o pedido acabar.
        val pedido = CoroutineScope(despachoDoPedido).async { pedir(ref, agora) }
        val resposta = try {
            withTimeoutOrNull(MAX_PEDIDO_MS) { pedido.await() }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null // um pedido que rebenta é um pedido falhado; só o cancelamento desta corrotina continua a subir
        } finally {
            pedido.cancel()
        }
        if (resposta is Resultado.Ok) desenhar(id, conteudoDoWidget(nome, resposta.valor to agora, relogio(), EstadoWidget.Normal))
        else desenharGuardadas(id, EstadoWidget.SemLigacao)
    }

    private suspend fun desenharGuardadas(id: Int, estado: EstadoWidget) {
        val agora = relogio()
        val paragem = paragemDe(id)
        desenhar(id, conteudoDoWidget(paragem?.second, paragem?.let { lerGuardadas(it.first, agora) }, agora, estado))
    }

    /** Uma chave que não se lê conta como não haver paragem: o widget pede outra em vez de ficar parado. */
    private fun paragemDe(id: Int): Pair<RefParagem, String>? {
        val (chave, nome) = dados.escolha(id) ?: return null
        return (RefParagem.daChave(chave) ?: return null) to nome
    }
}
