package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Sessao
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

sealed interface EstadoConta {
    data object SemConta : EstadoConta
    data class Com(val email: String, val sincronizadaEm: Instant?) : EstadoConta
}

/**
 * A sessão guardada no telemóvel. soNoTelemovel = as chaves dos favoritos que já cá estavam quando a pessoa entrou sem os juntar
 * à conta: nunca vão para ela (podem ser de outra pessoa que usou este telemóvel).
 */
@Serializable
private data class SessaoGuardada(
    val token: String,
    val email: String,
    val revisao: Long = 0,
    val sincronizadaEm: Long? = null,
    val soNoTelemovel: Set<String> = emptySet(),
)

private val JSON = Json { ignoreUnknownKeys = true }

/** Entrar, sair, apagar a conta e sincronizar os favoritos e os sítios com ela; só corre com a app aberta. */
class Conta(
    contexto: Context,
    private val servidor: ServidorParagemCerta,
    private val repositorio: Repositorio,
    private val relogio: () -> Instant = Instant::now,
    /** Os widgets guardam a paragem escolhida no telemóvel, fora da base de dados: também saem com os dados. */
    private val esquecerWidgets: suspend () -> Unit = {},
) {
    // Em noBackupFilesDir: o Android nunca o copia, por isso o token não vai parar a outro telemóvel.
    private val ficheiro = File(contexto.noBackupFilesDir, "conta.json")
    // Uma coisa de cada vez: duas sincronizações ao mesmo tempo enviavam o mesmo duas vezes e baralhavam a revisão.
    private val vez = Mutex()
    private var sessao: SessaoGuardada? = ler()
    private val _estado = MutableStateFlow(estadoDe(sessao))
    val estado: StateFlow<EstadoConta> = _estado

    init {
        repositorio.comConta = sessao != null
    }

    suspend fun pedirCodigo(email: String): Resultado<Unit> = servidor.pedirCodigo(email)

    suspend fun nonceGoogle(): Resultado<String> = servidor.nonceGoogle()

    /** Entra e sincroniza desde 0; [juntarFavoritos] = o que está no telemóvel vai para a conta (a pessoa escolheu). */
    suspend fun confirmarCodigo(email: String, codigo: String, juntarFavoritos: Boolean): Resultado<Unit> =
        entrar(servidor.confirmarCodigo(email, codigo), juntarFavoritos)

    suspend fun entrarComGoogle(idToken: String, juntarFavoritos: Boolean): Resultado<Unit> =
        entrar(servidor.entrarComGoogle(idToken), juntarFavoritos)

    private suspend fun entrar(r: Resultado<Sessao>, juntar: Boolean): Resultado<Unit> {
        val nova = when (r) {
            is Resultado.Ok -> r.valor
            is Resultado.Falha -> return r
        }
        vez.withLock {
            // Juntar fica marcado na base de dados e não juntar fica na sessão: uma primeira sincronização sem rede não muda nada.
            val soNoTelemovel = if (juntar) emptySet<String>().also { repositorio.marcarTudoPorEnviar() } else repositorio.chavesNoTelemovel()
            mudar(SessaoGuardada(nova.token, nova.email, soNoTelemovel = soNoTelemovel))
        }
        sincronizar()
        return Resultado.Ok(Unit)
    }

    /** Envia o que está por enviar e aplica o que mudou na conta. Sessão recusada (401) → sai da conta e os favoritos ficam. */
    suspend fun sincronizar(): Resultado<Unit> = vez.withLock {
        val s = sessao ?: return@withLock Resultado.Falha(401, limite = false)
        // No máximo 500 de cada vez (o limite do servidor): o resto continua marcado e vai na sincronização a seguir.
        val enviadas = repositorio.porSincronizar(s.soNoTelemovel).take(MAX_ALTERACOES)
        when (val r = servidor.sincronizar(s.token, s.revisao, enviadas)) {
            is Resultado.Ok -> {
                val recebidas = r.valor.alteracoes
                repositorio.aplicarSincronizacao(enviadas, recebidas, s.soNoTelemovel)
                // O que veio da conta com a mesma chave passou a ser dela.
                val daConta = recebidas.filter { !it.apagado }.map { it.chave }.toSet()
                mudar(s.copy(revisao = r.valor.revisao, sincronizadaEm = relogio().epochSecond, soNoTelemovel = s.soNoTelemovel - daConta))
                Resultado.Ok(Unit)
            }
            is Resultado.Falha -> {
                if (r.codigo == 401) esquecer()
                r
            }
        }
    }

    /**
     * Corre [pedido] com o token da sessão; sem sessão devolve `Falha(401, false, "sem_sessao")` sem pedir nada. Um 401 do
     * servidor = sessão recusada → sai da conta, como na sincronização. O token lê-se sob a vez, mas o pedido corre fora dela:
     * um pedido lento não pode prender a sincronização nem a saída.
     */
    suspend fun <T> comSessao(pedido: suspend (token: String) -> Resultado<T>): Resultado<T> {
        val token = vez.withLock { sessao?.token } ?: return Resultado.Falha(401, limite = false, erro = "sem_sessao")
        val r = pedido(token)
        // Só esquece se a sessão ainda for a deste pedido: a pessoa pode ter saído e entrado noutra conta entretanto.
        if (r is Resultado.Falha && r.codigo == 401) vez.withLock { if (sessao?.token == token) esquecer() }
        return r
    }

    /**
     * Com a app aberta: sincroniza já só se houver sessão e (houver alterações por enviar, ou nunca sincronizou, ou a última
     * foi há 15 min ou mais), e depois 2 s a seguir a cada alteração (também só com alterações por enviar: os favoritos que
     * ficaram só no telemóvel não contam). Quem chama cancela ao sair da app.
     */
    @OptIn(FlowPreview::class)
    suspend fun aoAbrir() {
        sessao?.let { s ->
            val desde = s.sincronizadaEm?.let { relogio().epochSecond - it }
            if (repositorio.porSincronizar(s.soNoTelemovel).isNotEmpty() || desde == null || desde < 0 || desde >= INTERVALO_AO_ABRIR_S) sincronizar()
        }
        // O primeiro valor é o que já lá estava ao abrir; só conta o que muda depois.
        repositorio.porEnviar().drop(1).debounce(ESPERA_MS).collect {
            val s = sessao
            if (s != null && repositorio.porSincronizar(s.soNoTelemovel).isNotEmpty()) sincronizar()
        }
    }

    /** Envia primeiro o que falta (para não se perder na conta), apaga a sessão no servidor se houver rede e esquece-a cá. */
    suspend fun sair() {
        sincronizar()
        vez.withLock {
            sessao?.let { servidor.sair(it.token) }
            esquecer()
        }
    }

    /** Definições → Apagar dados do telemóvel: a sessão também é um dado do telemóvel, por isso sai primeiro (e envia o que faltava). */
    suspend fun apagarDadosDoTelemovel() {
        sair()
        repositorio.apagarDadosDoTelemovel()
        esquecerWidgets()
    }

    /** Apaga a conta e os favoritos guardados nela; no telemóvel ficam. */
    suspend fun apagarConta(): Resultado<Unit> = vez.withLock {
        val s = sessao ?: return@withLock Resultado.Ok(Unit)
        val r = servidor.apagarConta(s.token)
        if (r is Resultado.Ok || (r as Resultado.Falha).codigo == 401) esquecer()
        r
    }

    // Primeiro a sessão e só depois as marcas: se a app morrer a meio, a próxima entrada pergunta outra vez se é para juntar.
    private suspend fun esquecer() {
        mudar(null)
        repositorio.limparMarcas()
    }

    private fun mudar(s: SessaoGuardada?) {
        if (s == null) {
            ficheiro.delete()
        } else {
            // Escreve ao lado e troca de uma vez: a meio de uma escrita nunca fica um ficheiro cortado.
            val novo = File(ficheiro.path + ".novo").apply { writeText(JSON.encodeToString(s)) }
            Files.move(novo.toPath(), ficheiro.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        sessao = s
        repositorio.comConta = s != null
        _estado.value = estadoDe(s)
    }

    // Um ficheiro estragado é o mesmo que não ter sessão: entra-se outra vez.
    private fun ler(): SessaoGuardada? = runCatching { JSON.decodeFromString<SessaoGuardada>(ficheiro.readText()) }.getOrNull()

    private fun estadoDe(s: SessaoGuardada?): EstadoConta =
        s?.let { EstadoConta.Com(it.email, it.sincronizadaEm?.let(Instant::ofEpochSecond)) } ?: EstadoConta.SemConta

    private companion object {
        const val MAX_ALTERACOES = 500
        const val ESPERA_MS = 2_000L
        const val INTERVALO_AO_ABRIR_S = 15 * 60L
    }
}
