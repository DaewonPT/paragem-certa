package pt.pixellusitano.paragemcerta.ui.rotas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.estado
import pt.pixellusitano.paragemcerta.core.atraso.rotuloAtraso
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.comum.LocalLimites
import pt.pixellusitano.paragemcerta.ui.comum.SeloLinha
import pt.pixellusitano.paragemcerta.ui.comum.corDaLinha
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.mapa.Percurso
import pt.pixellusitano.paragemcerta.ui.tema.CoresAtraso
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration

/** Sem desenho da fonte, o troço vai em linha reta de uma ponta à outra. */
private fun Etapa.pontos() = desenho.ifEmpty { listOf(de.lat to de.lon, para.lat to para.lon) }

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

@Composable
fun DetalheRotaEcra(it: Itinerario, de: String, para: String, aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val escuro = LocalEscuro.current
    DetalheRotaConteudo(
        it, de, para,
        mapa = {
            val percursos = it.etapas.map { e -> Percurso(e.pontos(), hex(corDaLinha(e.cor, e.modo)), e.aPe) }
            val inicio = percursos.first().pontos.first()
            Mapa(
                paragens = emptyList(), enquadrar = emptyList(), veiculos = emptyList(), coresLinhas = emptyMap(),
                escuro = escuro, centroInicial = LatLng(inicio.first, inicio.second), camaraInicial = null, centrarEm = null,
                aoMoverCamara = { _, _, _, _, _ -> }, aoTocarParagem = {}, aoTocarVeiculo = {},
                modifier = Modifier.fillMaxSize(), percursos = percursos,
            )
        },
        aoVoltar = aoVoltar,
        aoPartilhar = {
            val envio = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, textoPartilha(it, de, para, LISBOA_FUSO))
            contexto.startActivity(Intent.createChooser(envio, null))
        },
        aoAbrirTvde = {
            // Sem a app da loja (telemóveis sem Google), abre a loja no navegador.
            runCatching { contexto.startActivity(intentTvde(contexto.packageManager)) }.onFailure {
                runCatching {
                    contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=TVDE&c=apps")))
                }
            }
        },
    )
}

@Composable
fun DetalheRotaConteudo(
    it: Itinerario,
    de: String,
    para: String,
    mapa: @Composable () -> Unit,
    aoVoltar: () -> Unit,
    aoPartilhar: () -> Unit,
    aoAbrirTvde: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cores.background)) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            mapa()
            Surface(shape = CircleShape, shadowElevation = 4.dp, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                TextButton(onClick = aoVoltar) { Text("←") }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$de → $para", color = cores.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${textoDuracao(it.duracao)} · ${it.partida.horaLisboa()} – ${it.chegada.horaLisboa()}",
                        color = cores.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = cores.outlineVariant)
            }
            itemsIndexed(it.etapas) { i, e ->
                LinhaEtapa(e, paraNome = if (i == it.etapas.lastIndex) para else e.para.nome)
                HorizontalDivider(color = cores.outlineVariant)
            }
            item {
                Hora(it.chegada.horaLisboa(), cores.onSurface) {
                    Text("🏁 " + stringResource(R.string.detalhe_chegada, para), color = cores.onSurface, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = aoPartilhar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detalhe_partilhar)) }
                    OutlinedButton(onClick = aoAbrirTvde, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detalhe_tvde)) }
                }
            }
        }
    }
}

/** Hora à esquerda, numa coluna fixa, e o que acontece a essa hora à direita. */
@Composable
private fun Hora(hora: String, cor: Color, conteudo: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Largura mínima, não fixa: com letra grande a hora cresce em vez de partir em duas linhas.
        Text(hora, Modifier.widthIn(min = 56.dp), color = cor, fontWeight = FontWeight.ExtraBold, softWrap = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { conteudo() }
    }
}

@Composable
private fun LinhaEtapa(e: Etapa, paraNome: String) {
    val cores = MaterialTheme.colorScheme
    val minutos = minutosDe(Duration.between(e.partida, e.chegada)).toInt()
    if (e.aPe) {
        Hora(e.partida.horaLisboa(), cores.onSurfaceVariant) {
            Text(
                "🚶 " + stringResource(R.string.detalhe_a_pe, minutos) + (e.distancia?.let { " (${textoDistancia(it)})" } ?: ""),
                color = cores.onSurface,
            )
            Text(stringResource(R.string.detalhe_ate, paraNome), color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val prevista = e.partida.takeIf { e.aoVivo }
        val limites = LocalLimites.current
        val corHora = CoresAtraso.cor(estado(e.partidaProgramada, prevista, limites), LocalEscuro.current)
        val paragens = e.paragensIntermedias + 1 // contando a paragem onde se sai
        Hora(e.partida.horaLisboa(), corHora) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeloLinha(e.linhaNome ?: simboloModo(e.modo), corDaLinha(e.cor, e.modo))
                Text(
                    listOfNotNull(nomeModo(e.modo), e.linhaNome).joinToString(" ") + (e.destino?.let { " → $it" } ?: ""),
                    color = cores.onSurface, fontWeight = FontWeight.Bold,
                )
            }
            Text(rotuloAtraso(e.partidaProgramada, prevista, limites), color = corHora, style = MaterialTheme.typography.labelSmall)
            Text(
                listOf(
                    textoDuracao(Duration.between(e.partida, e.chegada)),
                    pluralStringResource(R.plurals.detalhe_paragens, paragens, paragens),
                    stringResource(R.string.detalhe_sai_em, paraNome),
                ).joinToString(" · "),
                color = cores.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
