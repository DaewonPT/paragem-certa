package pt.pixellusitano.paragemcerta.ui.tema

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Razão de contraste WCAG entre duas cores (1 a 21). */
fun contraste(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
