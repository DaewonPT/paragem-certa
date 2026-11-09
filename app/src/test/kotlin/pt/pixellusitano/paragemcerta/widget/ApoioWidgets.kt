package pt.pixellusitano.paragemcerta.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

internal const val ANDROID = "http://schemas.android.com/apk/res/android"

/** O onReceive como o Android o chama, com um PendingResult que sabe se levou com o finish(). */
internal fun entregar(contexto: Context, receiver: BroadcastReceiver, intent: Intent): BroadcastReceiver.PendingResult {
    val pendente = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
        ShadowBroadcastPendingResult::class.java, "create",
        ClassParameter.from(Int::class.java, 0), ClassParameter.from(String::class.java, null),
        ClassParameter.from(Bundle::class.java, null), ClassParameter.from(Boolean::class.java, false),
    )
    ReflectionHelpers.setField(receiver, "mPendingResult", pendente)
    receiver.onReceive(contexto, intent)
    return pendente
}

internal fun acabou(p: BroadcastReceiver.PendingResult) = Shadow.extract<ShadowBroadcastPendingResult>(p).future.isDone

internal fun esperarPeloFim(p: BroadcastReceiver.PendingResult) {
    val limite = System.currentTimeMillis() + 5_000
    while (!acabou(p) && System.currentTimeMillis() < limite) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(5)
    }
}

/** O XML do provider, já no elemento appwidget-provider. */
internal fun configuracao(contexto: Context, xml: Int): XmlResourceParser =
    contexto.resources.getXml(xml).apply { while (name != "appwidget-provider") next() }

internal fun noModo(contexto: Context, escuro: Boolean, letra: Float = 1f): Context = contexto.createConfigurationContext(
    Configuration(contexto.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (escuro) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        fontScale = letra
    },
)

/** O launcher volta a aplicar os mesmos RemoteViews quando o telemóvel muda de modo, sem nada redesenhar. */
internal fun aplicadoNoOutroModo(contexto: Context, desenhadoEscuro: Boolean, desenhar: (Context) -> RemoteViews): View {
    val ecra = noModo(contexto, !desenhadoEscuro)
    return desenhar(noModo(contexto, desenhadoEscuro)).apply(ecra, FrameLayout(ecra))
}
