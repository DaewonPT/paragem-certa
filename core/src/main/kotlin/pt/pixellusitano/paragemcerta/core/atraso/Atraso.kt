package pt.pixellusitano.paragemcerta.core.atraso

import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sign

/** Limites em minutos a partir dos quais uma chegada conta como atrasada e muito atrasada. */
data class Limites(val atrasado: Int = 2, val muitoAtrasado: Int = 5)

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Diferença em minutos inteiros; o meio minuto arredonda para longe de zero (+90 s → +2, −90 s → −2). */
fun minutosDeAtraso(programada: Instant, prevista: Instant): Int {
    val segundos = Duration.between(programada, prevista).seconds
    return ((abs(segundos) + 30) / 60).toInt() * segundos.sign
}

fun estado(c: Chegada, l: Limites = Limites()): EstadoAtraso {
    val d = minutosDeAtraso(c.programada, c.prevista ?: return EstadoAtraso.HORARIO)
    return when {
        d <= -2 -> EstadoAtraso.ADIANTADO
        d < l.atrasado -> EstadoAtraso.A_HORAS
        d <= l.muitoAtrasado -> EstadoAtraso.ATRASADO
        else -> EstadoAtraso.MUITO_ATRASADO
    }
}

/** "agora", "N min" (arredondado para baixo) até 60 min, ou a hora "HH:mm" acima disso e sempre que não há tempo real. */
fun rotuloTempo(c: Chegada, agora: Instant, zona: ZoneId): String {
    val prevista = c.prevista ?: return HORA.format(c.programada.atZone(zona))
    val segundos = Duration.between(agora, prevista).seconds
    return when {
        segundos < 60 -> "agora"
        segundos <= 60 * 60 -> "${segundos / 60} min"
        else -> HORA.format(prevista.atZone(zona))
    }
}

fun rotuloAtraso(c: Chegada, l: Limites = Limites()): String {
    val prevista = c.prevista ?: return "🕒 horário"
    val d = minutosDeAtraso(c.programada, prevista)
    return when (estado(c, l)) {
        EstadoAtraso.A_HORAS -> "a horas"
        EstadoAtraso.ADIANTADO -> "−${-d} adiantado"
        else -> "+$d"
    }
}

/** Falso quando a hora em uso (prevista ou, sem ela, programada) já passou há mais de 30 s. */
fun aindaPorChegar(c: Chegada, agora: Instant): Boolean =
    !(c.prevista ?: c.programada).plusSeconds(30).isBefore(agora)
