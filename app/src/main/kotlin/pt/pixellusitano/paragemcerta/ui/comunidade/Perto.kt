package pt.pixellusitano.paragemcerta.ui.comunidade

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.DISTANCIA_RELATAR_M
import pt.pixellusitano.paragemcerta.core.comunidade.chaveDaParagemEtapa
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.comunidade.pertoParaRelatar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.core.viagem.paragensDe
import pt.pixellusitano.paragemcerta.ui.inicio.pedirLocalizacaoAtual
import pt.pixellusitano.paragemcerta.ui.inicio.temLocalizacao
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import kotlin.coroutines.resume

enum class Proximidade { PERTO, LONGE, SEM_PERMISSAO, SEM_POSICAO, IMPRECISA }

/** De onde vem a posição atual; os testes trocam-na. */
internal object Perto {
    var posicao: suspend (Context) -> Location? = { c ->
        // Sem fornecedor ligado o pedido nunca responde: os 15 s dão a resposta "sem posição".
        withTimeoutOrNull(15_000) { suspendCancellableCoroutine { k -> pedirLocalizacaoAtual(c) { k.resume(it) } } }
    }
}

/**
 * Se a pessoa está a menos de 300 m da [paragem], medido aqui: a posição nunca sai do telemóvel. Só com a localização precisa
 * (a aproximada do Android é de quilómetros e não prova nada).
 */
suspend fun proximidade(c: Context, paragem: Paragem): Proximidade {
    if (!temLocalizacao(c)) return Proximidade.SEM_PERMISSAO
    if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return Proximidade.IMPRECISA
    }
    val aqui = Perto.posicao(c) ?: return Proximidade.SEM_POSICAO
    val precisao = if (aqui.hasAccuracy()) aqui.accuracy else null
    return when {
        pertoParaRelatar(distanciaMetros(aqui.latitude, aqui.longitude, paragem.lat, paragem.lon), precisao) -> Proximidade.PERTO
        precisao != null && precisao > DISTANCIA_RELATAR_M -> Proximidade.IMPRECISA
        else -> Proximidade.LONGE
    }
}

/**
 * Antes de publicar na [paragem] (relatar, ou votar num relato): a bordo da linha ou a menos de 300 m, medido aqui. Devolve a função
 * que verifica (a linha é null nos relatos da paragem) e só chama `aoPerto` se a pessoa puder; senão avisa porquê. Sem a permissão da
 * localização explica e pede-a (como o resto da app), e verifica outra vez. [paragem] pode ser null enquanto carrega: sem ela não se publica.
 */
@Composable
fun rememberVerificarPerto(paragem: Paragem?): (linha: String?, aoPerto: () -> Unit) -> Unit {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    // O que está à espera da permissão da localização.
    var porPermitir by remember { mutableStateOf<Pair<String?, () -> Unit>?>(null) }
    var explicar by remember { mutableStateOf(false) }

    fun verificar(linha: String?, aoPerto: () -> Unit) {
        val p = paragem ?: return
        escopo.launch {
            if (linha != null && withContext(Dispatchers.IO) { aBordoDaLinha(contexto, linha) }) return@launch aoPerto()
            when (proximidade(contexto, p)) {
                Proximidade.PERTO -> aoPerto()
                Proximidade.LONGE -> avisar(contexto, R.string.relatar_longe)
                Proximidade.IMPRECISA -> avisar(contexto, R.string.relatar_imprecisa)
                Proximidade.SEM_POSICAO -> avisar(contexto, R.string.relatar_sem_posicao)
                Proximidade.SEM_PERMISSAO -> {
                    porPermitir = linha to aoPerto
                    explicar = true
                }
            }
        }
    }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val escolha = porPermitir
        porPermitir = null
        if (escolha != null) {
            if (r.values.any { it }) verificar(escolha.first, escolha.second) else avisar(contexto, R.string.relatar_sem_localizacao)
        }
    }
    if (explicar) {
        AlertDialog(
            onDismissRequest = {
                explicar = false
                porPermitir = null
            },
            text = { Text(stringResource(R.string.relatar_localizacao_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    explicar = false
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.loc_aceitar)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explicar = false
                    porPermitir = null
                }) { Text(stringResource(R.string.comunidade_agora_nao)) }
            },
        )
    }
    return { linha, aoPerto -> verificar(linha, aoPerto) }
}

/** Há uma viagem a correr e a etapa atual é em veículo na linha [linhaNome]: a bordo não é preciso estar perto da paragem. */
fun aBordoDaLinha(c: Context, linhaNome: String): Boolean {
    val estado = FicheirosViagem.lerEstado(c)?.takeIf { !it.terminada } ?: return false
    val etapa = FicheirosViagem.lerPlano(c)?.itinerario?.etapas?.getOrNull(estado.etapa) ?: return false
    return !etapa.aPe && etapa.linhaNome?.let(::chaveLinha) == chaveLinha(linhaNome)
}

/** Onde fica um relato feito a bordo: a última paragem por onde a viagem passou e o nome da linha da etapa atual. */
fun ultimaParagemDaViagem(c: Context): Pair<String, String>? {
    val estado = FicheirosViagem.lerEstado(c)?.takeIf { !it.terminada } ?: return null
    val plano = FicheirosViagem.lerPlano(c) ?: return null
    val etapa = plano.itinerario.etapas.getOrNull(estado.etapa)?.takeIf { !it.aPe } ?: return null
    val linha = etapa.linhaNome ?: return null
    val paragem = paragensDe(etapa).getOrNull(Viagem(plano.itinerario).paragemPassada(estado)) ?: return null
    return (chaveDaParagemEtapa(paragem.id) ?: return null) to linha
}
