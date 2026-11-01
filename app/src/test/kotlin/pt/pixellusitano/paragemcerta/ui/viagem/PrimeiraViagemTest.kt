package pt.pixellusitano.paragemcerta.ui.viagem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimeiraViagemTest {
    private val tudoBem = Preparacao(localizacaoPrecisa = true, localizacaoLigada = true, notificacoes = true, bateriaLivre = true, volumeAlarme = 5)

    /** Os passos pela ordem em que a pessoa os vê, a dar cada um por feito (o telemóvel continua como está: recusou ou ignorou). */
    private fun passos(p: Preparacao, primeiraViagem: Boolean, comSom: Boolean = true): List<Passo> {
        val feitos = mutableSetOf<Passo>()
        while (true) feitos += proximoPasso(p, feitos, primeiraViagem, comSom) ?: return feitos.toList()
    }

    @Test fun nadaEmFaltaComecaLogo() {
        assertNull(proximoPasso(tudoBem, emptySet(), primeiraViagem = true, comSom = true))
    }

    @Test fun primeiraViagemSegueAOrdemDaSpec() {
        val semNada = Preparacao(localizacaoPrecisa = false, localizacaoLigada = false, notificacoes = false, bateriaLivre = false, volumeAlarme = 0)
        // Sem a permissão precisa não vale a pena avisar que a localização do telemóvel está desligada: a viagem corre pelo horário.
        assertEquals(listOf(Passo.LOCALIZACAO, Passo.NOTIFICACOES, Passo.BATERIA, Passo.VOLUME), passos(semNada, primeiraViagem = true))
    }

    @Test fun comAPermissaoPrecisaAvisaQueALocalizacaoEstaDesligada() {
        val desligada = tudoBem.copy(localizacaoLigada = false, notificacoes = false, bateriaLivre = false, volumeAlarme = 0)
        assertEquals(
            listOf(Passo.LOCALIZACAO_DESLIGADA, Passo.NOTIFICACOES, Passo.BATERIA, Passo.VOLUME),
            passos(desligada, primeiraViagem = true),
        )
    }

    @Test fun depoisDeDarAPermissaoPassaAVerSeALocalizacaoEstaLigada() {
        val sem = tudoBem.copy(localizacaoPrecisa = false, localizacaoLigada = false)
        assertEquals(Passo.LOCALIZACAO, proximoPasso(sem, emptySet(), primeiraViagem = false, comSom = true))
        // O telemóvel é lido de novo depois de cada passo: agora tem a permissão.
        assertEquals(Passo.LOCALIZACAO_DESLIGADA, proximoPasso(sem.copy(localizacaoPrecisa = true), setOf(Passo.LOCALIZACAO), false, true))
    }

    @Test fun segundaViagemNaoRepeteAExplicacaoDaBateria() {
        val sujeita = tudoBem.copy(bateriaLivre = false)
        assertEquals(listOf(Passo.BATERIA), passos(sujeita, primeiraViagem = true))
        assertEquals(emptyList<Passo>(), passos(sujeita, primeiraViagem = false))
    }

    @Test fun bateriaJaLivreNaoPergunta() {
        assertEquals(emptyList<Passo>(), passos(tudoBem, primeiraViagem = true))
    }

    @Test fun cadaPassoSoApareceUmaVez() {
        val recusou = tudoBem.copy(localizacaoPrecisa = false, notificacoes = false)
        assertEquals(Passo.NOTIFICACOES, proximoPasso(recusou, setOf(Passo.LOCALIZACAO), primeiraViagem = false, comSom = true))
        assertNull(proximoPasso(recusou, setOf(Passo.LOCALIZACAO, Passo.NOTIFICACOES), primeiraViagem = false, comSom = true))
    }

    @Test fun volumeDoAlarmeAZeroSoImportaComSom() {
        val mudo = tudoBem.copy(volumeAlarme = 0)
        assertEquals(listOf(Passo.VOLUME), passos(mudo, primeiraViagem = false, comSom = true))
        assertEquals(emptyList<Passo>(), passos(mudo, primeiraViagem = false, comSom = false))
        assertEquals(emptyList<Passo>(), passos(tudoBem.copy(volumeAlarme = 1), primeiraViagem = true))
    }

    @Test fun guiaDoFabricante() {
        assertEquals("https://dontkillmyapp.com/xiaomi", guiaBateria("Xiaomi"))
        assertEquals("https://dontkillmyapp.com/huawei", guiaBateria("HUAWEI"))
        assertEquals("https://dontkillmyapp.com/nothing-technology", guiaBateria("Nothing Technology"))
    }
}
