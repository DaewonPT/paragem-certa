package pt.pixellusitano.paragemcerta.ui.bussola

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.view.Surface
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.ui.comunidade.FotoDeRuaConteudo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta

/** O Android a responder ao pedido da localização: [dar] diz se a pessoa aceita. */
internal class RegistoFalso(private val dar: Boolean) : ActivityResultRegistry() {
    val pedidas = mutableListOf<List<String>>()
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        val permissoes = (input as Array<*>).map { it as String }
        pedidas += permissoes
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, permissoes.associateWith { dar } as O)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BussolaEcraTest {
    @get:Rule val regra = createComposeRule()

    private val app = RuntimeEnvironment.getApplication() as Application

    // ---- a orientação do sensor ----

    /** O vetor de rotação de um telemóvel deitado com o topo a apontar para o azimute dado (z = −sen(az/2), w = cos(az/2)). */
    private fun vetorDeitado(azimute: Double) = Math.toRadians(azimute / 2).let { floatArrayOf(0f, 0f, -Math.sin(it).toFloat(), Math.cos(it).toFloat()) }

    @Test fun orientacaoDeUmTelemovelDeitadoAApontarParaEste() {
        assertEquals(90.0, orientacaoGraus(vetorDeitado(90.0), Surface.ROTATION_0), 0.01)
        assertEquals(0.0, orientacaoGraus(vetorDeitado(0.0), Surface.ROTATION_0), 0.01)
    }

    @Test fun comOEcraRodadoAOrientacaoSegueOCimaDoEcraEnaoOCimaDoTelemovel() {
        // Deitado com o topo a este: com o ecrã rodado 90° (telemóvel virado à esquerda) o cimo do ecrã é o lado direito do telemóvel, a sul.
        assertEquals(180.0, Math.abs(orientacaoGraus(vetorDeitado(90.0), Surface.ROTATION_90)), 0.01)
        // Rodado ao contrário, o cimo do ecrã é o fundo do telemóvel: a oeste.
        assertEquals(-90.0, orientacaoGraus(vetorDeitado(90.0), Surface.ROTATION_180), 0.01)
        assertEquals(0.0, orientacaoGraus(vetorDeitado(90.0), Surface.ROTATION_270), 0.01)
    }

    // ---- a escolha da posição ----

    /** Uma posição de [fornecedor] medida aos [s] segundos; sem [precisao], hasAccuracy() dá false. */
    private fun posicao(fornecedor: String, s: Double, precisao: Float? = null, latitude: Double = 0.0, longitude: Double = 0.0) =
        Location(fornecedor).apply {
            this.latitude = latitude
            this.longitude = longitude
            elapsedRealtimeNanos = (s * 1e9).toLong()
            precisao?.let { accuracy = it }
        }

    @Test fun umaPosicaoMenosPrecisaDeOutroFornecedorSoGanhaPassados10s() {
        val gps = LocationManager.GPS_PROVIDER
        val rede = LocationManager.NETWORK_PROVIDER
        val gps5 = posicao(gps, 0.0, 5f)
        val primeira = posicao(rede, 0.0, 150f)
        assertSame(primeira, melhorPosicao(null, primeira))
        assertSame(gps5, melhorPosicao(gps5, posicao(rede, 2.0, 150f)))
        val redeAos10 = posicao(rede, 10.0, 150f)
        assertSame(redeAos10, melhorPosicao(gps5, redeAos10))
        // Do mesmo fornecedor passa sempre, senão a posição parava enquanto andas.
        val gps8 = posicao(gps, 2.0, 8f)
        assertSame(gps8, melhorPosicao(gps5, gps8))
        val gpsDepoisDaRede = posicao(gps, 1.0, 5f)
        assertSame(gpsDepoisDaRede, melhorPosicao(primeira, gpsDepoisDaRede))
        // Sem precisão conta como a pior.
        assertSame(gps5, melhorPosicao(gps5, posicao(rede, 2.0)))
        val redeDepoisDeGpsSemPrecisao = posicao(rede, 2.0, 150f)
        assertSame(redeDepoisDeGpsSemPrecisao, melhorPosicao(posicao(gps, 0.0), redeDepoisDeGpsSemPrecisao))
    }

    // ---- o conteúdo ----

    @Composable
    private fun Conteudo(estado: EstadoBussola, aoPermitir: () -> Unit = {}, modo: ModoTema = ModoTema.CLARO) = TemaParagemCerta(modo) {
        BussolaConteudo("Cais do Sodré", estado, aoPermitir, aoVoltar = {}, modifier = Modifier.width(360.dp))
    }

    @Test fun comSetaDizADistanciaEOLado() {
        regra.setContent { Conteudo(EstadoBussola.Ativa(120.0, rumo = 73.0, angulo = 80.0, semSensor = false, calibrar = false)) }
        regra.onNodeWithText("Encontrar a paragem").assertIsDisplayed()
        regra.onNodeWithText("120 m", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 120 m, à tua direita").assertExists()
        regra.onNodeWithText("Calibra a bússola: move o telemóvel num 8.").assertDoesNotExist()
        regra.onNodeWithText("Este telemóvel não tem bússola. Aqui vês só a distância e a direção.").assertDoesNotExist()
    }

    // Como nos outros ecrãs com faixa: o conteúdo começa 16 dp por baixo dela (a faixa acaba 8 dp abaixo do voltar).
    @Test fun oDiscoComecaA16DpDaFaixa() {
        regra.setContent { Conteudo(EstadoBussola.Ativa(120.0, rumo = 73.0, angulo = 80.0, semSensor = false, calibrar = false)) }
        val voltar = regra.onNodeWithContentDescription("Voltar").getUnclippedBoundsInRoot()
        val disco = regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 120 m, à tua direita").getUnclippedBoundsInRoot()
        assertTrue("o disco começa a ${disco.top - voltar.bottom} do voltar", disco.top - voltar.bottom >= 24.dp)
    }

    @Test fun oCabecalhoEUmTituloComOVoltarDe48dp() {
        var voltou = 0
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                BussolaConteudo("Cais do Sodré", EstadoBussola.AProcurar, aoPermitir = {}, aoVoltar = { voltou++ }, modifier = Modifier.width(360.dp))
            }
        }
        regra.onNode(hasText("Encontrar a paragem") and isHeading()).assertIsDisplayed()
        regra.onNodeWithContentDescription("Voltar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, voltou)
    }

    @Test fun osQuatroLadosDaDescricao() {
        var angulo by mutableDoubleStateOf(0.0)
        regra.setContent { Conteudo(EstadoBussola.Ativa(120.0, rumo = 0.0, angulo = angulo, semSensor = false, calibrar = false)) }
        listOf(0.0 to "em frente", 90.0 to "à tua direita", 180.0 to "atrás de ti", 270.0 to "à tua esquerda", 350.0 to "em frente").forEach { (a, lado) ->
            angulo = a
            regra.waitForIdle()
            regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 120 m, $lado").assertExists()
        }
    }

    @Test fun bussolaPoucoPrecisaPedeParaCalibrar() {
        regra.setContent { Conteudo(EstadoBussola.Ativa(120.0, rumo = 73.0, angulo = 80.0, semSensor = false, calibrar = true)) }
        regra.onNodeWithText("Calibra a bússola: move o telemóvel num 8.").assertIsDisplayed()
        regra.onNodeWithText("120 m", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun semSensorDizSoADistanciaEORumoEmTexto() {
        regra.setContent { Conteudo(EstadoBussola.Ativa(120.0, rumo = 40.0, angulo = null, semSensor = true, calibrar = false)) }
        regra.onNodeWithText("120 m", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("a nordeste", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("Este telemóvel não tem bússola. Aqui vês só a distância e a direção.").assertIsDisplayed()
        regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 120 m, a nordeste").assertExists()
    }

    @Test fun semLocalizacaoExplicaEPedeAPermissao() {
        var pedidos = 0
        regra.setContent { Conteudo(EstadoBussola.PorPedir, aoPermitir = { pedidos++ }) }
        regra.onNodeWithText("Para apontar para a paragem", substring = true).assertIsDisplayed()
        regra.onNodeWithText("Permitir localização").performClick()
        assertEquals(1, pedidos)
    }

    @Test fun localizacaoRecusadaExplicaOQueFazer() {
        regra.setContent { Conteudo(EstadoBussola.Recusada) }
        regra.onNodeWithText("Sem a localização não dá para apontar para a paragem. Podes dá-la nas definições do telemóvel.").assertIsDisplayed()
        regra.onNodeWithText("Permitir localização").assertIsDisplayed()
    }

    @Test fun localizacaoDesligadaEAProcurar() {
        regra.setContent { Conteudo(EstadoBussola.Desligada) }
        regra.onNodeWithText("Liga a localização para a Paragem Certa saber onde estás.").assertIsDisplayed()
    }

    @Test fun enquantoNaoHaPosicaoDizQueProcura() {
        regra.setContent { Conteudo(EstadoBussola.AProcurar) }
        regra.onNodeWithText("A procurar a tua posição…").assertIsDisplayed()
    }

    // A seta fixa em 60° (a paragem a nordeste, com o telemóvel a apontar para o norte-noroeste).
    @Config(qualifiers = "w360dp-h780dp")
    @Test fun bussola() = captureRoboImage("src/test/screenshots/bussola.png") {
        Conteudo(EstadoBussola.Ativa(120.0, rumo = 40.0, angulo = 60.0, semSensor = false, calibrar = false))
    }

    // A foto de rua por baixo do alvo, sem menu: a imagem é feita aqui, sem esperar por nenhum pedido.
    @Config(qualifiers = "w360dp-h780dp")
    @Test fun bussola_com_foto_claro() = captureRoboImage("src/test/screenshots/bussola_com_foto_claro.png") {
        val imagem = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888).also {
            val tela = Canvas(it)
            tela.drawColor(Color.rgb(120, 170, 220))
            tela.drawRect(0f, 480f, 960f, 720f, Paint().apply { color = Color.rgb(90, 90, 100) })
        }
        TemaParagemCerta(ModoTema.CLARO) {
            BussolaConteudo(
                "Cais do Sodré", EstadoBussola.Ativa(120.0, rumo = 40.0, angulo = 60.0, semSensor = false, calibrar = false),
                aoPermitir = {}, aoVoltar = {}, modifier = Modifier.width(360.dp),
                foto = {
                    FotoDeRuaConteudo(
                        FotoDeRua("pan-1", "https://panoramax.exemplo/pan-1/thumb.jpg", "Robot8A", "CC BY-SA 4.0", "2022-03-13"),
                        imagem.asImageBitmap(), falhou = false, aoAbrirPagina = {}, aoOcultar = null,
                    )
                },
            )
        }
    }

    @Config(qualifiers = "w360dp-h780dp")
    @Test fun bussola_escuro() = captureRoboImage("src/test/screenshots/bussola_escuro.png") {
        Conteudo(EstadoBussola.Ativa(120.0, rumo = 40.0, angulo = 60.0, semSensor = false, calibrar = false), modo = ModoTema.ESCURO)
    }

    // ---- o ecrã ligado à localização e ao sensor ----

    private val lat = 38.7060
    private val lon = -9.1440

    private fun ecra(registo: RegistoFalso = RegistoFalso(dar = true)) = regra.setContent {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registo },
        ) {
            TemaParagemCerta(ModoTema.CLARO) {
                // A paragem fica a 0,001° a norte (cerca de 110 m).
                BussolaEcra("Cais do Sodré", lat + 0.001, lon, aoVoltar = {})
            }
        }
    }

    private fun darLocalizacao() = shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun gestor() = app.getSystemService(LocationManager::class.java)

    private fun estouEm(latitude: Double, longitude: Double) {
        shadowOf(gestor()).simulateLocation(Location(LocationManager.GPS_PROVIDER).apply { this.latitude = latitude; this.longitude = longitude })
        regra.waitForIdle()
    }

    @Test fun semPermissaoPedeAPermissaoEQuandoERecusadaExplica() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val registo = RegistoFalso(dar = false)
        ecra(registo)
        regra.onNodeWithText("Para apontar para a paragem", substring = true).assertIsDisplayed()
        assertTrue(registo.pedidas.isEmpty()) // não surpreende a pessoa com o pedido: espera pelo toque
        regra.onNodeWithText("Permitir localização").performClick()
        assertEquals(listOf(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)), registo.pedidas)
        regra.onNodeWithText("Sem a localização não dá para apontar para a paragem. Podes dá-la nas definições do telemóvel.").assertIsDisplayed()
    }

    @Test fun comLocalizacaoDesligadaDizParaLigar() {
        darLocalizacao()
        shadowOf(gestor()).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(gestor()).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        ecra()
        regra.onNodeWithText("Liga a localização para a Paragem Certa saber onde estás.").assertIsDisplayed()
    }

    @Test fun semSensorDeBussolaMostraADistanciaEORumoEmTextoSemFalhar() {
        darLocalizacao()
        shadowOf(gestor()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        ecra()
        regra.onNodeWithText("A procurar a tua posição…").assertIsDisplayed()
        estouEm(lat, lon)
        regra.onNodeWithText("110 m", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("a norte", useUnmergedTree = true).assertIsDisplayed()
        regra.onNodeWithText("Este telemóvel não tem bússola. Aqui vês só a distância e a direção.").assertIsDisplayed()
    }

    @Test fun aRedeMenosPrecisaNaoFazSaltarADistanciaMasGanhaSeOGpsSeCalar() {
        darLocalizacao()
        shadowOf(gestor()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(gestor()).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        ecra()
        // O gestor deita fora uma posição do mesmo fornecedor a menos de 2 s da anterior: os tempos ficam afastados.
        fun chega(l: Location) {
            shadowOf(gestor()).simulateLocation(l)
            regra.waitForIdle()
        }
        fun mostra(distancia: String, rumo: String) {
            regra.onNodeWithText(distancia, useUnmergedTree = true).assertIsDisplayed()
            regra.onNodeWithText(rumo, useUnmergedTree = true).assertIsDisplayed()
        }
        // O GPS a 0,00027° a sul da paragem (30 m).
        chega(posicao(LocationManager.GPS_PROVIDER, 1.0, 5f, latitude = lat + 0.00073, longitude = lon))
        mostra("30 m", "a norte")
        // A rede, 0,0013° a norte da paragem (145 m) e muito menos precisa, logo a seguir: não conta.
        chega(posicao(LocationManager.NETWORK_PROVIDER, 3.0, 150f, latitude = lat + 0.0023, longitude = lon))
        mostra("30 m", "a norte")
        // O GPS calou-se há 12 s: fica a rede.
        chega(posicao(LocationManager.NETWORK_PROVIDER, 13.0, 150f, latitude = lat + 0.0023, longitude = lon))
        mostra("140 m", "a sul")
    }

    @Test fun comSensorAPontaApontaParaAParagem() {
        darLocalizacao()
        shadowOf(gestor()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val sensores = app.getSystemService(SensorManager::class.java)
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadowOf(sensores).addSensor(sensor)
        ecra()
        estouEm(lat, lon)

        // A paragem está a norte e o telemóvel aponta para este: fica à esquerda.
        val leitura = ReflectionHelpers.callConstructor(SensorEvent::class.java, ClassParameter.from(Int::class.javaPrimitiveType, 4)).also { it.sensor = sensor }
        vetorDeitado(90.0).copyInto(leitura.values)
        shadowOf(sensores).sendSensorEventToListeners(leitura)
        regra.waitForIdle()
        regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 110 m, à tua esquerda").assertExists()
        regra.onNodeWithText("Este telemóvel não tem bússola. Aqui vês só a distância e a direção.").assertDoesNotExist()

        // Virado para a paragem, está em frente.
        vetorDeitado(0.0).copyInto(leitura.values)
        shadowOf(sensores).sendSensorEventToListeners(leitura)
        regra.waitForIdle()
        regra.onNodeWithContentDescription("Paragem Cais do Sodré, a 110 m, em frente").assertExists()
    }

    @Test fun comPoucaPrecisaoPedeParaCalibrar() {
        darLocalizacao()
        shadowOf(gestor()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val sensores = app.getSystemService(SensorManager::class.java)
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadowOf(sensores).addSensor(sensor)
        ecra()
        estouEm(lat, lon)
        regra.onNodeWithText("Calibra a bússola: move o telemóvel num 8.").assertDoesNotExist()

        fun precisao(p: Int) {
            shadowOf(sensores).listeners.forEach { it.onAccuracyChanged(sensor, p) }
            regra.waitForIdle()
        }
        precisao(SensorManager.SENSOR_STATUS_ACCURACY_LOW)
        regra.onNodeWithText("Calibra a bússola: move o telemóvel num 8.").assertIsDisplayed()
        precisao(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        regra.onNodeWithText("Calibra a bússola: move o telemóvel num 8.").assertDoesNotExist()
    }
}
