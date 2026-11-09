package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.core.modelo.Modo

// Círculo escrito como dois arcos.
private fun circ(cx: Float, cy: Float, r: Float) =
    "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

// Grelha de 24, traço 2, pontas redondas; a cor vem do tint do Icon.
private fun icone(nome: String, vararg tracos: String, cheio: String? = null): ImageVector =
    ImageVector.Builder(nome, 24.dp, 24.dp, 24f, 24f).apply {
        cheio?.let {
            addPath(
                addPathNodes(it), fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        tracos.forEach {
            addPath(
                addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Icones {
    val Inicio = icone("Inicio", "M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z", "M9 4v14", "M15 6v14")
    val Rotas = icone("Rotas", circ(6f, 18f, 2f), circ(18f, 6f, 2f), "M8 18h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7")
    val Linhas = icone("Linhas", "M12 10v11", "M9 3h6a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z")
    val Menu = icone("Menu", "M4 6h16M4 12h16M4 18h16")
    val Posicao = icone("Posicao", circ(12f, 12f, 7f), "M12 2v3M12 19v3M2 12h3M19 12h3")
    val PosicaoCheia = icone("PosicaoCheia", circ(12f, 12f, 7f), "M12 2v3M12 19v3M2 12h3M19 12h3", cheio = circ(12f, 12f, 2.5f))
    val Bussola = icone("Bussola", "M12 21 L9 12 L12 13 L15 12 Z", cheio = "M12 3 L15 12 L12 11 L9 12 Z")
    val Navegacao = icone("Navegacao", cheio = "M12 3 L19 20 L12 16 L5 20 Z")
    val Pesquisa = icone("Pesquisa", circ(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Voltar = icone("Voltar", "M19 12H5M11 6l-6 6 6 6")
    val Fechar = icone("Fechar", "M6 6l12 12M18 6L6 18")
    val Trocar = icone("Trocar", "M4 8h15M15 4l4 4-4 4", "M20 16H5M9 12l-4 4 4 4")
    val Casa = icone("Casa", "M4 11l8-7 8 7", "M6 10v10h12V10")
    val Trabalho = icone("Trabalho", "M3 8h18v12H3z", "M9 8V5h6v3")
    val Estrela = icone("Estrela", "M12 3l2.8 5.8 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 3 1.1-6.2L3 9.7l6.2-.9z")
    val Relogio = icone("Relogio", circ(12f, 12f, 9f), "M12 7v5l3 2")
    val APe = icone("APe", circ(13f, 5f, 2f), "M12 9l-2 6 3 2 1 4M10 15l-2 5M12 9l3 3 3 1")
    val Autocarro = icone(
        "Autocarro", "M5 3h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M4 11h16", "M8 15h.01M16 15h.01", "M7 18v3M17 18v3",
    )
    val Metro = icone(
        "Metro", "M6 3h12a2 2 0 0 1 2 2v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V5a2 2 0 0 1 2-2z",
        "M4 10h16", "M8 14h.01M16 14h.01", "M8 18l-2 3M16 18l2 3",
    )
    val Comboio = icone(
        "Comboio", "M8 3h8a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z",
        "M9 8h6", "M9 14h.01M15 14h.01", "M8 18l-2 3M16 18l2 3",
    )
    val Barco = icone("Barco", "M3 16l2 4h14l2-4z", "M7 16v-6h10v6", "M12 10V4")
    val Eletrico = icone(
        "Eletrico", "M6 6h12a1 1 0 0 1 1 1v10H5V7a1 1 0 0 1 1-1z",
        "M5 11h14", "M8 14h.01M16 14h.01", "M9 2h6M12 2v4", "M7 17l-2 4M17 17l2 4",
    )
    val Avisar = icone("Avisar", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    val Repetir = icone("Repetir", "M20 12a8 8 0 1 1-2.34-5.66", "M18 3v3.5h-3.5")
    val Conta = icone("Conta", circ(12f, 8f, 4f), "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Partida = icone("Partida", "M5 21V4", "M5 4h12l-2 4 2 4H5")
    val Mais = icone("Mais", "M5 12h.01M12 12h.01M19 12h.01")

    val todos: List<Pair<String, ImageVector>> = listOf(
        "Inicio" to Inicio, "Rotas" to Rotas, "Linhas" to Linhas, "Menu" to Menu,
        "Posicao" to Posicao, "PosicaoCheia" to PosicaoCheia, "Bussola" to Bussola,
        "Navegacao" to Navegacao, "Pesquisa" to Pesquisa, "Voltar" to Voltar,
        "Fechar" to Fechar, "Trocar" to Trocar, "Casa" to Casa, "Trabalho" to Trabalho,
        "Estrela" to Estrela, "Relogio" to Relogio, "APe" to APe, "Autocarro" to Autocarro,
        "Metro" to Metro, "Comboio" to Comboio, "Barco" to Barco, "Eletrico" to Eletrico,
        "Avisar" to Avisar, "Repetir" to Repetir, "Conta" to Conta, "Partida" to Partida,
        "Mais" to Mais,
    )
}

fun iconeModo(m: Modo): ImageVector = when (m) {
    Modo.AUTOCARRO, Modo.OUTRO -> Icones.Autocarro
    Modo.ELETRICO -> Icones.Eletrico
    Modo.METRO -> Icones.Metro
    Modo.COMBOIO -> Icones.Comboio
    Modo.BARCO -> Icones.Barco
}

fun iconeLocal(tipo: String): ImageVector = when (tipo) {
    "CASA" -> Icones.Casa
    "TRABALHO" -> Icones.Trabalho
    else -> Icones.Estrela
}
