package pt.pixellusitano.paragemcerta.ui.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import pt.pixellusitano.paragemcerta.ui.comum.animacoesLigadas
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import pt.pixellusitano.paragemcerta.ui.inicio.Zona
import pt.pixellusitano.paragemcerta.ui.inicio.duracaoCamara
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
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
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.emel.EstacaoGira
import pt.pixellusitano.paragemcerta.core.fontes.servidor.DadosComunidade
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.dados.DetalheLinha
import pt.pixellusitano.paragemcerta.ui.comunidade.aplicarCorrecoes
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
private const val CAMADA_RELATOS = "camada-relatos"
private const val IMAGEM_RELATO = "relato"
private const val CAMADA_VEICULOS = "camada-veiculos"
private const val CAMADA_VEICULOS_TEXTO = "camada-veiculos-texto"
private const val FONTE_PERCURSOS = "fonte-percursos"
private const val CAMADA_PERCURSOS = "camada-percursos"
private const val CAMADA_A_PE = "camada-a-pe"
private const val FONTE_MARCAS = "fonte-marcas"
private const val CAMADA_MARCAS = "camada-marcas"
private const val CAMADA_MARCAS_TEXTO = "camada-marcas-texto"
private const val FONTE_GIRA = "fonte-gira"
private const val CAMADA_GIRA = "camada-gira"
private const val IMAGEM_GIRA = "gira"
private val SINAL_ANTIGO: Duration = Duration.ofMinutes(2)
private const val ZOOM_A_SEGUIR = 16.5

// As estações GIRA de uma rua ficam a poucas dezenas de metros: só se desenham a partir do zoom 13, e com o nome a partir do 15.
private const val ZOOM_GIRA = 13.0
private const val ZOOM_NOME_GIRA = 15.0

// Os autocarros da Carris levam sempre estas cores (a Carris não tem cor por linha no mapa); a CM leva a da linha.
private const val COR_CARRIS = "#F2B705"
private const val COR_TEXTO_CARRIS = "#1A1A1A"
private const val PREFIXO_VEICULO_CARRIS = "carris:"
private const val OPERADOR_CARRIS = "pt-Carris"

/**
 * Um troço de uma rota no mapa: pontos (lat, lon), cor "#RRGGBB" e se é a pé (desenhado a tracejado).
 * esbatido: na viagem, as etapas que não são a atual (mais finas e claras; a câmara só enquadra as outras).
 */
data class Percurso(val pontos: List<Pair<Double, Double>>, val cor: String, val aPe: Boolean, val esbatido: Boolean = false)

/** Uma paragem da etapa atual da viagem; sai: onde a pessoa sai (maior, com o nome). */
data class Marca(val lat: Double, val lon: Double, val nome: String, val sai: Boolean = false)

/**
 * As paragens a desenhar: como a comunidade as corrigiu (a posição e o nome) e sem as que dizem que já não existem.
 * O booleano diz se a paragem tem um relato confirmado (um por confirmar não se marca no mapa).
 */
fun paragensDoMapa(paragens: List<Paragem>, dados: DadosComunidade): List<Pair<Paragem, Boolean>> =
    paragens.mapNotNull { p ->
        val (vista, jaNaoExiste) = aplicarCorrecoes(p, dados.correcoes)
        if (jaNaoExiste) null else vista to dados.relatos.any { it.confirmado && it.paragem == p.ref.chave }
    }

fun giraVisivel(zoom: Double) = zoom >= ZOOM_GIRA

fun nomeGiraVisivel(zoom: Double) = zoom >= ZOOM_NOME_GIRA

/** Os veículos da Carris levam o prefixo "carris:" no id (os números repetem-se entre operadores). */
internal val Veiculo.daCarris get() = id.startsWith(PREFIXO_VEICULO_CARRIS)

/** Uma linha da Carris (as dos outros operadores do Transitous ainda não têm veículos ao vivo). */
internal val RefLinha.daCarris get() = fonte == Fonte.TRANSITOUS && id.substringBefore(':') == OPERADOR_CARRIS

/** Os veículos a desenhar no mapa: os da CM e os da Carris juntos, ou nenhum com a camada "Autocarros ao vivo" desligada. */
fun veiculosDoMapa(cm: List<Veiculo>, carris: List<Veiculo>, aoVivo: Boolean): List<Veiculo> = if (aoVivo) cm + carris else emptyList()

/**
 * Os veículos de [todos] que são da linha do ecrã dela: na CM pelo id da linha; na Carris pelo nome dela no catálogo, que é o
 * [Veiculo.linhaId] (o `veiculoDaCarris` já traduziu a rota do veículo para ele; uma rota que o catálogo não conhece fica sem nome e
 * não é de linha nenhuma). A mesma "1009" na CM e noutro operador não se confunde: os da Carris distinguem-se pelo prefixo do id.
 */
fun veiculosDaLinha(todos: List<Veiculo>, d: DetalheLinha): List<Veiculo> = when {
    d.ref.fonte == Fonte.CM -> todos.filter { !it.daCarris && it.linhaId == d.ref.id }
    d.ref.daCarris -> todos.filter { it.daCarris && it.linhaId == d.nome }
    else -> emptyList()
}

/** A cor do círculo e a do texto de um veículo: a Carris tem as suas; a CM, a da linha (ou o azul da app) com texto branco. */
internal fun coresDoVeiculo(v: Veiculo, coresLinhas: Map<String, String>): Pair<String, String> =
    if (v.daCarris) COR_CARRIS to COR_TEXTO_CARRIS else (coresLinhas[v.linhaId] ?: "#132A4F") to "#FFFFFF"

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
    /** As chaves das [paragens] com um relato confirmado da comunidade: levam o triângulo de aviso. */
    comRelato: Set<String> = emptySet(),
    /** As estações GIRA a desenhar (pino a partir do zoom 13, com o nome a partir do 15); vazio com a camada desligada. */
    gira: List<EstacaoGira> = emptyList(),
    aoTocarGira: (EstacaoGira) -> Unit = {},
    /** Modo bússola: a câmara segue a pessoa e roda com o telemóvel; ao sair, o mapa volta ao norte e fica plano. */
    bussola: Boolean = false,
    /** A pessoa começou a arrastar o mapa com um dedo (o zoom com dois dedos não conta). */
    aoArrastar: () -> Unit = {},
    /** O rumo da câmara (graus) sempre que ela se mexe. */
    aoRodar: (rumo: Double) -> Unit = {},
    /** A distância do topo a que ficam a atribuição e o logótipo. */
    margemTopo: Dp = 180.dp,
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val mover by rememberUpdatedState(aoMoverCamara)
    val tocarParagem by rememberUpdatedState(aoTocarParagem)
    val tocarVeiculo by rememberUpdatedState(aoTocarVeiculo)
    val tocarGira by rememberUpdatedState(aoTocarGira)
    val giraAtual by rememberUpdatedState(gira)
    val toqueLongo by rememberUpdatedState(aoToqueLongo)
    val paragensAtuais by rememberUpdatedState(paragens)
    val veiculosAtuais by rememberUpdatedState(veiculos)
    val posicaoVisivel by rememberUpdatedState(mostrarPosicao)
    val aSeguir by rememberUpdatedState(seguir)
    val deixouDeSeguir by rememberUpdatedState(aoDeixarDeSeguir)
    val aBussola by rememberUpdatedState(bussola)
    val arrastar by rememberUpdatedState(aoArrastar)
    val rodar by rememberUpdatedState(aoRodar)
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
                // A bússola do MapLibre diz "Compasso" em português: a app tem a sua.
                mapa.uiSettings.isCompassEnabled = false
                // Por omissão ficam no canto de baixo, tapados pelo painel: passam para cima (a margem vem de margemTopo).
                mapa.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
                mapa.uiSettings.logoGravity = Gravity.TOP or Gravity.START
                mapa.addOnCameraMoveStartedListener { razao ->
                    if (razao == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        estado.utilizadorMexeu = true
                        estado.centrarAoChegar = false
                    }
                }
                mapa.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) {
                        if (detector.pointersCount == 1) {
                            estado.aArrastar = true
                            arrastar()
                        }
                    }

                    override fun onMove(detector: MoveGestureDetector) = Unit

                    override fun onMoveEnd(detector: MoveGestureDetector) {
                        estado.aArrastar = false
                        if (estado.endireitarNoFim) {
                            estado.endireitarNoFim = false
                            endireitar(mapa, duracaoCamara(animacoesLigadas(contexto)))
                        }
                    }
                })
                mapa.addOnCameraMoveListener { rodar(mapa.cameraPosition.bearing) }
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
                    val tocado = mapa.queryRenderedFeatures(ecra, CAMADA_VEICULOS, CAMADA_RELATOS, CAMADA_PARAGENS, CAMADA_GIRA).firstOrNull()
                    val id = tocado?.getStringProperty("id")
                    when {
                        id == null -> false
                        tocado.hasProperty("linha") -> veiculosAtuais.firstOrNull { it.id == id }?.let { tocarVeiculo(it); true } ?: false
                        // As estações GIRA também não têm "linha": distinguem-se das paragens pelo id (o das paragens é "CM:…" ou "TRANSITOUS:…").
                        else -> giraAtual.firstOrNull { it.id == id }?.let { tocarGira(it); true }
                            ?: paragensAtuais.firstOrNull { it.ref.chave == id }?.let { tocarParagem(it); true } ?: false
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
            prepararCamadas(contexto, estilo, escuro)
            estado.estilo = estilo
            // Mudar de estilo (claro/escuro) deita fora o ponto da posição: volta a ativá-lo.
            if (posicaoVisivel) ativarPosicao(contexto, mapa, estilo, aSeguir, aBussola, ouvinteSeguir)
        }
    }

    // Abaixo do que o ecrã põe no topo: a atribuição do OpenStreetMap é obrigatória e tem de ficar à vista.
    LaunchedEffect(margemTopo, estado.mapa) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        val px = vista.resources.displayMetrics.density
        val topo = (margemTopo.value * px).toInt()
        mapa.uiSettings.setAttributionMargins(0, topo, (12 * px).toInt(), 0)
        mapa.uiSettings.setLogoMargins((12 * px).toInt(), topo, 0, 0)
    }

    // A permissão pode chegar depois de o estilo estar carregado.
    LaunchedEffect(mostrarPosicao) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        val estilo = estado.estilo ?: return@LaunchedEffect
        if (mostrarPosicao) ativarPosicao(contexto, mapa, estilo, aSeguir, aBussola, ouvinteSeguir)
    }

    // "Seguir-me" e a bússola põem a câmara na pessoa; mexer no mapa tira-a de lá.
    LaunchedEffect(seguir, bussola, estado.estilo) {
        val mapa = estado.mapa ?: return@LaunchedEffect
        val componente = mapa.locationComponent
        val duracao = duracaoCamara(animacoesLigadas(contexto))
        if (estado.estilo != null && componente.isLocationComponentActivated) seguirPosicao(componente, seguir, bussola, duracao)
        // Ao sair da bússola o mapa volta ao norte; a meio de um arrasto espera pelo fim do gesto, para não lutar com o dedo.
        if (estado.bussolaAntes && !bussola) {
            if (estado.aArrastar) estado.endireitarNoFim = true else endireitar(mapa, duracao)
        }
        estado.bussolaAntes = bussola
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
        // Na bússola quem move a câmara é o MapLibre: um easeCamera da app desligava-lhe o seguimento.
        if (aBussola) {
            estado.centrarAoChegar = false
            estado.utilizadorMexeu = true
            return@LaunchedEffect
        }
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

    LaunchedEffect(paragens, comRelato, estado.estilo) {
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_PARAGENS)?.setGeoJson(
            FeatureCollection.fromFeatures(paragens.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addStringProperty("id", p.ref.chave)
                    addBooleanProperty("relato", p.ref.chave in comRelato)
                }
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
                    val (cor, corTexto) = coresDoVeiculo(v, coresLinhas)
                    addStringProperty("cor", cor)
                    addStringProperty("corTexto", corTexto)
                    addBooleanProperty("antigo", Duration.between(v.ultimoSinal, agora) > SINAL_ANTIGO)
                }
            }),
        )
    }

    LaunchedEffect(gira, estado.estilo) {
        estado.estilo?.getSourceAs<GeoJsonSource>(FONTE_GIRA)?.setGeoJson(
            FeatureCollection.fromFeatures(gira.map { e ->
                Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
                    addStringProperty("id", e.id)
                    addStringProperty("nome", e.nome)
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
    /** Um dedo está a arrastar o mapa. */
    var aArrastar = false
    /** Saiu-se da bússola a meio de um arrasto: o mapa volta ao norte quando o dedo sair. */
    var endireitarNoFim = false
    var bussolaAntes = false
    var mapa: MapLibreMap? by mutableStateOf(null)
    var estilo: Style? by mutableStateOf(null)
}

/** Liga o ponto azul da posição (motor de localização do próprio MapLibre, sem serviços da Google). */
@SuppressLint("MissingPermission")
private fun ativarPosicao(
    contexto: Context,
    mapa: MapLibreMap,
    estilo: Style,
    seguir: Boolean,
    bussola: Boolean,
    ouvinteSeguir: OnCameraTrackingChangedListener,
) {
    if (!temLocalizacao(contexto)) return
    try {
        mapa.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(contexto, estilo).useDefaultLocationEngine(true).build())
            isLocationComponentEnabled = true
            // Ativar outra vez (novo estilo, permissão dada depois) não pode deixar o aviso repetido.
            removeOnCameraTrackingChangedListener(ouvinteSeguir)
            addOnCameraTrackingChangedListener(ouvinteSeguir)
            seguirPosicao(this, seguir, bussola, duracaoCamara(animacoesLigadas(contexto)))
        }
    } catch (_: SecurityException) {
        // A permissão foi retirada entre a verificação e a ativação: fica sem ponto.
    }
}

/**
 * O desenho do ponto e o modo da câmara: a bússola segue a pessoa e roda com o telemóvel; a viagem segue-a virada para onde
 * ela se move (seta no lugar do ponto); senão, só o ponto azul.
 */
internal fun modoDaCamara(seguir: Boolean, bussola: Boolean): Pair<Int, Int> = when {
    bussola -> RenderMode.COMPASS to CameraMode.TRACKING_COMPASS
    seguir -> RenderMode.GPS to CameraMode.TRACKING_GPS
    else -> RenderMode.NORMAL to CameraMode.NONE
}

private fun seguirPosicao(componente: LocationComponent, seguir: Boolean, bussola: Boolean, duracao: Int) {
    val (desenho, camara) = modoDaCamara(seguir, bussola)
    componente.renderMode = desenho
    when (camara) {
        // Uma transição de 0 ms nunca avisa que acabou e a câmara ficava parada sem seguir: 1 ms é igualmente imediato.
        CameraMode.TRACKING_COMPASS -> componente.setCameraMode(camara, maxOf(duracao, 1).toLong(), ZOOM_A_SEGUIR, null, 50.0, null)
        CameraMode.TRACKING_GPS -> componente.setCameraMode(camara, 750L, ZOOM_A_SEGUIR, null, 0.0, null)
        else -> componente.cameraMode = camara
    }
}

/** Volta o mapa ao norte e sem inclinação; sem animações, de imediato (o easeCamera não aceita duração 0). */
private fun endireitar(mapa: MapLibreMap, duracao: Int) {
    val direito = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(mapa.cameraPosition).bearing(0.0).tilt(0.0).build())
    if (duracao > 0) mapa.easeCamera(direito, duracao) else mapa.moveCamera(direito)
}

private fun prepararCamadas(contexto: Context, estilo: Style, escuro: Boolean) {
    // O triângulo é um desenho (vetor) e não um "⚠" em texto: as letras do OpenFreeMap não o têm.
    ContextCompat.getDrawable(contexto, R.drawable.relato_mapa)?.let { estilo.addImage(IMAGEM_RELATO, it) }
    ContextCompat.getDrawable(contexto, R.drawable.gira_mapa)?.let { estilo.addImage(IMAGEM_GIRA, it) }
    estilo.addSource(GeoJsonSource(FONTE_GIRA))
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
    // As estações GIRA ficam por baixo das paragens e dos veículos: pino (de zoom 13) com o nome por baixo (de zoom 15).
    estilo.addLayer(
        SymbolLayer(CAMADA_GIRA, FONTE_GIRA).withProperties(
            PropertyFactory.iconImage(IMAGEM_GIRA),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.textField(Expression.step(Expression.zoom(), Expression.literal(""), Expression.stop(ZOOM_NOME_GIRA, Expression.get("nome")))),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 0.2f)),
            // Com o zoom afastado as que se tapam saem sozinhas; um nome que não cabe sai sem tirar o pino.
            PropertyFactory.textOptional(true),
            PropertyFactory.textColor(if (escuro) "#FFFFFF" else "#0B1222"),
            PropertyFactory.textHaloColor(if (escuro) "#0B1222" else "#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
        ).apply { minZoom = ZOOM_GIRA.toFloat() },
    )
    estilo.addLayer(
        CircleLayer(CAMADA_PARAGENS, FONTE_PARAGENS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(if (escuro) "#7FB0FF" else "#1F4FA3"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(if (escuro) "#0B1222" else "#FFFFFF"),
        ),
    )
    // Por cima das paragens com um relato confirmado: o triângulo assenta em cima do ponto, sem o tapar.
    estilo.addLayer(
        SymbolLayer(CAMADA_RELATOS, FONTE_PARAGENS).withProperties(
            PropertyFactory.iconImage(IMAGEM_RELATO),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOffset(arrayOf(0f, -8f)),
        ).withFilter(Expression.eq(Expression.get("relato"), Expression.literal(true))),
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
            PropertyFactory.textColor(Expression.get("corTexto")),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textOpacity(opacidade),
        ),
    )
}
