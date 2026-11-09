package pt.pixellusitano.paragemcerta.widget

import androidx.compose.ui.graphics.toArgb
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

sealed interface EstadoWidget {
    data object Normal : EstadoWidget
    data object AAtualizar : EstadoWidget
    data object SemLigacao : EstadoWidget
}

/** [cor]: a cor do selo da linha, em ARGB. */
data class LinhaWidget(val linha: String, val destino: String, val hora: String, val aoVivo: Boolean, val cor: Int)

/** [paragem] null: o widget ainda não tem paragem escolhida (ou esqueceu-a). */
data class ConteudoWidget(val paragem: String?, val linhas: List<LinhaWidget>, val atualizadoAs: String?, val cinzento: Boolean, val estado: EstadoWidget)

private const val MAX_LINHAS = 3
private val VELHO_DEPOIS_DE: Duration = Duration.ofMinutes(15)
private val HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(LISBOA_FUSO)

fun conteudoDoWidget(paragem: String?, guardadas: Pair<List<Chegada>, Instant>?, agora: Instant, estado: EstadoWidget, cores: Map<String, String> = emptyMap()): ConteudoWidget {
    val linhas = guardadas?.first.orEmpty().filter { aindaPorChegar(it, agora) }.take(MAX_LINHAS).map { c ->
        LinhaWidget(c.linhaNome, c.destino, HORA.format(c.prevista ?: c.programada), aoVivo = c.prevista != null, cor = corDaLinha(cores[c.linhaId] ?: c.cor, c.modo).toArgb())
    }
    val em = guardadas?.second
    return ConteudoWidget(paragem, linhas, em?.let(HORA::format), em != null && Duration.between(em, agora) > VELHO_DEPOIS_DE, estado)
}
