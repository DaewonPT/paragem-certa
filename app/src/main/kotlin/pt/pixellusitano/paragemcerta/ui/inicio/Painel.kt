package pt.pixellusitano.paragemcerta.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.regras.favoritaPorOmissao
import pt.pixellusitano.paragemcerta.core.regras.maisProximas
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.comum.FaixaEstado
import pt.pixellusitano.paragemcerta.ui.comum.LinhaChegada
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import java.time.Instant

/** Uma paragem no painel, com as suas chegadas (estado null enquanto carrega). */
data class BlocoParagem(val ref: RefParagem, val nome: String, val estado: EstadoChegadas?, val maximo: Int)

/** Conteúdo puro do painel (testado com capturas). */
@Composable
fun PainelConteudo(
    favoritas: List<BlocoParagem>,
    pertoDeTi: List<BlocoParagem>?,
    linhas: Map<String, Linha>,
    agora: Instant,
    aoAbrirParagem: (RefParagem) -> Unit,
    aoPesquisar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        FrisoAzulejo(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        if (favoritas.isEmpty()) {
            Text(stringResource(R.string.painel_sem_favoritas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        favoritas.forEach { Bloco(it, "★ ", linhas, agora, aoAbrirParagem) }

        Text(stringResource(R.string.painel_perto), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        if (pertoDeTi == null) {
            Text(
                stringResource(R.string.painel_procura),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoPesquisar)
                    .padding(vertical = 12.dp),
            )
        } else {
            pertoDeTi.forEach { Bloco(it, "", linhas, agora, aoAbrirParagem) }
        }
    }
}

@Composable
private fun Bloco(b: BlocoParagem, prefixo: String, linhas: Map<String, Linha>, agora: Instant, aoAbrir: (RefParagem) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { aoAbrir(b.ref) }.padding(vertical = 6.dp)) {
        Text(prefixo + b.nome, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        val e = b.estado
        if (e == null) {
            Text(stringResource(R.string.a_carregar), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            FaixaEstado(e, agora, Modifier.padding(vertical = 4.dp))
            // Filtra outra vez com o relógio do ecrã: sem rede, a próxima resposta pode demorar minutos.
            val visiveis = e.chegadas.filter { aindaPorChegar(it, agora) }
            if (visiveis.isEmpty() && !e.semLigacao && !e.tempoRealIndisponivel) {
                Text(stringResource(R.string.sem_chegadas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            }
            visiveis.take(b.maximo).forEach { LinhaChegada(it, linhas[it.linhaId], agora) }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Painel ligado aos dados: favoritas (a por omissão com 3 chegadas, as outras com 2) e até 5 paragens a 800 m. */
@Composable
fun Painel(
    repositorio: Repositorio,
    posicao: Pair<Double, Double>?,
    aoAbrirParagem: (RefParagem) -> Unit,
    aoPesquisar: () -> Unit,
) {
    val favoritas by remember { repositorio.favoritas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val linhas by produceState(emptyMap<String, Linha>()) {
        value = repositorio.linhasCm()
    }
    val perto by produceState<List<pt.pixellusitano.paragemcerta.core.modelo.Paragem>?>(null, posicao) {
        value = posicao?.let { (lat, lon) ->
            maisProximas(repositorio.paragensNaZona(lat - 0.008, lon - 0.01, lat + 0.008, lon + 0.01), lat, lon)
        }
    }
    val agora by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }
    val primeira = remember(favoritas, posicao) { favoritaPorOmissao(favoritas, posicao) }
    val ordenadas = listOfNotNull(primeira) + favoritas.filter { it.ref != primeira?.ref }

    PainelConteudo(
        favoritas = ordenadas.map { f ->
            BlocoParagem(f.ref, f.nome, estadoDe(repositorio, f.ref), maximo = if (f.ref == primeira?.ref) 3 else 2)
        },
        pertoDeTi = perto?.map { p -> BlocoParagem(p.ref, p.nome, estadoDe(repositorio, p.ref), maximo = 1) },
        linhas = linhas,
        agora = agora,
        aoAbrirParagem = aoAbrirParagem,
        aoPesquisar = aoPesquisar,
        modifier = Modifier.verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun estadoDe(repositorio: Repositorio, ref: RefParagem): EstadoChegadas? {
    val fluxo = remember(ref) { repositorio.chegadas(ref) }
    // Só consulta enquanto o ecrã está visível (a recolha pára abaixo de STARTED).
    return fluxo.collectAsStateWithLifecycle(initialValue = null).value
}
