package pt.pixellusitano.paragemcerta.ui.paragem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.Interruptores
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resposta
import pt.pixellusitano.paragemcerta.dados.AoVivo
import pt.pixellusitano.paragemcerta.dados.BaseDados
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.Conta
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.LojaEmMemoria
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.descricaoChegada
import pt.pixellusitano.paragemcerta.ui.linhas.CAIS_SODRE
import pt.pixellusitano.paragemcerta.ui.linhas.ESTADO_DA_LINHA
import pt.pixellusitano.paragemcerta.ui.linhas.LinhaConteudo
import pt.pixellusitano.paragemcerta.ui.mapa.Dono
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema
import pt.pixellusitano.paragemcerta.ui.tema.TemaParagemCerta
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A paragem do Cais Sodré (a do ecrã da linha de teste), a 9.ª paragem de todas as viagens do teste. */
private const val PARAGEM = "pt-Carris_3803"
private val PARAGENS_DA_VIAGEM = (1..8).map { "pt-Carris_${it * 100}" } + PARAGEM + (10..20).map { "pt-Carris_${it * 100}" }

// O trip_id do Transitous e o do GTFS-RT da mesma viagem (a Carris manda este, sem a data e a hora do Transitous nem o operador).
private fun idDaViagem(n: Int) = "20261103_15:56_pt-Carris_${n}_20260606_76_0_33"
private fun viagemDaCarris(n: Int) = "${n}_20260606_76_0_33"

private fun ponto(id: String) = """{"stopId":"$id"}"""

private fun viagemJson(paragens: List<String>) =
    """{"startTime":"2026-11-03T10:00:00Z","endTime":"2026-11-03T11:00:00Z","legs":[{"startTime":"2026-11-03T10:00:00Z","endTime":"2026-11-03T11:00:00Z",""" +
        """"from":${ponto(paragens.first())},"to":${ponto(paragens.last())},"intermediateStops":[${paragens.drop(1).dropLast(1).joinToString(",") { ponto(it) }}]}]}"""

private fun partidaJson(n: Int, em: Instant) =
    """{"place":{"stopId":"$PARAGEM","scheduledDeparture":"$em"},"mode":"BUS","realTime":false,"headsign":"Restelo",""" +
        """"routeId":"pt-Carris_76_0","routeShortName":"76","tripId":"${idDaViagem(n)}"}"""

/** Os autocarros que a Carris diz que estão na estrada: viagem e `current_stop_sequence` de cada um. */
private fun carrisJson(vararg veiculos: Pair<Int, Int>) = """{"atualizado":1,"veiculos":[""" +
    veiculos.joinToString(",") { (n, seq) ->
        """{"id":"$n","rota":"76_0","viagem":"${viagemDaCarris(n)}","sentido":0,"lat":38.7,"lon":-9.1,"seq":$seq,"paragem":null,"ts":1}"""
    } + "]}"

/** O que a paragem pede: as chegadas e as viagens ao Transitous (GET) e as posições da Carris ao servidor (enviar); guarda o URL de cada pedido. */
private class RedeDaCarris : Http {
    var partidas = emptyList<String>()
    var carris = Resposta(200, carrisJson())
    val pedidos = mutableListOf<String>()

    fun a(parte: String) = pedidos.filter { parte in it }

    override suspend fun get(url: String): Resposta {
        pedidos += url
        return when {
            "/api/v6/trip?" in url -> Resposta(200, viagemJson(PARAGENS_DA_VIAGEM))
            "/api/v6/stoptimes" in url -> Resposta(200, """{"stopTimes":[${partidas.joinToString(",")}]}""")
            else -> Resposta(404, "")
        }
    }

    override suspend fun enviar(metodo: String, url: String, corpo: String?, cabecalhos: Map<String, String>): Resposta {
        pedidos += url
        return if ("/api/rt/carris" in url) carris else Resposta(404, "")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp")
class AoVivoNaParagemTest {
    @get:Rule val regra = createComposeRule()

    private val contexto = RuntimeEnvironment.getApplication()
    private val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val rede = RedeDaCarris()
    private val servidor = ServidorParagemCerta(rede)
    private val agora = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private var carrisLigado = true
    private val repositorio = Repositorio(
        bd, CarrisMetropolitana(rede), Transitous(rede), GeoApi(rede), relogio = { agora },
        servidor = servidor, interruptores = { Interruptores(carrisAoVivo = carrisLigado) },
    )
    private val preferencias = Preferencias(LojaEmMemoria())
    private lateinit var configuracoes: Configuracoes
    private lateinit var comunidade: Comunidade
    private var dono: Dono? = null
    private val ref = RefParagem(Fonte.TRANSITOUS, PARAGEM)

    @Before fun preparar() {
        File(contexto.noBackupFilesDir, "conta.json").delete()
        File(contexto.filesDir, "config.json").delete()
        configuracoes = Configuracoes(contexto, servidor)
        comunidade = Comunidade(servidor, Conta(contexto, servidor, repositorio), configuracoes, preferencias)
    }

    @After fun fechar() = bd.close()

    // ---- o ecrã da paragem verdadeiro, ligado aos dados, e a lista das próximas do ecrã da linha (sem o MapView, que o Robolectric não desenha) ----

    @Composable
    private fun EcraDaParagem() = ParagemEcra(
        ref, repositorio, comunidade, configuracoes, preferencias, carregarFoto = { null }, aoEncontrar = {}, aoAbrirConta = {}, aoVoltar = {},
    )

    // Como o LinhaEcra: o mesmo rememberAoVivoCarris, com as chegadas da paragem escolhida, e o resultado vai no estado do ecrã.
    @Composable
    private fun ProximasDaLinha() {
        val chegadas = remember { listOf(chegada(5166, 5)) }
        val aoVivo = rememberAoVivoCarris(repositorio, CAIS_SODRE.ref.id, remember { flowOf(chegadas) })
        LinhaConteudo(
            estado = ESTADO_DA_LINHA.copy(escolhida = CAIS_SODRE, chegadas = EstadoChegadas(chegadas, agora, false, false), aoVivo = aoVivo),
            aoMudarSentido = {}, aoEscolherParagem = {}, aoAbrirParagem = {}, aoFavorita = {}, aoUsarComoHabitual = {}, aoVerHorario = {},
            aoAbrirMapaOficial = null, mapa = {}, modifier = Modifier.width(360.dp), agora = agora,
        )
    }

    private fun abrir(ecra: @Composable () -> Unit) {
        regra.mainClock.autoAdvance = false // o relógio do teste só anda quando o teste o manda
        regra.setContent {
            TemaParagemCerta(ModoTema.CLARO) {
                val d = dono
                if (d == null) ecra() else CompositionLocalProvider(LocalLifecycleOwner provides d) { ecra() }
            }
        }
    }

    private fun abrirParagem() = abrir { EcraDaParagem() }

    private fun chegada(n: Int, minutos: Long) = Chegada(
        Fonte.TRANSITOUS, "pt-Carris_76_0", "76", "Restelo", agora.plusSeconds(minutos * 60), null, idDaViagem(n), null, Modo.AUTOCARRO, null, null,
    )

    /** As viagens [n] a chegar de 5 em 5 minutos, a partir daqui a 5, e os autocarros que a Carris diz que vão na paragem [seq]. */
    private fun haViagens(seq: Int, vararg n: Int) {
        rede.partidas = n.mapIndexed { i, id -> partidaJson(id, agora.plusSeconds((i + 1) * 300L)) }
        rede.carris = Resposta(200, carrisJson(*n.map { it to seq }.toTypedArray()))
    }

    // O relógio do teste anda à risca (sem arredondar aos 16 ms de um quadro).
    private fun passar(ms: Long) = regra.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)

    private fun quadro() {
        regra.waitForIdle()
        regra.mainClock.advanceTimeByFrame()
    }

    private fun irPara(estado: Lifecycle.State) {
        regra.runOnUiThread { dono!!.registo.currentState = estado }
        regra.waitForIdle()
    }

    // Converter uma resposta passa por fios de verdade: o relógio do teste não os espera, por isso espera-se em tempo real, um quadro de cada vez.
    private fun esperarPor(condicao: () -> Boolean) {
        repeat(600) {
            if (condicao()) return
            Thread.sleep(10)
            quadro()
        }
        fail("não chegou o que se esperava; pedidos: ${rede.pedidos}")
    }

    // Com o texto das etiquetas lido da árvore sem juntar: a chegada junta tudo numa só descrição para o leitor de ecrã.
    private fun vezes(texto: String) = regra.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperarPelaChegada() = esperarPor { vezes("Restelo") > 0 }

    private fun viagensPedidas() = rede.a("/api/v6/trip?")

    // ---- as chegadas da Carris ----

    @Test fun chegadaDaCarrisComVeiculoMostraAoVivo() {
        haViagens(seq = 4, 5166) // o autocarro vai na 4.ª paragem da viagem, a do Cais Sodré é a 9.ª
        abrirParagem()
        esperarPor { vezes("ao vivo, a 5 paragens") == 1 }
        // Uma só leitura da viagem, com o id inteiro do Transitous; a etiqueta tomou o lugar do "horário".
        assertEquals(1, viagensPedidas().size)
        assertTrue(viagensPedidas().single().contains("tripId=20261103_15%3A56_pt-Carris_5166_20260606_76_0_33"))
        assertEquals(0, vezes("horário"))
    }

    @Test fun veiculoNaParagemDizAChegar() {
        haViagens(seq = 9, 5166)
        abrirParagem()
        esperarPor { vezes("ao vivo, a chegar") == 1 }
        assertEquals(0, vezes("ao vivo, a 0"))
    }

    @Test fun autocarroQueJaPassouNaoTemEtiqueta() {
        haViagens(seq = 12, 5166) // já vai na 12.ª: passou a 9.ª
        abrirParagem()
        esperarPelaChegada()
        esperarPor { viagensPedidas().size == 1 }
        quadro()
        quadro()
        // Nunca "a −3 paragens": fica o horário.
        assertEquals(0, vezes("ao vivo"))
        assertEquals(1, vezes("horário"))
    }

    @Test fun aoVivoNaDescricaoDoLeitorDeEcra() {
        haViagens(seq = 4, 5166)
        abrirParagem()
        esperarPor { vezes("ao vivo, a 5 paragens") == 1 }
        val hora = DateTimeFormatter.ofPattern("HH:mm").format(agora.plusSeconds(300).atZone(LISBOA_FUSO))
        val descricao = regra.onNode(hasContentDescription("Linha 76 para Restelo", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        // O leitor de ecrã diz o mesmo que se vê, em vez de "hora do horário, sem tempo real".
        assertEquals("Linha 76 para Restelo, às $hora, ao vivo, a 5 paragens", descricao)

        val c = chegada(5166, 5)
        assertEquals("Linha 76 para Restelo, às $hora, ao vivo, a chegar", descricaoChegada(c, agora, aoVivo = "ao vivo, a chegar"))
        assertEquals("Linha 76 para Restelo, às $hora, hora do horário, sem tempo real", descricaoChegada(c, agora))
    }

    @Test fun semVeiculoFicaOHorario() {
        rede.partidas = listOf(partidaJson(5166, agora.plusSeconds(300)))
        rede.carris = Resposta(200, carrisJson(7777 to 4)) // o autocarro que a Carris vê é de outra viagem
        abrirParagem()
        esperarPelaChegada()
        esperarPor { rede.a("/api/rt/carris").size == 1 }
        quadro()
        quadro()
        assertEquals(0, vezes("ao vivo"))
        assertEquals(1, vezes("horário"))
        // Sem veículo não se gasta um pedido à viagem.
        assertEquals(emptyList<String>(), viagensPedidas())
    }

    @Test fun carrisEmBaixoFicaOHorario() {
        rede.partidas = listOf(partidaJson(5166, agora.plusSeconds(300)))
        rede.carris = Resposta(502, """{"erro":"fonte_falhou"}""")
        abrirParagem()
        esperarPelaChegada()
        esperarPor { rede.a("/api/rt/carris").size == 1 }
        quadro()
        quadro()
        assertEquals(0, vezes("ao vivo"))
        assertEquals(1, vezes("horário"))
        assertEquals(emptyList<String>(), viagensPedidas())
    }

    @Test fun noMaximoTresPedidosDeViagem() {
        haViagens(seq = 4, 5166, 5167, 5168, 5169, 5170)
        abrirParagem()
        esperarPor { vezes("ao vivo, a 5 paragens") == 3 }
        // As 3 primeiras a chegar; as outras 2 ficam com o horário, e nem ao fim de outra leitura da Carris se pede mais.
        passar(31_000)
        quadro()
        quadro()
        assertEquals(2, rede.a("/api/rt/carris").size)
        assertEquals(3, viagensPedidas().size)
        listOf(5166, 5167, 5168).forEach { n -> assertTrue("viagem $n", viagensPedidas().any { "_${n}_" in it }) }
        assertEquals(3, vezes("ao vivo"))
        assertEquals(2, vezes("horário"))
    }

    @Test fun viagemLidaFicaGuardadaNaSessao() = runBlocking {
        rede.carris = Resposta(200, carrisJson(5166 to 4))
        val lista = listOf(chegada(5166, 5))
        // Dois ecrãs seguidos (cada um com o seu fluxo): a lista de paragens da viagem só se pede uma vez.
        repeat(2) {
            assertEquals(AoVivo(idDaViagem(5166), 5), repositorio.aoVivoCarris(PARAGEM, flowOf(lista)).first { it.isNotEmpty() }[idDaViagem(5166)])
        }
        assertEquals(1, viagensPedidas().size)
    }

    // ---- só pede com o ecrã à vista e o interruptor ligado ----

    @Test fun paragemDaCarrisDeixaDePedirEmSegundoPlano() {
        haViagens(seq = 4, 5166)
        dono = Dono().also { it.registo.currentState = Lifecycle.State.CREATED } // a app não está à vista
        abrirParagem()
        passar(60_000)
        assertEquals(emptyList<String>(), rede.pedidos)

        // À vista, a primeira leitura sai logo e a seguinte 30 s depois de a resposta chegar.
        irPara(Lifecycle.State.STARTED)
        esperarPor { vezes("ao vivo, a 5 paragens") == 1 }
        assertEquals(1, rede.a("/api/rt/carris").size)
        passar(29_000)
        assertEquals(1, rede.a("/api/rt/carris").size)
        passar(2_000)
        assertEquals(2, rede.a("/api/rt/carris").size)

        // Em segundo plano pára: nem a Carris nem a viagem voltam a pedir.
        val carris = rede.a("/api/rt/carris").size
        val viagens = viagensPedidas().size
        irPara(Lifecycle.State.CREATED)
        passar(120_000)
        assertEquals(carris, rede.a("/api/rt/carris").size)
        assertEquals(viagens, viagensPedidas().size)
    }

    @Test fun carrisDesligadoNaoPedeNaParagemNemNaLinha() {
        carrisLigado = false
        haViagens(seq = 4, 5166)
        // As duas à vista ao mesmo tempo: a paragem e as próximas da paragem escolhida no ecrã da linha.
        abrir {
            Column {
                Box(Modifier.height(380.dp).width(360.dp)) { EcraDaParagem() }
                Box(Modifier.height(380.dp)) { ProximasDaLinha() }
            }
        }
        esperarPor { vezes("Restelo") == 2 }
        passar(60_000)
        assertEquals(emptyList<String>(), rede.a("/api/rt/carris"))
        assertEquals(emptyList<String>(), viagensPedidas())
        assertEquals(0, vezes("ao vivo"))
        assertTrue(vezes("horário") >= 2) // as duas mostram o horário de sempre
    }

    // ---- as próximas da paragem escolhida no ecrã da linha ----

    @Test fun proximasDaLinhaMostramAoVivo() {
        haViagens(seq = 4, 5166)
        abrir { ProximasDaLinha() }
        esperarPor { vezes("ao vivo, a 5 paragens") == 1 }
        assertEquals(1, viagensPedidas().size)
    }

    // ---- a captura (só se grava no CI) ----

    private val agoraDaCaptura = Instant.parse("2026-11-03T15:50:00Z")

    private fun daCarris(linha: String, destino: String, minutos: Long) = Chegada(
        Fonte.TRANSITOUS, "pt-Carris_${linha}_0", linha, destino, agoraDaCaptura.plusSeconds(minutos * 60), null, "viagem-$linha-$minutos", null,
        Modo.AUTOCARRO, null, null,
    )

    @Test fun paragem_carris_ao_vivo_claro() = captureRoboImage("src/test/screenshots/paragem_carris_ao_vivo_claro.png") {
        val chegadas = listOf(daCarris("728", "Restelo", 3), daCarris("201", "Odivelas", 6), daCarris("728", "Restelo", 18), daCarris("728", "Restelo", 31))
        val aoVivo = chegadas.take(2).zip(listOf(5, 0)).associate { (c, n) -> c.idViagem!! to AoVivo(c.idViagem!!, n) }
        TemaParagemCerta(ModoTema.CLARO) {
            ParagemConteudo(
                nome = "Cais Sodré", codigo = "3803", acessivel = null, favorita = false, vigiada = false,
                estado = EstadoChegadas(chegadas, agoraDaCaptura, semLigacao = false, tempoRealIndisponivel = false), filtro = null,
                linhasPresentes = listOf("201" to "201", "728" to "728"), alertas = emptyList(), linhas = emptyMap(), agora = agoraDaCaptura,
                aoFiltrar = {}, aoFavoritar = {}, aoAvisaMe = {}, aoReportar = {}, aoVoltar = {}, aoVivo = aoVivo,
                modifier = Modifier.width(360.dp),
            )
        }
    }
}
