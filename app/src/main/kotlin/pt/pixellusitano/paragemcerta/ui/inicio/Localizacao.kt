package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import kotlin.math.abs

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

/** Estados do mapa quanto à posição: livre (sem seguir), centrado na posição, ou bússola (centrado e a rodar com o telemóvel). */
enum class ModoPosicao { LIVRE, CENTRADO, BUSSOLA }

/** Sem permissão fica sempre livre; arrastar o mapa sai sempre de centrado ou da bússola. */
fun aoTocar(modo: ModoPosicao, temBussola: Boolean, temPermissao: Boolean): ModoPosicao = when {
    !temPermissao -> ModoPosicao.LIVRE
    modo == ModoPosicao.LIVRE -> ModoPosicao.CENTRADO
    modo == ModoPosicao.CENTRADO && temBussola -> ModoPosicao.BUSSOLA
    modo == ModoPosicao.CENTRADO -> ModoPosicao.CENTRADO
    else -> ModoPosicao.LIVRE
}

fun aoArrastar(modo: ModoPosicao): ModoPosicao = ModoPosicao.LIVRE

/** Rotação do telemóvel (vetor de rotação) ou, na falta dele, acelerómetro com magnetómetro. */
fun temBussola(contexto: Context): Boolean {
    val sensores = contexto.getSystemService(SensorManager::class.java) ?: return false
    fun tem(tipo: Int) = sensores.getDefaultSensor(tipo) != null
    return tem(Sensor.TYPE_ROTATION_VECTOR) || (tem(Sensor.TYPE_ACCELEROMETER) && tem(Sensor.TYPE_MAGNETIC_FIELD))
}

/** Última câmara enviada ao mapa; serve para não repetir envios com movimentos pequenos. */
data class CamaraEnviada(val lat: Double, val lon: Double, val zoom: Double)

/** Envia se for a primeira, se a posição mudou 30 m ou mais, ou se o zoom mudou 0,5 ou mais. */
fun deveEnviarCamara(ultima: CamaraEnviada?, lat: Double, lon: Double, zoom: Double): Boolean =
    ultima == null || distanciaMetros(ultima.lat, ultima.lon, lat, lon) >= 30.0 || abs(zoom - ultima.zoom) >= 0.5

/** Duração em milissegundos da animação da câmara; sem animações, muda de imediato. */
fun duracaoCamara(animacoes: Boolean): Int = if (animacoes) 750 else 0

private const val PREFS = "inicio"
private const val CHAVE_PERGUNTADA = "localizacao_perguntada"

fun localizacaoJaPerguntada(contexto: Context): Boolean =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CHAVE_PERGUNTADA, false)

fun marcarLocalizacaoPerguntada(contexto: Context) =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CHAVE_PERGUNTADA, true).apply()
