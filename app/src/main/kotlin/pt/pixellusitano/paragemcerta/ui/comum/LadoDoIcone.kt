package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** O lado de um ícone que está ao lado de texto: [sp] em dp, para crescer com a letra grande como o texto cresce. */
@Composable
fun ladoDoIcone(sp: Int): Dp = with(LocalDensity.current) { sp.sp.toDp() }
