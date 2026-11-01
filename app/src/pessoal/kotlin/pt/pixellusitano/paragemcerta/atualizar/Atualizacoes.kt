package pt.pixellusitano.paragemcerta.atualizar

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
import android.os.Build
import android.os.Process
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.isNewer
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

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
 * Atualizador da variante pessoal. Em segundo plano (AtualizacaoAutomatica) verifica de 6 em 6 horas e instala sem perguntar;
 * quem desligar "Atualizar sozinha" fica com o aviso manual, no máximo uma vez por dia.
 */
object Atualizacoes {
    private const val PREFS = "atualizacoes"
    private const val ULTIMA_VERIFICACAO = "ultima_verificacao"
    private const val SOZINHA = "atualizar_sozinha"
    private const val DADOS_MOVEIS = "usar_dados_moveis"
    private const val NOVIDADES_TAG = "novidades_tag"
    private const val NOVIDADES_NOTAS = "novidades_notas"
    private const val TRABALHO = "atualizacao-automatica"

    /** Canal das notificações de "atualização pronta". */
    const val CANAL = "atualizacoes"

    val disponivel: Boolean = true

    private fun prefs(contexto: Context) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun atualizarSozinha(contexto: Context): Boolean = prefs(contexto).getBoolean(SOZINHA, true)

    fun definirAtualizarSozinha(contexto: Context, valor: Boolean) = prefs(contexto).edit().putBoolean(SOZINHA, valor).apply()

    fun usarDadosMoveis(contexto: Context): Boolean = prefs(contexto).getBoolean(DADOS_MOVEIS, false)

    fun definirUsarDadosMoveis(contexto: Context, valor: Boolean) = prefs(contexto).edit().putBoolean(DADOS_MOVEIS, valor).apply()

    /** (Re)agenda a verificação em segundo plano com as definições atuais, ou cancela-a se "Atualizar sozinha" estiver desligado. */
    fun agendar(contexto: Context) {
        val trabalhos = WorkManager.getInstance(contexto)
        if (!atualizarSozinha(contexto)) {
            trabalhos.cancelUniqueWork(TRABALHO)
            return
        }
        val rede = if (usarDadosMoveis(contexto)) NetworkType.CONNECTED else NetworkType.UNMETERED
        val pedido = PeriodicWorkRequestBuilder<AtualizacaoAutomatica>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(rede).setRequiresBatteryNotLow(true).build())
            .build()
        trabalhos.enqueueUniquePeriodicWork(TRABALHO, ExistingPeriodicWorkPolicy.UPDATE, pedido)
    }

    suspend fun verificar(contexto: Context): NovaVersao? {
        val prefs = prefs(contexto)
        val ultima = Instant.ofEpochSecond(prefs.getLong(ULTIMA_VERIFICACAO, 0))
        if (Duration.between(ultima, Instant.now()) < Duration.ofDays(1)) return null
        prefs.edit().putLong(ULTIMA_VERIFICACAO, Instant.now().epochSecond).apply()
        return VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE)
    }

    /**
     * Descarrega o APK para a cache e entrega-o ao instalador; o Android recusa outra assinatura.
     * Com [semPerguntar] (Android 12+) pede para atualizar sem confirmação, o que só resulta se esta app foi quem instalou a atual;
     * senão o instalador devolve STATUS_PENDING_USER_ACTION e o [ResultadoInstalacao] trata disso.
     */
    suspend fun instalar(contexto: Context, nova: NovaVersao, semPerguntar: Boolean = false) = withContext(Dispatchers.IO) {
        val ficheiro = File(contexto.cacheDir, "atualizacao.apk")
        OkHttpClient().newCall(Request.Builder().url(nova.urlApk).header("User-Agent", userAgent(BuildConfig.VERSION_NAME)).build())
            .execute().use { r ->
                check(r.isSuccessful) { "falha a descarregar a atualização (${r.code})" }
                ficheiro.outputStream().use { r.body.byteStream().copyTo(it) }
            }
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (semPerguntar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            parametros.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessao = instalador.createSession(parametros)
        instalador.openSession(sessao).use { s ->
            s.openWrite("paragem-certa", 0, ficheiro.length()).use { saida ->
                ficheiro.inputStream().use { it.copyTo(saida) }
                s.fsync(saida)
            }
            val aviso = PendingIntent.getBroadcast(
                contexto, sessao, Intent(contexto, ResultadoInstalacao::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            guardarNovidades(contexto, nova)
            s.commit(aviso.intentSender)
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
}

/** Há uma atividade desta app à frente do utilizador? Instalar agora mataria a app a meio do uso. */
internal fun emPrimeiroPlano(contexto: Context): Boolean {
    val eu = contexto.getSystemService(ActivityManager::class.java).runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
    return eu != null && eu.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

/**
 * O instalador responde aqui. Se precisa da confirmação do utilizador: com a app à frente abre o ecrã do sistema,
 * em segundo plano o Android não deixa abrir ecrãs, por isso avisa com uma notificação que abre esse ecrã ao tocar.
 */
class ResultadoInstalacao : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (emPrimeiroPlano(contexto)) contexto.startActivity(confirmar) else avisar(contexto, confirmar)
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
        gestor.notify(NOTIFICACAO, aviso)
    }

    private companion object {
        const val NOTIFICACAO = 1
    }
}
