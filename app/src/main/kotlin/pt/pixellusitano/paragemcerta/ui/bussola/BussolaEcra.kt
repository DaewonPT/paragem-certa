package pt.pixellusitano.paragemcerta.ui.bussola

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.viagem.anguloDaSeta
import pt.pixellusitano.paragemcerta.core.viagem.rumo
import pt.pixellusitano.paragemcerta.core.viagem.setor
import pt.pixellusitano.paragemcerta.ui.comum.BotaoPrincipal
import pt.pixellusitano.paragemcerta.ui.comum.CabecalhoAzulejo
import pt.pixellusitano.paragemcerta.ui.inicio.temLocalizacao
import pt.pixellusitano.paragemcerta.ui.rotas.textoDistancia
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo

/**
 * O que o ecrã tem para mostrar. Ativa: a distância e o rumo (norte 0) até à paragem; angulo é o da seta, que só existe com
 * sensor e depois da primeira leitura; calibrar: o sensor diz que está pouco preciso.
 */
sealed interface EstadoBussola {
    data object PorPedir : EstadoBussola
    data object Recusada : EstadoBussola
    data object Desligada : EstadoBussola
    data object AProcurar : EstadoBussola
    data class Ativa(val distancia: Double, val rumo: Double, val angulo: Double?, val semSensor: Boolean, val calibrar: Boolean) : EstadoBussola
}

/**
 * O ecrã "Encontrar a paragem" ligado à localização e ao sensor de rotação, que só trabalham enquanto o ecrã está à vista.
 * [foto] desenha-se por baixo do alvo (a foto da paragem, quando a há).
 */
@Composable
fun BussolaEcra(nome: String, lat: Double, lon: Double, aoVoltar: () -> Unit, foto: @Composable () -> Unit = {}) {
    val contexto = LocalContext.current
    val vista = LocalView.current

    var permitida by remember { mutableStateOf(temLocalizacao(contexto)) }
    var recusada by rememberSaveable { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permitida = r.values.any { it }
        recusada = !permitida
    }
    // A permissão pode ter sido dada nas definições do sistema.
    LifecycleResumeEffect(Unit) {
        permitida = temLocalizacao(contexto)
        onPauseOrDispose { }
    }

    // A posição atual, de qualquer fornecedor que esteja ligado (sem os serviços da Google).
    var posicao by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var desligada by remember { mutableStateOf(false) }
    LifecycleStartEffect(permitida) {
        val gestor = contexto.getSystemService(LocationManager::class.java)
        val ouvinte = LocationListenerCompat { posicao = it.latitude to it.longitude }
        if (permitida && gestor != null) desligada = !seguirPosicao(contexto, gestor, ouvinte)
        onStopOrDispose { gestor?.let { largarPosicao(it, ouvinte) } }
    }

    // A orientação do telemóvel; sem este sensor há só a distância e o rumo em texto.
    val sensores = remember { contexto.getSystemService(SensorManager::class.java) }
    val sensor = remember { sensores?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var orientacao by remember { mutableStateOf<Double?>(null) }
    var calibrar by remember { mutableStateOf(false) }
    LifecycleStartEffect(sensor) {
        val ouvinte = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                orientacao = orientacaoGraus(e.values, vista.display?.rotation ?: Surface.ROTATION_0)
            }

            override fun onAccuracyChanged(s: Sensor, precisao: Int) {
                calibrar = precisao <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        if (sensor != null) sensores?.registerListener(ouvinte, sensor, SensorManager.SENSOR_DELAY_UI)
        onStopOrDispose { sensores?.unregisterListener(ouvinte) }
    }

    val aqui = posicao
    // A declinação passa o norte magnético da bússola ao verdadeiro, que é o dos rumos; muda com o sítio e não a cada leitura.
    val declinacao = remember(aqui) {
        aqui?.let { GeomagneticField(it.first.toFloat(), it.second.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble() } ?: 0.0
    }
    val estado = when {
        !permitida -> if (recusada) EstadoBussola.Recusada else EstadoBussola.PorPedir
        aqui == null -> if (desligada) EstadoBussola.Desligada else EstadoBussola.AProcurar
        else -> {
            val rumoParagem = rumo(aqui.first, aqui.second, lat, lon)
            EstadoBussola.Ativa(
                distanciaMetros(aqui.first, aqui.second, lat, lon), rumoParagem,
                angulo = orientacao?.let { anguloDaSeta(rumoParagem, it, declinacao) },
                semSensor = sensor == null, calibrar = calibrar,
            )
        }
    }
    BussolaConteudo(
        nome, estado,
        aoPermitir = { pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        aoVoltar = aoVoltar, modifier = Modifier.fillMaxSize(), foto = foto,
    )
}

/** Começa a ouvir a posição; false se nenhum fornecedor está ligado (ou o Android recusou), isto é, a localização do telemóvel está desligada. */
@SuppressLint("MissingPermission")
private fun seguirPosicao(contexto: Context, gestor: LocationManager, ouvinte: LocationListenerCompat): Boolean {
    val pedido = LocationRequestCompat.Builder(2_000L).setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
    var ouvindo = false
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { fornecedor ->
        runCatching {
            if (gestor.isProviderEnabled(fornecedor)) {
                LocationManagerCompat.requestLocationUpdates(gestor, fornecedor, pedido, ContextCompat.getMainExecutor(contexto), ouvinte)
                ouvindo = true
            }
        }
    }
    return ouvindo
}

@SuppressLint("MissingPermission") // sem a permissão não há pedidos a largar
private fun largarPosicao(gestor: LocationManager, ouvinte: LocationListenerCompat) = LocationManagerCompat.removeUpdates(gestor, ouvinte)

/**
 * O azimute do telemóvel (para onde aponta o cimo do ecrã), em graus de −180 a 180 contra o norte magnético, a partir do
 * vetor de rotação. [rotacaoEcra] é a do ecrã (Surface.ROTATION_*): com o ecrã deitado o cimo já não é o cimo do telemóvel.
 */
internal fun orientacaoGraus(vetor: FloatArray, rotacaoEcra: Int): Double {
    val matriz = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(matriz, vetor)
    val (x, y) = when (rotacaoEcra) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    val rodada = FloatArray(9)
    SensorManager.remapCoordinateSystem(matriz, x, y, rodada)
    val angulos = FloatArray(3)
    SensorManager.getOrientation(rodada, angulos)
    return Math.toDegrees(angulos[0].toDouble())
}

/** O conteúdo puro do ecrã (testado com capturas). [foto] vai por baixo de tudo o resto, seja qual for o estado. */
@Composable
fun BussolaConteudo(
    nome: String,
    estado: EstadoBussola,
    aoPermitir: () -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    foto: @Composable () -> Unit = {},
) {
    val cores = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cores.background)) {
        CabecalhoAzulejo(stringResource(R.string.bussola_encontrar), aoVoltar)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (estado) {
                EstadoBussola.PorPedir -> Pedido(R.string.bussola_localizacao_texto, aoPermitir)
                EstadoBussola.Recusada -> Pedido(R.string.bussola_localizacao_recusada, aoPermitir)
                EstadoBussola.Desligada -> Mensagem(stringResource(R.string.viagem_liga_localizacao))
                EstadoBussola.AProcurar -> Mensagem(stringResource(R.string.bussola_a_procurar))
                is EstadoBussola.Ativa -> Alvo(nome, estado)
            }
            foto()
        }
    }
}

@Composable
private fun Mensagem(texto: String) =
    Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)

@Composable
private fun Pedido(texto: Int, aoPermitir: () -> Unit) {
    Mensagem(stringResource(texto))
    BotaoPrincipal(aoPermitir, Modifier.fillMaxWidth()) { Text(stringResource(R.string.loc_aceitar)) }
}

/** A seta (ou, sem sensor, só o rumo em texto), a distância e o nome da paragem. */
@Composable
private fun Alvo(nome: String, e: EstadoBussola.Ativa) {
    val cores = MaterialTheme.colorScheme
    val distancia = textoDistancia(e.distancia)
    val rumoEmTexto = stringArrayResource(R.array.bussola_rumos)[setor(e.rumo, 8)]
    // Para quem não vê a seta: onde fica a paragem em relação a ti, ou, sem bússola, a direção em que fica.
    val ondeFica = e.angulo?.let { stringArrayResource(R.array.bussola_lados)[setor(it, 4)] } ?: rumoEmTexto
    val descricao = stringResource(R.string.bussola_descricao, nome, distancia, ondeFica)
    // Em cima da seta: com letra grande a seta e a distância empurram o resto para fora do ecrã.
    if (e.semSensor) Mensagem(stringResource(R.string.bussola_sem_sensor))
    else if (e.calibrar) Mensagem(stringResource(R.string.bussola_calibrar))
    Column(
        Modifier.clearAndSetSemantics { contentDescription = descricao },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        e.angulo?.let { Seta(it.toFloat()) }
        Text(distancia, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = cores.onBackground)
        if (e.angulo == null) Text(rumoEmTexto, style = MaterialTheme.typography.titleLarge, color = cores.onBackground)
        Text(nome, style = MaterialTheme.typography.titleLarge, color = cores.onBackground, textAlign = TextAlign.Center)
    }
}

/** Uma seta grande a apontar para cima, rodada [graus] para a direita: amarela num disco cobalto, nos dois temas. */
@Composable
private fun Seta(graus: Float) {
    Canvas(Modifier.size(240.dp).graphicsLayer { rotationZ = graus }) {
        val l = size.width
        drawCircle(Azulejo.Cobalto)
        drawPath(
            Path().apply {
                moveTo(l * 0.50f, l * 0.08f)
                lineTo(l * 0.85f, l * 0.50f)
                lineTo(l * 0.62f, l * 0.50f)
                lineTo(l * 0.62f, l * 0.92f)
                lineTo(l * 0.38f, l * 0.92f)
                lineTo(l * 0.38f, l * 0.50f)
                lineTo(l * 0.15f, l * 0.50f)
                close()
            },
            Azulejo.Eletrico,
        )
    }
}
