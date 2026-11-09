package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade

/** Cores do estilo Azulejo. */
object Azulejo {
    val Cobalto = Color(0xFF1F4FA3)
    val Eletrico = Color(0xFFFFC629)
    val Tinta = Color(0xFF132A4F)
    val Branco = Color(0xFFFFFFFF)
    val Cinza = Color(0xFF56647C) // 5,98:1 sobre branco, 5,57:1 sobre marfim
    val Marfim = Color(0xFFF4F7FC)
    val Vermelho = Color(0xFFC81E1E)
    val Contorno = Color(0xFFDCE4F2)

    val Noite = Color(0xFF0B1222)
    val NoiteSuperficie = Color(0xFF121B30)
    val CeuNoite = Color(0xFF7FB0FF)
    val TextoNoite = Color(0xFFEAF0FB)
    val CinzaNoite = Color(0xFF8D9AB3)
    val LinhaEscura = Color(0xFF223052)
    val ContornoNoite = Color(0xFF33467A)
    val VermelhoNoite = Color(0xFFF87171)
    val FrisoNoite = Color(0xFF2A4F96)
}

/** Cores dos minutos conforme o atraso. */
object CoresAtraso {
    fun cor(estado: EstadoAtraso, escuro: Boolean): Color = when (estado) {
        EstadoAtraso.A_HORAS -> if (escuro) Color(0xFF4ADE80) else Color(0xFF15803D)
        EstadoAtraso.ATRASADO -> if (escuro) Color(0xFFFBBF24) else Color(0xFFB45309)
        EstadoAtraso.MUITO_ATRASADO -> if (escuro) Azulejo.VermelhoNoite else Azulejo.Vermelho
        EstadoAtraso.ADIANTADO -> if (escuro) Color(0xFF7FB0FF) else Color(0xFF1F4FA3)
        EstadoAtraso.HORARIO -> if (escuro) Color(0xFF9AA4B2) else Color(0xFF5B6472)
    }
}

/** Fundo e texto de cada gravidade (alertas e relatos). */
object CoresGravidade {
    fun fundo(g: Gravidade, escuro: Boolean): Color = when (g) {
        Gravidade.GRAVE -> if (escuro) Color(0xFF3A1414) else Color(0xFFFDECEC)
        Gravidade.AVISO -> if (escuro) Color(0xFF3A2E0B) else Color(0xFFFFF4CF)
        Gravidade.INFO -> if (escuro) Color(0xFF14233F) else Color(0xFFE8EFFB)
    }

    fun texto(g: Gravidade, escuro: Boolean): Color = when (g) {
        Gravidade.GRAVE -> if (escuro) Color(0xFFF87171) else Color(0xFF9B1C1C)
        Gravidade.AVISO -> if (escuro) Color(0xFFFBBF24) else Color(0xFF7A5A00)
        Gravidade.INFO -> if (escuro) Color(0xFF7FB0FF) else Color(0xFF1F4FA3)
    }
}
