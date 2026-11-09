package pt.pixellusitano.paragemcerta.widget

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * O trabalho dos receivers dos widgets. Na linha principal, onde os onReceive correm um de cada vez e o immediate entra logo: dois
 * toques seguidos não passam os dois pela regra dos 30 s. Uma falha aqui não pode deitar a app abaixo, e sem o finish() o Android dava o
 * receiver por encravado.
 */
internal fun BroadcastReceiver.emSegundoPlano(tarefa: suspend () -> Unit) {
    val pendente = goAsync()
    CoroutineScope(Dispatchers.Main.immediate).launch {
        try {
            tarefa()
        } catch (e: Exception) {
            // o widget fica como estava; o próximo toque ou redesenho tenta outra vez
        } finally {
            pendente.finish()
        }
    }
}
