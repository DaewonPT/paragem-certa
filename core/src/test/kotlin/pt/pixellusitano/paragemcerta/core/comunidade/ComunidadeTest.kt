package pt.pixellusitano.paragemcerta.core.comunidade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.viagem.Percurso
import pt.pixellusitano.paragemcerta.core.viagem.Viagem
import java.time.Instant

class ComunidadeTest {
    @Test fun pertoSoAte300m() {
        assertEquals(300.0, DISTANCIA_RELATAR_M, 0.0)
        assertTrue(pertoParaRelatar(299.9, 8f))
        assertTrue(pertoParaRelatar(300.0, 300f))
        assertFalse(pertoParaRelatar(300.1, 8f))
        assertFalse(pertoParaRelatar(100.0, 2000f)) // perto, mas a posição não é de confiança
        assertFalse(pertoParaRelatar(100.0, 300.1f))
        assertTrue(pertoParaRelatar(299.9, null)) // sem precisão, vale a distância
        assertFalse(pertoParaRelatar(300.1, null))
    }

    @Test fun chaveDaLinhaEmMaiusculas() {
        assertEquals("15E", chaveLinha(" 15e "))
        assertEquals("736", chaveLinha("736"))
    }

    @Test fun osTiposDeRelatoUsamAsChavesDoServidor() {
        assertEquals(
            setOf("acidente", "desvio", "evento", "nao_passou", "vai_cheio", "elevador", "problema_paragem"),
            TipoRelato.entries.map { it.chave }.toSet(),
        )
        assertEquals(setOf("acidente", "desvio", "evento", "nao_passou", "vai_cheio"), TipoRelato.entries.filter { it.deLinha }.map { it.chave }.toSet())
        assertEquals(setOf("falso", "ofensivo", "dados_pessoais", "perigo", "outro"), MotivoDenuncia.entries.map { it.chave }.toSet())
    }

    @Test fun paragemDaViagemDaCmFicaComChaveCm() {
        assertEquals("CM:020047", chaveDaParagemEtapa("pt-Carris-Metropolitana_320047"))
        assertEquals("TRANSITOUS:x_1", chaveDaParagemEtapa("x_1"))
        assertNull(chaveDaParagemEtapa(null))
    }

    private val t0 = Instant.parse("2026-11-03T08:00:00Z")

    private fun lugar(p: ParagemEtapa) = Lugar(p.nome, p.lat, p.lon, TipoLugar.PARAGEM)

    /** Uma etapa em autocarro com uma paragem de cada id, a cerca de 1 km uma da outra, para leste. */
    private fun autocarro(lon0: Double, vararg ids: String?): Etapa {
        val paragens = ids.mapIndexed { k, id -> ParagemEtapa(id, "Paragem $k", 38.7, lon0 + k * 0.0115, t0.plusSeconds(k * 120L)) }
        return Etapa(
            aPe = false, modo = Modo.AUTOCARRO, linhaNome = "736", linhaId = "736", destino = "Fim",
            de = lugar(paragens.first()), para = lugar(paragens.last()), partida = paragens.first().hora, chegada = paragens.last().hora,
            partidaProgramada = paragens.first().hora, aoVivo = false, paragensIntermedias = ids.size - 2,
            desenho = paragens.map { it.lat to it.lon }, cor = null, idViagem = "v1", paragens = paragens,
        )
    }

    private fun aPe(lon0: Double): Etapa {
        val de = ParagemEtapa(null, "Casa", 38.7, lon0, t0)
        val para = ParagemEtapa(null, "Paragem", 38.7, lon0 + 0.0023, t0.plusSeconds(180))
        return Etapa(
            aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null, de = lugar(de), para = lugar(para),
            partida = de.hora, chegada = para.hora, partidaProgramada = de.hora, aoVivo = false, paragensIntermedias = 0,
            desenho = listOf(de.lat to de.lon, para.lat to para.lon), cor = null,
        )
    }

    private fun itinerario(vararg etapas: Etapa) = Itinerario(etapas.toList(), etapas.first().partida, etapas.last().chegada, 1)

    @Test fun paragemAFrenteSoDepoisDaPassada() {
        val primeira = autocarro(-9.20, "pt-Carris-Metropolitana_300001", "pt-Carris-Metropolitana_300002", "pt-Carris-Metropolitana_300003", "pt-Carris-Metropolitana_300004")
        val segunda = autocarro(-9.10, "pt-Carris-Metropolitana_310001", "y_2")
        val viagem = Viagem(itinerario(primeira, segunda))
        val a = Percurso(primeira.desenho, primeira.paragens).noPercurso
        // Já saiu da paragem 1 (a segunda): a passada, a atual e as de trás não contam; as da frente e as da etapa seguinte contam.
        val nasegunda = viagem.inicio(t0).copy(progresso = a[1] + 100)
        assertEquals(1, viagem.paragemPassada(nasegunda))
        assertFalse(viagem.paragemAFrente(nasegunda, "CM:000001"))
        assertFalse(viagem.paragemAFrente(nasegunda, "CM:000002"))
        assertTrue(viagem.paragemAFrente(nasegunda, "CM:000003"))
        assertTrue(viagem.paragemAFrente(nasegunda, "CM:000004"))
        assertTrue(viagem.paragemAFrente(nasegunda, "CM:010001"))
        assertTrue(viagem.paragemAFrente(nasegunda, "TRANSITOUS:y_2"))
        assertFalse(viagem.paragemAFrente(nasegunda, "CM:999999"))
        // À espera na primeira paragem (ainda não saiu dela): a de embarque não é "à frente".
        val aEspera = viagem.inicio(t0)
        assertFalse(viagem.paragemAFrente(aEspera, "CM:000001"))
        assertTrue(viagem.paragemAFrente(aEspera, "CM:000002"))
        // Na última etapa, as paragens da primeira ficaram para trás.
        val naUltima = viagem.inicio(t0).copy(etapa = 1)
        assertFalse(viagem.paragemAFrente(naUltima, "CM:000003"))
        assertFalse(viagem.paragemAFrente(naUltima, "CM:010001"))
        assertTrue(viagem.paragemAFrente(naUltima, "TRANSITOUS:y_2"))
    }

    @Test fun aPeTodasAsParagensDeVeiculoEstaoAFrente() {
        val viagem = Viagem(itinerario(aPe(-9.20), autocarro(-9.19, "pt-Carris-Metropolitana_300001", "pt-Carris-Metropolitana_300002")))
        val aAndar = viagem.inicio(t0)
        assertEquals(0, aAndar.etapa)
        assertTrue(viagem.paragemAFrente(aAndar, "CM:000001"))
        assertTrue(viagem.paragemAFrente(aAndar, "CM:000002"))
        assertFalse(viagem.paragemAFrente(aAndar, "CM:999999"))
    }
}
