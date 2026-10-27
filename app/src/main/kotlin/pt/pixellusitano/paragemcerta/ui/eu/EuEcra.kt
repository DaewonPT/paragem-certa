package pt.pixellusitano.paragemcerta.ui.eu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.atraso.Limites
import pt.pixellusitano.paragemcerta.core.regras.Favorita
import pt.pixellusitano.paragemcerta.dados.Preferencias
import pt.pixellusitano.paragemcerta.dados.Repositorio
import pt.pixellusitano.paragemcerta.ui.sobre.EMAIL_CONTACTO
import pt.pixellusitano.paragemcerta.ui.tema.FrisoAzulejo
import pt.pixellusitano.paragemcerta.ui.tema.ModoTema

fun intentOpiniao(versao: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_CONTACTO"))
        .putExtra(Intent.EXTRA_SUBJECT, "Paragem Certa — opinião")
        .putExtra(Intent.EXTRA_TEXT, "Versão da app: $versao\n\n")

@Composable
fun EuEcra(repositorio: Repositorio, preferencias: Preferencias, aoAbrirFavorita: (Favorita) -> Unit, aoAbrirSobre: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val favoritas by remember { repositorio.favoritas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val modo by preferencias.modo.collectAsStateWithLifecycle(initialValue = ModoTema.SISTEMA)
    val limites by preferencias.limites.collectAsStateWithLifecycle(initialValue = Limites())
    EuConteudo(
        favoritas = favoritas,
        modo = modo,
        limites = limites,
        aoAbrirFavorita = aoAbrirFavorita,
        aoSubir = { f ->
            val i = favoritas.indexOf(f)
            if (i > 0) escopo.launch {
                repositorio.reordenarFavoritas(favoritas.toMutableList().apply { add(i - 1, removeAt(i)) })
            }
        },
        aoRemover = { f -> escopo.launch { repositorio.removerFavorita(f.ref) } },
        aoMudarModo = { escopo.launch { preferencias.definirModo(it) } },
        aoMudarLimites = { l -> escopo.launch { runCatching { preferencias.definirLimites(l) } } },
        aoAbrirSobre = aoAbrirSobre,
        aoEnviarOpiniao = { runCatching { contexto.startActivity(intentOpiniao(BuildConfig.VERSION_NAME)) } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun EuConteudo(
    favoritas: List<Favorita>,
    modo: ModoTema,
    limites: Limites,
    aoAbrirFavorita: (Favorita) -> Unit,
    aoSubir: (Favorita) -> Unit,
    aoRemover: (Favorita) -> Unit,
    aoMudarModo: (ModoTema) -> Unit,
    aoMudarLimites: (Limites) -> Unit,
    aoAbrirSobre: () -> Unit,
    aoEnviarOpiniao: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        Text(stringResource(R.string.eu_favoritas), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (favoritas.isEmpty()) {
            Text(stringResource(R.string.eu_sem_favoritas), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        favoritas.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "★ " + f.nome,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { aoAbrirFavorita(f) }.padding(vertical = 12.dp),
                )
                if (i > 0) TextButton(onClick = { aoSubir(f) }) { Text("↑ " + stringResource(R.string.eu_subir)) }
                TextButton(onClick = { aoRemover(f) }) { Text(stringResource(R.string.eu_remover)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Definicoes(modo, limites, aoMudarModo, aoMudarLimites, Modifier.padding(top = 24.dp))

        Column(Modifier.padding(top = 24.dp)) {
            Ligacao(stringResource(R.string.eu_sobre), aoAbrirSobre)
            Ligacao(stringResource(R.string.eu_opiniao), aoEnviarOpiniao)
        }
    }
}

@Composable
private fun Ligacao(texto: String, aoTocar: () -> Unit) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = aoTocar).padding(vertical = 12.dp),
    )
}

@Composable
fun Definicoes(
    modo: ModoTema,
    limites: Limites,
    aoMudarModo: (ModoTema) -> Unit,
    aoMudarLimites: (Limites) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Text(stringResource(R.string.eu_definicoes), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        FrisoAzulejo(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(stringResource(R.string.eu_modo), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        listOf(
            ModoTema.SISTEMA to R.string.modo_sistema,
            ModoTema.CLARO to R.string.modo_claro,
            ModoTema.ESCURO to R.string.modo_escuro,
        ).forEach { (m, texto) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = modo == m, role = Role.RadioButton) { aoMudarModo(m) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = modo == m, onClick = null)
                Text(stringResource(texto), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.eu_limites), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 16.dp))
        Ajuste(
            stringResource(R.string.eu_limite_atrasado, limites.atrasado),
            menos = { if (limites.atrasado > 1) aoMudarLimites(limites.copy(atrasado = limites.atrasado - 1)) },
            mais = { if (limites.atrasado + 1 < limites.muitoAtrasado) aoMudarLimites(limites.copy(atrasado = limites.atrasado + 1)) },
        )
        Ajuste(
            stringResource(R.string.eu_limite_muito, limites.muitoAtrasado),
            menos = { if (limites.muitoAtrasado - 1 > limites.atrasado) aoMudarLimites(limites.copy(muitoAtrasado = limites.muitoAtrasado - 1)) },
            mais = { if (limites.muitoAtrasado < 30) aoMudarLimites(limites.copy(muitoAtrasado = limites.muitoAtrasado + 1)) },
        )
    }
}

@Composable
private fun Ajuste(texto: String, menos: () -> Unit, mais: () -> Unit) {
    val rotuloMenos = stringResource(R.string.menos)
    val rotuloMais = stringResource(R.string.mais)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(texto, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        TextButton(onClick = menos, modifier = Modifier.semantics { contentDescription = "$rotuloMenos: $texto" }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = mais, modifier = Modifier.semantics { contentDescription = "$rotuloMais: $texto" }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
