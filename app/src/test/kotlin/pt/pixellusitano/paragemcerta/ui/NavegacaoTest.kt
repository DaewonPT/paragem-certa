package pt.pixellusitano.paragemcerta.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A barra de baixo com os destinos reais da app (os ecrãs trocados por texto: só interessa a pilha de navegação). */
@RunWith(RobolectricTestRunner::class)
class NavegacaoTest {
    @get:Rule val regra = createComposeRule()
    private lateinit var nav: NavHostController

    @Before fun montar() {
        regra.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Inicio()) {
                composable<Inicio> { Text("início") }
                composable<Pesquisa> { Text("pesquisa") }
                composable<Rotas> { Text("rotas") }
                composable<Viagem> { Text("viagem") }
                composable<Menu> { Text("menu") }
            }
        }
    }

    private fun naInicio() = nav.currentDestination!!.hasRoute(Inicio::class)
    private fun nasRotas() = nav.currentDestination!!.hasRoute(Rotas::class)

    @Test fun inicioVoltaDepoisDeUmaRotaDaPesquisa() {
        // O caminho do autor: "Para onde vais?" → destino → "Começar viagem" → "Voltar" → tocar em "Início".
        regra.runOnIdle {
            nav.navigate(Pesquisa)
            nav.navigate(Rotas(destino = "Fundação Champalimaud|38.6935|-9.2238")) { popUpTo<Inicio>() }
            nav.navigate(Viagem)
            nav.popBackStack()
        }
        regra.runOnIdle { assertTrue(nasRotas()) }
        regra.runOnIdle { nav.irParaSeparador(Inicio()) }
        regra.runOnIdle { assertTrue("tocar em Início tem de mostrar o Início", naInicio()) }
        regra.runOnIdle { nav.irParaSeparador(Inicio()) }
        regra.runOnIdle { assertTrue(naInicio()) }
    }

    @Test fun separadorRotasGuardaOQueEstavaAberto() {
        regra.runOnIdle { nav.irParaSeparador(Rotas()) }
        val entrada = regra.runOnIdle { nav.currentBackStackEntry!!.id }
        regra.runOnIdle { nav.irParaSeparador(Inicio()) }
        regra.runOnIdle { assertTrue(naInicio()) }
        regra.runOnIdle { nav.irParaSeparador(Rotas()) }
        regra.runOnIdle {
            assertTrue(nasRotas())
            assertEquals("o separador Rotas volta como estava", entrada, nav.currentBackStackEntry!!.id)
        }
    }

    @Test fun inicioVoltaDepoisDoSeparadorMenu() {
        regra.runOnIdle { nav.irParaSeparador(Menu) }
        regra.runOnIdle { nav.irParaSeparador(Inicio()) }
        regra.runOnIdle { assertTrue(naInicio()) }
    }
}
