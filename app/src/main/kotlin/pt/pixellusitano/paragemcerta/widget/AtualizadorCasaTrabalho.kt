package pt.pixellusitano.paragemcerta.widget

import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.inicio.EstadoLocal
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import pt.pixellusitano.paragemcerta.ui.inicio.estadoDoLocal
import pt.pixellusitano.paragemcerta.ui.inicio.melhorLigacao
import pt.pixellusitano.paragemcerta.ui.rotas.MINHA_POSICAO
import pt.pixellusitano.paragemcerta.ui.rotas.minutosDe
import java.time.Duration
import java.time.Instant

private val ENTRE_TOQUES: Duration = Duration.ofSeconds(30)
private const val MAX_POSICAO_MS = 3_000L
private const val MAX_PEDIDOS_MS = 6_000L

/**
 * O widget "Casa e Trabalho". Só vai à rede no ↻ ([atualizar], dentro do receiver: no máximo 3 s de posição e 6 s de pedidos);
 * com a app aberta usa o que o painel já calculou ([aoSugerir]). [todos]: os ids dos widgets postos agora; [despachoDoPedido]: os
 * testes trocam-no para o pedido correr no tempo virtual deles.
 */
class AtualizadorCasaTrabalho(
    private val dados: DadosWidget,
    private val lerLocais: suspend () -> List<LocalGuardado>,
    private val planear: suspend (Lugar, Lugar, Instant) -> Resultado<PlanoRotas>,
    private val posicaoAtual: suspend () -> PosicaoComHora?,
    private val desenhar: (Int, ConteudoCasaTrabalho) -> Unit,
    private val todos: () -> Collection<Int>,
    private val relogio: () -> Instant = Instant::now,
    private val despachoDoPedido: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Só o que está guardado, sem rede. */
    suspend fun redesenhar() = desenharTodos(lerLocais(), aCalcular = false)

    /** O ↻ de qualquer um destes widgets: a posição atual (ou a guardada), um pedido por destino em paralelo, e redesenha todos. */
    suspend fun atualizar() {
        val agora = relogio()
        // Uma chave só para todos: com dois widgets postos, cada toque não pode pedir outra vez. Negativo: o relógio andou para trás.
        val passou = dados.ultimaTentativaCasaTrabalho()?.let { Duration.between(it, agora) }
        if (passou != null && !passou.isNegative && passou < ENTRE_TOQUES) return
        dados.marcarTentativaCasaTrabalho(agora) // antes da primeira suspensão: dois toques seguidos não passam os dois
        val locais = lerLocais()
        desenharTodos(locais, aCalcular = true)

        val atual = try {
            withTimeoutOrNull(MAX_POSICAO_MS) { posicaoAtual() }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null // sem posição atual fica a guardada; só o cancelamento desta corrotina continua a subir
        }
        if (atual != null) dados.guardarPosicao(atual)
        val posicao = atual ?: dados.posicao() ?: return desenharTodos(locais, aCalcular = false)
        dados.marcarDaPosicaoGuardada(atual == null)

        val de = Lugar(MINHA_POSICAO, posicao.lat, posicao.lon, TipoLugar.POSICAO)
        val destinos = casaETrabalho(locais).filterNotNull().filter { estadoDoLocal(it, posicao.lat to posicao.lon) != EstadoLocal.PERTO }
        // Cada um no seu âmbito: o OkHttp não pára um pedido a meio, e como filho desta corrotina prendia-a até o pedido acabar.
        val pedidos = destinos.associateWith { l -> CoroutineScope(despachoDoPedido).async { melhorLigacao(planear, de, l.lugar, agora) } }
        try {
            withTimeoutOrNull(MAX_PEDIDOS_MS) { pedidos.values.joinAll() }
        } finally {
            pedidos.values.forEach { it.cancel() }
        }
        for ((local, pedido) in pedidos) {
            // Um pedido que rebentou ou não chegou a tempo conta como falhado.
            val r = if (pedido.isCompleted && !pedido.isCancelled) pedido.await() else null
            val ligacao = when (r) {
                is Resultado.Ok -> r.valor?.let { ligacaoDe(it, agora) } ?: semItinerario(EstadoLigacao.SEM_TRANSPORTES, agora)
                else -> semItinerario(EstadoLigacao.FALHOU, agora)
            }
            dados.guardarLigacao(local.tipo, ligacao)
        }
        desenharTodos(locais, aCalcular = false)
    }

    /** O painel calculou a sugestão de [local] a partir de [de]: guarda-a e redesenha, sem pedidos novos. */
    suspend fun aoSugerir(local: LocalGuardado, itinerario: Itinerario, de: Pair<Double, Double>) {
        dados.guardarLigacao(local.tipo, ligacaoDe(itinerario, relogio()))
        dados.marcarDaPosicaoGuardada(false)
        // Sem posição guardada (o pedido do Início ainda não respondeu), fica a do painel, sem hora: não se sabe de quando é.
        if (dados.posicao() == null) dados.guardarPosicao(PosicaoComHora(de.first, de.second, hora = 0))
        redesenhar()
    }

    private fun desenharTodos(locais: List<LocalGuardado>, aCalcular: Boolean) {
        val (casa, trabalho) = casaETrabalho(locais)
        val ligacoes = TIPOS.mapNotNull { t -> dados.ligacao(t)?.let { t to it } }.toMap()
        val conteudo = conteudoCasaTrabalho(casa, trabalho, ligacoes, dados.posicao(), dados.daPosicaoGuardada(), relogio(), aCalcular)
        todos().forEach { desenhar(it, conteudo) }
    }

    private fun casaETrabalho(locais: List<LocalGuardado>) = TIPOS.map { t -> locais.firstOrNull { it.tipo == t } }

    private companion object {
        val TIPOS = listOf("CASA", "TRABALHO")

        /** A linha da primeira etapa em veículo (ou a pé, se a viagem for só a pé), como o cartão do Início. */
        fun ligacaoDe(i: Itinerario, em: Instant): LigacaoGuardada {
            val e = i.etapas.firstOrNull { !it.aPe }
            return LigacaoGuardada(
                e?.linhaNome, e?.let { corDaLinha(it.cor, it.modo).toArgb() } ?: 0, e?.modo ?: Modo.OUTRO, soAPe = e == null,
                i.partida.toEpochMilli(), minutosDe(i.duracao).toInt(), em.toEpochMilli(), EstadoLigacao.OK,
            )
        }

        fun semItinerario(estado: EstadoLigacao, em: Instant) = LigacaoGuardada(null, 0, Modo.OUTRO, false, 0, 0, em.toEpochMilli(), estado)
    }
}
