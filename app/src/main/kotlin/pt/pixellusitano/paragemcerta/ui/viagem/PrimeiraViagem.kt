package pt.pixellusitano.paragemcerta.ui.viagem

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.viagem.PlanoViagem
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.viagem.FicheirosViagem
import pt.pixellusitano.paragemcerta.viagem.ServicoViagem
import java.io.IOException
import java.time.Instant

/** Os passos antes de começar uma viagem, pela ordem em que a pessoa os vê (spec 7.2). */
enum class Passo { LOCALIZACAO, LOCALIZACAO_DESLIGADA, NOTIFICACOES, BATERIA, VOLUME }

/** O que o telemóvel tem agora. volumeAlarme 0 = mudo. */
data class Preparacao(
    val localizacaoPrecisa: Boolean, val localizacaoLigada: Boolean, val notificacoes: Boolean,
    val bateriaLivre: Boolean, val volumeAlarme: Int,
)

/**
 * O próximo passo que ainda falta, ou null se pode começar. Cada passo aparece uma só vez (feitos), mesmo que a pessoa o
 * recuse: nenhum impede de começar. A bateria só se explica na primeira viagem; o volume só importa se o aviso toca.
 */
fun proximoPasso(p: Preparacao, feitos: Set<Passo>, primeiraViagem: Boolean, comSom: Boolean): Passo? =
    Passo.entries.firstOrNull {
        it !in feitos && when (it) {
            Passo.LOCALIZACAO -> !p.localizacaoPrecisa
            // Sem a permissão precisa a viagem corre pelo horário: não vale a pena avisar que a localização está desligada.
            Passo.LOCALIZACAO_DESLIGADA -> p.localizacaoPrecisa && !p.localizacaoLigada
            Passo.NOTIFICACOES -> !p.notificacoes
            Passo.BATERIA -> primeiraViagem && !p.bateriaLivre
            Passo.VOLUME -> comSom && p.volumeAlarme == 0
        }
    }

/** O guia, no dontkillmyapp.com, de como o fabricante fecha as apps em segundo plano. */
fun guiaBateria(fabricante: String) = "https://dontkillmyapp.com/" + fabricante.lowercase().replace(' ', '-')

fun lerPreparacao(c: Context): Preparacao {
    fun tem(permissao: String) = ContextCompat.checkSelfPermission(c, permissao) == PackageManager.PERMISSION_GRANTED
    return Preparacao(
        localizacaoPrecisa = tem(Manifest.permission.ACCESS_FINE_LOCATION),
        localizacaoLigada = LocationManagerCompat.isLocationEnabled(c.getSystemService(LocationManager::class.java)),
        // Antes do Android 13 as notificações não se pedem.
        notificacoes = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || tem(Manifest.permission.POST_NOTIFICATIONS),
        bateriaLivre = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName),
        volumeAlarme = c.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM),
    )
}

/**
 * Grava o plano e o primeiro estado e pede ao serviço (processo :trip) que comece. O estado vai já daqui: o ecrã da viagem
 * abre logo a seguir e, sem ele, achava que não há viagem e saía antes de o serviço escrever o dele. Falso se o Android
 * recusou o serviço ou não houve onde gravar; então não fica nada, para não aparecer "Viagem interrompida" de uma viagem que
 * nunca começou.
 */
fun comecarViagem(c: Context, itinerario: Itinerario, som: Boolean, vibrar: Boolean): Boolean {
    try {
        FicheirosViagem.apagar(c)
        FicheirosViagem.gravarPlano(c, PlanoViagem(itinerario, som, vibrar))
        FicheirosViagem.gravarEstado(c, Viagem(itinerario).inicio(Instant.now()))
        if (arrancarServico(c, ServicoViagem.ACAO_COMECAR)) return true
    } catch (_: IOException) {
    }
    FicheirosViagem.apagar(c)
    return false
}

/** Só a partir da app visível (o Android 14 não deixa arrancar este serviço de outra maneira). */
fun retomarViagem(c: Context): Boolean = arrancarServico(c, ServicoViagem.ACAO_RETOMAR)

/** O Android recusa o serviço em primeiro plano com um IllegalStateException (ForegroundServiceStartNotAllowedException, a partir do 12). */
private fun arrancarServico(c: Context, acao: String): Boolean = try {
    ContextCompat.startForegroundService(c, Intent(c, ServicoViagem::class.java).setAction(acao))
    true
} catch (_: IllegalStateException) {
    false
}

/** Há uma viagem por acabar e o serviço não está a correr: o Android fechou-o, ou o arranque falhou. */
fun viagemInterrompida(c: Context): Boolean =
    FicheirosViagem.lerEstado(c)?.let { !it.terminada && !FicheirosViagem.emCurso(c) } ?: false

private class Avisos(val primeiraViagem: Boolean, val som: Boolean, val vibrar: Boolean)

/**
 * "Começar viagem": percorre os passos de proximoPasso, um aviso de cada vez, e no fim arranca a viagem (aoComecou).
 * Tocar fora de um aviso, ou "voltar", desiste (aoCancelar). Recusar uma permissão nunca impede de começar: sem a
 * localização precisa a viagem corre pelo horário, sem notificações os avisos só tocam e vibram.
 */
@SuppressLint("InlinedApi") // o passo das notificações só chega a quem tem o Android 13 ou mais (ver lerPreparacao)
@Composable
fun PreparacaoViagem(itinerario: Itinerario, preferencias: Preferencias, aoComecou: () -> Unit, aoCancelar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    // As definições lêem-se uma vez, antes do primeiro passo (null = a ler): o valor por omissão faria aparecer passos a mais.
    val avisos by produceState<Avisos?>(null) {
        value = Avisos(!preferencias.primeiraViagemFeita.first(), preferencias.somAviso.first(), preferencias.vibracaoAviso.first())
    }
    var feitos by remember { mutableStateOf(emptySet<Passo>()) }
    var semNotificacoes by remember { mutableStateOf(false) }
    val pedirLocalizacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { feitos += Passo.LOCALIZACAO }
    val pedirNotificacoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dada ->
        if (dada) feitos += Passo.NOTIFICACOES else semNotificacoes = true
    }
    val a = avisos ?: return
    // O telemóvel lê-se de novo a cada passo: dar a localização precisa pode revelar que a localização do telemóvel está desligada.
    val passo = proximoPasso(lerPreparacao(contexto), feitos, a.primeiraViagem, a.som)

    LaunchedEffect(passo) {
        when (passo) {
            null -> if (withContext(Dispatchers.IO) { comecarViagem(contexto, itinerario, a.som, a.vibrar) }) {
                preferencias.marcarPrimeiraViagemFeita()
                aoComecou()
            } else {
                Toast.makeText(contexto, R.string.viagem_nao_comecou, Toast.LENGTH_LONG).show()
                aoCancelar()
            }
            Passo.NOTIFICACOES -> pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {}
        }
    }

    fun abrir(intent: Intent) {
        runCatching { contexto.startActivity(intent) }
    }

    when (passo) {
        Passo.LOCALIZACAO -> AvisoPasso(
            R.string.prep_localizacao_titulo, R.string.prep_localizacao_texto, aoCancelar,
            confirmar = R.string.prep_localizacao_permitir,
            aoConfirmar = { pedirLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            recusar = R.string.prep_localizacao_horario, aoRecusar = { feitos += Passo.LOCALIZACAO },
        )
        Passo.LOCALIZACAO_DESLIGADA -> AvisoPasso(
            R.string.prep_desligada_titulo, R.string.prep_desligada_texto, aoCancelar,
            confirmar = R.string.prep_abrir_definicoes,
            // Ao voltar das definições a pessoa toca em "Começar viagem" outra vez e a localização já está ligada.
            aoConfirmar = {
                abrir(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                aoCancelar()
            },
            recusar = R.string.prep_comecar_mesmo, aoRecusar = { feitos += Passo.LOCALIZACAO_DESLIGADA },
        )
        Passo.NOTIFICACOES -> if (semNotificacoes) AvisoPasso(
            R.string.prep_notificacoes_titulo, R.string.prep_notificacoes_texto, aoCancelar,
            confirmar = R.string.prep_comecar_mesmo, aoConfirmar = { feitos += Passo.NOTIFICACOES },
        )
        Passo.BATERIA -> AvisoPasso(
            R.string.prep_bateria_titulo, R.string.prep_bateria_texto, aoCancelar,
            confirmar = R.string.prep_bateria_definicoes,
            aoConfirmar = {
                abrir(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                escopo.launch { preferencias.marcarPrimeiraViagemFeita() } // já foi explicado: não se repete, mesmo que não chegue a começar
                aoCancelar()
            },
            recusar = R.string.prep_continuar, aoRecusar = { feitos += Passo.BATERIA },
            extra = {
                TextButton(onClick = { abrir(Intent(Intent.ACTION_VIEW, Uri.parse(guiaBateria(Build.MANUFACTURER)))) }) {
                    Text(stringResource(R.string.prep_bateria_guia))
                }
            },
        )
        Passo.VOLUME -> AvisoPasso(
            R.string.prep_volume_titulo, R.string.prep_volume_texto, aoCancelar,
            confirmar = R.string.prep_continuar, aoConfirmar = { feitos += Passo.VOLUME },
        )
        null -> {}
    }
}

@Composable
private fun AvisoPasso(
    titulo: Int, texto: Int, aoCancelar: () -> Unit,
    confirmar: Int, aoConfirmar: () -> Unit,
    recusar: Int? = null, aoRecusar: () -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(stringResource(titulo)) },
        // Com letra grande o texto e o botão do guia não cabem: rola, como o aviso de atualização.
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(texto))
                extra()
            }
        },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(stringResource(confirmar)) } },
        dismissButton = recusar?.let { { TextButton(onClick = aoRecusar) { Text(stringResource(it)) } } },
    )
}
