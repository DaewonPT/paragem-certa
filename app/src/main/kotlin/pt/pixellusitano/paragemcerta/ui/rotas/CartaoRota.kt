package pt.pixellusitano.paragemcerta.ui.rotas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.atraso.rotuloAtraso
import pt.pixellusitano.paragemcerta.core.modelo.Etiqueta
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
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

internal fun simboloModo(m: Modo) = when (m) {
    Modo.AUTOCARRO -> "🚌"
    Modo.ELETRICO -> "🚋"
    Modo.METRO -> "Ⓜ"
    Modo.COMBOIO -> "🚆"
    Modo.BARCO -> "⛴"
    Modo.OUTRO -> "🚍"
}

@Composable
internal fun textoEtiqueta(e: Etiqueta) = stringResource(
    when (e) {
        Etiqueta.MAIS_RAPIDA -> R.string.etiqueta_mais_rapida
        Etiqueta.MENOS_A_PE -> R.string.etiqueta_menos_a_pe
        Etiqueta.DIRETA -> R.string.etiqueta_direta
        Etiqueta.TRANSBORDO_APERTADO -> R.string.etiqueta_transbordo_apertado
    },
)

/** Um itinerário na lista: duração, horas, etapas, a pé e transbordos, a 1.ª partida com cor de atraso e as etiquetas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoRota(
    it: Itinerario,
    etiquetas: Set<Etiqueta>,
    comAlerta: Boolean,
    aoAbrir: () -> Unit,
    limites: Limites = LocalLimites.current,
) {
    val cores = MaterialTheme.colorScheme
    val primeira = it.etapas.firstOrNull { e -> !e.aPe }
    OutlinedCard(onClick = aoAbrir, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    textoDuracao(it.duracao), Modifier.weight(1f),
                    color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                Text("${it.partida.horaLisboa()} – ${it.chegada.horaLisboa()}", color = cores.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                it.etapas.forEachIndexed { i, e ->
                    if (i > 0) Text("›", color = cores.onSurfaceVariant)
                    if (e.aPe) Text("🚶${minutosDe(Duration.between(e.partida, e.chegada))}", color = cores.onSurfaceVariant)
                    else SeloLinha(e.linhaNome ?: simboloModo(e.modo), corDaLinha(e.cor, e.modo))
                }
            }
            val transbordos =
                if (it.transbordos == 0) stringResource(R.string.rotas_sem_transbordos)
                else pluralStringResource(R.plurals.rotas_transbordos, it.transbordos, it.transbordos)
            Text(
                stringResource(R.string.rotas_a_pe, minutosDe(it.tempoAPe).toInt()) + " · " + transbordos,
                color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            primeira?.let { e ->
                val prevista = e.partida.takeIf { e.aoVivo }
                val cor = CoresAtraso.cor(estado(e.partidaProgramada, prevista, limites), LocalEscuro.current)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.rotas_sai_de, e.de.nome), Modifier.weight(1f),
                        color = cores.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(e.partida.horaLisboa(), color = cor, fontWeight = FontWeight.ExtraBold)
                        Text(rotuloAtraso(e.partidaProgramada, prevista, limites), color = cor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (etiquetas.isNotEmpty() || comAlerta) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    etiquetas.sortedBy { e -> e.ordinal }.forEach { e ->
                        Text(
                            textoEtiqueta(e),
                            color = if (e == Etiqueta.TRANSBORDO_APERTADO) cores.error else cores.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (comAlerta) Text("⚠ " + stringResource(R.string.rotas_aviso_linha), color = cores.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
