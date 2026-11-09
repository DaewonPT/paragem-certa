package pt.pixellusitano.paragemcerta.ui.rotas

import pt.pixellusitano.paragemcerta.ui.comum.SeloModo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.EstadoAtraso
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.ui.comunidade.nomeDoTipo
import pt.pixellusitano.paragemcerta.ui.comum.CartaoAzulejo
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.comum.RotuloAtraso
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.comum.minutos
import pt.pixellusitano.paragemcerta.ui.comum.situacaoAtraso
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
import pt.pixellusitano.paragemcerta.ui.tema.Icones
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

internal fun Instant.horaLisboa(): String = HORA.format(atZone(LISBOA_FUSO))

internal fun minutosDe(d: Duration) = (d.seconds + 30) / 60

/** "25 min", "1 h 05 min". */
internal fun textoDuracao(d: Duration): String {
    val m = minutosDe(d)
    return if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)} min"
}

@Composable
internal fun textoEtiqueta(e: Etiqueta) = stringResource(
    when (e) {
        Etiqueta.MAIS_RAPIDA -> R.string.etiqueta_mais_rapida
        Etiqueta.CHEGADA_MAIS_CEDO -> R.string.etiqueta_chegada_mais_cedo
        Etiqueta.MENOS_A_PE -> R.string.etiqueta_menos_a_pe
        Etiqueta.DIRETA -> R.string.etiqueta_direta
        Etiqueta.CAMINHADA_LONGA -> R.string.etiqueta_caminhada_longa
        Etiqueta.TRANSBORDO_APERTADO -> R.string.etiqueta_transbordo_apertado
    },
)

/** Minutos até uma partida em tempo real que falta até 1 h (nunca negativo); null = mostra-se a hora (do horário, ou mais longe). */
private fun minutosPara(c: Chegada, agora: Instant): Long? {
    val segundos = Duration.between(agora, c.prevista ?: return null).seconds
    return if (segundos <= 3600) maxOf(segundos / 60, 0) else null
}

/** Os tempos de "Parte de X dentro de …": os minutos até 1 h, depois a hora; o booleano diz se é tempo real. */
internal fun textoPartidas(partidas: List<Chegada>, agora: Instant): List<Pair<String, Boolean>> =
    partidas.map { c -> (minutosPara(c, agora)?.toString() ?: (c.prevista ?: c.programada).horaLisboa()) to (c.prevista != null) }

/** "daqui a 3 minutos, 18 minutos, às 11:37": o "daqui a" abre cada grupo de minutos seguidos. */
private fun frasePartidas(partidas: List<Chegada>, agora: Instant): String {
    var emMinutos = false
    return partidas.joinToString(", ") { c ->
        val m = minutosPara(c, agora)
        val frase = when (m) {
            null -> "às ${(c.prevista ?: c.programada).horaLisboa()}"
            0L -> "agora"
            else -> (if (emMinutos) "" else "daqui a ") + minutos(m)
        }
        frase.also { emMinutos = m != null && m > 0 }
    }
}

/**
 * O relato que o cartão da rota mostra: o primeiro confirmado numa linha em veículo do itinerário, pela ordem acidente, desvio,
 * evento, não passou, vai cheio (os relatos de paragem não são avisos de linha). [relatos] já vêm sem os que a pessoa ocultou.
 */
fun relatoDoCartao(it: Itinerario, relatos: List<RelatoRemoto>): RelatoRemoto? {
    val linhas = it.etapas.filter { e -> !e.aPe }.mapNotNull { e -> e.linhaNome?.let(::chaveLinha) }.toSet()
    val daRota = relatos.filter { r -> r.confirmado && r.linha in linhas }
    return TipoRelato.entries.filter { t -> t.deLinha }.firstNotNullOfOrNull { t -> daRota.firstOrNull { r -> r.tipo == t.chave } }
}

/** O cartão inteiro numa frase para o leitor de ecrã, sem os ícones. */
internal fun descricaoRota(
    it: Itinerario, etiquetas: List<String>, comAlerta: Boolean, limites: Limites,
    partidas: List<Chegada>? = null, agora: Instant = Instant.now(), relatoTexto: String? = null,
): String {
    val etapas = it.etapas.joinToString(", ") { e ->
        if (e.aPe) "a pé ${minutosDe(Duration.between(e.partida, e.chegada))} min"
        else listOfNotNull(nomeModo(e.modo).lowercase(), e.linhaNome).joinToString(" ")
    }.replaceFirstChar { c -> c.uppercase() }
    val transbordos = when (it.transbordos) {
        0 -> "sem transbordos"
        1 -> "1 transbordo"
        else -> "${it.transbordos} transbordos"
    }
    val primeira = it.etapas.firstOrNull { e -> !e.aPe }
    return listOfNotNull(
        "Rota de ${textoDuracao(it.duracao)}, das ${it.partida.horaLisboa()} às ${it.chegada.horaLisboa()}",
        etapas,
        "${minutosDe(it.tempoAPe)} min a pé, $transbordos",
        primeira?.let { e ->
            "Sai de ${e.de.nome} às ${e.partida.horaLisboa()}, ${situacaoAtraso(e.partidaProgramada, e.partida.takeIf { e.aoVivo }, limites)}"
        },
        partidas?.takeIf { p -> p.isNotEmpty() }?.let { p -> "Próximas partidas: ${frasePartidas(p, agora)}" },
        etiquetas.takeIf { e -> e.isNotEmpty() }?.let { e -> "Etiquetas: ${e.joinToString(", ")}" },
        // O relato confirmado fica no lugar do aviso numa linha, com o mesmo texto do cartão.
        relatoTexto ?: "Aviso numa linha".takeIf { comAlerta },
    ).joinToString(". ", postfix = ".")
}

/** O aviso a vermelho do cartão, com o triângulo à frente. */
@Composable
private fun AvisoDaLinha(texto: String) {
    val erro = MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icones.Aviso, null, Modifier.size(16.dp), tint = erro)
        Text(texto, color = erro, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Um itinerário na lista: duração, horas, etapas, a pé e transbordos, a 1.ª partida com cor de atraso (ou, havendo [partidas],
 * as próximas partidas dessa linha) e as etiquetas. A lista pode ficar aberta enquanto o relógio anda: as que já passaram não contam.
 * Com um [relato] confirmado (ver [relatoDoCartao]) o cartão diz "Desvio confirmado na 736" (com o triângulo de aviso) no lugar do "aviso numa linha".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoRota(
    it: Itinerario,
    etiquetas: Set<Etiqueta>,
    comAlerta: Boolean,
    aoAbrir: () -> Unit,
    limites: Limites = LocalLimites.current,
    partidas: List<Chegada>? = null,
    agora: Instant = Instant.now(),
    relato: RelatoRemoto? = null,
) {
    val cores = MaterialTheme.colorScheme
    val primeira = it.etapas.firstOrNull { e -> !e.aPe }
    val proximas = partidas.orEmpty().filter { p -> aindaPorChegar(p, agora) }
    val nomes = etiquetas.sortedBy { e -> e.ordinal }.map { e -> textoEtiqueta(e) }
    val tipoRelato = relato?.let { r -> TipoRelato.entries.firstOrNull { t -> t.chave == r.tipo } }
    val textoRelato = if (relato != null && tipoRelato != null) {
        stringResource(R.string.rotas_relato_confirmado, stringResource(nomeDoTipo(tipoRelato)), relato.linha)
    } else null
    val descricao = descricaoRota(it, nomes, comAlerta, limites, proximas, agora, textoRelato)
    CartaoAzulejo(Modifier.fillMaxWidth(), aoTocar = aoAbrir) {
        Column(
            Modifier.padding(16.dp).clearAndSetSemantics { contentDescription = descricao },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Com letra grande a hora passa para a linha de baixo em vez de espremer a duração.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    textoDuracao(it.duracao), color = cores.onSurface, softWrap = false,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                )
                Text("${it.partida.horaLisboa()} – ${it.chegada.horaLisboa()}", color = cores.onSurfaceVariant)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                it.etapas.forEachIndexed { i, e ->
                    if (i > 0) Icon(Icones.Seguinte, null, Modifier.size(16.dp), tint = cores.onSurfaceVariant)
                    if (e.aPe) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icones.APe, null, Modifier.size(16.dp), tint = cores.onSurfaceVariant)
                        Text(minutosDe(Duration.between(e.partida, e.chegada)).toString(), color = cores.onSurfaceVariant)
                    }
                    else e.linhaNome?.let { SeloLinha(it, corDaLinha(e.cor, e.modo)) } ?: SeloModo(e.modo, corDaLinha(e.cor, e.modo))
                }
            }
            val transbordos =
                if (it.transbordos == 0) stringResource(R.string.rotas_sem_transbordos)
                else pluralStringResource(R.plurals.rotas_transbordos, it.transbordos, it.transbordos)
            Text(
                stringResource(R.string.rotas_a_pe, minutosDe(it.tempoAPe).toInt()) + " · " + transbordos,
                color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            if (primeira != null && proximas.isNotEmpty()) {
                // "dentro de 3, 18" lê-se bem; "dentro de 11:37" não: se a 1.ª partida é uma hora, "às 11:37".
                val parteDe = stringResource(
                    if (minutosPara(proximas.first(), agora) != null) R.string.rotas_parte_de else R.string.rotas_parte_de_as,
                    primeira.de.nome,
                ) + " "
                val aHoras = CoresAtraso.cor(EstadoAtraso.A_HORAS, LocalEscuro.current)
                val tempos = buildAnnotatedString {
                    append(parteDe)
                    textoPartidas(proximas, agora).forEachIndexed { i, (tempo, aoVivo) ->
                        if (i > 0) append(", ")
                        withStyle(SpanStyle(color = if (aoVivo) aHoras else cores.onSurfaceVariant, fontWeight = FontWeight.ExtraBold)) { append(tempo) }
                    }
                }
                Text(tempos, color = cores.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else primeira?.let { e ->
                val prevista = e.partida.takeIf { e.aoVivo }
                val cor = CoresAtraso.cor(estado(e.partidaProgramada, prevista, limites), LocalEscuro.current)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.rotas_sai_de, e.de.nome), Modifier.weight(1f),
                        color = cores.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(e.partida.horaLisboa(), color = cor, fontWeight = FontWeight.ExtraBold)
                        RotuloAtraso(e.partidaProgramada, prevista, limites, cor)
                    }
                }
            }
            if (etiquetas.isNotEmpty() || comAlerta || textoRelato != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    etiquetas.sortedBy { e -> e.ordinal }.forEach { e ->
                        Text(
                            textoEtiqueta(e),
                            color = if (e == Etiqueta.TRANSBORDO_APERTADO) cores.error else cores.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (textoRelato != null) AvisoDaLinha(textoRelato)
                    else if (comAlerta) AvisoDaLinha(stringResource(R.string.rotas_aviso_linha))
                }
            }
        }
    }
}
