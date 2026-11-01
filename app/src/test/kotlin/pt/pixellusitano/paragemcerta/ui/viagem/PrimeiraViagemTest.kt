package pt.pixellusitano.paragemcerta.ui.viagem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun doAndroid14ParaCimaOServicoPedeAlgumaLocalizacao() {
        assertFalse(servicoPodeArrancar(34, precisa = false, aproximada = false))
        assertFalse(servicoPodeArrancar(36, precisa = false, aproximada = false))
        assertTrue(servicoPodeArrancar(34, precisa = true, aproximada = false))
        assertTrue(servicoPodeArrancar(34, precisa = false, aproximada = true))
        assertTrue(servicoPodeArrancar(36, precisa = true, aproximada = true))
        // Antes do 14 o serviço arranca sem localização: a viagem corre pelo horário.
        assertTrue(servicoPodeArrancar(33, precisa = false, aproximada = false))
        assertTrue(servicoPodeArrancar(26, precisa = false, aproximada = false))
    }

    @Test fun semNenhumaLocalizacaoNoAndroid14ExplicaAntesDeComecar() {
        val semServico = tudoBem.copy(localizacaoPrecisa = false, servicoPode = false)
        // Primeiro o pedido da precisa; se foi recusado, o Android pede pelo menos a aproximada.
        assertEquals(listOf(Passo.LOCALIZACAO, Passo.LOCALIZACAO_SERVICO), passos(semServico, primeiraViagem = false))
        assertEquals(Passo.LOCALIZACAO_SERVICO, proximoPasso(semServico, setOf(Passo.LOCALIZACAO), primeiraViagem = false, comSom = true))
        // Com a aproximada o serviço arranca: não há passo a mais.
        assertEquals(listOf(Passo.LOCALIZACAO), passos(tudoBem.copy(localizacaoPrecisa = false), primeiraViagem = false))
    }

    @Test fun guiaDoFabricante() {
        assertEquals("https://dontkillmyapp.com/xiaomi", guiaBateria("Xiaomi"))
        assertEquals("https://dontkillmyapp.com/huawei", guiaBateria("HUAWEI"))
        assertEquals("https://dontkillmyapp.com/nothing-technology", guiaBateria("Nothing Technology"))
    }
}
