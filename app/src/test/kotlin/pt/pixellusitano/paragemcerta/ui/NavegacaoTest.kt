package pt.pixellusitano.paragemcerta.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Linha
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.dados.EcraInicial
import pt.pixellusitano.paragemcerta.dados.LinhaFavorita

/** A barra de baixo com os destinos reais da app (os ecrãs trocados por texto: só interessa a pilha de navegação). */
@RunWith(RobolectricTestRunner::class)
class NavegacaoTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var nav: NavHostController
    /** O que a MainActivity guarda quando um widget é tocado, e quantas vezes a Navegacao avisou que já abriu. */
    private var pedidoDoWidget by mutableStateOf<String?>(null)
    private var avisos = 0

    @Before fun montar() {
        regra.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Inicio) {
                composable<Inicio> { Text("início") }
                composable<Pesquisa> { Text("pesquisa") }
                composable<Rotas> { Text("rotas") }
                composable<Linhas> { Text("linhas") }
                composable<LinhaRota> { Text("linha") }
                composable<Viagem> { Text("viagem") }
                composable<Menu> { Text("menu") }
                composable<Paragem> { Text("paragem") }
            }
            AbrirParagemDoWidget(nav, pedidoDoWidget, aoAbrir = { pedidoDoWidget = null; avisos++ })
        }
    }

    private fun naInicio() = nav.currentDestination!!.hasRoute(Inicio::class)
    private fun nasRotas() = nav.currentDestination!!.hasRoute(Rotas::class)

    @Test fun inicioVoltaDepoisDeUmaRotaDaPesquisa() {
        // O caminho que dava o erro: "Para onde vais?" → destino → "Começar viagem" → "Voltar" → tocar em "Início".
        regra.runOnIdle {
            nav.navigate(Pesquisa)
            nav.navigate(Rotas(destino = "Fundação Champalimaud|38.6935|-9.2238")) { popUpTo<Inicio>() }
            nav.navigate(Viagem)
            nav.popBackStack()
        }
        regra.runOnIdle { assertTrue(nasRotas()) }
        regra.runOnIdle { nav.irParaSeparador(Inicio) }
        regra.runOnIdle { assertTrue("tocar em Início tem de mostrar o Início", naInicio()) }
        regra.runOnIdle { nav.irParaSeparador(Inicio) }
        regra.runOnIdle { assertTrue(naInicio()) }
    }

    @Test fun separadorRotasGuardaOQueEstavaAberto() {
        regra.runOnIdle { nav.irParaSeparador(Rotas()) }
        val entrada = regra.runOnIdle { nav.currentBackStackEntry!!.id }
        regra.runOnIdle { nav.irParaSeparador(Inicio) }
        regra.runOnIdle { assertTrue(naInicio()) }
        regra.runOnIdle { nav.irParaSeparador(Rotas()) }
        regra.runOnIdle {
            assertTrue(nasRotas())
            assertEquals("o separador Rotas volta como estava", entrada, nav.currentBackStackEntry!!.id)
        }
    }

    @Test fun separadorLinhasGuardaOQueEstavaAberto() {
        // A lista das linhas, uma linha aberta por cima, e para o Início: a linha fica à espera no separador Linhas.
        regra.runOnIdle {
            nav.irParaSeparador(Linhas)
            nav.navigate(LinhaRota("CM:1009", sentido = 1, paragem = "CM:060419"))
        }
        val entrada = regra.runOnIdle { nav.currentBackStackEntry!!.id }
        regra.runOnIdle { nav.irParaSeparador(Inicio) }
        regra.runOnIdle { assertTrue(naInicio()) }
        regra.runOnIdle { nav.irParaSeparador(Linhas) }
        regra.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute(LinhaRota::class))
            assertEquals("o separador Linhas volta com a linha aberta", entrada, nav.currentBackStackEntry!!.id)
        }
        // E o separador Rotas não leva a linha atrás.
        regra.runOnIdle { nav.irParaSeparador(Rotas()) }
        regra.runOnIdle { assertTrue(nasRotas()) }
    }

    @Test fun destinoInicialDeCadaEscolha() {
        assertEquals(Inicio, destinoInicial(EcraInicial.MAPA))
        assertEquals(Rotas(), destinoInicial(EcraInicial.ROTAS))
        assertEquals(Linhas, destinoInicial(EcraInicial.LINHAS))
        // Os favoritos são o Início com o painel aberto: o destino é o mesmo, o painel abre-se no InicioEcra.
        assertEquals(Inicio, destinoInicial(EcraInicial.FAVORITOS))
    }

    @Test fun pesquisaDeLinhaAbreOEcraDaLinha() {
        val l3708 = Linha(Fonte.CM, "3708", "3708", "Cais do Sodré - Cacilhas", null, null, Modo.AUTOCARRO)
        regra.runOnIdle {
            nav.navigate(Pesquisa)
            nav.abrirLinhaPesquisada(l3708)
        }
        regra.runOnIdle {
            assertTrue(nav.currentDestination!!.hasRoute(LinhaRota::class))
            assertEquals(LinhaRota("CM:3708"), nav.currentBackStackEntry!!.toRoute<LinhaRota>())
            nav.popBackStack()
        }
        regra.runOnIdle { assertTrue("voltar regressa aos resultados da pesquisa", nav.currentDestination!!.hasRoute(Pesquisa::class)) }
    }

    @Test fun linhaFavoritaAbreALinhaNoSentidoENaParagemHabitual() {
        val f = LinhaFavorita(RefLinha(Fonte.CM, "1009"), 1, RefParagem(Fonte.CM, "060419"), "1009", "Setúbal", 0)
        assertEquals(LinhaRota("CM:1009", sentido = 1, paragem = "CM:060419"), f.rotaDaLinha())
    }

    @Test fun inicioJaNaoTemModoLinha() {
        // O Início é um destino simples: a rota já não leva o argumento "linha" do antigo Inicio(linha).
        regra.runOnIdle { assertEquals("pt.pixellusitano.paragemcerta.ui.Inicio", nav.currentDestination!!.route) }
    }

    @Test fun inicioVoltaDepoisDoSeparadorMenu() {
        regra.runOnIdle { nav.irParaSeparador(Menu) }
        regra.runOnIdle { nav.irParaSeparador(Inicio) }
        regra.runOnIdle { assertTrue(naInicio()) }
    }

    @Test fun widgetAbreAParagem() {
        regra.runOnIdle { nav.abrirParagemDoWidget("CM:060419") }
        regra.runOnIdle {
            assertEquals(Paragem("CM:060419"), nav.currentBackStackEntry!!.toRoute<Paragem>())
            nav.popBackStack()
        }
        regra.runOnIdle { assertTrue("voltar regressa ao que estava aberto", naInicio()) }
    }

    @Test fun chaveInvalidaNaoFazNada() {
        regra.runOnIdle { nav.abrirParagemDoWidget("isto não é uma chave") }
        regra.runOnIdle { assertTrue(naInicio()) }
        regra.runOnIdle { pedidoDoWidget = "isto também não" }
        regra.runOnIdle {
            assertTrue(naInicio())
            assertEquals("mesmo sem abrir, o pedido gasta-se", 1, avisos)
            assertEquals(null, pedidoDoWidget)
        }
    }

    @Test fun pedidoDoWidgetAbreUmaVezEAvisa() {
        regra.runOnIdle { pedidoDoWidget = "CM:060419" }
        regra.runOnIdle {
            assertEquals(Paragem("CM:060419"), nav.currentBackStackEntry!!.toRoute<Paragem>())
            assertEquals(1, avisos)
            assertEquals(null, pedidoDoWidget)
            nav.popBackStack()
        }
        // Tocar outra vez na mesma paragem, com a app ainda aberta, tem de a abrir de novo.
        regra.runOnIdle { pedidoDoWidget = "CM:060419" }
        regra.runOnIdle {
            assertEquals(Paragem("CM:060419"), nav.currentBackStackEntry!!.toRoute<Paragem>())
            assertEquals(2, avisos)
        }
    }

    @Test fun paragemDoWidgetJaAbertaNaoEmpilhaOutraIgual() {
        regra.runOnIdle { pedidoDoWidget = "CM:060419" }
        regra.runOnIdle { pedidoDoWidget = "CM:060419" }
        regra.runOnIdle { nav.popBackStack() }
        regra.runOnIdle { assertTrue(naInicio()) }
    }
}
