package pt.pixellusitano.paragemcerta.atualizar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.aviso.ServicoAvisoChegada
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem

enum class Decisao { NADA, ESPERAR, INSTALAR }

/**
 * Só instala com a opção ligada e uma versão nova; com a app em uso espera, para a instalação não a matar a meio. O mesmo
 * com uma viagem por acabar ([emViagem]): instalar mata o processo :trip e com ele o aviso da paragem; e com um aviso de
 * chegada a correr ([comAviso]), que a instalação também mataria.
 * Se essa versão já está descarregada à espera de confirmação ([pendente]), não repete a transferência nem abre outra sessão.
 */
fun decidir(
    nova: NovaVersao?, ligado: Boolean, emPrimeiroPlano: Boolean, pendente: String? = null, emViagem: Boolean = false, comAviso: Boolean = false,
): Decisao = when {
    !ligado || nova == null || nova.tag == pendente -> Decisao.NADA
    emPrimeiroPlano || emViagem || comAviso -> Decisao.ESPERAR
    else -> Decisao.INSTALAR
}

/** De 6 em 6 horas (ver Atualizacoes.agendar) procura uma versão nova no GitHub e instala-a sem perguntar. */
class AtualizacaoAutomatica(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result = try {
        val ligado = Atualizacoes.atualizarSozinha(applicationContext)
        val nova = if (ligado) VerificadorVersoes(OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))).verificar(BuildConfig.VERSION_CODE) else null
        val emViagem = FicheirosViagem.porAcabar(applicationContext)
        when (decidir(nova, ligado, emPrimeiroPlano(applicationContext), Atualizacoes.pendente(applicationContext)?.tag, emViagem, ServicoAvisoChegada.aCorrer)) {
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
