package pt.pixellusitano.paragemcerta.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
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

/** Nas Rotas a Navegacao não decide a barra: quem a decide é o ecrã (ver [BarraDeEstadoDasRotas]), porque a lista e o Detalhe pedem coisas diferentes. */
@Composable
fun BarraDeEstadoDaNavegacao(nasRotas: Boolean, comFaixa: Boolean) {
    if (!nasRotas) AjustarBarraDeEstado(comFaixa)
}

/** A janela por trás do Compose segue o modo da app (que pode ser o contrário do do telemóvel), para não aparecer a cor errada ao rodar ou redimensionar. */
@Composable
fun AjustarJanela(escuro: Boolean) {
    val vista = LocalView.current
    DisposableEffect(escuro) {
        (vista.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable((if (escuro) Azulejo.Noite else Azulejo.Marfim).toArgb()))
        onDispose { }
    }
}
