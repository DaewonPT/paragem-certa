package pt.pixellusitano.paragemcerta.ui.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import pt.pixellusitano.paragemcerta.ui.inicio.Zona
import pt.pixellusitano.paragemcerta.ui.inicio.maisRecente
import pt.pixellusitano.paragemcerta.ui.inicio.temLocalizacao
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
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
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
private const val FONTE_PERCURSOS = "fonte-percursos"
private const val CAMADA_PERCURSOS = "camada-percursos"
private const val CAMADA_A_PE = "camada-a-pe"
private const val FONTE_MARCAS = "fonte-marcas"
private const val CAMADA_MARCAS = "camada-marcas"
private const val CAMADA_MARCAS_TEXTO = "camada-marcas-texto"
private val SINAL_ANTIGO: Duration = Duration.ofMinutes(2)
private const val ZOOM_A_SEGUIR = 16.5

/**
 * Um troço de uma rota no mapa: pontos (lat, lon), cor "#RRGGBB" e se é a pé (desenhado a tracejado).
 * esbatido: na viagem, as etapas que não são a atual (mais finas e claras; a câmara só enquadra as outras).
 */
data class Percurso(val pontos: List<Pair<Double, Double>>, val cor: String, val aPe: Boolean, val esbatido: Boolean = false)

/** Uma paragem da etapa atual da viagem; sai: onde a pessoa sai (maior, com o nome). */
data class Marca(val lat: Double, val lon: Double, val nome: String, val sai: Boolean = false)

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
    percursos: List<Percurso> = emptyList(),
    aoToqueLongo: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    /** Mostra o ponto azul da posição da pessoa (só aparece se a app tiver permissão de localização). */
    mostrarPosicao: Boolean = false,
    /** Contador: cada vez que sobe, a câmara vai para a posição da pessoa. */
    centrarAgora: Int = 0,
    /** A hora (ms) a que foi medida a posição de [centrarEm], para a comparar com a do ponto azul; 0 = não se sabe. */
    horaCentrarEm: Long = 0L,
    /** As paragens da etapa atual da viagem, por cima dos percursos. */
    marcas: List<Marca> = emptyList(),
    /**
     * Modo "seguir" (a viagem): a câmara acompanha a pessoa, virada para onde ela se move, até ela mexer no mapa
     * (aoDeixarDeSeguir). Precisa de mostrarPosicao; a seguir, não enquadra os percursos.
     */
    seguir: Boolean = false,
    aoDeixarDeSeguir: () -> Unit = {},
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mover by rememberUpdatedState(aoMoverCamara)
    val tocarParagem by rememberUpdatedState(aoTocarParagem)
    val tocarVeiculo by rememberUpdatedState(aoTocarVeiculo)
    val toqueLongo by rememberUpdatedState(aoToqueLongo)
    val paragensAtuais by rememberUpdatedState(paragens)
    val veiculosAtuais by rememberUpdatedState(veiculos)
    val posicaoVisivel by rememberUpdatedState(mostrarPosicao)
    val aSeguir by rememberUpdatedState(seguir)
    val deixouDeSeguir by rememberUpdatedState(aoDeixarDeSeguir)
    // O MapLibre avisa quando a pessoa mexe na câmara a meio de uma viagem e passa sozinho a NONE.
    val ouvinteSeguir = remember {
        object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() = deixouDeSeguir()
            override fun onCameraTrackingChanged(currentMode: Int) = Unit
        }
    }

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
                // Mais abaixo que a pesquisa e o botão de centrar; a atribuição do OpenStreetMap é obrigatória e tem de ficar à vista.
                mapa.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
                mapa.uiSettings.setAttributionMargins(0, (180 * px).toInt(), (12 * px).toInt(), 0)
                mapa.uiSettings.logoGravity = Gravity.TOP or Gravity.START
                mapa.uiSettings.setLogoMargins((12 * px).toInt(), (180 * px).toInt(), 0, 0)
                mapa.addOnCameraMoveStartedListener { razao ->
                    if (razao == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        estado.utilizadorMexeu = true
                        estado.centrarAoChegar = false
                    }
                }
                mapa.addOnCameraIdleListener {
                    val b = mapa.projection.visibleRegion.latLngBounds
                    mover(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast, mapa.cameraPosition.zoom)
                }
                mapa.addOnMapLongClickListener { ponto ->
                    toqueLongo(ponto.latitude, ponto.longitude)
                    true
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
            // Mudar de estilo (claro/escuro) deita fora o ponto da posição: volta a ativá-lo.
            if (posicaoVisivel) ativarPosicao(contexto, mapa, estilo, aSeguir, ouvinteSeguir)
        }
    }

    // A permissão pode chegar depois de o estilo estar carregado.
    LaunchedEffect(mostrarPosicao) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        val estilo = estado.estilo ?: return@LaunchedEffect
        if (mostrarPosicao) ativarPosicao(contexto, mapa, estilo, aSeguir, ouvinteSeguir)
    }

    // "Seguir-me" volta a pôr a câmara na pessoa; mexer no mapa tira-a de lá.
    LaunchedEffect(seguir, estado.estilo) {
        val componente = estado.mapa?.locationComponent ?: return@LaunchedEffect
        if (estado.estilo != null && componente.isLocationComponentActivated) seguirPosicao(componente, seguir)
    }

    LaunchedEffect(centrarAgora, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        if (centrarAgora <= 0) return@LaunchedEffect
        val destino = posicaoMaisNova(mapa, centrarEm, horaCentrarEm) ?: return@LaunchedEffect
        mapa.easeCamera(CameraUpdateFactory.newLatLngZoom(destino, 16.0))
        // A posição pedida ao mesmo tempo ainda vai a caminho e pode ser de outro sítio: quando chegar, centra outra vez.
        estado.centrarAoChegar = true
    }

    // A primeira posição real chega depois do mapa: centra nela, a menos que a pessoa já tenha mexido no mapa.
    // Logo a seguir a "Centrar", a posição nova centra sempre, se a pessoa não mexeu no mapa entretanto.
    LaunchedEffect(centrarEm, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        if (centrarEm == null) return@LaunchedEffect
        if (estado.centrarAoChegar) {
            estado.centrarAoChegar = false
            val destino = posicaoMaisNova(mapa, centrarEm, horaCentrarEm) ?: centrarEm
            mapa.easeCamera(CameraUpdateFactory.newLatLngZoom(destino, 16.0))
        } else if (!estado.utilizadorMexeu && enquadrar.isEmpty()) {
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

    LaunchedEffect(percursos, estado.estilo) {
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_PERCURSOS)?.setGeoJson(
            FeatureCollection.fromFeatures(percursos.filter { it.pontos.size >= 2 }.map { p ->
                Feature.fromGeometry(LineString.fromLngLats(p.pontos.map { (lat, lon) -> Point.fromLngLat(lon, lat) })).apply {
                    addStringProperty("cor", p.cor)
                    addBooleanProperty("aPe", p.aPe)
                    addBooleanProperty("esbatido", p.esbatido)
                }
            }),
        )
    }

    LaunchedEffect(marcas, estado.estilo) {
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_MARCAS)?.setGeoJson(
            FeatureCollection.fromFeatures(marcas.map { m ->
                Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
                    addStringProperty("nome", m.nome)
                    addBooleanProperty("sai", m.sai)
                }
            }),
        )
    }

    // A seguir a pessoa manda a câmara do MapLibre; senão (sem localização, ou depois de a pessoa mexer no mapa) enquadra-se
    // o percurso, mas só quando ele muda: arrastar o mapa não o pode devolver ao sítio de onde a pessoa o tirou.
    LaunchedEffect(percursos, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        if (aSeguir) return@LaunchedEffect
        val pontos = percursos.filterNot { it.esbatido }.flatMap { it.pontos }.distinct()
        if (pontos.size < 2) return@LaunchedEffect
        val limites = LatLngBounds.Builder().includes(pontos.map { (lat, lon) -> LatLng(lat, lon) }).build()
        mapa.easeCamera(CameraUpdateFactory.newLatLngBounds(limites, 80))
    }

    AndroidView(factory = { vista }, modifier = modifier)
}

/** A posição mais nova entre a do ponto azul do mapa e [centrarEm]: depois de um salto grande, a do ponto pode ser a antiga. */
@SuppressLint("MissingPermission")
private fun posicaoMaisNova(mapa: MapLibreMap, centrarEm: LatLng?, hora: Long): LatLng? {
    val componente = mapa.locationComponent
    val doMapa = (if (componente.isLocationComponentActivated) componente.lastKnownLocation else null)
        ?.let { PosicaoComHora(it.latitude, it.longitude, it.time) }
    return maisRecente(doMapa, centrarEm?.let { PosicaoComHora(it.latitude, it.longitude, hora) })?.let { LatLng(it.lat, it.lon) }
}

private class EstadoMapa {
    var utilizadorMexeu = false
    /** "Centrar" foi tocado e a posição pedida nessa altura ainda não chegou. */
    var centrarAoChegar = false
    var mapa: MapLibreMap? by mutableStateOf(null)
    var estilo: Style? by mutableStateOf(null)
}

/** Liga o ponto azul da posição (motor de localização do próprio MapLibre, sem serviços da Google). */
@SuppressLint("MissingPermission")
private fun ativarPosicao(contexto: Context, mapa: MapLibreMap, estilo: Style, seguir: Boolean, ouvinteSeguir: OnCameraTrackingChangedListener) {
    if (!temLocalizacao(contexto)) return
    try {
        mapa.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(contexto, estilo).useDefaultLocationEngine(true).build())
            isLocationComponentEnabled = true
            // Ativar outra vez (novo estilo, permissão dada depois) não pode deixar o aviso repetido.
            removeOnCameraTrackingChangedListener(ouvinteSeguir)
            addOnCameraTrackingChangedListener(ouvinteSeguir)
            seguirPosicao(this, seguir)
        }
    } catch (_: SecurityException) {
        // A permissão foi retirada entre a verificação e a ativação: fica sem ponto.
    }
}

/** A seguir: a câmara fica na pessoa, virada para onde ela se move (seta no lugar do ponto); senão, só o ponto azul. */
private fun seguirPosicao(componente: LocationComponent, seguir: Boolean) {
    componente.renderMode = if (seguir) RenderMode.GPS else RenderMode.NORMAL
    if (seguir) componente.setCameraMode(CameraMode.TRACKING_GPS, 750L, ZOOM_A_SEGUIR, null, 0.0, null)
    else componente.cameraMode = CameraMode.NONE
}

private fun prepararCamadas(estilo: Style, escuro: Boolean) {
    estilo.addSource(GeoJsonSource(FONTE_PARAGENS))
    estilo.addSource(GeoJsonSource(FONTE_VEICULOS))
    estilo.addSource(GeoJsonSource(FONTE_PERCURSOS))
    estilo.addSource(GeoJsonSource(FONTE_MARCAS))
    // Os percursos ficam por baixo das paragens e dos veículos.
    estilo.addLayer(
        LineLayer(CAMADA_PERCURSOS, FONTE_PERCURSOS).withProperties(
            PropertyFactory.lineColor(Expression.get("cor")),
            PropertyFactory.lineWidth(Expression.switchCase(Expression.get("esbatido"), Expression.literal(3f), Expression.literal(5f))),
            PropertyFactory.lineOpacity(Expression.switchCase(Expression.get("esbatido"), Expression.literal(0.45f), Expression.literal(1f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(Expression.eq(Expression.get("aPe"), Expression.literal(false))),
    )
    estilo.addLayer(
        LineLayer(CAMADA_A_PE, FONTE_PERCURSOS).withProperties(
            PropertyFactory.lineColor(if (escuro) "#8D9AB3" else "#627189"),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(Expression.switchCase(Expression.get("esbatido"), Expression.literal(0.45f), Expression.literal(1f))),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineDasharray(arrayOf(1f, 2f)),
        ).withFilter(Expression.eq(Expression.get("aPe"), Expression.literal(true))),
    )
    estilo.addLayer(
        CircleLayer(CAMADA_PARAGENS, FONTE_PARAGENS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(if (escuro) "#7FB0FF" else "#1F4FA3"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(if (escuro) "#0B1222" else "#FFFFFF"),
        ),
    )
    // As paragens da viagem: pequenas, e a de saída maior, de outra cor e com o nome.
    estilo.addLayer(
        CircleLayer(CAMADA_MARCAS, FONTE_MARCAS).withProperties(
            PropertyFactory.circleRadius(Expression.switchCase(Expression.get("sai"), Expression.literal(10f), Expression.literal(5f))),
            PropertyFactory.circleColor(
                Expression.switchCase(
                    Expression.get("sai"), Expression.literal(if (escuro) "#FFA94D" else "#D9480F"),
                    Expression.literal(if (escuro) "#7FB0FF" else "#1F4FA3"),
                ),
            ),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(if (escuro) "#0B1222" else "#FFFFFF"),
        ),
    )
    estilo.addLayer(
        SymbolLayer(CAMADA_MARCAS_TEXTO, FONTE_MARCAS).withProperties(
            PropertyFactory.textField(Expression.get("nome")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1f)),
            PropertyFactory.textColor(if (escuro) "#FFFFFF" else "#0B1222"),
            PropertyFactory.textHaloColor(if (escuro) "#0B1222" else "#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
        ).withFilter(Expression.eq(Expression.get("sai"), Expression.literal(true))),
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
