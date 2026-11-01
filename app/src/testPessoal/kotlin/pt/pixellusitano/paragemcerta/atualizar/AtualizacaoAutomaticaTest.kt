package pt.pixellusitano.paragemcerta.atualizar

import org.junit.Assert.assertEquals
import org.junit.Test

class AtualizacaoAutomaticaTest {
    private val nova = NovaVersao("v9.9.9", "https://exemplo/paragem-certa.apk", "Notas.")

    @Test fun desligadaNaoFazNada() = assertEquals(Decisao.NADA, decidir(nova, ligado = false, emPrimeiroPlano = false))

    @Test fun semVersaoNovaNaoFazNada() = assertEquals(Decisao.NADA, decidir(null, ligado = true, emPrimeiroPlano = false))

    @Test fun comAppEmUsoEspera() = assertEquals(Decisao.ESPERAR, decidir(nova, ligado = true, emPrimeiroPlano = true))

    @Test fun comAppEmSegundoPlanoInstala() = assertEquals(Decisao.INSTALAR, decidir(nova, ligado = true, emPrimeiroPlano = false))

    @Test fun desligadaGanhaMesmoComAppEmUso() = assertEquals(Decisao.NADA, decidir(nova, ligado = false, emPrimeiroPlano = true))

    @Test fun confirmacaoPendenteDaMesmaVersaoNaoDescarregaOutraVez() =
        assertEquals(Decisao.NADA, decidir(nova, ligado = true, emPrimeiroPlano = false, pendente = nova.tag))

    @Test fun confirmacaoPendenteGanhaMesmoComAppEmUso() =
        assertEquals(Decisao.NADA, decidir(nova, ligado = true, emPrimeiroPlano = true, pendente = nova.tag))

    @Test fun confirmacaoPendenteDeVersaoAnteriorNaoImpedeAMaisNova() =
        assertEquals(Decisao.INSTALAR, decidir(nova, ligado = true, emPrimeiroPlano = false, pendente = "v0.0.9"))
}
