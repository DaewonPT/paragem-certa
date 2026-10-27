package pt.pixellusitano.paragemcerta.atualizar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.isNewer
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import java.io.File
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

/** Atualizador da variante pessoal: verifica no máximo uma vez por dia e instala com o instalador do Android. */
object Atualizacoes {
    private const val PREFS = "atualizacoes"
    private const val ULTIMA_VERIFICACAO = "ultima_verificacao"

    suspend fun verificar(contexto: Context): NovaVersao? {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultima = Instant.ofEpochSecond(prefs.getLong(ULTIMA_VERIFICACAO, 0))
        if (Duration.between(ultima, Instant.now()) < Duration.ofDays(1)) return null
        prefs.edit().putLong(ULTIMA_VERIFICACAO, Instant.now().epochSecond).apply()
        return VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE)
    }

    /** Descarrega o APK para a cache e entrega-o ao instalador; o Android pede confirmação e recusa outra assinatura. */
    suspend fun instalar(contexto: Context, nova: NovaVersao) = withContext(Dispatchers.IO) {
        val ficheiro = File(contexto.cacheDir, "atualizacao.apk")
        OkHttpClient().newCall(Request.Builder().url(nova.urlApk).header("User-Agent", userAgent(BuildConfig.VERSION_NAME)).build())
            .execute().use { r ->
                check(r.isSuccessful) { "falha a descarregar a atualização (${r.code})" }
                ficheiro.outputStream().use { r.body.byteStream().copyTo(it) }
            }
        val instalador = contexto.packageManager.packageInstaller
        val sessao = instalador.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        instalador.openSession(sessao).use { s ->
            s.openWrite("paragem-certa", 0, ficheiro.length()).use { saida ->
                ficheiro.inputStream().use { it.copyTo(saida) }
                s.fsync(saida)
            }
            val aviso = PendingIntent.getBroadcast(
                contexto, sessao, Intent(contexto, ResultadoInstalacao::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            s.commit(aviso.intentSender)
        }
    }
}

/** O instalador responde aqui; quando precisa da confirmação do utilizador, abre o ecrã do sistema. */
class ResultadoInstalacao : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            contexto.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
