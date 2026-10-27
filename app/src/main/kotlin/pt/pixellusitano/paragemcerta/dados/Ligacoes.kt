package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import androidx.room.Room
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.userAgent

/** Liga as peças da app à mão (sem biblioteca de injeção de dependências). */
class Ligacoes(contexto: Context) {
    val http = OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))
    val bd: BaseDados = Room.databaseBuilder(contexto, BaseDados::class.java, "paragem-certa.db").build()
    val repositorio = Repositorio(bd, CarrisMetropolitana(http), Transitous(http))
}
