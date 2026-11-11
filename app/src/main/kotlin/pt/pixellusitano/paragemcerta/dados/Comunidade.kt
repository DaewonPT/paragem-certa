package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.core.comunidade.Correcao
import pt.pixellusitano.paragemcerta.core.comunidade.MotivoDenuncia
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinhaValida
import pt.pixellusitano.paragemcerta.core.comunidade.chaveParagemValida
import pt.pixellusitano.paragemcerta.core.fontes.servidor.DadosComunidade
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoCriado
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Voto
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Os relatos e as correções da comunidade e as ações da pessoa (relatar, votar, corrigir, denunciar).
 * Só lê com o interruptor `comunidade` ligado na config; `correcoes` desligado limpa essa parte.
 * Os ocultos ("relato:<id>") só tiram relatos das listas, aqui: nunca vão para o servidor.
 */
class Comunidade(
    private val servidor: ServidorParagemCerta,
    private val conta: Conta,
    private val configuracoes: Configuracoes,
    private val preferencias: Preferencias,
    private val relogio: () -> Instant = Instant::now,
) {
    private class Guardado<T>(val em: Instant, val dados: T)

    // Limite: nunca se limpam (ocupam pouco e a app vive pouco); limpar se a camada passar a cobrir o país inteiro.
    // Uma leitura que falha deixa a entrada como estava: é o que se mostra até haver rede.
    private val paragens = ConcurrentHashMap<String, Guardado<DadosComunidade>>()
    private val linhas = ConcurrentHashMap<String, Guardado<List<RelatoRemoto>>>()

    // O mapa lê-se de uma vez: a cache só se escreve depois da resposta, e sem isto duas voltas ao mapa a meio pediam o mesmo duas vezes.
    private val vezDoMapa = Mutex()

    private val comunidadeLigada get() = configuracoes.atual.value.interruptores.comunidade

    /** O email da conta com sessão, ou null sem conta: é para lá que o servidor manda o recibo e a decisão de uma denúncia, e nunca para outro. */
    val emailDaConta: String? get() = (conta.estado.value as? EstadoConta.Com)?.email

    /** O ecrã da paragem: sempre do servidor, sem cache. */
    suspend fun daParagem(chave: String): Resultado<DadosComunidade> {
        if (!comunidadeLigada) return Resultado.Ok(DadosComunidade())
        return when (val r = servidor.comunidade(listOf(chave))) {
            is Resultado.Ok -> Resultado.Ok(r.valor.visivel())
            is Resultado.Falha -> r
        }
    }

    /**
     * O mapa: cada paragem guarda-se 2 min e pedem-se só as que faltam, 100 de cada vez; se falhar, fica o que estava guardado.
     * Só vão chaves que o servidor aceita (uma má dava 400 ao pedido inteiro) e só há um pedido do mapa de cada vez: uma volta
     * ao mapa a meio de outra espera por ela e, com a resposta já guardada, não pede o mesmo outra vez.
     */
    suspend fun dasParagens(chaves: List<String>): DadosComunidade {
        if (!comunidadeLigada) return DadosComunidade()
        val unicas = chaves.distinct().filter(::chaveParagemValida)
        vezDoMapa.withLock {
            val agora = relogio()
            for (grupo in unicas.filter { !recente(paragens[it], agora) }.chunked(MAX_PARAGENS)) {
                currentCoroutineContext().ensureActive() // cancelado: o grupo em voo acaba, os seguintes já ninguém os quer
                // O OkHttp não pára um pedido a meio: se quem pediu for cancelado, a resposta que chega fica guardada na mesma.
                withContext(NonCancellable) {
                    (servidor.comunidade(grupo) as? Resultado.Ok)?.valor?.also { d -> grupo.forEach { paragens[it] = Guardado(agora, d.so(it)) } }
                } ?: break
            }
        }
        val todos = unicas.mapNotNull { paragens[it]?.dados }
        return DadosComunidade(
            relatos = todos.flatMap { it.relatos },
            correcoes = todos.flatMap { it.correcoes },
        ).visivel()
    }

    /** As rotas: os relatos confirmados das linhas (pelo nome visível), cada linha guardada 2 min, 20 de cada vez (o máximo do servidor). */
    suspend fun confirmadosNasLinhas(nomes: Collection<String>): List<RelatoRemoto> {
        if (!comunidadeLigada) return emptyList()
        val agora = relogio()
        // Só chaves que o servidor aceita: uma má (um nome com "/" ou com mais de 16 letras) dava 400 ao grupo inteiro e aos seguintes.
        val chaves = nomes.map(::chaveLinha).filter(::chaveLinhaValida).distinct()
        for (grupo in chaves.filter { !recente(linhas[it], agora) }.chunked(MAX_LINHAS)) {
            val relatos = (servidor.avisos(grupo) as? Resultado.Ok)?.valor?.relatos ?: break
            // Uma linha sem relatos também fica guardada (vazia): não se pergunta outra vez nos 2 min seguintes.
            grupo.forEach { linha -> linhas[linha] = Guardado(agora, relatos.filter { it.linha == linha }) }
        }
        val ocultos = preferencias.ocultos.first()
        return chaves.flatMap { linhas[it]?.dados.orEmpty() }.filter { it.confirmado && "relato:${it.id}" !in ocultos }
    }

    /**
     * O ecrã da linha: os relatos ativos da linha (confirmados e por confirmar), sem os que a pessoa ocultou. Sem a comunidade ligada, ou
     * com um nome que o servidor recusaria, não pede nada; uma falha dá a lista vazia (a secção não aparece).
     */
    suspend fun daLinha(nome: String): List<RelatoRemoto> {
        val chave = chaveLinha(nome).takeIf { comunidadeLigada && chaveLinhaValida(it) } ?: return emptyList()
        val relatos = (servidor.relatosDasLinhas(listOf(chave)) as? Resultado.Ok)?.valor ?: return emptyList()
        val ocultos = preferencias.ocultos.first()
        return relatos.filter { "relato:${it.id}" !in ocultos }
    }

    suspend fun relatar(tipo: TipoRelato, paragem: String, linha: String?): Resultado<RelatoCriado> =
        conta.comSessao { servidor.relatar(it, tipo, paragem, linha) }

    suspend fun votar(relato: String, aindaEsta: Boolean): Resultado<Voto> =
        conta.comSessao { servidor.votar(it, relato, aindaEsta) }

    suspend fun corrigir(paragem: String, correcao: Correcao): Resultado<Unit> =
        conta.comSessao { servidor.corrigir(it, paragem, correcao) }

    suspend fun denunciar(conteudo: String, motivo: MotivoDenuncia, nome: String, explicacao: String): Resultado<Unit> =
        conta.comSessao { servidor.denunciar(it, conteudo, motivo, nome, explicacao) }

    /** Aceita os termos em vigor (os da config), que o servidor pede antes de relatar ou votar. */
    suspend fun aceitarTermos(): Resultado<Unit> {
        val versao = configuracoes.atual.value.versaoTermos
        return conta.comSessao { servidor.aceitarTermos(it, versao) }
    }

    private fun recente(g: Guardado<*>?, agora: Instant) = g != null && agora < g.em + GUARDAR

    // O que o servidor devolveu, só da paragem [chave].
    private fun DadosComunidade.so(chave: String) = DadosComunidade(
        relatos = relatos.filter { it.paragem == chave },
        correcoes = correcoes.filter { it.paragem == chave },
    )

    // Guarda-se o que o servidor disse e filtra-se ao ler: esconder um relato ou ligar um interruptor vale logo, sem esperar pela cache.
    private suspend fun DadosComunidade.visivel(): DadosComunidade {
        val interruptores = configuracoes.atual.value.interruptores
        val ocultos = preferencias.ocultos.first()
        return DadosComunidade(
            relatos = relatos.filter { "relato:${it.id}" !in ocultos },
            correcoes = if (interruptores.correcoes) correcoes else emptyList(),
        )
    }

    private companion object {
        val GUARDAR: Duration = Duration.ofMinutes(2)
        const val MAX_PARAGENS = 100
        const val MAX_LINHAS = 20
    }
}
