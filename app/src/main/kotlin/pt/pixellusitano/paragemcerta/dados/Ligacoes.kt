package pt.pixellusitano.paragemcerta.dados

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import java.time.Instant

private val Context.lojaPreferencias by preferencesDataStore(name = "preferencias")

/** Liga as peças da app à mão (sem biblioteca de injeção de dependências). */
class Ligacoes(contexto: Context) {
    val http = OkHttpHttp(userAgent(BuildConfig.VERSION_NAME))
    val bd: BaseDados = Room.databaseBuilder(contexto, BaseDados::class.java, "paragem-certa.db")
        .addMigrations(MIGRACAO_2_3, MIGRACAO_3_4).build()
    val cm = CarrisMetropolitana(http)
    val transitous = Transitous(http)
    val servidor = ServidorParagemCerta(http)
    val configuracoes = Configuracoes(contexto, servidor)
    val catalogoLinhas = CatalogoLinhas(contexto.filesDir, http, BASE_SERVIDOR)
    val repositorio = Repositorio(
        bd, cm, transitous, GeoApi(http), servidor = servidor, interruptores = { configuracoes.atual.value.interruptores },
        catalogo = { catalogoLinhas.atual },
    )
    val preferencias = Preferencias(contexto.applicationContext.lojaPreferencias)
    val conta = Conta(contexto, servidor, repositorio)
    val comunidade by lazy { Comunidade(servidor, conta, configuracoes, preferencias) }
    val detalheDasLinhas = DetalheDasLinhas(cm, transitous, catalogoLinhas, repositorio)
    val estacoesGira = EstacoesGira(contexto.filesDir, Gira(http), Instant::now)
    val perturbacoes = FontePerturbacoes(cm)

    private companion object {
        // O mesmo do ServidorParagemCerta: o linhas.json está nos ficheiros de dados do servidor.
        const val BASE_SERVIDOR = "https://paragemcerta.pixellusitano.com"
    }
}
