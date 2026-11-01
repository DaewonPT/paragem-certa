package pt.pixellusitano.paragemcerta

import android.app.Application
import pt.pixellusitano.paragemcerta.dados.Ligacoes

/**
 * Corre também no processo :trip da viagem, por isso não faz nada ao arrancar: nada de WorkManager nem de base de
 * dados (as ligações só se criam quando alguém as pede, e o :trip nunca pede).
 */
class ParagemCertaApp : Application() {
    val ligacoes by lazy { Ligacoes(this) }
}
