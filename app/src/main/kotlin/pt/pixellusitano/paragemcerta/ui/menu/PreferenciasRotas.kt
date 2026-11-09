package pt.pixellusitano.paragemcerta.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.modelo.Ordem
import pt.pixellusitano.paragemcerta.core.modelo.Ritmo
import pt.pixellusitano.paragemcerta.ui.comum.TituloSecao
import pt.pixellusitano.paragemcerta.ui.comum.bordaDoFiltro
import pt.pixellusitano.paragemcerta.ui.rotas.MODOS_FILTRO
import pt.pixellusitano.paragemcerta.ui.tema.iconeModo

private val MINUTOS_A_PE = listOf(5, 10, 15, 20)

/** As mesmas definições dos filtros rápidos do ecrã Rotas; escreve pelo mesmo [aoMudar] (o `definirFiltros`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferenciasRotas(filtros: Filtros, aoMudar: (Filtros) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        TituloSecao(stringResource(R.string.pref_rotas))
        CartaoDeSecao {
            Subtitulo(stringResource(R.string.pref_ordenar), Modifier.padding(top = 12.dp))
            Escolha(
                listOf(
                    Ordem.MAIS_RAPIDA to stringResource(R.string.pref_ordem_mais_rapida),
                    Ordem.CHEGADA_MAIS_CEDO to stringResource(R.string.pref_ordem_chegada_mais_cedo),
                    Ordem.MENOS_A_PE to stringResource(R.string.pref_ordem_menos_a_pe),
                    Ordem.MENOS_TRANSBORDOS to stringResource(R.string.pref_ordem_menos_transbordos),
                ),
                filtros.ordem, { aoMudar(filtros.copy(ordem = it)) },
            )
            Subtitulo(stringResource(R.string.pref_meios), Modifier.padding(top = 16.dp, bottom = 8.dp))
            // Os meios passam à linha seguinte em vez de ficarem escondidos de lado (ecrãs estreitos ou letra grande).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MODOS_FILTRO.forEach { (modo, texto) ->
                    val escolhido = modo in filtros.modos
                    FilterChip(
                        escolhido, { aoMudar(filtros.copy(modos = if (escolhido) filtros.modos - modo else filtros.modos + modo)) },
                        leadingIcon = { Icon(iconeModo(modo), null, Modifier.size(FilterChipDefaults.IconSize)) }, label = { Text(stringResource(texto)) },
                        border = bordaDoFiltro(escolhido),
                    )
                }
            }
            Subtitulo(stringResource(R.string.pref_tempo_a_pe), Modifier.padding(top = 16.dp))
            Escolha(MINUTOS_A_PE.map { it to stringResource(R.string.pref_minutos, it) }, filtros.maxAPeMin, { aoMudar(filtros.copy(maxAPeMin = it)) })
            Subtitulo(stringResource(R.string.pref_ritmo), Modifier.padding(top = 16.dp))
            Escolha(
                listOf(
                    Ritmo.LENTO to stringResource(R.string.pref_ritmo_lento),
                    Ritmo.NORMAL to stringResource(R.string.pref_ritmo_normal),
                    Ritmo.RAPIDO to stringResource(R.string.pref_ritmo_rapido),
                ),
                filtros.ritmo, { aoMudar(filtros.copy(ritmo = it)) },
                Modifier.padding(bottom = 8.dp),
            )
            Interruptor(stringResource(R.string.pref_acessivel), filtros.acessivel, { aoMudar(filtros.copy(acessivel = it)) })
        }
    }
}
