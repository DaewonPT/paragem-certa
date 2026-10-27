package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

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

private const val PREFS = "inicio"
private const val CHAVE_PERGUNTADA = "localizacao_perguntada"

fun localizacaoJaPerguntada(contexto: Context): Boolean =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CHAVE_PERGUNTADA, false)

fun marcarLocalizacaoPerguntada(contexto: Context) =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CHAVE_PERGUNTADA, true).apply()
