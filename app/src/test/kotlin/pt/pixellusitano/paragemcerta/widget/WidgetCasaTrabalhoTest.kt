package pt.pixellusitano.paragemcerta.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import pt.pixellusitano.paragemcerta.MainActivity
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.PlanoRotas
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.comum.corDoTextoSobre
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/** O provider a sério, com o atualizador trocado. */
private class CasaTrabalhoDeTeste(private val a: () -> AtualizadorCasaTrabalho) : WidgetCasaTrabalho() {
    override fun atualizador(contexto: Context) = a()
}

@RunWith(RobolectricTestRunner::class)
class WidgetCasaTrabalhoTest {
    private val contexto: Context = RuntimeEnvironment.getApplication()
    private val dados = DadosWidget(contexto)
    // Em novembro Lisboa está em UTC: 18:40Z lê-se 18:40.
    private val agora = Instant.parse("2026-11-09T18:40:00Z")
    private val vermelho = 0xFFE2001A.toInt()
    private val ligacao = LigacaoGuardada("210", vermelho, Modo.AUTOCARRO, false, agora.plusSeconds(120).toEpochMilli(), 30, agora.toEpochMilli(), EstadoLigacao.OK)
    private val rotaCasa = "Casa|38.7369|-9.1427"
    private val rotaTrabalho = "Trabalho|38.7058|-9.1431"
    private val comLigacoes = ConteudoCasaTrabalho(
        Destino.Ligacao("Casa", ligacao), Destino.Ligacao("Trabalho", ligacao.copy(linha = "728", cor = Azulejo.Cobalto.toArgb())),
        Rodape("18:40", daPosicaoGuardada = false), rotaCasa, rotaTrabalho,
    )

    // ---- o tamanho ----

    @Test fun tamanhoPelaAlturaMudaNos100Dp() {
        assertEquals(TamanhoWidget.T4x1, tamanhoPelaAltura(40))
        assertEquals(TamanhoWidget.T4x1, tamanhoPelaAltura(99))
        assertEquals(TamanhoWidget.T4x2, tamanhoPelaAltura(100))
        assertEquals(TamanhoWidget.T4x2, tamanhoPelaAltura(170))
    }

    // As chaves do Android 12 ou mais e a escolha do 8 a 11 têm de dar o mesmo; com largura 0 só a altura decide.
    @Test fun chavesDoTamanhoBatemComATamanhoPelaAltura() {
        assertEquals(setOf(TamanhoWidget.T4x1, TamanhoWidget.T4x2), CHAVES_TAMANHO.values.toSet())
        CHAVES_TAMANHO.forEach { (chave, tamanho) ->
            assertEquals(0f, chave.width)
            assertEquals(tamanho, tamanhoPelaAltura(chave.height.toInt()))
        }
        assertEquals(SizeF(0f, 40f), CHAVES_TAMANHO.entries.first { it.value == TamanhoWidget.T4x1 }.key)
    }

    // A MIN_HEIGHT é a altura em paisagem: um 4×2 em retrato pode vir com ela abaixo dos 100 dp.
    @Test fun alturaDoWidgetLeAAlturaEmRetrato() {
        val opcoes = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 170)
        }
        assertEquals(170, alturaDoWidget(opcoes))
        assertEquals("sem opções ainda, o tamanho por omissão (um 4×2)", TamanhoWidget.T4x2, tamanhoPelaAltura(alturaDoWidget(Bundle())))
    }

    // ---- a troca automática ----

    @Test fun trocaAutomaticaSoComAnimacoesESemTalkBack() {
        assertTrue(trocaAutomatica(animacoes = true, talkBack = false))
        assertFalse(trocaAutomatica(animacoes = false, talkBack = false))
        assertFalse(trocaAutomatica(animacoes = true, talkBack = true))
    }

    private fun intervalo(v: View) = ReflectionHelpers.getField<Int>(v.findViewById<ViewFlipper>(R.id.ct_flipper), "mFlipInterval")

    /** O desenho que vai para o ecrã inicial; no Robolectric, o mapa de tamanhos aplica o mais pequeno (o 4×1). */
    private fun noEcra(conteudo: ConteudoCasaTrabalho = comLigacoes, id: Int = 1) =
        desenhoDoWidget(contexto, id, conteudo).apply(contexto, FrameLayout(contexto))

    @After fun reporDefinicoes() {
        Settings.Global.putFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        shadowOf(contexto.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(false)
    }

    @Test fun semAnimacoesOuComTalkBackNaoTrocaSozinho() {
        assertEquals(8_000, intervalo(noEcra()))
        Settings.Global.putFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertEquals(Int.MAX_VALUE, intervalo(noEcra()))
        Settings.Global.putFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        shadowOf(contexto.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        assertEquals(Int.MAX_VALUE, intervalo(noEcra()))
    }

    @Test fun oRedesenhoVoltaALigarATroca() {
        // O launcher reaplica as ações sobre as mesmas vistas: o intervalo de um desenho sem troca não pode ficar.
        val v = vistasCasaTrabalho(contexto, 1, comLigacoes, TamanhoWidget.T4x1, automatica = false).apply(contexto, FrameLayout(contexto))
        vistasCasaTrabalho(contexto, 1, comLigacoes, TamanhoWidget.T4x1, automatica = true).reapply(contexto, v)
        assertEquals(8_000, intervalo(v))
    }

    // ---- o provider ----

    @Test fun naoAcordaSozinhoNemPedeParaEscolher() {
        val c = configuracao(contexto, R.xml.widget_casa_trabalho)
        assertEquals(0, c.getAttributeIntValue(ANDROID, "updatePeriodMillis", -1))
        assertNull(c.getAttributeValue(ANDROID, "configure"))
        assertNull(c.getAttributeValue(ANDROID, "widgetFeatures"))
        assertEquals("40.0dip", c.getAttributeValue(ANDROID, "minResizeHeight"))
        assertEquals(R.string.widget_casa_trabalho_descricao, c.getAttributeResourceValue(ANDROID, "description", 0))
    }

    @Test fun naListaDeWidgetsTemONomeDele() =
        assertEquals(R.string.widget_casa_trabalho_nome, contexto.packageManager.getReceiverInfo(ComponentName(contexto, WidgetCasaTrabalho::class.java), 0).labelRes)

    // ---- botões ----

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun atualizarETrocarTem48DpEDescricao() {
        val dp = contexto.resources.displayMetrics.density
        // No 4×1 só o destino à vista se mede: o Trocar de cada um vê-se com ele à vista.
        val casos = listOf(Triple(TamanhoWidget.T4x1, 0, R.id.ct_casa_trocar), Triple(TamanhoWidget.T4x1, 1, R.id.ct_trabalho_trocar), Triple(TamanhoWidget.T4x2, 0, null))
        for ((tamanho, mostrado, trocar) in casos) {
            val v = medido(vistasCasaTrabalho(contexto, 1, comLigacoes, tamanho, automatica = true, mostrado), if (tamanho == TamanhoWidget.T4x1) 80 else 170)
            val botoes = listOfNotNull(R.id.ct_atualizar to "Atualizar", trocar?.let { it to "Trocar entre Casa e Trabalho" })
            for ((id, descricao) in botoes) {
                val b = v.findViewById<View>(id)
                assertEquals("$tamanho ${b.resources.getResourceEntryName(id)}", (48 * dp).toInt(), b.width)
                assertEquals("$tamanho ${b.resources.getResourceEntryName(id)}", (48 * dp).toInt(), b.height)
                assertEquals(descricao, b.contentDescription)
            }
        }
    }

    // ---- toques ----

    private fun app() = shadowOf(contexto as Application)

    private fun tocarAbre(v: View, id: Int): Intent {
        v.findViewById<View>(id).performClick()
        return app().nextStartedActivity
    }

    // Sem um Uri por destino o Android juntava os dois PendingIntent e tocar na Casa abria o Trabalho.
    @Test fun casaETrabalhoAbremCadaUmOSeu() {
        for (tamanho in TamanhoWidget.entries) {
            val v = vistasCasaTrabalho(contexto, 1, comLigacoes, tamanho, automatica = true).apply(contexto, FrameLayout(contexto))
            val casa = tocarAbre(v, R.id.ct_casa)
            val trabalho = tocarAbre(v, R.id.ct_trabalho)
            assertEquals(MainActivity::class.java.name, casa.component!!.className)
            assertEquals(rotaCasa, casa.getStringExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL))
            assertEquals(rotaTrabalho, trabalho.getStringExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL))
            assertFalse(casa.filterEquals(trabalho))
        }
    }

    @Test fun semMoradaAbreOEscolherDesseTipo() {
        val v = vistasCasaTrabalho(contexto, 1, comLigacoes.copy(trabalho = Destino.SemMorada, rotaTrabalho = null), TamanhoWidget.T4x2, automatica = true)
            .apply(contexto, FrameLayout(contexto))
        val trabalho = tocarAbre(v, R.id.ct_trabalho)
        assertEquals("TRABALHO", trabalho.getStringExtra(MainActivity.EXTRA_DEFINIR_LOCAL))
        assertNull(trabalho.getStringExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL))
        assertEquals(rotaCasa, tocarAbre(v, R.id.ct_casa).getStringExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL))
    }

    @Test fun doisWidgetsNaoTrocamOsToques() {
        val v1 = vistasCasaTrabalho(contexto, 1, comLigacoes, TamanhoWidget.T4x2, automatica = true).apply(contexto, FrameLayout(contexto))
        vistasCasaTrabalho(contexto, 2, comLigacoes.copy(rotaCasa = "Outra|38.7|-9.1"), TamanhoWidget.T4x2, automatica = true)
        assertEquals(rotaCasa, tocarAbre(v1, R.id.ct_casa).getStringExtra(MainActivity.EXTRA_ABRIR_ROTA_LOCAL))
    }

    private fun tocarEnvia(v: View, id: Int): Intent {
        val antes = app().broadcastIntents.size
        v.findViewById<View>(id).performClick()
        return app().broadcastIntents.drop(antes).single()
    }

    // O Trocar de cada filho pede o outro; só diferiam no índice, e o Android juntava-os sem um Uri para cada.
    @Test fun cadaTrocarPedeOOutroDestino() {
        val v = vistasCasaTrabalho(contexto, 1, comLigacoes, TamanhoWidget.T4x1, automatica = true).apply(contexto, FrameLayout(contexto))
        val daCasa = tocarEnvia(v, R.id.ct_casa_trocar)
        val doTrabalho = tocarEnvia(v, R.id.ct_trabalho_trocar)
        assertEquals(WidgetCasaTrabalho.ACAO_TROCAR, daCasa.action)
        assertEquals(1, daCasa.getIntExtra(WidgetCasaTrabalho.EXTRA_MOSTRAR, -1))
        assertEquals(0, doTrabalho.getIntExtra(WidgetCasaTrabalho.EXTRA_MOSTRAR, -1))
        assertEquals(1, daCasa.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertFalse(daCasa.filterEquals(doTrabalho))
    }

    @Test fun oAtualizarPedeAoProviderNovo() {
        val v = vistasCasaTrabalho(contexto, 1, comLigacoes, TamanhoWidget.T4x2, automatica = true).apply(contexto, FrameLayout(contexto))
        val i = tocarEnvia(v, R.id.ct_atualizar)
        assertEquals(WidgetCasaTrabalho.ACAO_ATUALIZAR, i.action)
        assertEquals(WidgetCasaTrabalho::class.java.name, i.component!!.className)
    }

    // ---- o receiver ----

    private val casa = LocalGuardado(1, "Casa", Lugar("Casa", 38.7369, -9.1427, TipoLugar.MORADA), "CASA", 0)
    private val trabalho = LocalGuardado(2, "Trabalho", Lugar("Trabalho", 38.7058, -9.1431, TipoLugar.MORADA), "TRABALHO", 1)
    // Os dois pedidos correm ao mesmo tempo, em linhas diferentes.
    private val pedidos = AtomicInteger()
    private val desenhados = mutableMapOf<Int, ConteudoCasaTrabalho>()

    private fun itinerario(em: Instant): Itinerario {
        val l = Lugar("Paragem", 38.7, -9.15, TipoLugar.PARAGEM)
        val sai = em.plusSeconds(120)
        val chega = sai.plusSeconds(1_800)
        return Itinerario(listOf(Etapa(false, Modo.AUTOCARRO, "210", null, null, l, l, sai, chega, sai, false, 3, emptyList(), "#E2001A")), sai, chega, 0)
    }

    /** Um verdadeiro, sobre falsos: a posição atual em Alcântara, longe dos dois. */
    private fun atualizador(lerLocais: suspend () -> List<LocalGuardado> = { listOf(casa, trabalho) }, ids: List<Int> = listOf(1)) = AtualizadorCasaTrabalho(
        dados, lerLocais,
        planear = { _, _, em -> pedidos.incrementAndGet(); Resultado.Ok(PlanoRotas(listOf(itinerario(em)), null, null)) },
        posicaoAtual = { PosicaoComHora(38.7036, -9.1772, agora.toEpochMilli()) },
        desenhar = { id, c -> desenhados[id] = c },
        todos = { ids },
        relogio = { agora },
    )

    private fun atualizacao(vararg ids: Int) = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    private fun toque(id: Int) = Intent(WidgetCasaTrabalho.ACAO_ATUALIZAR).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    private fun trocar(id: Int, mostrar: Int) =
        Intent(WidgetCasaTrabalho.ACAO_TROCAR).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(WidgetCasaTrabalho.EXTRA_MOSTRAR, mostrar)
    private fun opcoesMudaram(id: Int) = Intent(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, Bundle())

    private fun correr(a: AtualizadorCasaTrabalho, intent: Intent) {
        val p = entregar(contexto, CasaTrabalhoDeTeste { a }, intent)
        esperarPeloFim(p)
        assertTrue(acabou(p))
    }

    @Test fun onUpdateSoRedesenhaEOAtualizarVaiARede() {
        val a = atualizador()
        correr(a, atualizacao(1))
        assertEquals(0, pedidos.get())
        assertTrue(1 in desenhados)
        correr(a, toque(1))
        assertEquals(2, pedidos.get())
    }

    @Test fun doisWidgetsPartilhamOsTrintaSegundos() {
        val a = atualizador(ids = listOf(1, 2))
        correr(a, toque(1))
        correr(a, toque(2))
        assertEquals("um pedido por destino, não por widget", 2, pedidos.get())
        assertEquals(setOf(1, 2), desenhados.keys)
    }

    @Test fun trocarRedesenhaComOOutroSemRede() {
        var desenho: View? = null
        val a = AtualizadorCasaTrabalho(
            dados, { listOf(casa, trabalho) }, { _, _, _ -> pedidos.incrementAndGet(); Resultado.Falha(null, limite = false) }, { null },
            desenhar = { id, c -> desenho = desenhoDoWidget(contexto, id, c).apply(contexto, FrameLayout(contexto)) },
            todos = { listOf(1, 2) }, relogio = { agora },
        )
        correr(a, trocar(1, mostrar = 1))
        assertEquals(1, desenho!!.findViewById<ViewFlipper>(R.id.ct_flipper).displayedChild)
        correr(a, trocar(1, mostrar = 0))
        assertEquals(0, desenho!!.findViewById<ViewFlipper>(R.id.ct_flipper).displayedChild)
        assertEquals(0, pedidos.get())
    }

    @Test fun mudarDeTamanhoRedesenhaEsseWidget() {
        correr(atualizador(ids = listOf(1, 2)), opcoesMudaram(2))
        assertEquals(setOf(2), desenhados.keys)
        assertEquals(0, pedidos.get())
    }

    @Test fun apagarOWidgetEsqueceOQueMostrava() {
        dados.mostrar(1, 1)
        WidgetCasaTrabalho().onReceive(contexto, Intent(AppWidgetManager.ACTION_APPWIDGET_DELETED).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1))
        assertEquals(0, dados.mostrado(1))
    }

    @Test fun oFimDoReceiverEChamadoSempre() {
        val rebenta = atualizador(lerLocais = { throw IllegalStateException("a base de dados rebentou") })
        for (intent in listOf(atualizacao(1), toque(1), trocar(1, 1), opcoesMudaram(1))) {
            assertTrue("${intent.action}", acabou(entregar(contexto, CasaTrabalhoDeTeste { rebenta }, intent)))
            assertTrue("${intent.action} sem atualizador", acabou(entregar(contexto, CasaTrabalhoDeTeste { throw IllegalStateException("não abriu") }, intent)))
        }
    }

    // ---- o que se lê ----

    private fun desenhado(c: ConteudoCasaTrabalho, tamanho: TamanhoWidget, ctx: Context = contexto) =
        vistasCasaTrabalho(ctx, 1, c, tamanho, automatica = true).apply(ctx, FrameLayout(ctx))

    private fun texto(v: View, id: Int) = v.findViewById<TextView>(id).text.toString()
    private fun descricao(v: View, id: Int) = v.findViewById<View>(id).contentDescription.toString()

    @Test fun mostraALigacaoDeCadaDestino() {
        val v = desenhado(comLigacoes, TamanhoWidget.T4x2)
        assertEquals("Casa e Trabalho", texto(v, R.id.ct_titulo))
        assertEquals("Casa", texto(v, R.id.ct_casa_nome))
        assertEquals("210", texto(v, R.id.ct_casa_selo))
        assertEquals("30 min · sai às 18:42", texto(v, R.id.ct_casa_texto))
        assertEquals("728", texto(v, R.id.ct_trabalho_selo))
        assertEquals("atualizado às 18:40", texto(v, R.id.ct_rodape))
        assertEquals(View.GONE, v.findViewById<View>(R.id.ct_casa_selo_icone).visibility)
        assertEquals("Casa: autocarro 210, sai às 18:42, 30 minutos", descricao(v, R.id.ct_casa))
        assertEquals("Trabalho: autocarro 728, sai às 18:42, 30 minutos", descricao(v, R.id.ct_trabalho))
    }

    // Sem rodapé no 4×1: o "atualizado às…" e o "posição das…" vão na descrição.
    @Test fun no4x1ADescricaoLevaDeQuandoE() {
        assertEquals("Casa: autocarro 210, sai às 18:42, 30 minutos, atualizado às 18:40", descricao(desenhado(comLigacoes, TamanhoWidget.T4x1), R.id.ct_casa))
        val guardada = comLigacoes.copy(rodape = Rodape("18:20", daPosicaoGuardada = true))
        assertEquals("Trabalho: autocarro 728, sai às 18:42, 30 minutos, posição das 18:20", descricao(desenhado(guardada, TamanhoWidget.T4x1), R.id.ct_trabalho))
        assertEquals("posição das 18:20", texto(desenhado(guardada, TamanhoWidget.T4x2), R.id.ct_rodape))
    }

    @Test fun cadaEstadoTemOTextoEADescricao() {
        val casos = listOf(
            Destino.Perto("Casa") to ("Estás perto" to "Casa: estás perto"),
            Destino.SemMorada to ("Toca para escolher a morada" to "Casa: toca para escolher a morada"),
            Destino.TocaAtualizar("Casa") to ("Toca em ↻" to "Casa: toca em ↻"),
            Destino.ACalcular("Casa") to ("A calcular…" to "Casa: a calcular…"),
            Destino.SemTransportes("Casa") to ("Não há transportes para lá agora" to "Casa: não há transportes para lá agora"),
            Destino.SemLigacao("Casa") to ("Sem ligação — toca em ↻" to "Casa: sem ligação — toca em ↻"),
            Destino.SemPosicao("Casa") to ("Abre a app para saber onde estás" to "Casa: abre a app para saber onde estás"),
        )
        for ((destino, esperado) in casos) {
            val v = desenhado(comLigacoes.copy(casa = destino), TamanhoWidget.T4x2)
            assertEquals(esperado.first, texto(v, R.id.ct_casa_texto))
            assertEquals(esperado.second, descricao(v, R.id.ct_casa))
            assertEquals("Casa", texto(v, R.id.ct_casa_nome))
            assertEquals(View.GONE, v.findViewById<View>(R.id.ct_casa_selo).visibility)
            assertEquals(View.GONE, v.findViewById<View>(R.id.ct_casa_selo_icone).visibility)
        }
    }

    @Test fun semNumeroDeLinhaOuSoAPeMostraOIcone() {
        val metro = ligacao.copy(linha = null, modo = Modo.METRO)
        val v = desenhado(comLigacoes.copy(casa = Destino.Ligacao("Casa", metro), trabalho = Destino.Ligacao("Trabalho", ligacao.copy(linha = null, soAPe = true))), TamanhoWidget.T4x2)
        assertEquals(View.GONE, v.findViewById<View>(R.id.ct_casa_selo).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.ct_casa_selo_icone).visibility)
        assertEquals("Casa: metro, sai às 18:42, 30 minutos", descricao(v, R.id.ct_casa))
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.ct_trabalho_selo_icone).visibility)
        assertEquals("Trabalho: a pé, sai às 18:42, 30 minutos", descricao(v, R.id.ct_trabalho))
    }

    @Test fun aLigacaoQueJaSaiuFicaACinzento() {
        val v = desenhado(comLigacoes.copy(casa = Destino.Passada("Casa", ligacao)), TamanhoWidget.T4x2)
        val cinzento = contexto.getColor(R.color.widget_cinzento)
        assertEquals(cinzento, v.findViewById<TextView>(R.id.ct_casa_texto).currentTextColor)
        assertEquals(cinzento, v.findViewById<View>(R.id.ct_casa_selo).backgroundTintList!!.defaultColor)
        assertEquals("Casa: autocarro 210, saiu às 18:42, 30 minutos", descricao(v, R.id.ct_casa))
    }

    // ---- legível ----

    @Test fun osTextosLeemSeDepoisDeMudarDeModo() {
        val casos = listOf(comLigacoes, comLigacoes.copy(casa = Destino.Passada("Casa", ligacao), trabalho = Destino.SemLigacao("Trabalho")))
        val textos = listOf(R.id.ct_casa_nome, R.id.ct_casa_texto, R.id.ct_trabalho_nome, R.id.ct_trabalho_texto, R.id.ct_rodape)
        for (escuro in listOf(false, true)) for (c in casos) for (tamanho in TamanhoWidget.entries) {
            val v = aplicadoNoOutroModo(contexto, escuro) { vistasCasaTrabalho(it, 1, c, tamanho, automatica = true) }
            val fundo = (v.background as GradientDrawable).color!!.defaultColor
            textos.mapNotNull { v.findViewById<TextView>(it) }.filter { it.visibility == View.VISIBLE }.forEach {
                val contraste = ColorUtils.calculateContrast(it.currentTextColor, fundo)
                assertTrue("escuro=$escuro $tamanho ${it.resources.getResourceEntryName(it.id)}: $contraste", contraste >= 4.5)
            }
            listOf(R.id.ct_casa_selo, R.id.ct_trabalho_selo).map { v.findViewById<TextView>(it) }.filter { it.visibility == View.VISIBLE }.forEach {
                val selo = it.backgroundTintList!!.defaultColor
                assertEquals(corDoTextoSobre(Color(selo)).toArgb(), it.currentTextColor)
            }
            v.findViewById<TextView>(R.id.ct_titulo)?.let {
                assertEquals(Color.White.toArgb(), it.currentTextColor)
                assertTrue(ColorUtils.calculateContrast(it.currentTextColor, Azulejo.Cobalto.toArgb()) >= 4.5)
            }
        }
    }

    @Test fun oTextoDoSeloLeSeNasCoresConhecidas() {
        for (cor in listOf(Azulejo.Cobalto, Azulejo.Eletrico, Azulejo.Vermelho)) {
            val v = desenhado(comLigacoes.copy(casa = Destino.Ligacao("Casa", ligacao.copy(cor = cor.toArgb()))), TamanhoWidget.T4x2)
            val selo = v.findViewById<TextView>(R.id.ct_casa_selo)
            assertTrue(ColorUtils.calculateContrast(selo.currentTextColor, cor.toArgb()) >= 4.5)
        }
    }

    private fun medido(rv: android.widget.RemoteViews, altura: Int, ctx: Context = contexto): View {
        val v = rv.apply(ctx, FrameLayout(ctx))
        v.measure(View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(altura, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 340, altura)
        return v
    }

    private fun textosVisiveis(v: View): List<TextView> = when {
        v.visibility != View.VISIBLE -> emptyList()
        v is TextView -> listOf(v)
        v is ViewGroup -> (0 until v.childCount).flatMap { textosVisiveis(v.getChildAt(it)) }
        else -> emptyList()
    }

    // A letra a 130 % num 4×2 comum (340×170 dp, a mdpi): nada fica cortado nem com reticências, e o rodapé continua lá.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun com130PorCentoO4x2NaoCorta() {
        val ctx = noModo(contexto, escuro = false, letra = 1.3f)
        val c = comLigacoes.copy(trabalho = Destino.SemTransportes("Trabalho"))
        val v = medido(vistasCasaTrabalho(ctx, 1, c, TamanhoWidget.T4x2, automatica = true), 170, ctx)
        val raiz = IntArray(2).also { v.getLocationInWindow(it) }
        val textos = textosVisiveis(v)
        assertTrue(textos.any { it.id == R.id.ct_rodape })
        for (t in textos) {
            val nome = t.resources.getResourceEntryName(t.id)
            val topo = IntArray(2).also { t.getLocationInWindow(it) }[1] - raiz[1]
            assertTrue("$nome acaba em ${topo + t.height} de ${v.height}", t.height > 0 && topo + t.height <= v.height)
            assertTrue("$nome cabe em altura: ${t.height} < ${t.layout.height}", t.height >= t.layout.height + t.totalPaddingTop + t.totalPaddingBottom)
            assertEquals("$nome sem reticências", 0, (0 until t.layout.lineCount).sumOf { t.layout.getEllipsisCount(it) })
        }
    }
}
