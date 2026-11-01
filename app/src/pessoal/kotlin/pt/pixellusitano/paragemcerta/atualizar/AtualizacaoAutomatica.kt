package pt.pixellusitano.paragemcerta.atualizar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.userAgent

enum class Decisao { NADA, ESPERAR, INSTALAR }

/** Só instala com a opção ligada e uma versão nova; com a app em uso espera, para a instalação não a matar a meio. */
fun decidir(nova: NovaVersao?, ligado: Boolean, emPrimeiroPlano: Boolean): Decisao = when {
    !ligado || nova == null -> Decisao.NADA
    emPrimeiroPlano -> Decisao.ESPERAR
    else -> Decisao.INSTALAR
}

/** De 6 em 6 horas (ver Atualizacoes.agendar) procura uma versão nova no GitHub e instala-a sem perguntar. */
class AtualizacaoAutomatica(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result = try {
        val ligado = Atualizacoes.atualizarSozinha(applicationContext)
        val nova = if (ligado) VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE) else null
        when (decidir(nova, ligado, emPrimeiroPlano(applicationContext))) {
            Decisao.NADA -> Result.success()
            Decisao.ESPERAR -> Result.retry() // o WorkManager volta a tentar mais tarde, com recuo crescente
            Decisao.INSTALAR -> {
                Atualizacoes.instalar(applicationContext, requireNotNull(nova), semPerguntar = true)
                Result.success()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}
