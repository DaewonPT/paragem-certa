package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso

/** Cores do estilo Azulejo (especificação, secção 5.3). */
object Azulejo {
    val Cobalto = Color(0xFF1F4FA3)
    val Eletrico = Color(0xFFFFC629)
    val Tinta = Color(0xFF132A4F)
    val Branco = Color(0xFFFFFFFF)
    val Cinza = Color(0xFF6B7A90)
    val LinhaClara = Color(0xFFE5EAF3)

    val Noite = Color(0xFF0B1222)
    val NoiteSuperficie = Color(0xFF121B30)
    val CeuNoite = Color(0xFF7FB0FF)
    val TextoNoite = Color(0xFFEAF0FB)
    val CinzaNoite = Color(0xFF8D9AB3)
    val LinhaEscura = Color(0xFF223052)
    val FrisoNoite = Color(0xFF2A4F96)
}

/** Cores dos minutos conforme o atraso (especificação, secção 5.4). */
object CoresAtraso {
    fun cor(estado: EstadoAtraso, escuro: Boolean): Color = when (estado) {
        EstadoAtraso.A_HORAS -> if (escuro) Color(0xFF4ADE80) else Color(0xFF15803D)
        EstadoAtraso.ATRASADO -> if (escuro) Color(0xFFFBBF24) else Color(0xFFB45309)
        EstadoAtraso.MUITO_ATRASADO -> if (escuro) Color(0xFFF87171) else Color(0xFFC81E1E)
        EstadoAtraso.ADIANTADO -> if (escuro) Color(0xFF7FB0FF) else Color(0xFF1F4FA3)
        EstadoAtraso.HORARIO -> if (escuro) Color(0xFF9AA4B2) else Color(0xFF5B6472)
    }
}
