package pt.pixellusitano.paragemcerta

import android.app.Application
import pt.pixellusitano.paragemcerta.dados.AtualizacaoDiaria
import pt.pixellusitano.paragemcerta.dados.Ligacoes

class ParagemCertaApp : Application() {
    val ligacoes by lazy { Ligacoes(this) }

    override fun onCreate() {
        super.onCreate()
        AtualizacaoDiaria.agendar(this)
    }
}
