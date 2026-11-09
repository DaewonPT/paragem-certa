package pt.pixellusitano.paragemcerta.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import pt.pixellusitano.paragemcerta.ui.tema.LocalEscuro

/** Ícones escuros só no tema claro e sem faixa azul por baixo; sobre o azul (ou no escuro) ficam claros. */
fun iconesEscurosNaBarraDeEstado(escuro: Boolean, comFaixa: Boolean): Boolean = !escuro && !comFaixa

/** Corre depois dos efeitos de `enableEdgeToEdge` da atividade (que repõem os ícones ao mudar o tema), por isso fica a última palavra. */
@Composable
fun AjustarBarraDeEstado(comFaixa: Boolean) {
    val vista = LocalView.current
    val escuro = LocalEscuro.current
    SideEffect {
        val janela = (vista.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(janela, vista).isAppearanceLightStatusBars = iconesEscurosNaBarraDeEstado(escuro, comFaixa)
    }
}
