package pt.pixellusitano.paragemcerta.dados

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import pt.pixellusitano.paragemcerta.BuildConfig
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.emel.Gira
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.ServidorParagemCerta
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import kotlinx.coroutines.flow.first
import pt.pixellusitano.paragemcerta.core.fotos.lerFotosDeRua
import pt.pixellusitano.paragemcerta.core.linhas.lerCatalogo
import pt.pixellusitano.paragemcerta.core.modelo.Filtros
import pt.pixellusitano.paragemcerta.core.rede.OkHttpHttp
import pt.pixellusitano.paragemcerta.core.rede.userAgent
import pt.pixellusitano.paragemcerta.ui.inicio.posicaoAtual
import pt.pixellusitano.paragemcerta.widget.AtualizadorCasaTrabalho
import pt.pixellusitano.paragemcerta.widget.DadosWidget
import pt.pixellusitano.paragemcerta.widget.WidgetCasaTrabalho
import pt.pixellusitano.paragemcerta.widget.criarAtualizadorWidgets
import pt.pixellusitano.paragemcerta.widget.desenhoDoWidget
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
    val catalogoLinhas = FicheiroDoServidor(contexto.filesDir, "linhas.json", http, "$BASE_SERVIDOR/dados/linhas.json", ler = ::lerCatalogo)
    val fotosDeRua = FicheiroDoServidor(contexto.filesDir, "fotos-de-rua.json", http, "$BASE_SERVIDOR/dados/fotos-de-rua.json", ler = ::lerFotosDeRua)
    val repositorio = Repositorio(
        bd, cm, transitous, GeoApi(http), servidor = servidor, interruptores = { configuracoes.atual.value.interruptores },
        catalogo = { catalogoLinhas.atual },
    )
    val preferencias = Preferencias(contexto.applicationContext.lojaPreferencias)
    val widgets = criarAtualizadorWidgets(contexto.applicationContext, repositorio)
    val casaTrabalho = AtualizadorCasaTrabalho(
        DadosWidget(contexto.applicationContext),
        lerLocais = { repositorio.locais().first() },
        planear = { de, para, hora -> repositorio.planear(de, para, hora, false, Filtros()) },
        posicaoAtual = { posicaoAtual(contexto.applicationContext) },
        desenhar = { id, conteudo -> AppWidgetManager.getInstance(contexto).updateAppWidget(id, desenhoDoWidget(contexto.applicationContext, id, conteudo)) },
        todos = { AppWidgetManager.getInstance(contexto).getAppWidgetIds(ComponentName(contexto, WidgetCasaTrabalho::class.java)).toList() },
    )
    val conta = Conta(contexto, servidor, repositorio, esquecerWidgets = { widgets.aoApagarDados(); casaTrabalho.aoApagarDados() })
    val comunidade by lazy { Comunidade(servidor, conta, configuracoes, preferencias) }
    val detalheDasLinhas = DetalheDasLinhas(cm, transitous, catalogoLinhas, repositorio)
    val estacoesGira = EstacoesGira(contexto.filesDir, Gira(http), Instant::now)
    val perturbacoes = FontePerturbacoes(cm)

    private companion object {
        // O mesmo do ServidorParagemCerta: o linhas.json e o fotos-de-rua.json estão nos ficheiros de dados do servidor.
        const val BASE_SERVIDOR = "https://paragemcerta.pixellusitano.com"
    }
}
