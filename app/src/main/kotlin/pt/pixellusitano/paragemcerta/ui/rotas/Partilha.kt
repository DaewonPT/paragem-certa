package pt.pixellusitano.paragemcerta.ui.rotas

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val PORTUGAL = Locale.forLanguageTag("pt-PT")

internal fun nomeModo(m: Modo) = when (m) {
    Modo.AUTOCARRO -> "Autocarro"
    Modo.ELETRICO -> "Elétrico"
    Modo.METRO -> "Metro"
    Modo.COMBOIO -> "Comboio"
    Modo.BARCO -> "Barco"
    Modo.OUTRO -> "Transporte"
}

/**
 * A linha para dizer depois de "o": o número chega ("736", "15E"); um nome próprio precisa do modo à frente, senão não se
 * percebe o que é ("comboio da Linha de Cascais", "metro Azul"); sem nome, só o modo ("comboio").
 */
internal fun nomeDaLinha(modo: Modo, linhaNome: String?): String {
    val nome = linhaNome?.trim().orEmpty()
    val porModo = nomeModo(modo).lowercase()
    return when {
        nome.isEmpty() -> porModo
        nome.any(Char::isDigit) && nome.none(Char::isWhitespace) -> nome
        nome.startsWith("Linha", ignoreCase = true) -> "$porModo da $nome"
        else -> "$porModo $nome"
    }
}

/** "120 m" (às dezenas) ou "1,3 km". */
internal fun textoDistancia(metros: Double): String =
    if (metros < 1000) "${(metros / 10).roundToInt() * 10} m" else String.format(PORTUGAL, "%.1f km", metros / 1000)

/** Texto simples para mandar por mensagem: origem e destino, horas e uma linha por etapa. */
fun textoPartilha(it: Itinerario, de: String, para: String, zona: ZoneId): String {
    fun h(i: Instant) = HORA.format(i.atZone(zona))
    val linhas = mutableListOf(
        "Paragem Certa — $de → $para",
        "Sai às ${h(it.partida)} · chega às ${h(it.chegada)} (${textoDuracao(it.duracao)})",
    )
    it.etapas.forEachIndexed { i, e ->
        val ultima = i == it.etapas.lastIndex
        linhas += if (e.aPe) {
            "• A pé ${minutosDe(Duration.between(e.partida, e.chegada))} min até ${if (ultima) "ao destino" else e.para.nome}"
        } else {
            val linha = listOfNotNull(nomeModo(e.modo), e.linhaNome).joinToString(" ")
            "• $linha até ${if (ultima) para else e.para.nome} (${h(e.partida)} → ${h(e.chegada)})"
        }
    }
    return linhas.joinToString("\n")
}

// Uber, Bolt e FREENOW, por esta ordem.
private val APPS_TVDE = listOf("com.ubercab", "ee.mtakso.client", "taxi.android.client")

/** Abre a primeira app de TVDE instalada; sem nenhuma, a loja com a pesquisa "TVDE". */
fun intentTvde(pm: PackageManager): Intent =
    APPS_TVDE.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=TVDE&c=apps"))
