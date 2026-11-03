package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.fontes.servidor.RelatoRemoto
import pt.pixellusitano.paragemcerta.core.modelo.Gravidade
import pt.pixellusitano.paragemcerta.ui.paragem.estiloDaGravidade
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro
import java.time.Duration
import java.time.Instant

/**
 * Um relato da comunidade no ecrã da paragem. Por confirmar fica discreto (o estilo "informação" dos alertas); confirmado,
 * em destaque (o de "aviso"). [nomeLinha] é a linha de um relato de linha, null nos da paragem. Os botões dizem a que relato
 * se referem, para o leitor de ecrã os distinguir quando há vários cartões. Nunca se mostra quem relatou nem quem votou.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoRelato(
    r: RelatoRemoto,
    nomeLinha: String?,
    agora: Instant,
    aoVotar: (aindaEsta: Boolean) -> Unit,
    aoDenunciar: () -> Unit,
    aoOcultar: () -> Unit,
) {
    // Um tipo que esta versão da app não conhece (o servidor é mais novo) não se mostra: não há como lhe dar nome.
    val tipo = TipoRelato.entries.firstOrNull { it.chave == r.tipo } ?: return
    val nome = stringResource(nomeDoTipo(tipo))
    val titulo = if (nomeLinha == null) nome else stringResource(R.string.relato_na_linha, nome, nomeLinha)
    val estilo = estiloDaGravidade(if (r.confirmado) Gravidade.AVISO else Gravidade.INFO, LocalEscuro.current)
    val quando = haQuanto(Instant.ofEpochMilli(r.criadoEm), agora)
    // Quem relatou é sempre um dos passageiros: com 0 confirmações (mudou de ideias) continua a ser 1 quem relatou.
    val passageiros = maxOf(r.confirmacoes, 1)
    val detalhe = pluralStringResource(if (r.confirmado) R.plurals.relato_confirmado else R.plurals.relato_por_confirmar, passageiros, passageiros, quando)
    var menu by remember { mutableStateOf(false) }
    val botao = ButtonDefaults.textButtonColors(contentColor = estilo.texto)
    val opcoes = stringResource(R.string.relato_mais_opcoes, titulo)
    val ainda = stringResource(R.string.relato_ainda_esta_de, titulo)
    val jaNao = stringResource(R.string.relato_ja_nao_esta_de, titulo)
    val denunciar = stringResource(R.string.relato_denunciar_de, titulo)

    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().background(estilo.fundo, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Row {
            // Título e detalhe lêem-se juntos; com letra grande quebram em vez de empurrar o menu para fora.
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("${estilo.icone} $titulo", color = estilo.texto, style = MaterialTheme.typography.labelLarge)
                Text(detalhe, color = estilo.texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            Box {
                TextButton(onClick = { menu = true }, colors = botao, modifier = Modifier.semantics { contentDescription = opcoes }) { Text("⋯") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.relato_ocultar)) }, onClick = { menu = false; aoOcultar() })
                }
            }
        }
        // Três botões não cabem numa linha com letra a 200%: quebram para a linha de baixo.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { aoVotar(true) }, colors = botao, modifier = Modifier.semantics { contentDescription = ainda }) {
                Text(stringResource(R.string.relato_ainda_esta))
            }
            TextButton(onClick = { aoVotar(false) }, colors = botao, modifier = Modifier.semantics { contentDescription = jaNao }) {
                Text(stringResource(R.string.relato_ja_nao_esta))
            }
            TextButton(onClick = aoDenunciar, colors = botao, modifier = Modifier.semantics { contentDescription = denunciar }) {
                Text(stringResource(R.string.relato_denunciar))
            }
        }
    }
}

/** "agora mesmo", "há 4 min", "há 3 h", "há 2 dias": os relatos duram de 30 min a 7 dias. */
@Composable
private fun haQuanto(criado: Instant, agora: Instant): String {
    val minutos = Duration.between(criado, agora).toMinutes() // negativo se o relógio do telemóvel estiver atrasado
    return when {
        minutos < 1 -> stringResource(R.string.relato_agora)
        minutos < 60 -> stringResource(R.string.relato_ha_min, minutos.toInt())
        minutos < 24 * 60 -> stringResource(R.string.relato_ha_horas, (minutos / 60).toInt())
        else -> (minutos / (24 * 60)).toInt().let { pluralStringResource(R.plurals.relato_ha_dias, it, it) }
    }
}
