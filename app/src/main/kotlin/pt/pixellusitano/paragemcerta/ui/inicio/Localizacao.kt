package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

fun temLocalizacao(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Última posição conhecida (sem serviços da Google, para funcionar em qualquer Android); null sem permissão ou sem posição. */
@SuppressLint("MissingPermission")
fun ultimaPosicao(contexto: Context): Pair<Double, Double>? {
    if (!temLocalizacao(contexto)) return null
    val gestor = contexto.getSystemService(LocationManager::class.java) ?: return null
    return gestor.getProviders(true)
        .mapNotNull { runCatching { gestor.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull(Location::getTime)
        ?.let { it.latitude to it.longitude }
}

/** Pede uma posição atual (a última conhecida pode ser de outro sítio, ou nem existir depois de reiniciar). */
fun pedirPosicaoAtual(contexto: Context, aoReceber: (Pair<Double, Double>) -> Unit) =
    pedirLocalizacaoAtual(contexto) { aoReceber(it.latitude to it.longitude) }

/** Como [pedirPosicaoAtual], mas entrega a localização inteira: a hora da medição diz qual de duas posições é a mais nova. */
@SuppressLint("MissingPermission")
fun pedirLocalizacaoAtual(contexto: Context, aoReceber: (Location) -> Unit) {
    if (!temLocalizacao(contexto)) return
    val gestor = contexto.getSystemService(LocationManager::class.java) ?: return
    val fornecedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .firstOrNull { runCatching { gestor.isProviderEnabled(it) }.getOrDefault(false) } ?: return
    LocationManagerCompat.getCurrentLocation(
        gestor, fornecedor, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(contexto),
    ) { local: Location? ->
        local?.let(aoReceber)
    }
}

/** Uma posição com a hora da medição (ms desde 1970); hora 0 = não se sabe. */
data class PosicaoComHora(val lat: Double, val lon: Double, val hora: Long)

/** A mais nova das duas; sem hora ou na mesma hora fica a primeira. */
fun maisRecente(a: PosicaoComHora?, b: PosicaoComHora?): PosicaoComHora? = if (a != null && (b == null || b.hora <= a.hora)) a else b

private const val PREFS = "inicio"
private const val CHAVE_PERGUNTADA = "localizacao_perguntada"

fun localizacaoJaPerguntada(contexto: Context): Boolean =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CHAVE_PERGUNTADA, false)

fun marcarLocalizacaoPerguntada(contexto: Context) =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CHAVE_PERGUNTADA, true).apply()
