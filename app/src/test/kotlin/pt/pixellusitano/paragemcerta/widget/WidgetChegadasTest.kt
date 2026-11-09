package pt.pixellusitano.paragemcerta.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.ui.comum.corDoTextoSobre
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import java.time.Instant

private val COBALTO = Azulejo.Cobalto.toArgb()
private val ELETRICO = Azulejo.Eletrico.toArgb()

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/** O provider a sério, com o atualizador trocado: é por aqui que os testes fazem o trabalho falhar e contam os pedidos. */
private class WidgetDeTeste(private val a: AtualizadorWidgets) : WidgetChegadas() {
    override fun atualizador(contexto: Context) = a
}

@RunWith(RobolectricTestRunner::class)
class WidgetChegadasTest {
    private val contexto: Context = RuntimeEnvironment.getApplication()
    private val dados = DadosWidget(contexto)
    private val agora = Instant.parse("2026-11-04T14:00:00Z")
    private var pedidos = 0

    private fun atualizador(ler: suspend () -> Pair<List<Chegada>, Instant>? = { null }) =
        AtualizadorWidgets(dados, { _, _ -> ler() }, { emptyMap() }, { _, _ -> pedidos++; Resultado.Ok(emptyList()) }, { _, _ -> }, { emptyList() }, { agora })

    /** O onReceive como o Android o chama, com um PendingResult que sabe se levou com o finish(). */
    private fun entregar(receiver: BroadcastReceiver, intent: Intent): BroadcastReceiver.PendingResult {
        val pendente = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
            ShadowBroadcastPendingResult::class.java, "create",
            ClassParameter.from(Int::class.java, 0), ClassParameter.from(String::class.java, null),
            ClassParameter.from(Bundle::class.java, null), ClassParameter.from(Boolean::class.java, false),
        )
        ReflectionHelpers.setField(receiver, "mPendingResult", pendente)
        receiver.onReceive(contexto, intent)
        return pendente
    }

    private fun acabou(p: BroadcastReceiver.PendingResult) = Shadow.extract<ShadowBroadcastPendingResult>(p).future.isDone

    private fun esperarPeloFim(p: BroadcastReceiver.PendingResult) {
        val limite = System.currentTimeMillis() + 5_000
        while (!acabou(p) && System.currentTimeMillis() < limite) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
    }

    private fun atualizacao(vararg ids: Int) = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

    private fun toque(id: Int) = Intent(WidgetChegadas.ACAO_ATUALIZAR).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun configuracao() = contexto.resources.getXml(R.xml.widget_chegadas).apply { while (name != "appwidget-provider") next() }

    private fun desenhado(conteudo: ConteudoWidget) = vistas(contexto, 1, conteudo).apply(contexto, FrameLayout(contexto))

    private fun noModo(escuro: Boolean) = contexto.createConfigurationContext(
        Configuration(contexto.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (escuro) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        },
    )

    /** O launcher volta a aplicar os mesmos RemoteViews quando o telemóvel muda de modo, sem nada redesenhar. */
    private fun aplicadoNoOutroModo(desenhadoEscuro: Boolean, conteudo: ConteudoWidget): View {
        val ecra = noModo(!desenhadoEscuro)
        return vistas(noModo(desenhadoEscuro), 1, conteudo).apply(ecra, FrameLayout(ecra))
    }

    private val comChegadas = ConteudoWidget(
        "Cais",
        listOf(LinhaWidget("728", "Restelo", "14:32", aoVivo = true, cor = COBALTO), LinhaWidget("15E", "Algés", "14:40", aoVivo = false, cor = ELETRICO)),
        "14:00", cinzento = false, EstadoWidget.Normal,
    )

    @Test fun oTextoLeSeDepoisDeMudarDeModo() {
        val textos = listOf(
            R.id.widget_aviso, R.id.widget_rodape,
            R.id.widget_linha_1_destino, R.id.widget_linha_1_hora,
            R.id.widget_linha_2_destino, R.id.widget_linha_2_hora,
        )
        val casos = listOf(
            comChegadas,
            comChegadas.copy(cinzento = true),
            comChegadas.copy(linhas = emptyList()),
            ConteudoWidget(null, emptyList(), null, false, EstadoWidget.Normal),
        )
        for (escuro in listOf(false, true)) for (conteudo in casos) {
            val v = aplicadoNoOutroModo(escuro, conteudo)
            val fundo = (v.background as GradientDrawable).color!!.defaultColor
            // O texto de uma linha escondida continua VISIBLE; quem esconde é a linha, a mãe dele.
            textos.map { v.findViewById<TextView>(it) }.filter { it.visibility == View.VISIBLE && (it.parent as View).visibility == View.VISIBLE }.forEach {
                val contraste = ColorUtils.calculateContrast(it.currentTextColor, fundo)
                assertTrue("desenhado escuro=$escuro, ${it.resources.getResourceEntryName(it.id)} em $conteudo: $contraste", contraste >= 4.5)
            }
        }
    }

    // A pixeis: o que conta é a cor que se vê, e o ícone não pode ter um android:tint a pintá-lo de outra.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun oAtualizarFicaBrancoNosDoisModos() {
        for (escuro in listOf(false, true)) {
            val botao = aplicadoNoOutroModo(escuro, comChegadas).findViewById<ImageButton>(R.id.widget_atualizar)
            val lado = View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY)
            botao.measure(lado, lado)
            botao.layout(0, 0, 48, 48)
            val imagem = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { botao.draw(Canvas(it)) }
            val pixeis = IntArray(48 * 48).also { imagem.getPixels(it, 0, 48, 0, 0, 48, 48) }
            assertTrue("desenhado escuro=$escuro", Color.White.toArgb() in pixeis)
            assertFalse("desenhado escuro=$escuro", noModo(escuro).getColor(R.color.widget_texto) in pixeis)
        }
    }

    @Test fun oNomeLeSeSobreOCobaltoEFicaBrancoTambemNoEstadoAntigo() {
        for (escuro in listOf(false, true)) for (conteudo in listOf(comChegadas, comChegadas.copy(cinzento = true))) {
            val nome = aplicadoNoOutroModo(escuro, conteudo).findViewById<TextView>(R.id.widget_paragem)
            assertEquals(Color.White.toArgb(), nome.currentTextColor)
            assertTrue(ColorUtils.calculateContrast(nome.currentTextColor, Azulejo.Cobalto.toArgb()) >= 4.5)
        }
    }

    @Test fun oNumeroDeCadaLinhaLeSeNoSeuSelo() {
        val cores = listOf(Azulejo.Cobalto, Azulejo.Eletrico, Azulejo.Vermelho)
        val linhas = cores.mapIndexed { i, c -> LinhaWidget("${i + 1}", "Restelo", "14:32", aoVivo = false, cor = c.toArgb()) }
        val v = desenhado(comChegadas.copy(linhas = linhas))
        listOf(R.id.widget_linha_1_nome, R.id.widget_linha_2_nome, R.id.widget_linha_3_nome).zip(cores).forEach { (id, cor) ->
            val selo = v.findViewById<TextView>(id)
            assertEquals(cor.toArgb(), selo.backgroundTintList!!.defaultColor)
            assertEquals(corDoTextoSobre(cor).toArgb(), selo.currentTextColor)
            assertTrue(ColorUtils.calculateContrast(selo.currentTextColor, cor.toArgb()) >= 4.5)
        }
    }

    @Test fun noEstadoAntigoOSeloCinzentoEscolheOTextoPeloContraste() {
        for (escuro in listOf(false, true)) {
            val v = aplicadoNoOutroModo(escuro, comChegadas.copy(cinzento = true))
            val cinzento = noModo(escuro).getColor(R.color.widget_cinzento)
            val selo = v.findViewById<TextView>(R.id.widget_linha_1_nome)
            assertEquals(cinzento, selo.backgroundTintList!!.defaultColor)
            assertEquals(corDoTextoSobre(Color(cinzento)).toArgb(), selo.currentTextColor)
            assertTrue(ColorUtils.calculateContrast(selo.currentTextColor, cinzento) >= 4.5)
        }
    }

    @Test fun naoAcordaSozinho() = assertEquals(0, configuracao().getAttributeIntValue(ANDROID, "updatePeriodMillis", -1))

    // O Android faz ComponentName(pacote, configure) sem acrescentar nada: ".widget.X" não abria, e a variante pessoal tem outro pacote.
    @Test fun configuraComONomeCompletoDaAtividade() =
        assertEquals(EscolherParagemAtividade::class.java.name, configuracao().getAttributeValue(ANDROID, "configure"))

    @Test fun naListaDeWidgetsTemONomeDele() =
        assertEquals(R.string.widget_nome, contexto.packageManager.getReceiverInfo(ComponentName(contexto, WidgetChegadas::class.java), 0).labelRes)

    @Test fun vistasMostramAsChegadas() {
        val v = desenhado(
            ConteudoWidget(
                "Cais do Sodré",
                listOf(LinhaWidget("728", "Restelo", "14:32", aoVivo = true, cor = COBALTO), LinhaWidget("15E", "Algés", "14:40", aoVivo = false, cor = ELETRICO)),
                "14:00", cinzento = false, EstadoWidget.Normal,
            ),
        )
        fun texto(id: Int) = v.findViewById<TextView>(id).text.toString()

        assertEquals("Cais do Sodré", texto(R.id.widget_paragem))
        assertEquals("728", texto(R.id.widget_linha_1_nome))
        assertEquals("Restelo", texto(R.id.widget_linha_1_destino))
        assertEquals("14:32", texto(R.id.widget_linha_1_hora))
        assertEquals("15E", texto(R.id.widget_linha_2_nome))
        assertEquals("728 para Restelo às 14:32, em tempo real", v.findViewById<View>(R.id.widget_linha_1).contentDescription)
        assertEquals("15E para Algés às 14:40", v.findViewById<View>(R.id.widget_linha_2).contentDescription)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_linha_2).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_linha_3).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_aviso).visibility)
        assertEquals("atualizado às 14:00", texto(R.id.widget_rodape))
        assertEquals(contexto.getColor(R.color.widget_verde), v.findViewById<TextView>(R.id.widget_linha_1_hora).currentTextColor)
        assertEquals(contexto.getColor(R.color.widget_texto), v.findViewById<TextView>(R.id.widget_linha_2_hora).currentTextColor)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_atualizar).visibility)
        assertEquals("Atualizar", v.findViewById<View>(R.id.widget_atualizar).contentDescription)
    }

    @Test fun vistasCinzentasPerdemAsCores() {
        val v = desenhado(ConteudoWidget("Cais", listOf(LinhaWidget("728", "Restelo", "14:32", aoVivo = true, cor = COBALTO)), "14:00", cinzento = true, EstadoWidget.Normal))
        val cinzento = contexto.getColor(R.color.widget_cinzento)
        listOf(R.id.widget_linha_1_destino, R.id.widget_linha_1_hora, R.id.widget_rodape).forEach {
            assertEquals(cinzento, v.findViewById<TextView>(it).currentTextColor)
        }
    }

    @Test fun rodapeDizOEstado() {
        fun rodape(estado: EstadoWidget, atualizadoAs: String?) =
            desenhado(ConteudoWidget("Cais", emptyList(), atualizadoAs, false, estado)).findViewById<TextView>(R.id.widget_rodape).text.toString()
        assertEquals("A atualizar…", rodape(EstadoWidget.AAtualizar, "14:00"))
        assertEquals("Sem ligação — dados das 14:00", rodape(EstadoWidget.SemLigacao, "14:00"))
        assertEquals("Sem ligação", rodape(EstadoWidget.SemLigacao, null))
        assertEquals("", rodape(EstadoWidget.Normal, null))
    }

    @Test fun semChegadasDizParaTocar() {
        val v = desenhado(ConteudoWidget("Cais", emptyList(), null, false, EstadoWidget.Normal))
        assertEquals("Ainda sem chegadas. Toca em ↻.", v.findViewById<TextView>(R.id.widget_aviso).text.toString())
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.widget_aviso).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_linha_1).visibility)
    }

    @Test fun semParagemPedeParaEscolher() {
        val v = desenhado(ConteudoWidget(null, emptyList(), null, false, EstadoWidget.Normal))
        assertEquals("Toca para escolher a paragem", v.findViewById<TextView>(R.id.widget_aviso).text.toString())
        assertEquals(View.GONE, v.findViewById<View>(R.id.widget_atualizar).visibility)
    }

    @Test fun oFimDoReceiverEChamadoSempre() {
        dados.escolher(1, "CM:060419", "Cais")
        val rebenta = atualizador(ler = { throw IllegalStateException("a base de dados rebentou") })
        assertTrue("redesenhar", acabou(entregar(WidgetDeTeste(atualizador()), atualizacao(1))))
        assertTrue("redesenhar que rebenta", acabou(entregar(WidgetDeTeste(rebenta), atualizacao(1))))
        assertTrue("tocar que rebenta", acabou(entregar(WidgetDeTeste(rebenta), toque(1))))
        dados.esquecer(1)
        assertTrue("tocar sem paragem", acabou(entregar(WidgetDeTeste(atualizador()), toque(1))))
    }

    @Test fun oFimEChamadoMesmoSemSeConseguirCriarOAtualizador() {
        val estragado = object : WidgetChegadas() {
            override fun atualizador(contexto: Context): AtualizadorWidgets = throw IllegalStateException("não abriu")
        }
        assertTrue(acabou(entregar(estragado, atualizacao(1))))
    }

    @Test fun oFimEsperaPeloTrabalho() {
        dados.escolher(1, "CM:060419", "Cais")
        val demora = CompletableDeferred<Unit>()
        val p = entregar(WidgetDeTeste(atualizador(ler = { demora.await(); null })), atualizacao(1))
        assertFalse("ainda a trabalhar", acabou(p))
        demora.complete(Unit)
        esperarPeloFim(p)
        assertTrue(acabou(p))
    }

    @Test fun atualizacaoDoAndroidSoRedesenha() {
        dados.escolher(1, "CM:060419", "Cais")
        entregar(WidgetDeTeste(atualizador()), atualizacao(1))
        assertEquals(0, pedidos)
    }

    @Test fun toqueVaiARede() {
        dados.escolher(1, "CM:060419", "Cais")
        val p = entregar(WidgetDeTeste(atualizador()), toque(1))
        esperarPeloFim(p)
        assertTrue(acabou(p))
        assertEquals(1, pedidos)
    }

    @Test fun apagarWidgetEsqueceAParagem() {
        dados.escolher(1, "CM:060419", "Cais")
        dados.escolher(2, "CM:1", "Outra")
        dados.marcarTentativa(1, agora)
        WidgetChegadas().onReceive(contexto, Intent(AppWidgetManager.ACTION_APPWIDGET_DELETED).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1))
        assertNull(dados.escolha(1))
        assertNull(dados.ultimaTentativa(1))
        assertEquals("CM:1" to "Outra", dados.escolha(2))
    }
}
