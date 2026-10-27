package pt.pixellusitano.paragemcerta.ui.mapa

import android.annotation.SuppressLint
import android.view.Gravity
import pt.pixellusitano.paragemcerta.ui.inicio.Zona
import android.graphics.Color as CorAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import java.time.Duration
import java.time.Instant

// Verificado a 27/10/2026: os dois estilos do OpenFreeMap respondem 200.
private const val ESTILO_CLARO = "https://tiles.openfreemap.org/styles/positron"
private const val ESTILO_ESCURO = "https://tiles.openfreemap.org/styles/dark"

/** Centro de Lisboa, usado quando não há localização. */
val LISBOA = LatLng(38.7223, -9.1393)

private const val FONTE_PARAGENS = "fonte-paragens"
private const val FONTE_VEICULOS = "fonte-veiculos"
private const val CAMADA_PARAGENS = "camada-paragens"
private const val CAMADA_VEICULOS = "camada-veiculos"
private const val CAMADA_VEICULOS_TEXTO = "camada-veiculos-texto"
private val SINAL_ANTIGO: Duration = Duration.ofMinutes(2)

@SuppressLint("MissingPermission")
@Composable
fun Mapa(
    paragens: List<Paragem>,
    enquadrar: List<Paragem>,
    veiculos: List<Veiculo>,
    coresLinhas: Map<String, String>,
    escuro: Boolean,
    centroInicial: LatLng,
    camaraInicial: Zona?,
    centrarEm: LatLng?,
    aoMoverCamara: (minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, zoom: Double) -> Unit,
    aoTocarParagem: (Paragem) -> Unit,
    aoTocarVeiculo: (Veiculo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mover by rememberUpdatedState(aoMoverCamara)
    val tocarParagem by rememberUpdatedState(aoTocarParagem)
    val tocarVeiculo by rememberUpdatedState(aoTocarVeiculo)
    val paragensAtuais by rememberUpdatedState(paragens)
    val veiculosAtuais by rememberUpdatedState(veiculos)

    val estado = remember { EstadoMapa() }
    val vista = remember {
        MapLibre.getInstance(contexto)
        MapView(contexto).apply {
            onCreate(null)
            getMapAsync { mapa ->
                estado.mapa = mapa
                mapa.cameraPosition = camaraInicial?.let {
                    CameraPosition.Builder().target(LatLng((it.minLat + it.maxLat) / 2, (it.minLon + it.maxLon) / 2)).zoom(it.zoom).build()
                } ?: CameraPosition.Builder().target(centroInicial).zoom(15.5).build()
                estado.utilizadorMexeu = camaraInicial != null
                mapa.uiSettings.isRotateGesturesEnabled = false
                // Por omissão ficam no canto de baixo, tapados pelo painel: passam para cima, por baixo da pesquisa.
                val px = resources.displayMetrics.density
                mapa.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
                mapa.uiSettings.setAttributionMargins(0, (120 * px).toInt(), (12 * px).toInt(), 0)
                mapa.uiSettings.logoGravity = Gravity.TOP or Gravity.START
                mapa.uiSettings.setLogoMargins((12 * px).toInt(), (120 * px).toInt(), 0, 0)
                mapa.addOnCameraMoveStartedListener { razao ->
                    if (razao == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) estado.utilizadorMexeu = true
                }
                mapa.addOnCameraIdleListener {
                    val b = mapa.projection.visibleRegion.latLngBounds
                    mover(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast, mapa.cameraPosition.zoom)
                }
                mapa.addOnMapClickListener { ponto ->
                    val ecra = mapa.projection.toScreenLocation(ponto)
                    val tocado = mapa.queryRenderedFeatures(ecra, CAMADA_VEICULOS, CAMADA_PARAGENS).firstOrNull()
                    val id = tocado?.getStringProperty("id")
                    when {
                        id == null -> false
                        tocado.hasProperty("linha") -> veiculosAtuais.firstOrNull { it.id == id }?.let { tocarVeiculo(it); true } ?: false
                        else -> paragensAtuais.firstOrNull { it.ref.chave == id }?.let { tocarParagem(it); true } ?: false
                    }
                }
            }
        }
    }

    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            vista.onDestroy()
        }
    }

    LaunchedEffect(escuro, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        mapa.setStyle(Style.Builder().fromUri(if (escuro) ESTILO_ESCURO else ESTILO_CLARO)) { estilo ->
            prepararCamadas(estilo, escuro)
            estado.estilo = estilo
        }
    }

    // A primeira posição real chega depois do mapa: centra nela, a menos que a pessoa já tenha mexido no mapa.
    LaunchedEffect(centrarEm, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        if (centrarEm != null && !estado.utilizadorMexeu && enquadrar.isEmpty()) {
            mapa.easeCamera(CameraUpdateFactory.newLatLngZoom(centrarEm, 15.5))
        }
    }

    LaunchedEffect(enquadrar, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        if (enquadrar.size < 2) return@LaunchedEffect
        val limites = LatLngBounds.Builder().includes(enquadrar.map { LatLng(it.lat, it.lon) }).build()
        mapa.easeCamera(CameraUpdateFactory.newLatLngBounds(limites, 120))
    }

    LaunchedEffect(paragens, estado.estilo) {
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_PARAGENS)?.setGeoJson(
            FeatureCollection.fromFeatures(paragens.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addStringProperty("id", p.ref.chave) }
            }),
        )
    }

    LaunchedEffect(veiculos, coresLinhas, estado.estilo) {
        val agora = Instant.now()
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_VEICULOS)?.setGeoJson(
            FeatureCollection.fromFeatures(veiculos.map { v ->
                Feature.fromGeometry(Point.fromLngLat(v.lon, v.lat)).apply {
                    addStringProperty("id", v.id)
                    addStringProperty("linha", v.linhaId)
                    addStringProperty("cor", coresLinhas[v.linhaId] ?: "#132A4F")
                    addBooleanProperty("antigo", Duration.between(v.ultimoSinal, agora) > SINAL_ANTIGO)
                }
            }),
        )
    }

    AndroidView(factory = { vista }, modifier = modifier)
}

private class EstadoMapa {
    var utilizadorMexeu = false
    var mapa: MapLibreMap? by mutableStateOf(null)
    var estilo: Style? by mutableStateOf(null)
}

private fun prepararCamadas(estilo: Style, escuro: Boolean) {
    estilo.addSource(GeoJsonSource(FONTE_PARAGENS))
    estilo.addSource(GeoJsonSource(FONTE_VEICULOS))
    estilo.addLayer(
        CircleLayer(CAMADA_PARAGENS, FONTE_PARAGENS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(if (escuro) "#7FB0FF" else "#1F4FA3"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(if (escuro) "#0B1222" else "#FFFFFF"),
        ),
    )
    val opacidade = Expression.switchCase(Expression.get("antigo"), Expression.literal(0.4f), Expression.literal(1f))
    estilo.addLayer(
        CircleLayer(CAMADA_VEICULOS, FONTE_VEICULOS).withProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor(Expression.get("cor")),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(CorAndroid.WHITE),
            PropertyFactory.circleOpacity(opacidade),
            PropertyFactory.circleStrokeOpacity(opacidade),
        ),
    )
    estilo.addLayer(
        SymbolLayer(CAMADA_VEICULOS_TEXTO, FONTE_VEICULOS).withProperties(
            PropertyFactory.textField(Expression.get("linha")),
            // Letras que o servidor do OpenFreeMap tem (as do MapLibre por omissão não existem lá).
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(9f),
            PropertyFactory.textColor(CorAndroid.WHITE),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOpacity(opacidade),
        ),
    )
}
