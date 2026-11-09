package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
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

private const val ESTRELA = "M12 3l2.8 5.8 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 3 1.1-6.2L3 9.7l6.2-.9z"

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
    val Estrela = icone("Estrela", ESTRELA)
    val EstrelaCheia = icone("EstrelaCheia", cheio = ESTRELA)
    val Relogio = icone("Relogio", circ(12f, 12f, 9f), "M12 7v5l3 2")
    val APe = icone("APe", circ(13f, 5f, 2f), "M12 9l-2 6 3 2 1 4M10 15l-2 5M12 9l3 3 3 1")
    val Autocarro = icone(
        "Autocarro", "M5 3h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M4 11h16", "M8 15h.01M16 15h.01", "M7 18v3M17 18v3",
    )
    val Metro = icone(
        "Metro", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M7 17V8l5 6 5-6v9",
    )
    val Comboio = icone(
        "Comboio", "M3 18V7a2 2 0 0 1 2-2h7c4 0 7 5 9 9v4z",
        "M7 9h4v3H7z", "M7 21h.01M17 21h.01",
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
    val Lugar = icone("Lugar", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z", circ(12f, 10f, 2.5f))
    val Aviso = icone("Aviso", "M12 3l9.5 17h-19z", "M12 10v4M12 17h.01")
    val Grave = icone("Grave", circ(12f, 12f, 9f), "M8 12h8")
    val Info = icone("Info", circ(12f, 12f, 9f), "M12 11v5M12 8h.01")
    val Seguinte = icone("Seguinte", "M9 6l6 6-6 6")
    val Subir = icone("Subir", "M12 19V5M5 12l7-7 7 7")
    val Diminuir = icone("Diminuir", "M5 12h14")
    val Aumentar = icone("Aumentar", "M12 5v14M5 12h14")
    val Lapis = icone("Lapis", "M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z", "M13.5 6.5l4 4")

    // Carro de frente com a estrela do choque no canto.
    val Acidente = icone(
        "Acidente", "M3 20v-5l1.5-4h10l1.5 4v5z", "M3 15h13", "M6 17.5h.01M13 17.5h.01", "M5 20v1.5M14 20v1.5",
        cheio = "M19 2l.9 2.4 2.4-.6-1.2 2.2 1.9 1.5-2.5.4.1 2.5-1.6-1.9-1.6 1.9.1-2.5-2.5-.4 1.9-1.5-1.2-2.2 2.4.6z",
    )
    val Desvio = icone("Desvio", "M7 21v-8a5 5 0 0 1 5-5h7", "M16 5l3 3-3 3", "M7 3h.01M7 6.5h.01")
    val Evento = icone(
        "Evento", "M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M4 10h16", "M8 3v4M16 3v4", cheio = "M14 14h3v3h-3z",
    )
    val NaoPassou = icone("NaoPassou", circ(10f, 14f, 7f), "M10 10.5V14l2.5 1.5", "M16.5 2.5l5 5M21.5 2.5l-5 5")
    // Três pessoas: uma à frente e duas meio escondidas.
    val Cheio = icone(
        "Cheio", circ(12f, 9f, 3f), "M6 21c0-3.3 2.7-6 6-6s6 2.7 6 6",
        "M5.5 4.5a2.5 2.5 0 0 0 0 5M18.5 4.5a2.5 2.5 0 0 1 0 5", "M2 19c0-2.5 1.3-4.5 3.5-5.3M22 19c0-2.5-1.3-4.5-3.5-5.3",
    )
    val Elevador = icone(
        "Elevador", "M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        cheio = "M12 6.5l2.5 3h-5zM12 17.5l2.5-3h-5z",
    )
    // Placa redonda de paragem com "!", num poste.
    val ProblemaParagem = icone("ProblemaParagem", circ(12f, 8f, 6f), "M12 5v3.5M12 11h.01", "M12 14v7", "M9 21h6")

    val todos: List<Pair<String, ImageVector>> = listOf(
        "Inicio" to Inicio, "Rotas" to Rotas, "Linhas" to Linhas, "Menu" to Menu,
        "Posicao" to Posicao, "PosicaoCheia" to PosicaoCheia, "Bussola" to Bussola,
        "Navegacao" to Navegacao, "Pesquisa" to Pesquisa, "Voltar" to Voltar,
        "Fechar" to Fechar, "Trocar" to Trocar, "Casa" to Casa, "Trabalho" to Trabalho,
        "Estrela" to Estrela, "Relogio" to Relogio, "APe" to APe, "Autocarro" to Autocarro,
        "Metro" to Metro, "Comboio" to Comboio, "Barco" to Barco, "Eletrico" to Eletrico,
        "Avisar" to Avisar, "Repetir" to Repetir, "Conta" to Conta, "Partida" to Partida,
        "Mais" to Mais, "EstrelaCheia" to EstrelaCheia, "Lugar" to Lugar, "Aviso" to Aviso,
        "Grave" to Grave, "Info" to Info, "Seguinte" to Seguinte, "Subir" to Subir,
        "Diminuir" to Diminuir, "Aumentar" to Aumentar, "Lapis" to Lapis, "Acidente" to Acidente,
        "Desvio" to Desvio, "Evento" to Evento, "NaoPassou" to NaoPassou, "Cheio" to Cheio,
        "Elevador" to Elevador, "ProblemaParagem" to ProblemaParagem,
    )
}

fun iconeModo(m: Modo): ImageVector = when (m) {
    Modo.AUTOCARRO, Modo.OUTRO -> Icones.Autocarro
    Modo.ELETRICO -> Icones.Eletrico
    Modo.METRO -> Icones.Metro
    Modo.COMBOIO -> Icones.Comboio
    Modo.BARCO -> Icones.Barco
}

fun iconeRelato(t: TipoRelato): ImageVector = when (t) {
    TipoRelato.ACIDENTE -> Icones.Acidente
    TipoRelato.DESVIO -> Icones.Desvio
    TipoRelato.EVENTO -> Icones.Evento
    TipoRelato.NAO_PASSOU -> Icones.NaoPassou
    TipoRelato.VAI_CHEIO -> Icones.Cheio
    TipoRelato.ELEVADOR -> Icones.Elevador
    TipoRelato.PROBLEMA_PARAGEM -> Icones.ProblemaParagem
}

fun iconeLocal(tipo: String): ImageVector = when (tipo) {
    "CASA" -> Icones.Casa
    "TRABALHO" -> Icones.Trabalho
    else -> Icones.Estrela
}
