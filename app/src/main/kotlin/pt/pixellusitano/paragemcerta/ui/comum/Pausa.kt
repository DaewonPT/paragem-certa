package pt.pixellusitano.paragemcerta.ui.comum

import android.content.Context
import androidx.annotation.StringRes
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.servidor.fimDaPausa
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/** A hora (em Lisboa) a que a pausa acaba, com o artigo: "às 00:00" ou "à 01:00". */
fun horaDoFimDaPausa(agora: Instant = Instant.now()): String {
    val fim: ZonedDateTime = fimDaPausa(agora).atZone(LISBOA_FUSO)
    return (if (fim.hour == 1) "à " else "às ") + HORA.format(fim)
}

/** O texto de um aviso; o da pausa leva a hora a que acaba. */
fun textoDoAviso(c: Context, @StringRes id: Int, agora: Instant = Instant.now()): String =
    if (id == R.string.servidor_em_pausa) c.getString(id, horaDoFimDaPausa(agora)) else c.getString(id)
