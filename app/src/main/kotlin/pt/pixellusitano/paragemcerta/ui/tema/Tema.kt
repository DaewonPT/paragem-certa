package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

enum class ModoTema { SISTEMA, CLARO, ESCURO }

/** Verdadeiro quando o ecrã está no Azulejo noite (as cores de atraso dependem disto). */
val LocalEscuro = staticCompositionLocalOf { false }

private val Claro = lightColorScheme(
    primary = Azulejo.Cobalto,
    onPrimary = Azulejo.Branco,
    secondary = Azulejo.Eletrico,
    onSecondary = Azulejo.Tinta,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE6F7),
    onSecondaryContainer = Azulejo.Tinta,
    background = Azulejo.Marfim,
    onBackground = Azulejo.Tinta,
    surface = Azulejo.Branco,
    onSurface = Azulejo.Tinta,
    onSurfaceVariant = Azulejo.Cinza,
    outlineVariant = Azulejo.Contorno,
    surfaceVariant = Azulejo.Branco,
    surfaceContainerLowest = Azulejo.Branco,
    surfaceContainerLow = Azulejo.Branco,
    surfaceContainer = Azulejo.Branco,
    surfaceContainerHigh = Azulejo.Branco,
    surfaceContainerHighest = Azulejo.Branco,
    surfaceBright = Azulejo.Branco,
    surfaceDim = Azulejo.Branco,
)

private val Escuro = darkColorScheme(
    primary = Azulejo.CeuNoite,
    onPrimary = Azulejo.Noite,
    secondary = Azulejo.Eletrico,
    onSecondary = Azulejo.Tinta,
    secondaryContainer = Azulejo.LinhaEscura,
    onSecondaryContainer = Azulejo.TextoNoite,
    background = Azulejo.Noite,
    onBackground = Azulejo.TextoNoite,
    surface = Azulejo.NoiteSuperficie,
    onSurface = Azulejo.TextoNoite,
    onSurfaceVariant = Azulejo.CinzaNoite,
    outlineVariant = Azulejo.ContornoNoite,
    surfaceVariant = Azulejo.NoiteSuperficie,
    surfaceContainerLowest = Azulejo.NoiteSuperficie,
    surfaceContainerLow = Azulejo.NoiteSuperficie,
    surfaceContainer = Azulejo.NoiteSuperficie,
    surfaceContainerHigh = Azulejo.NoiteSuperficie,
    surfaceContainerHighest = Azulejo.NoiteSuperficie,
    surfaceBright = Azulejo.NoiteSuperficie,
    surfaceDim = Azulejo.NoiteSuperficie,
)

val FormasParagemCerta = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TemaParagemCerta(modo: ModoTema = ModoTema.SISTEMA, content: @Composable () -> Unit) {
    val escuro = when (modo) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.ESCURO -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalEscuro provides escuro) {
        MaterialTheme(colorScheme = if (escuro) Escuro else Claro, typography = LetraParagemCerta, shapes = FormasParagemCerta, content = content)
    }
}

/** Faixa às riscas de 6 dp, como um friso de azulejos. */
@Composable
fun FrisoAzulejo(modifier: Modifier = Modifier) {
    val escuro = LocalEscuro.current
    val risca = if (escuro) Azulejo.FrisoNoite else Azulejo.Cobalto
    Canvas(modifier.height(6.dp)) {
        val passo = 6.dp.toPx()
        clipRect {
            var x = -size.height
            while (x < size.width + size.height) {
                drawPath(
                    Path().apply {
                        moveTo(x, size.height); lineTo(x + size.height, 0f)
                        lineTo(x + size.height + passo, 0f); lineTo(x + passo, size.height); close()
                    },
                    risca,
                )
                x += passo * 2
            }
        }
    }
}
