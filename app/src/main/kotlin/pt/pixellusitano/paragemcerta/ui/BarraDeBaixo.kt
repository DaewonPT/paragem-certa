package pt.pixellusitano.paragemcerta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.ui.comum.gelatina
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.Icones

enum class Separador { INICIO, ROTAS, LINHAS, MENU }

/** A barra dos quatro separadores; [atual] é nulo fora deles. */
@Composable
fun BarraDeBaixo(atual: Separador?, aoEscolher: (Separador) -> Unit, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    val itemCores = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = Azulejo.Tinta,
        unselectedIconColor = cores.onSurfaceVariant,
        selectedTextColor = cores.onSurface,
        unselectedTextColor = cores.onSurfaceVariant,
    )
    NavigationBar(modifier, containerColor = cores.surface) {
        listOf(
            Triple(Separador.INICIO, Icones.Inicio, R.string.nav_inicio),
            Triple(Separador.ROTAS, Icones.Rotas, R.string.nav_rotas),
            Triple(Separador.LINHAS, Icones.Linhas, R.string.nav_linhas),
            Triple(Separador.MENU, Icones.Menu, R.string.nav_menu),
        ).forEach { (separador, icone, rotulo) ->
            val escolhido = atual == separador
            NavigationBarItem(
                selected = escolhido, onClick = { aoEscolher(separador) }, colors = itemCores,
                icon = { Pilula(icone, escolhido) },
                label = { Text(stringResource(rotulo), fontWeight = if (escolhido) FontWeight.Black else FontWeight.Bold) },
            )
        }
    }
}

// Pílula amarela com contorno escuro por trás do ícone do separador ativo.
@Composable
private fun Pilula(icone: ImageVector, escolhido: Boolean) {
    val fundo = if (escolhido) Modifier.clip(CircleShape).background(Azulejo.Eletrico).border(1.dp, Azulejo.Tinta, CircleShape) else Modifier
    Box(Modifier.size(56.dp, 32.dp).gelatina(escolhido).then(fundo), contentAlignment = Alignment.Center) {
        Icon(icone, contentDescription = null)
    }
}
