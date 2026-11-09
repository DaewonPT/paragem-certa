package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.atraso.minutosDeAtraso
import pt.pixellusitano.paragemcerta.core.atraso.rotuloAtraso
import pt.pixellusitano.paragemcerta.core.atraso.rotuloTempo
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.dados.AoVivo
import pt.pixellusitano.paragemcerta.ui.linhas.textoDoModo
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import pt.pixellusitano.paragemcerta.ui.tema.contraste
import pt.pixellusitano.paragemcerta.ui.tema.iconeModo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LISBOA_FUSO: ZoneId = ZoneId.of("Europe/Lisbon")

/** Limites de atraso escolhidos nas definições; chegam a todas as linhas de chegada sem passar por cada ecrã. */
val LocalLimites = androidx.compose.runtime.staticCompositionLocalOf { Limites() }
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

internal fun minutos(n: Long) = if (n == 1L) "1 minuto" else "$n minutos"

/** "a horas", "atrasado 3 minutos", "adiantado 2 minutos" ou "hora do horário, sem tempo real", para o leitor de ecrã. */
fun situacaoAtraso(programada: Instant, prevista: Instant?, limites: Limites): String {
    val atraso = prevista?.let { minutosDeAtraso(programada, it).toLong() }
    return when (estado(programada, prevista, limites)) {
        EstadoAtraso.A_HORAS -> "a horas"
        EstadoAtraso.ATRASADO -> "atrasado ${minutos(atraso!!)}"
        EstadoAtraso.MUITO_ATRASADO -> "muito atrasado, ${minutos(atraso!!)}"
        EstadoAtraso.ADIANTADO -> "adiantado ${minutos(-atraso!!)}"
        EstadoAtraso.HORARIO -> "hora do horário, sem tempo real"
    }
}

/**
 * Frase completa para o leitor de ecrã, por exemplo "Linha 728 para Restelo, daqui a 7 minutos, atrasado 3 minutos". [aoVivo] é o texto
 * "ao vivo, a 5 paragens" que se vê no lugar do "horário": o leitor diz o mesmo em vez de "hora do horário, sem tempo real".
 */
fun descricaoChegada(c: Chegada, agora: Instant, limites: Limites = Limites(), aoVivo: String? = null): String {
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
    val situacao = aoVivo ?: situacaoAtraso(c.programada, c.prevista, limites)
    val acessivel = if (c.acessivel == true) ", acessível a cadeira de rodas" else ""
    return "Linha ${c.linhaNome} para ${c.destino}, $quando, $situacao$acessivel"
}

@Composable
fun SeloLinha(nome: String, cor: Color, corTexto: Color? = null, modifier: Modifier = Modifier) {
    Text(
        nome,
        color = corTexto ?: corDoTextoSobre(cor),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.widthIn(min = 40.dp).background(cor, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Branco ou tinta, o que tiver mais contraste com [fundo] (empate: branco). */
fun corDoTextoSobre(fundo: Color): Color =
    if (contraste(Color.White, fundo) >= contraste(Azulejo.Tinta, fundo)) Color.White else Azulejo.Tinta

/** O mesmo selo, com o ícone do modo de transporte em vez do número da linha. */
@Composable
fun SeloModo(modo: Modo, cor: Color) {
    Box(Modifier.widthIn(min = 40.dp).background(cor, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Icon(iconeModo(modo), contentDescription = stringResource(textoDoModo(modo)), tint = corDoTextoSobre(cor), modifier = Modifier.size(18.dp))
    }
}

/** "ao vivo, a 5 paragens" ou, a zero, "ao vivo, a chegar". */
@Composable
private fun textoAoVivo(a: AoVivo): String =
    if (a.paragensEmFalta == 0) stringResource(R.string.ao_vivo_a_chegar)
    else pluralStringResource(R.plurals.ao_vivo_paragens, a.paragensEmFalta, a.paragensEmFalta)

/** "+3", "a horas" ou, sem hora prevista (só a do horário), o relógio e "horário". */
@Composable
fun RotuloAtraso(programada: Instant, prevista: Instant?, limites: Limites, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (prevista == null) Icon(Icones.Relogio, null, Modifier.size(ladoDoIcone(12)), tint = cor)
        Text(rotuloAtraso(programada, prevista, limites), color = cor, style = MaterialTheme.typography.labelSmall)
    }
}

/** [aoVivo] (só Carris) toma o lugar do "horário", a verde; com tempo real do Transitous na chegada, esse é que se mostra. */
@Composable
fun LinhaChegada(c: Chegada, linha: Linha?, agora: Instant, limites: Limites = LocalLimites.current, aoVivo: AoVivo? = null) {
    val escuro = LocalEscuro.current
    val cor = CoresAtraso.cor(estado(c, limites), escuro)
    val etiquetaAoVivo = aoVivo?.takeIf { c.prevista == null }?.let { textoAoVivo(it) }
    val descricao = descricaoChegada(c, agora, limites, etiquetaAoVivo)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).clearAndSetSemantics { contentDescription = descricao },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeloLinha(
            linha?.nomeCurto ?: c.linhaNome,
            corDaLinha(linha?.cor ?: c.cor, c.modo),
            linha?.corTexto?.let { corDaLinha(it, c.modo) },
        )
        Text(
            c.destino + if (c.acessivel == true) "  ♿" else "",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(rotuloTempo(c, agora, LISBOA_FUSO), color = cor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            if (etiquetaAoVivo != null) Text(etiquetaAoVivo, color = CoresAtraso.cor(EstadoAtraso.A_HORAS, escuro), style = MaterialTheme.typography.labelSmall)
            else RotuloAtraso(c.programada, c.prevista, limites, cor)
        }
    }
}
