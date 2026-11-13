package pt.pixellusitano.paragemcerta.atualizar

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada
import pt.pixellusitano.paragemcerta.core.isNewer
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.Notificacoes
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant

private const val ULTIMA_RELEASE = "https://api.github.com/repos/DaewonPT/paragem-certa/releases/latest"
private val JSON = Json { ignoreUnknownKeys = true }

@Serializable private class ReleaseJson(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val assets: List<AssetJson> = emptyList(),
)

@Serializable private class AssetJson(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/** Lê a última release do GitHub e diz se é mais recente do que a instalada. */
class VerificadorVersoes(private val http: Http) {
    suspend fun verificar(versionCodeAtual: Int): NovaVersao? {
        val r = http.pedir(ULTIMA_RELEASE) { JSON.decodeFromString<ReleaseJson>(it) } as? Resultado.Ok ?: return null
        val release = r.valor
        if (!isNewer(versionCodeAtual, release.tag)) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return NovaVersao(release.tag, apk.url, release.body.orEmpty())
    }
}

/**
 * Atualizador da variante pessoal. Com a app fechada não corre nada: ao abrir ([aoAbrir]) procura uma versão nova, no máximo de
 * 6 em 6 horas, e descarrega-a; quando a pessoa sai da app ([aoSair]) instala-a sem perguntar.
 * Quem desligar "Atualizar sozinha" fica com o aviso manual, no máximo uma vez por dia.
 */
object Atualizacoes {
    private const val PREFS = "atualizacoes"
    private const val ULTIMA_VERIFICACAO = "ultima_verificacao"
    private const val ULTIMA_PROCURA = "ultima_procura"
    private const val SOZINHA = "atualizar_sozinha"
    private const val DADOS_MOVEIS = "usar_dados_moveis"
    private const val NOVIDADES_TAG = "novidades_tag"
    private const val NOVIDADES_NOTAS = "novidades_notas"
    private const val PENDENTE_TAG = "pendente_tag"
    private const val PENDENTE_URL = "pendente_url"
    private const val PENDENTE_NOTAS = "pendente_notas"
    private const val NOTIFICACOES_PEDIDAS = "notificacoes_pedidas"
    private val PROCURA_A_CADA = Duration.ofHours(6)

    /** Canal das notificações de "atualização pronta". */
    const val CANAL = "atualizacoes"

    val disponivel: Boolean = true

    /**
     * A versão já descarregada (o APK está na cache) que espera que a pessoa saia da app. Vive no processo: se ele morrer antes,
     * procura-se e descarrega-se outra vez na procura seguinte.
     */
    @Volatile internal var descarregada: NovaVersao? = null

    /** Fora do ciclo da atividade: sair com "Voltar" destrói-a logo a seguir e a instalação não pode ficar a meio. */
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Um descarregamento de cada vez: rodar o ecrã recomeça a procura, e dois a escrever no mesmo `.parte` estragavam-se um
     * ao outro. Fica em quem chama e não em [descarregar] porque o Mutex não é reentrante.
     */
    private val vezDeDescarregar = Mutex()

    private fun prefs(contexto: Context) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun atualizarSozinha(contexto: Context): Boolean = prefs(contexto).getBoolean(SOZINHA, true)

    fun definirAtualizarSozinha(contexto: Context, valor: Boolean) = prefs(contexto).edit().putBoolean(SOZINHA, valor).apply()

    fun usarDadosMoveis(contexto: Context): Boolean = prefs(contexto).getBoolean(DADOS_MOVEIS, false)

    fun definirUsarDadosMoveis(contexto: Context, valor: Boolean) = prefs(contexto).edit().putBoolean(DADOS_MOVEIS, valor).apply()

    /**
     * Ao abrir a app: com "Atualizar sozinha" ligado procura uma versão nova (só com Wi-Fi, a não ser que a pessoa tenha deixado
     * os dados móveis) e descarrega-a. Se a pessoa sair a meio, quem chama cancela e o ficheiro incompleto nunca se instala;
     * a abertura seguinte continua-o.
     */
    suspend fun aoAbrir(contexto: Context, agora: Instant = Instant.now()) {
        if (descarregada != null || !reservarProcura(contexto, agora)) return
        try {
            val nova = VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE) ?: return
            // A app está à frente, por isso o melhor que `decidir` diz é ESPERAR: descarrega já, instala à saída.
            if (decidir(nova, atualizarSozinha(contexto), emPrimeiroPlano = true, pendente(contexto)?.tag) == Decisao.NADA) return
            vezDeDescarregar.withLock { descarregar(nova, ficheiroApk(contexto)) }
            descarregada = nova
        } catch (e: CancellationException) {
            // Saíste a meio: a abertura seguinte procura outra vez em vez de esperar 6 horas por uma transferência que não acabou.
            liberarProcura(contexto)
            throw e
        }
    }

    internal fun liberarProcura(contexto: Context) = prefs(contexto).edit().remove(ULTIMA_PROCURA).apply()

    /** Diz se é altura de procurar e, se for, gasta já a tentativa: no máximo de 6 em 6 horas, mesmo que a procura falhe. */
    internal fun reservarProcura(contexto: Context, agora: Instant): Boolean {
        if (!atualizarSozinha(contexto)) return false
        val prefs = prefs(contexto)
        if (Duration.between(Instant.ofEpochSecond(prefs.getLong(ULTIMA_PROCURA, 0)), agora) < PROCURA_A_CADA) return false
        // Sem rede não gasta a tentativa: a abertura seguinte procura.
        val rede = contexto.getSystemService(ConnectivityManager::class.java)
        if (rede.activeNetwork == null || (rede.isActiveNetworkMetered && !usarDadosMoveis(contexto))) return false
        prefs.edit().putLong(ULTIMA_PROCURA, agora.epochSecond).apply()
        return true
    }

    /**
     * A pessoa saiu da app (a atividade parou sem ser por rodar o ecrã): é agora que se instala a versão que ficou descarregada,
     * a não ser que haja uma viagem ou um aviso de chegada a correr (ver [decidir]).
     */
    fun aoSair(contexto: Context) {
        if (descarregada == null) return
        escopo.launch {
            // Nada aqui pode deitar a app abaixo: ela já está fechada. Se a sessão falhar, a versão fica pendente para o aviso.
            runCatching { instalarAoSair(contexto, FicheirosViagem.porAcabar(contexto), ServicoAvisoChegada.aCorrer) }
        }
    }

    @Synchronized
    internal fun instalarAoSair(contexto: Context, emViagem: Boolean, comAviso: Boolean): Decisao {
        val nova = descarregada ?: return Decisao.NADA
        val decisao = decidir(nova, atualizarSozinha(contexto), emPrimeiroPlano = false, pendente(contexto)?.tag, emViagem, comAviso)
        when (decisao) {
            Decisao.NADA -> descarregada = null
            Decisao.ESPERAR -> Unit // fica descarregada para a saída seguinte
            Decisao.INSTALAR -> {
                descarregada = null
                abrirSessao(contexto, nova, ficheiroApk(contexto), semPerguntar = true)
            }
        }
        return decisao
    }

    suspend fun verificar(contexto: Context): NovaVersao? {
        val prefs = prefs(contexto)
        val ultima = Instant.ofEpochSecond(prefs.getLong(ULTIMA_VERIFICACAO, 0))
        if (Duration.between(ultima, Instant.now()) < Duration.ofDays(1)) return null
        prefs.edit().putLong(ULTIMA_VERIFICACAO, Instant.now().epochSecond).apply()
        return VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE)
    }

    private fun ficheiroApk(contexto: Context) = File(contexto.cacheDir, "atualizacao.apk")

    /** O "Atualizar" do aviso tem o seu ficheiro: nunca apaga nem mistura a versão que a atualização automática deixou para a saída. */
    private fun ficheiroManual(contexto: Context) = File(contexto.cacheDir, "atualizacao-manual.apk")

    /**
     * Descarrega o APK para [ficheiro]. Escreve num ficheiro à parte e só o renomeia no fim, por isso um descarregamento cortado
     * nunca é instalado, e o seguinte continua onde esse ficou, se for da mesma versão.
     */
    internal suspend fun descarregar(nova: NovaVersao, ficheiro: File): File = withContext(Dispatchers.IO) {
        val parte = File(ficheiro.path + ".parte")
        // O endereço tem a tag (…/download/v0.5.4/paragem-certa-v0.5.4.apk): um `.parte` de outra versão, ou sem este ficheiro
        // (deixado por uma versão antiga), deita-se fora em vez de se continuar.
        val origem = File(ficheiro.path + ".parte.url")
        if (!origem.exists() || origem.readText() != nova.urlApk) {
            parte.delete()
            origem.writeText(nova.urlApk)
        }
        // Limite: um `.parte` que ninguém continua fica na cache (até 56 MB) até ao descarregamento seguinte ou até o Android a limpar.
        // Limite: se o APK da mesma tag for substituído a meio (o CI volta a publicar com --clobber), juntam-se pedaços de dois
        // ficheiros; o Android recusa-o pela assinatura, a versão fica pendente e o "Atualizar" descarrega-a de novo.
        val desde = parte.length()
        val pedido = Request.Builder().url(nova.urlApk).header("User-Agent", userAgent(BuildConfig.VERSION_NAME))
        // O OkHttp leva o Range pelo redirecionamento do GitHub e, com ele, não pede gzip: os bytes são os do ficheiro.
        if (desde > 0) pedido.header("Range", "bytes=$desde-")
        OkHttpClient().newCall(pedido.build()).execute().use { r ->
            // 416: o `.parte` já tem o tamanho todo (o processo morreu antes de o renomear); a vez seguinte recomeça do zero.
            if (r.code == 416) {
                parte.delete()
                origem.delete()
            }
            check(r.isSuccessful) { "falha a descarregar a atualização (${r.code})" }
            val bloco = ByteArray(64 * 1024)
            r.body.byteStream().use { entrada ->
                // 206 continua o `.parte`; 200 (o servidor ignorou o Range) escreve por cima, do zero.
                FileOutputStream(parte, r.code == 206).use { saida ->
                    while (true) {
                        val lidos = entrada.read(bloco)
                        if (lidos < 0) break
                        ensureActive() // a pessoa saiu da app: pára de descarregar
                        saida.write(bloco, 0, lidos)
                    }
                }
            }
        }
        ficheiro.delete()
        check(parte.renameTo(ficheiro)) { "falha a guardar a atualização" }
        origem.delete()
        ficheiro
    }

    /**
     * Descarrega o APK para a cache e entrega-o ao instalador; o Android recusa outra assinatura.
     * Com [semPerguntar] (Android 12+) pede para atualizar sem confirmação, o que só resulta se esta app foi quem instalou a atual;
     * senão o instalador devolve STATUS_PENDING_USER_ACTION e o [ResultadoInstalacao] trata disso.
     */
    suspend fun instalar(contexto: Context, nova: NovaVersao, semPerguntar: Boolean = false) = withContext(Dispatchers.IO) {
        // Só larga a vez depois de o instalador ter a sua cópia: um segundo toque não apaga o ficheiro a meio.
        // Limite: um toque espera calado atrás de uma atualização automática a descarregar, e um segundo toque no aviso de uma
        // versão pendente, ao reabrir a app com o descarregamento a meio, volta a descarregar e abre outra sessão do instalador.
        vezDeDescarregar.withLock { abrirSessao(contexto, nova, descarregar(nova, ficheiroManual(contexto)), semPerguntar) }
    }

    /**
     * Entrega o APK já descarregado ao instalador e apaga-o, mesmo que falhe: a sessão guarda a sua cópia, e assim uma saída
     * seguinte não o instala outra vez.
     */
    private fun abrirSessao(contexto: Context, nova: NovaVersao, ficheiro: File, semPerguntar: Boolean) {
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (semPerguntar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            parametros.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        var sessao: Int? = null
        try {
            sessao = instalador.createSession(parametros)
            instalador.openSession(sessao).use { s ->
                s.openWrite("paragem-certa", 0, ficheiro.length()).use { saida ->
                    ficheiro.inputStream().use { it.copyTo(saida) }
                    s.fsync(saida)
                }
                val aviso = PendingIntent.getBroadcast(
                    contexto, sessao, Intent(contexto, ResultadoInstalacao::class.java).comVersao(nova),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                guardarNovidades(contexto, nova)
                s.commit(aviso.intentSender)
            }
        } catch (e: Exception) {
            // Sem espaço, ou sem o APK (o Android limpa a cache quando falta espaço): fecha a sessão, que senão ficava com a cópia
            // a meio a ocupar espaço, e deixa a versão pendente, como as recusas que chegam ao [ResultadoInstalacao].
            sessao?.let { runCatching { instalador.abandonSession(it) } }
            guardarPendente(contexto, nova)
            throw e
        } finally {
            ficheiro.delete()
        }
    }

    /** Guarda as notas da versão que vai ser instalada, para as mostrar quando a app abrir já atualizada. */
    @SuppressLint("ApplySharedPref") // commit e não apply: a instalação mata a app, o que ainda estivesse na fila perdia-se
    internal fun guardarNovidades(contexto: Context, nova: NovaVersao) {
        prefs(contexto).edit().putString(NOVIDADES_TAG, nova.tag).putString(NOVIDADES_NOTAS, nova.notas).commit()
    }

    /** As notas guardadas, se forem da versão que está a correr e ainda não tiverem sido mostradas. */
    fun novidadesPorMostrar(contexto: Context): NovaVersao? {
        val prefs = prefs(contexto)
        val tag = prefs.getString(NOVIDADES_TAG, null)?.takeIf { it == "v" + BuildConfig.VERSION_NAME } ?: return null
        return NovaVersao(tag, "", prefs.getString(NOVIDADES_NOTAS, "").orEmpty())
    }

    fun marcarNovidadesVistas(contexto: Context) = prefs(contexto).edit().remove(NOVIDADES_TAG).remove(NOVIDADES_NOTAS).apply()

    /** Guarda a versão que ficou à espera de um toque do utilizador, ou que o Android recusou, para a app a mostrar quando abrir. */
    @SuppressLint("ApplySharedPref") // commit e não apply: o processo pode morrer logo a seguir ao receiver
    internal fun guardarPendente(contexto: Context, nova: NovaVersao) {
        prefs(contexto).edit().putString(PENDENTE_TAG, nova.tag).putString(PENDENTE_URL, nova.urlApk).putString(PENDENTE_NOTAS, nova.notas).commit()
    }

    /** A versão à espera de confirmação, ou recusada, enquanto for mais nova do que a que está a correr; senão já foi instalada (ou ultrapassada) e apaga-se. */
    fun pendente(contexto: Context): NovaVersao? {
        val prefs = prefs(contexto)
        val tag = prefs.getString(PENDENTE_TAG, null) ?: return null
        if (!isNewer(BuildConfig.VERSION_CODE, tag)) {
            limparPendente(contexto)
            return null
        }
        return NovaVersao(tag, prefs.getString(PENDENTE_URL, "").orEmpty(), prefs.getString(PENDENTE_NOTAS, "").orEmpty())
    }

    fun limparPendente(contexto: Context) = prefs(contexto).edit().remove(PENDENTE_TAG).remove(PENDENTE_URL).remove(PENDENTE_NOTAS).apply()

    /**
     * Android 13+: sem a permissão a notificação "atualização pronta" é descartada. Pede-se uma só vez, e só a quem deixou
     * a atualização automática ligada (é ela que precisa de avisar); quem recusa pode dá-la nas definições do sistema.
     */
    fun pedirNotificacoes(contexto: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && atualizarSozinha(contexto) &&
            !prefs(contexto).getBoolean(NOTIFICACOES_PEDIDAS, false) &&
            contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun marcarNotificacoesPedidas(contexto: Context) = prefs(contexto).edit().putBoolean(NOTIFICACOES_PEDIDAS, true).apply()
}

/** O instalador devolve ao receiver o intent com que a sessão foi criada, por isso a versão viaja nele. */
internal fun Intent.comVersao(nova: NovaVersao): Intent =
    putExtra("versao_tag", nova.tag).putExtra("versao_url", nova.urlApk).putExtra("versao_notas", nova.notas)

internal fun Intent.versao(): NovaVersao? =
    getStringExtra("versao_tag")?.let { NovaVersao(it, getStringExtra("versao_url").orEmpty(), getStringExtra("versao_notas").orEmpty()) }

/** Há uma atividade desta app à frente do utilizador? Instalar agora mataria a app a meio do uso. */
internal fun emPrimeiroPlano(contexto: Context): Boolean {
    val eu = contexto.getSystemService(ActivityManager::class.java).runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
    return eu != null && eu.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

/**
 * O instalador responde aqui. Se precisa da confirmação do utilizador: com a app à frente abre o ecrã do sistema,
 * em segundo plano o Android não deixa abrir ecrãs, por isso avisa com uma notificação que abre esse ecrã ao tocar.
 * Se o Android recusar a instalação, ou a pessoa a cancelar, a versão fica pendente e a app mostra o aviso quando abrir.
 */
class ResultadoInstalacao : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        // Recusada pelo Android (sem espaço, outra assinatura, perfil de trabalho) ou cancelada no ecrã do sistema: fica pendente,
        // para a app a mostrar no aviso em vez de a voltar a descarregar de 6 em 6 horas sem dizer nada.
        if (estado >= PackageInstaller.STATUS_FAILURE) intent.versao()?.let { Atualizacoes.guardarPendente(contexto, it) }
        if (estado != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (emPrimeiroPlano(contexto)) {
            contexto.startActivity(confirmar)
        } else {
            // A notificação pode ser descartada (Android 13+ sem permissão): a app mostra o aviso quando abrir.
            intent.versao()?.let { Atualizacoes.guardarPendente(contexto, it) }
            avisar(contexto, confirmar)
        }
    }

    private fun avisar(contexto: Context, confirmar: Intent) {
        val gestor = contexto.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(
            NotificationChannel(Atualizacoes.CANAL, contexto.getString(R.string.atualizacao_canal), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val abrir = PendingIntent.getActivity(contexto, 0, confirmar, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val aviso = Notification.Builder(contexto, Atualizacoes.CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(contexto.getString(R.string.atualizacao_pronta))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        gestor.notify(Notificacoes.ID_ATUALIZACAO, aviso)
    }
}
