package pt.pixellusitano.paragemcerta.ui.inicio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.geometry.LatLng
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.Veiculo
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.mapa.LISBOA
import pt.pixellusitano.paragemcerta.ui.mapa.Mapa
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration
import java.time.Instant

@Composable
fun InicioEcra(
    repositorio: Repositorio,
    aoAbrirParagem: (Paragem) -> Unit,
    painel: @Composable (posicao: Pair<Double, Double>?) -> Unit = {},
) {
    val contexto = LocalContext.current
    val vm = viewModel { InicioViewModel(repositorio::paragensNaZona, repositorio::veiculos) }
    val paragens by vm.paragens.collectAsStateWithLifecycle()
    val veiculos by vm.veiculos.collectAsStateWithLifecycle()
    val escolhido by vm.veiculoEscolhido.collectAsStateWithLifecycle()
    val cores by produceState(emptyMap<String, String>()) { value = repositorio.coresDasLinhasCm() }

    var comLocalizacao by remember { mutableStateOf(temLocalizacao(contexto)) }
    var explicar by remember { mutableStateOf(!comLocalizacao && !localizacaoJaPerguntada(contexto)) }
    val posicao = remember(comLocalizacao) { ultimaPosicao(contexto) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        comLocalizacao = r.values.any { it }
    }

    LaunchedEffect(Unit) {
        // Primeira utilização: as paragens da Carris Metropolitana ainda não estão na base local.
        if (repositorio.redeEmFalta()) repositorio.atualizarRede()
    }

    Box(Modifier.fillMaxSize()) {
        Mapa(
            paragens = paragens,
            veiculos = veiculos,
            coresLinhas = cores,
            escuro = LocalEscuro.current,
            centroInicial = posicao?.let { LatLng(it.first, it.second) } ?: LISBOA,
            aoMoverCamara = vm::aoMoverCamara,
            aoTocarParagem = aoAbrirParagem,
            aoTocarVeiculo = vm::escolherVeiculo,
            modifier = Modifier.fillMaxSize(),
        )
        escolhido?.let { CartaoVeiculo(it, cores[it.linhaId], aoFechar = { vm.escolherVeiculo(null) }) }
        painel(posicao)
        if (explicar) {
            PedidoLocalizacao(
                aoAceitar = {
                    marcarLocalizacaoPerguntada(contexto)
                    explicar = false
                    pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                aoRecusar = {
                    marcarLocalizacaoPerguntada(contexto)
                    explicar = false
                },
            )
        }
    }
}

@Composable
private fun CartaoVeiculo(v: Veiculo, cor: String?, aoFechar: () -> Unit) {
    val segundos = Duration.between(v.ultimoSinal, Instant.now()).seconds.coerceAtLeast(0)
    Surface(
        Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), tonalElevation = 3.dp, shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.veiculo_linha, v.linhaId), style = MaterialTheme.typography.titleMedium)
                v.destino?.let { Text(stringResource(R.string.veiculo_destino, it)) }
                Text(stringResource(R.string.veiculo_sinal, segundos), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = aoFechar) { Text(stringResource(R.string.fechar)) }
        }
    }
}

@Composable
private fun PedidoLocalizacao(aoAceitar: () -> Unit, aoRecusar: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.loc_titulo), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            FrisoAzulejo(Modifier.fillMaxWidth())
            Text(stringResource(R.string.loc_texto), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = aoAceitar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.loc_aceitar)) }
            TextButton(onClick = aoRecusar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.loc_recusar)) }
        }
    }
}
