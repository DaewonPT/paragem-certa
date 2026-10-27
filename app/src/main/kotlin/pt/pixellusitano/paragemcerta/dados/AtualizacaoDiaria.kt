package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pt.pixellusitano.paragemcerta.ParagemCertaApp
import java.util.concurrent.TimeUnit

/** Atualiza as paragens e linhas da Carris Metropolitana uma vez por dia, só com rede. */
class AtualizacaoDiaria(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val repositorio = (applicationContext as ParagemCertaApp).ligacoes.repositorio
        return if (repositorio.atualizarRede()) Result.success() else Result.retry()
    }

    companion object {
        fun agendar(contexto: Context) {
            val pedido = PeriodicWorkRequestBuilder<AtualizacaoDiaria>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork("rede-cm", ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
