package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.atraso.minutosDeAtraso
import pt.pixellusitano.paragemcerta.core.atraso.rotuloAtraso
import pt.pixellusitano.paragemcerta.core.atraso.rotuloTempo
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LISBOA_FUSO: ZoneId = ZoneId.of("Europe/Lisbon")
private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Cor neutra quando o operador não publica a cor da linha. */
private fun corNeutra(modo: Modo) = when (modo) {
    Modo.METRO -> Color(0xFF4E84C4)
    Modo.COMBOIO -> Color(0xFF1E9D4A)
    Modo.BARCO -> Color(0xFF0E7490)
    Modo.ELETRICO -> Color(0xFFE2A400)
    else -> Color(0xFF1F4FA3) // cobalto: visível no claro e no escuro
}

fun corDaLinha(hex: String?, modo: Modo): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: corNeutra(modo)

private fun minutos(n: Long) = if (n == 1L) "1 minuto" else "$n minutos"

/** Frase completa para o leitor de ecrã, por exemplo "Linha 728 para Restelo, daqui a 7 minutos, atrasado 3 minutos". */
fun descricaoChegada(c: Chegada, agora: Instant, limites: Limites = Limites()): String {
    val quando = when {
        c.prevista == null -> "às ${HORA.format(c.programada.atZone(LISBOA_FUSO))}"
        else -> {
            val s = Duration.between(agora, c.prevista).seconds
            when {
                s < 60 -> "a chegar agora"
                s <= 3600 -> "daqui a ${minutos(s / 60)}"
                else -> "às ${HORA.format(c.prevista!!.atZone(LISBOA_FUSO))}"
            }
        }
    }
    val atraso = c.prevista?.let { minutosDeAtraso(c.programada, it).toLong() }
    val situacao = when (estado(c, limites)) {
        EstadoAtraso.A_HORAS -> "a horas"
        EstadoAtraso.ATRASADO -> "atrasado ${minutos(atraso!!)}"
        EstadoAtraso.MUITO_ATRASADO -> "muito atrasado, ${minutos(atraso!!)}"
        EstadoAtraso.ADIANTADO -> "adiantado ${minutos(-atraso!!)}"
        EstadoAtraso.HORARIO -> "hora do horário, sem tempo real"
    }
    val acessivel = if (c.acessivel == true) ", acessível a cadeira de rodas" else ""
    return "Linha ${c.linhaNome} para ${c.destino}, $quando, $situacao$acessivel"
}

@Composable
fun SeloLinha(nome: String, cor: Color, corTexto: Color = Color.White) {
    Text(
        nome,
        color = corTexto,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 40.dp).background(cor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
fun LinhaChegada(c: Chegada, linha: Linha?, agora: Instant, limites: Limites = Limites()) {
    val cor = CoresAtraso.cor(estado(c, limites), LocalEscuro.current)
    val descricao = descricaoChegada(c, agora, limites)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).clearAndSetSemantics { contentDescription = descricao },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeloLinha(
            linha?.nomeCurto ?: c.linhaNome,
            corDaLinha(linha?.cor ?: c.cor, c.modo),
            linha?.corTexto?.let { corDaLinha(it, c.modo) } ?: Color.White,
        )
        Text(
            c.destino + if (c.acessivel == true) "  ♿" else "",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(rotuloTempo(c, agora, LISBOA_FUSO), color = cor, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text(rotuloAtraso(c, limites), color = cor, style = MaterialTheme.typography.labelSmall)
        }
    }
}
