package pt.pixellusitano.paragemcerta.dados

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.geoapi.GeoApi
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FavoritoRemoto
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rede.Resposta

@RunWith(RobolectricTestRunner::class)
class LinhasFavoritasTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private fun novaBd() = Room.inMemoryDatabaseBuilder(contexto, BaseDados::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val bd = novaBd()
    private val http = HttpPorUrl(
        "/stops" to listOf({ Resposta(200, fixture("cm-stops.json")) }),
        "/lines" to listOf({ Resposta(200, fixture("cm-lines.json")) }),
        "geocode" to listOf({ Resposta(200, "[]") }),
    )
    private fun repo(b: BaseDados = bd) = Repositorio(b, CarrisMetropolitana(http), Transitous(http), GeoApi(http))
    private val repo = repo()

    private val l1009 = RefLinha(Fonte.CM, "1009")
    private val l728 = RefLinha(Fonte.TRANSITOUS, "pt-Carris:728")
    private val cais = Paragem(RefParagem(Fonte.CM, "060419"), "Cais do Sodré", 38.7056, -9.1448, null)
    private val metro = Paragem(RefParagem(Fonte.CM, "060451"), "Cais do Sodré (Metro)", 38.7062, -9.1450, null)
    private val martim = Paragem(RefParagem(Fonte.TRANSITOUS, "pt-Carris_3803"), "Martim Moniz", 38.7166, -9.1357, null)

    @After fun fechar() = bd.close()

    @Test fun linhaFavoritaUmaPorLinhaESentido() = runTest {
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", cais, "1009")
        repo.alternarLinhaFavorita(l1009, 1, "Setúbal", cais, "1009")
        repo.alternarLinhaFavorita(l728, 0, "Portela", martim, "728")
        val todas = repo.linhasFavoritas().first()
        assertEquals(listOf(l1009 to 0, l1009 to 1, l728 to 0), todas.map { it.ref to it.sentido })
        assertEquals(listOf(0, 1, 2), todas.map { it.ordem })
        assertEquals(LinhaFavorita(l1009, 0, cais.ref, "1009", "Lisboa", 0), todas.first())
        // A mesma linha e sentido noutra paragem tira a ★: nunca há duas do mesmo sentido.
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", metro, "1009")
        assertEquals(listOf(l1009 to 1, l728 to 0), repo.linhasFavoritas().first().map { it.ref to it.sentido })
    }

    @Test fun mudarAParagemHabitualSincroniza() = runTest {
        repo.comConta = true
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", cais, "1009")
        val enviadas = repo.porSincronizar()
        repo.aplicarSincronizacao(enviadas, emptyList(), emptySet())
        assertTrue(repo.porSincronizar().isEmpty())

        repo.mudarParagemHabitual(repo.linhasFavoritas().first().single(), metro)
        val f = repo.linhasFavoritas().first().single()
        assertEquals(metro.ref, f.paragem)
        assertEquals(0, f.ordem) // fica no mesmo sítio da lista
        val porEnviar = repo.porSincronizar().map { it.chave to it.apagado }.toSet()
        assertEquals(setOf("LINHA|CM:1009|0|CM:060419" to true, "LINHA|CM:1009|0|CM:060451" to false), porEnviar)
        assertEquals(2, repo.porEnviar().first())
    }

    @Test fun sincronizaLinhaFavorita() = runTest {
        repo.alternarLinhaFavorita(l728, 1, "Restelo", martim, "728")
        val enviadas = repo.porSincronizar()
        val remota = enviadas.single()
        assertEquals("LINHA|TRANSITOUS:pt-Carris:728|1|TRANSITOUS:pt-Carris_3803", remota.chave)
        assertEquals("LINHA", remota.tipo)
        assertEquals(38.7166, remota.lat, 1e-9)
        assertEquals(1, repo.porEnviar().first())

        // A resposta traz a mesma de volta: fica igual e deixa de estar por enviar (não cai no ramo dos sítios).
        repo.aplicarSincronizacao(enviadas, listOf(remota.copy(revisao = 1)), emptySet())
        assertTrue(repo.porSincronizar().isEmpty())
        assertEquals(0, repo.porEnviar().first())
        assertEquals(listOf(LinhaFavorita(l728, 1, martim.ref, "728", "Restelo", 0)), repo.linhasFavoritas().first())

        // Noutro telemóvel volta igual, com o destino.
        val outra = novaBd()
        repo(outra).aplicarSincronizacao(emptyList(), listOf(remota.copy(revisao = 1)), emptySet())
        assertEquals(repo.linhasFavoritas().first(), repo(outra).linhasFavoritas().first())
        outra.close()
    }

    @Test fun favoritoDeLinhaComChaveLongaFicaNoTelemovel() = runTest {
        repo.comConta = true
        val prefixo = "LINHA|TRANSITOUS:pt-Comboios-de-Portugal(CP):|0|TRANSITOUS:pt-Comboios-de-Portugal(CP)_94_69005"
        val comprida = RefLinha(Fonte.TRANSITOUS, "pt-Comboios-de-Portugal(CP):" + "Linha ó ".repeat(40).take(260 - prefixo.length))
        val estacao = Paragem(RefParagem(Fonte.TRANSITOUS, "pt-Comboios-de-Portugal(CP)_94_69005"), "Cais do Sodré", 38.706, -9.145, null)
        repo.alternarLinhaFavorita(comprida, 0, "Cascais", estacao, "Linha de Cascais")
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", cais, "1009")
        assertEquals(260, bd.linhasFavoritas().pendentes().maxOf { it.chave.length })

        assertEquals(listOf("LINHA|CM:1009|0|CM:060419"), repo.porSincronizar().map { it.chave })
        assertEquals(1, repo.porEnviar().first())
        // Nem depois de juntar tudo à conta, nem ao tirar a ★: a conta nunca a conheceu.
        repo.marcarTudoPorEnviar()
        assertEquals(1, repo.porEnviar().first())
        repo.alternarLinhaFavorita(comprida, 0, "Cascais", estacao, "Linha de Cascais")
        assertEquals(listOf("LINHA|CM:1009|0|CM:060419"), repo.porSincronizar().map { it.chave })
        assertEquals(1, repo.porEnviar().first())
    }

    @Test fun linhaFavoritaDeOutroTelemovelAparece() = runTest {
        val remota = FavoritoRemoto("LINHA|CM:1009|1|CM:060419", "LINHA", "1009\nSetúbal", 38.7, -9.14, 3, apagado = false, revisao = 4)
        repo.aplicarSincronizacao(emptyList(), listOf(remota), emptySet())
        assertEquals(listOf(LinhaFavorita(l1009, 1, cais.ref, "1009", "Setúbal", 3)), repo.linhasFavoritas().first())
        assertTrue(repo.porSincronizar().isEmpty()) // o que vem da conta não fica por enviar
        // Uma chave estragada de outra versão não rebenta nada: fica de fora.
        repo.aplicarSincronizacao(emptyList(), listOf(remota.copy(chave = "LINHA|sem sentido")), emptySet())
        assertEquals(1, repo.linhasFavoritas().first().size)

        repo.aplicarSincronizacao(emptyList(), listOf(remota.copy(apagado = true)), emptySet())
        assertTrue(repo.linhasFavoritas().first().isEmpty())
    }

    @Test fun marcasDasLinhasFavoritasSaemComAConta() = runTest {
        repo.comConta = true
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", cais, "1009")
        assertEquals(setOf("LINHA|CM:1009|0|CM:060419"), repo.chavesNoTelemovel())
        repo.limparMarcas()
        assertEquals(0, repo.porEnviar().first())
        repo.marcarTudoPorEnviar()
        assertEquals(listOf("LINHA|CM:1009|0|CM:060419"), repo.porSincronizar().map { it.chave })
    }

    @Test fun apagarDadosApagaLinhasFavoritasEVistas() = runTest {
        val prefs = Preferencias(LojaEmMemoria())
        repo.alternarLinhaFavorita(l1009, 0, "Lisboa", cais, "1009")
        prefs.marcarVistas(setOf("cm:1", "metro:VD:0a1b2c3d"))
        repo.apagarDadosDoTelemovel()
        prefs.esquecerVistas()
        assertTrue(repo.linhasFavoritas().first().isEmpty())
        assertTrue(prefs.perturbacoesVistas.first().isEmpty())
    }

    @Test fun linhasDasParagensFavoritas() = runTest {
        assertTrue(repo.atualizarRede())
        repo.alternarFavorita(cais)
        repo.alternarFavorita(martim)
        // As últimas chegadas guardadas da paragem do Transitous dizem que linhas lá passam.
        bd.chegadas().guardar(
            ChegadasGuardadasEntidade(
                martim.ref.chave,
                """[{"fonte":"TRANSITOUS","linhaId":"pt-Carris_204_0","linhaNome":"728","destino":"Portela","programada":0,"prevista":null,
                   |"idViagem":null,"idVeiculo":null,"modo":"AUTOCARRO","cor":null,"acessivel":null},
                   |{"fonte":"TRANSITOUS","linhaId":"pt-Carris_15","linhaNome":"28e","destino":"Prazeres","programada":0,"prevista":null,
                   |"idViagem":null,"idVeiculo":null,"modo":"ELETRICO","cor":null,"acessivel":null}]""".trimMargin(),
                0,
            ),
        )
        assertEquals(setOf("3708", "728", "28E"), repo.linhasDasParagensFavoritas())
    }

    @Test fun linhasCmFlowMudaQuandoARedeChega() = runTest {
        assertTrue(repo.linhasCmFlow().first().isEmpty())
        repo.atualizarRede()
        assertTrue(repo.linhasCmFlow().first().any { it.id == "3708" })
    }
}
