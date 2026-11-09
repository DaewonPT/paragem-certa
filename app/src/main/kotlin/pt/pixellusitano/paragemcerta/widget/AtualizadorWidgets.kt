package pt.pixellusitano.paragemcerta.widget

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant

private val ENTRE_TOQUES: Duration = Duration.ofSeconds(30)
private val GUARDADA_VALE: Duration = Duration.ofSeconds(60)
private const val MAX_PEDIDO_MS = 8_000L

/** [todos]: os ids dos widgets postos agora (o Android é que sabe); [despachoDoPedido]: os testes trocam-no para o pedido correr no tempo virtual deles. */
class AtualizadorWidgets(
    private val dados: DadosWidget,
    private val lerGuardadas: suspend (RefParagem, Instant) -> Pair<List<Chegada>, Instant>?,
    private val lerCores: suspend () -> Map<String, String>,
    private val pedir: suspend (RefParagem, Instant) -> Resultado<List<Chegada>>,
    private val desenhar: (Int, ConteudoWidget) -> Unit,
    private val todos: () -> Collection<Int>,
    private val relogio: () -> Instant = Instant::now,
    private val despachoDoPedido: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Só o que está guardado: é o que corre quando o Android acorda o widget, por isso nunca vai à rede. */
    suspend fun redesenhar(ids: Collection<Int>) = ids.forEach { desenharGuardadas(it, EstadoWidget.Normal) }

    /**
     * A app abriu: uma leitura por paragem de widget (dois widgets na mesma paragem fazem um pedido), só se a guardada tiver
     * mais de 60 s, para voltar à app várias vezes não gastar pedidos. Depois redesenha todos, com ou sem resposta.
     */
    suspend fun aoAbrirApp() {
        val ids = todos().toList()
        ids.mapNotNull { paragemDe(it)?.first }.distinct().forEach { ref ->
            val agora = relogio() // de cada vez: um pedido lento não pode dar a hora velha ao seguinte
            val guardadaEm = lerGuardadas(ref, agora)?.second
            // Negativo: o relógio andou para trás, e uma guardada "do futuro" não pode calar os pedidos até ele a alcançar.
            val passou = guardadaEm?.let { Duration.between(it, agora) }
            if (passou != null && !passou.isNegative && passou <= GUARDADA_VALE) return@forEach
            try {
                pedir(ref, agora)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive() // uma paragem que falha não impede as outras; sair da app (cancelar) continua a subir
            }
        }
        redesenhar(ids)
    }

    /** A app fechou: só o que já está guardado, sem rede. */
    // Só leituras locais e um desenho; o Back no Android 8 a 11 cancela o âmbito da atividade logo a seguir ao onStop.
    suspend fun aoSairDaApp() = withContext(NonCancellable) { redesenhar(todos()) }

    /** Apagar dados do telemóvel: os widgets deixam de ter paragem e ficam a pedir uma. */
    suspend fun aoApagarDados() {
        val ids = todos().toList()
        dados.esquecerTudo()
        redesenhar(ids)
    }

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
        if (resposta is Resultado.Ok) desenhar(id, conteudoDoWidget(nome, resposta.valor to agora, relogio(), EstadoWidget.Normal, lerCores()))
        else desenharGuardadas(id, EstadoWidget.SemLigacao)
    }

    private suspend fun desenharGuardadas(id: Int, estado: EstadoWidget) {
        val agora = relogio()
        val paragem = paragemDe(id)
        desenhar(id, conteudoDoWidget(paragem?.second, paragem?.let { lerGuardadas(it.first, agora) }, agora, estado, lerCores()))
    }

    /** Uma chave que não se lê conta como não haver paragem: o widget pede outra em vez de ficar parado. */
    private fun paragemDe(id: Int): Pair<RefParagem, String>? {
        val (chave, nome) = dados.escolha(id) ?: return null
        return (RefParagem.daChave(chave) ?: return null) to nome
    }
}
