package pt.pixellusitano.paragemcerta.ui.rotas

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.fontes.transitous.TransitousParser
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.dados.fixture

class NomesDaRotaTest {
    private val rota = TransitousParser.rotas(fixture("transitous-plan.json"))[0]

    @Test fun osExtremosEmCoordenadasPassamAOsNomesQueAPessoaViu() {
        // O Transitous chama START e END às pontas dadas em coordenadas; é o que chegava à notificação e ao "Chegaste a ...".
        assertEquals("START", rota.etapas.first().de.nome)
        assertEquals("END", rota.etapas.last().para.nome)

        val nomeada = rota.comNomes("A minha posição", "Parque das Nações")

        assertEquals("A minha posição", nomeada.etapas.first().de.nome)
        assertEquals("Parque das Nações", nomeada.etapas.last().para.nome)
        // Só os nomes das pontas mudam: nem as coordenadas, nem o resto das etapas.
        assertEquals(rota.etapas.first().de.copy(nome = "A minha posição"), nomeada.etapas.first().de)
        assertEquals(rota.etapas.last().para.copy(nome = "Parque das Nações"), nomeada.etapas.last().para)
        assertEquals(rota.etapas.first().para, nomeada.etapas.first().para)
        assertEquals(rota.etapas.drop(1).dropLast(1), nomeada.etapas.drop(1).dropLast(1))
        assertEquals(rota.copy(etapas = emptyList()), nomeada.copy(etapas = emptyList()))
    }

    @Test fun umNomeVerdadeiroNuncaSeSubstitui() {
        // Quem escolheu uma paragem como origem ou destino: a fonte já a nomeia, e é esse o nome certo.
        val comParagens = rota.copy(
            etapas = rota.etapas.mapIndexed { i, e ->
                when (i) {
                    0 -> e.copy(de = e.de.copy(nome = "Cais do Sodré"))
                    rota.etapas.lastIndex -> e.copy(para = e.para.copy(nome = "Oriente"))
                    else -> e
                }
            },
        )
        assertEquals(comParagens, comParagens.comNomes("A minha posição", "Parque das Nações"))
    }

    @Test fun cadaPontaDecideSozinha() {
        val soOrigem = rota.copy(etapas = rota.etapas.dropLast(1) + rota.etapas.last().let { it.copy(para = it.para.copy(nome = "Oriente")) })
        val nomeada = soOrigem.comNomes("Casa", "Trabalho")
        assertEquals("Casa", nomeada.etapas.first().de.nome)
        assertEquals("Oriente", nomeada.etapas.last().para.nome)
    }

    @Test fun umaViagemDeUmaEtapaTrocaAsDuasPontas() {
        val etapa = rota.etapas.first().let { it.copy(para = it.para.copy(nome = "END")) }
        val so: Itinerario = rota.copy(etapas = listOf(etapa))
        val nomeada = so.comNomes("Casa", "Trabalho")
        assertEquals("Casa", nomeada.etapas.single().de.nome)
        assertEquals("Trabalho", nomeada.etapas.single().para.nome)
    }
}
