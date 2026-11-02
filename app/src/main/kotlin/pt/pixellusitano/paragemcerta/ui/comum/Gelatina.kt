package pt.pixellusitano.paragemcerta.ui.comum

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

private const val DURACAO_GELATINA_MS = 520

/** Os pontos do efeito: (t em [0,1], scaleX, scaleY). Entre pontos, interpolação linear. */
private val PONTOS_GELATINA = listOf(
    Triple(0f, 1f, 1f), Triple(0.25f, 1.3f, 0.75f), Triple(0.45f, 0.82f, 1.2f),
    Triple(0.62f, 1.1f, 0.92f), Triple(0.80f, 0.97f, 1.03f), Triple(1f, 1f, 1f),
)

/** A escala (scaleX, scaleY) do efeito gelatina em [t], de 0 a 1; fora disso, em repouso (1×1). */
fun escalaGelatina(t: Float): Pair<Float, Float> {
    if (t <= 0f || t >= 1f) return 1f to 1f
    val i = PONTOS_GELATINA.indexOfLast { it.first <= t }
    val (t0, x0, y0) = PONTOS_GELATINA[i]
    val (t1, x1, y1) = PONTOS_GELATINA[i + 1]
    val f = (t - t0) / (t1 - t0)
    return (x0 + (x1 - x0) * f) to (y0 + (y1 - y0) * f)
}

/** "Remover animações" do Android (ou a escala de animação a 0) desliga o efeito. */
fun animacoesLigadas(contexto: Context): Boolean =
    Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** Estica o ícone na horizontal, depois na vertical, e assenta (cerca de 0,5 s) quando [escolhido] passa a verdadeiro; ao aparecer já escolhido, não anima. */
fun Modifier.gelatina(escolhido: Boolean): Modifier = composed {
    val contexto = LocalContext.current
    val progresso = remember { Animatable(1f) }
    var primeira by remember { mutableStateOf(true) }
    LaunchedEffect(escolhido) {
        if (escolhido && !primeira && animacoesLigadas(contexto)) {
            progresso.snapTo(0f)
            progresso.animateTo(1f, tween(DURACAO_GELATINA_MS, easing = LinearEasing))
        } else {
            // Trocar de separador a meio do efeito cancela-o: o ícone que se deixa assenta já.
            progresso.snapTo(1f)
        }
        primeira = false
    }
    graphicsLayer {
        val (x, y) = escalaGelatina(progresso.value)
        scaleX = x
        scaleY = y
    }
}
