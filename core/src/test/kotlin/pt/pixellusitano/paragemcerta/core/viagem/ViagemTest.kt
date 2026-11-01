package pt.pixellusitano.paragemcerta.core.viagem

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import pt.pixellusitano.paragemcerta.core.viagem.Evento.CHEGOU
import pt.pixellusitano.paragemcerta.core.viagem.Evento.ETAPA_SEGUINTE
import pt.pixellusitano.paragemcerta.core.viagem.Evento.PREPARA_TE
import pt.pixellusitano.paragemcerta.core.viagem.Evento.SAI_AGORA
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

class ViagemTest {
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")
    private fun aos(s: Double): Instant = t0.plusMillis((s * 1000).roundToLong())

    // Metros a leste (x) e a norte (y) de um ponto de Lisboa, como (lat, lon).
    private fun ponto(x: Double, y: Double): Pair<Double, Double> =
        (38.7 + y / 111_194.93) to (-9.1 + x / (111_194.93 * cos(Math.toRadians(38.7))))

    private fun lugar(nome: String, xy: Pair<Double, Double>) =
        ponto(xy.first, xy.second).let { Lugar(nome, it.first, it.second, TipoLugar.PARAGEM) }

    /** A pé em linha reta, a 1,4 m/s, a partir de partidaS segundos. */
    private fun aPe(partidaS: Double, de: Pair<Double, Double>, para: Pair<Double, Double>): Etapa {
        val duracao = hypot(para.first - de.first, para.second - de.second) / 1.4
        return Etapa(
            aPe = true, modo = Modo.OUTRO, linhaNome = null, linhaId = null, destino = null,
            de = lugar("Partida", de), para = lugar("Chegada", para), partida = aos(partidaS), chegada = aos(partidaS + duracao),
            partidaProgramada = aos(partidaS), aoVivo = false, paragensIntermedias = 0,
            desenho = listOf(ponto(de.first, de.second), ponto(para.first, para.second)), cor = null,
        )
    }

    /**
     * Em veículo pela linha (pontos em metros), com paragens aos metros dados (a primeira no início da linha).
     * As horas seguem o andamento do Tracos.sintetico: vMs, parado paragemS em cada intermédia.
     */
    private fun veiculo(
        partidaS: Double, linha: List<Pair<Double, Double>>, paragensM: List<Double>, modo: Modo = Modo.AUTOCARRO,
        vMs: Double = 7.0, paragemS: Int = 20, comDesenho: Boolean = true,
    ): Etapa {
        val acumulado = linha.indices.map { i -> (1..i).sumOf { hypot(linha[it].first - linha[it - 1].first, linha[it].second - linha[it - 1].second) } }
        fun em(m: Double): Pair<Double, Double> {
            val i = (1 until linha.size).first { m <= acumulado[it] + 1e-9 }
            val t = (m - acumulado[i - 1]) / (acumulado[i] - acumulado[i - 1])
            return ponto(linha[i - 1].first + (linha[i].first - linha[i - 1].first) * t, linha[i - 1].second + (linha[i].second - linha[i - 1].second) * t)
        }
        val d = paragensM.lastIndex
        val paragens = paragensM.mapIndexed { k, m ->
            val paradas = if (k == d) (k - 1).coerceAtLeast(0) else k
            val (lat, lon) = em(m)
            ParagemEtapa("p$k", "Paragem $k", lat, lon, aos(partidaS + m / vMs + paradas * paragemS))
        }
        fun lugarDe(p: ParagemEtapa) = Lugar(p.nome, p.lat, p.lon, TipoLugar.PARAGEM)
        return Etapa(
            aPe = false, modo = modo, linhaNome = "736", linhaId = "736", destino = "Fim",
            de = lugarDe(paragens.first()), para = lugarDe(paragens.last()), partida = paragens.first().hora,
            chegada = paragens.last().hora, partidaProgramada = paragens.first().hora, aoVivo = false,
            paragensIntermedias = d - 1, desenho = if (comDesenho) linha.map { ponto(it.first, it.second) } else emptyList(),
            cor = null, idViagem = "v1", paragens = paragens,
        )
    }

    private fun itinerario(vararg etapas: Etapa) =
        Itinerario(etapas.toList(), etapas.first().partida, etapas.last().chegada, (etapas.count { !it.aPe } - 1).coerceAtLeast(0))

    // Autocarro numa recta de 3 km para leste, paragens a cada 600 m (a de saída é a 5).
    private val recta = listOf(0.0 to 0.0, 3000.0 to 0.0)
    private fun autocarro(partidaS: Double = 60.0) = veiculo(partidaS, recta, listOf(0.0, 600.0, 1200.0, 1800.0, 2400.0, 3000.0))

    /** À espera na primeira paragem desde o início e depois no veículo, que parte a partidaS. */
    private fun tracoVeiculo(etapa: Etapa, partidaS: Double = 60.0, vMs: Double = 7.0, ruidoM: Double = 0.0, saltos: Double = 0.0, buraco: IntRange? = null, semente: Int = 1) =
        Tracos.parado(etapa.paragens[0].lat, etapa.paragens[0].lon, 0.0, partidaS) +
            Tracos.sintetico(etapa, vMs, ruidoM = ruidoM, saltos = saltos, buraco = buraco, semente = semente).mais(partidaS)

    private fun eventos(passos: List<Passo>) = passos.flatMap { it.eventos }
    private fun com(passos: List<Passo>, evento: Evento) = passos.first { evento in it.eventos }

    @Test fun autocarroLimpo() {
        val etapa = autocarro()
        val viagem = Viagem(itinerario(etapa))
        val posicoes = tracoVeiculo(etapa)
        val passos = Tracos.correr(viagem, t0, posicoes)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        verificarAvisos(viagem, passos, t0, posicoes)
        // Prepara-te na primeira posição depois de sair da antepenúltima (PREPARA_ANTES = 2), posições a cada 14 m.
        val a = Percurso(etapa.desenho, etapa.paragens).noPercurso
        val prepara = com(passos, PREPARA_TE).depois.progresso
        assertTrue(prepara > a[a.lastIndex - 2] + 30 && prepara <= a[a.lastIndex - 2] + 30 + 14)
        val sai = com(passos, SAI_AGORA).entrada as Entrada.Gps
        val destino = etapa.paragens.last()
        assertTrue(distanciaMetros(sai.lat, sai.lon, destino.lat, destino.lon) <= 150.0)
    }

    @Test fun ruidoESaltos() {
        val etapa = autocarro()
        val viagem = Viagem(itinerario(etapa))
        val d = etapa.paragens.lastIndex
        for (semente in 1..10) {
            val passos = Tracos.correr(viagem, t0, tracoVeiculo(etapa, ruidoM = 25.0, saltos = 0.05, semente = semente))
            passos.forEach { assertTrue("semente $semente", viagem.faltam(it.depois) <= viagem.faltam(it.antes)) }
            assertEquals("semente $semente", listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
            val sai = com(passos, SAI_AGORA).entrada.hora
            // Depois de o autocarro sair da penúltima e antes de chegar ao destino.
            assertTrue("semente $semente", sai > etapa.paragens[d - 1].hora)
            assertTrue("semente $semente", sai < etapa.paragens[d].hora)
        }
    }

    @Test fun penultimaACemMetrosDoDestino() {
        val etapa = veiculo(60.0, listOf(0.0 to 0.0, 1300.0 to 0.0), listOf(0.0, 600.0, 1200.0, 1300.0))
        val viagem = Viagem(itinerario(etapa))
        val posicoes = tracoVeiculo(etapa)
        val passos = Tracos.correr(viagem, t0, posicoes)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        val sai = com(passos, SAI_AGORA)
        assertEquals(listOf(SAI_AGORA), sai.eventos)
        assertTrue(sai.entrada.hora > etapa.paragens[2].hora)
        verificarAvisos(viagem, passos, t0, posicoes)
    }

    @Test fun posicoesImprecisasIgnoradas() {
        val etapa = autocarro()
        val viagem = Viagem(itinerario(etapa))
        val posicoes = Tracos.parado(etapa.paragens[0].lat, etapa.paragens[0].lon, 0.0, 60.0) +
            Tracos.sintetico(etapa).mais(60.0).map { it.copy(precisao = 150f) }
        val passos = Tracos.correr(viagem, t0, posicoes)
        val imprecisas = passos.filter { (it.entrada as? Entrada.Gps)?.precisaoM == 150f }
        assertTrue(imprecisas.isNotEmpty())
        imprecisas.forEach {
            assertEquals(it.antes.progresso, it.depois.progresso, 0.0)
            assertEquals(it.antes.ultimoGps, it.depois.ultimoGps)
        }
        assertTrue(passos.any { it.depois.origem == Origem.HORARIO })
    }

    // Metro de 3 km sem desenho, estações a cada 1 km, parte aos 120 s e chega aos 360 s.
    private fun metro() = veiculo(120.0, recta, listOf(0.0, 1000.0, 2000.0, 3000.0), Modo.METRO, vMs = 15.0, comDesenho = false)

    @Test fun metroSemPosicoes() {
        val etapa = metro()
        val viagem = Viagem(itinerario(etapa))
        val passos = Tracos.correr(viagem, t0, emptyList())
        assertEquals(aos(60.0), passos.first { it.depois.origem == Origem.HORARIO }.entrada.hora)
        assertEquals(etapa.partida, passos.first { it.depois.fase == Fase.A_BORDO }.entrada.hora)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        assertEquals(etapa.chegada.minusSeconds(60), com(passos, SAI_AGORA).entrada.hora)
        assertTrue(com(passos, PREPARA_TE).entrada.hora <= etapa.chegada.minusSeconds(120))
        assertEquals(etapa.chegada.plusSeconds(60), com(passos, CHEGOU).entrada.hora)
    }

    @Test fun tunelEDepoisGps() {
        val etapa = veiculo(60.0, listOf(0.0 to 0.0, 3200.0 to 0.0), listOf(0.0, 800.0, 1600.0, 2400.0, 3200.0), vMs = 10.0)
        val viagem = Viagem(itinerario(etapa))
        val passos = Tracos.correr(viagem, t0, tracoVeiculo(etapa, vMs = 10.0, buraco = 60..200))
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        val horario = passos.indexOfFirst { it.depois.origem == Origem.HORARIO }
        assertTrue(horario > 0)
        assertTrue(passos.drop(horario).any { it.entrada is Entrada.Gps && it.depois.origem == Origem.GPS })
        assertEquals(Origem.GPS, com(passos, SAI_AGORA).depois.origem)
    }

    @Test fun partidaAtrasadaDesloca() {
        val etapa = autocarro(partidaS = 120.0)
        val viagem = Viagem(itinerario(etapa))
        val prevista = etapa.partida.plusSeconds(240)
        val passos = Tracos.correr(viagem, t0, emptyList(), listOf(Entrada.Partida(0, prevista, aos(10.0))))
        val depoisDaPartida = passos.first { it.entrada is Entrada.Partida }.depois
        assertEquals(240L, depoisDaPartida.atrasoS)
        assertEquals(etapa.chegada.plusSeconds(240), viagem.eta(depoisDaPartida))
        assertEquals(prevista, passos.first { it.depois.fase == Fase.A_BORDO }.entrada.hora)
        // Aos 400 s o autocarro vai onde o horário o punha aos 160 s.
        val aos400 = passos.first { it.entrada is Entrada.Relogio && it.entrada.hora == aos(400.0) }.depois
        assertEquals(Percurso(etapa.desenho, etapa.paragens).progressoEm(aos(160.0)), aos400.progresso, 1.0)
    }

    @Test fun etapaDeUmaParagem() {
        val etapa = veiculo(60.0, listOf(0.0 to 0.0, 800.0 to 0.0), listOf(0.0, 800.0))
        val viagem = Viagem(itinerario(etapa))
        val posicoes = tracoVeiculo(etapa)
        val passos = Tracos.correr(viagem, t0, posicoes)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        assertTrue(PREPARA_TE in passos.first { it.depois.fase == Fase.A_BORDO }.eventos)
        assertTrue(viagem.inicio(t0).altaPrecisao)
        assertTrue(passos.all { it.depois.altaPrecisao })
        verificarAvisos(viagem, passos, t0, posicoes)
    }

    @Test fun lacoQuePassaPertoDoDestino() {
        // Passa a 80 m do destino aos 400 m e só chega a ele aos 1480 m (o laço do PercursoTest).
        val laco = listOf(80.0 to 0.0, 80.0 to 600.0, -300.0 to 600.0, -300.0 to 400.0, 0.0 to 400.0)
        val etapa = veiculo(60.0, laco, listOf(0.0, 600.0, 980.0, 1480.0))
        val viagem = Viagem(itinerario(etapa))
        val posicoes = tracoVeiculo(etapa)
        val passos = Tracos.correr(viagem, t0, posicoes)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
        assertTrue(com(passos, PREPARA_TE).entrada.hora > etapa.paragens[1].hora)
        assertTrue(com(passos, SAI_AGORA).entrada.hora > etapa.paragens[2].hora)
        verificarAvisos(viagem, passos, t0, posicoes)
    }

    @Test fun aPeAutocarroAPeMetroAPe() {
        val ida = aPe(0.0, 0.0 to -300.0, 0.0 to 0.0)
        val autocarro = veiculo(300.0, listOf(0.0 to 0.0, 2000.0 to 0.0), listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0))
        val transbordo = aPe(650.0, 2000.0 to 0.0, 2000.0 to 200.0)
        val metro = veiculo(900.0, listOf(2000.0 to 200.0, 5000.0 to 200.0), listOf(0.0, 1000.0, 2000.0, 3000.0), Modo.METRO, vMs = 15.0, comDesenho = false)
        val fim = aPe(1150.0, 5000.0 to 200.0, 5000.0 to 500.0)
        val viagem = Viagem(itinerario(ida, autocarro, transbordo, metro, fim))
        val estacao = metro.paragens[0]
        // Entra na estação aos 830 s e só volta a ter GPS à saída, aos 1160 s.
        val posicoes = Tracos.sintetico(ida, 1.4, 0) +
            Tracos.parado(autocarro.paragens[0].lat, autocarro.paragens[0].lon, 216.0, 300.0) +
            Tracos.sintetico(autocarro).mais(300.0) +
            Tracos.sintetico(transbordo, 1.4, 0).mais(648.0) +
            Tracos.parado(estacao.lat, estacao.lon, 792.0, 830.0) +
            Tracos.sintetico(fim, 1.4, 0).mais(1160.0)
        val passos = Tracos.correr(viagem, t0, posicoes)
        assertEquals(
            listOf(ETAPA_SEGUINTE, PREPARA_TE, SAI_AGORA, ETAPA_SEGUINTE, ETAPA_SEGUINTE, PREPARA_TE, SAI_AGORA, ETAPA_SEGUINTE, CHEGOU),
            eventos(passos),
        )
        assertTrue(passos.last().depois.terminada)
        assertEquals(Fase.TERMINADA, passos.last().depois.fase)
        val saiMetro = passos.filter { SAI_AGORA in it.eventos }[1]
        assertEquals(3, saiMetro.antes.etapa)
        assertEquals(Origem.HORARIO, saiMetro.depois.origem)
        verificarAvisos(viagem, passos, t0, posicoes)
    }

    @Test fun andarAPeAoLongoDaLinhaNaoEmbarca() {
        val etapa = autocarro(partidaS = 1200.0)
        val viagem = Viagem(itinerario(etapa))
        val posicoes = Tracos.parado(etapa.paragens[0].lat, etapa.paragens[0].lon, 0.0, 30.0) +
            Tracos.sintetico(etapa, vMs = 1.4, paragemS = 0).mais(30.0).filter { it.s < 600 }
        val passos = Tracos.correr(viagem, t0, posicoes).filter { it.entrada.hora < aos(600.0) }
        assertTrue(passos.last().depois.progresso > 600.0)
        assertTrue(passos.all { it.depois.fase == Fase.ESPERA })
        assertTrue(eventos(passos).isEmpty())
    }

    @Test fun jaEmbarcouSemPassarPelaParagem() {
        // Fica a 80 m da paragem (do outro lado da rua) e as primeiras posições já são no autocarro.
        val ida = aPe(0.0, 0.0 to -300.0, 0.0 to 0.0)
        val etapa = autocarro(partidaS = 300.0)
        val viagem = Viagem(itinerario(ida, etapa))
        val longe = ponto(0.0, -80.0)
        val posicoes = Tracos.sintetico(ida, 1.4, 0).filter { it.s < 157 } +
            Tracos.parado(longe.first, longe.second, 158.0, 300.0) +
            Tracos.sintetico(etapa).mais(300.0).filter { it.s >= 312 }
        val passos = Tracos.correr(viagem, t0, posicoes)
        val salto = com(passos, ETAPA_SEGUINTE)
        assertEquals(Fase.A_PE, salto.antes.fase)
        assertEquals(Fase.A_BORDO, salto.depois.fase)
        assertEquals(listOf(ETAPA_SEGUINTE, PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
    }

    @Test fun chegarAPeAoLongoDaLinhaNaoEmbarca() {
        // Vem a pé pela rua do autocarro, no sentido contrário: passa por cima da linha antes de chegar à paragem.
        val ida = aPe(0.0, 400.0 to 0.0, 0.0 to 0.0)
        val etapa = autocarro(partidaS = 400.0)
        val viagem = Viagem(itinerario(ida, etapa))
        val passos = Tracos.correr(viagem, t0, Tracos.sintetico(ida, 1.4, 0))
        val chegou = com(passos, ETAPA_SEGUINTE)
        assertEquals(Fase.ESPERA, chegou.depois.fase)
        val g = chegou.entrada as Entrada.Gps
        assertTrue(distanciaMetros(g.lat, g.lon, ida.para.lat, ida.para.lon) <= 50.0)
    }

    @Test fun jaSaiAMeioDaEtapa() {
        val etapa = autocarro()
        val depois = aPe(570.0, 3000.0 to 0.0, 3000.0 to 300.0)
        val viagem = Viagem(itinerario(etapa, depois))
        val passos = Tracos.correr(viagem, t0, tracoVeiculo(etapa).filter { it.s < 300 }, listOf(Entrada.JaSai(aos(300.0))))
        val ja = passos.first { it.entrada is Entrada.JaSai }
        assertEquals(Fase.A_BORDO, ja.antes.fase)
        assertEquals(listOf(SAI_AGORA, ETAPA_SEGUINTE), ja.eventos)
        assertEquals(1, ja.depois.etapa)
        assertEquals(Fase.A_PE, ja.depois.fase)
        assertTrue(ja.depois.avisados.isEmpty())
    }

    @Test fun jaSaiNaUltimaEtapa() {
        val etapa = autocarro()
        val viagem = Viagem(itinerario(etapa))
        val posicoes = tracoVeiculo(etapa)
        val sai = com(Tracos.correr(viagem, t0, posicoes), SAI_AGORA).entrada.hora
        val passos = Tracos.correr(viagem, t0, posicoes, listOf(Entrada.JaSai(sai.plusSeconds(3))))
        val ja = passos.first { it.entrada is Entrada.JaSai }
        assertEquals(listOf(CHEGOU), ja.eventos)
        assertTrue(ja.depois.terminada)
        assertEquals(listOf(PREPARA_TE, SAI_AGORA, CHEGOU), eventos(passos))
    }

    @Test fun retomarDepoisDeGravarNaoRepeteAvisos() {
        val etapa = autocarro()
        val plano = PlanoViagem(itinerario(etapa), som = true, vibrar = false)
        val posicoes = tracoVeiculo(etapa)
        val prepara = com(Tracos.correr(Viagem(plano.itinerario), t0, posicoes), PREPARA_TE)
        val estado = Json.decodeFromString<EstadoViagem>(Json.encodeToString(prepara.depois))
        assertEquals(prepara.depois, estado)
        val lido = Json.decodeFromString<PlanoViagem>(Json.encodeToString(plano))
        assertEquals(plano, lido)
        // O Android fechou o serviço; retoma 2 min depois.
        val retoma = Duration.between(t0, prepara.entrada.hora).toMillis() / 1000.0 + 120
        val passos = Tracos.correr(Viagem(lido.itinerario), aos(retoma), posicoes.filter { it.s > retoma }.mais(-retoma), estado = estado)
        assertEquals(listOf(SAI_AGORA, CHEGOU), eventos(passos))
    }

    @Test fun posicaoForaDaLinhaIgnorada() {
        val etapa = autocarro()
        val viagem = Viagem(itinerario(etapa))
        val (latLado, lonLado) = ponto(850.0, 80.0)     // onde vai o autocarro, mas 80 m ao lado
        val (latFim, lonFim) = ponto(2900.0, 200.0)     // perto do destino, 200 m ao lado
        val posicoes = tracoVeiculo(etapa).filter { it.s < 200 } + Posicao(201.0, latLado, lonLado) + Posicao(203.0, latFim, lonFim)
        val passos = Tracos.correr(viagem, t0, posicoes)
        listOf(aos(201.0), aos(203.0)).forEach { h ->
            val p = passos.first { it.entrada is Entrada.Gps && it.entrada.hora == h }
            assertEquals(Fase.A_BORDO, p.antes.fase)
            assertEquals(p.antes.progresso, p.depois.progresso, 0.0)
            assertEquals(p.antes.ultimoGps, p.depois.ultimoGps)
            assertTrue(p.eventos.isEmpty())
        }
    }

    @Test fun altaPrecisao() {
        val etapa = autocarro()
        val depois = aPe(570.0, 3000.0 to 0.0, 3000.0 to 300.0)
        val viagem = Viagem(itinerario(etapa, depois))
        val passos = Tracos.correr(viagem, t0, tracoVeiculo(etapa) + Tracos.sintetico(depois, 1.4, 0).mais(572.0))
        val a = Percurso(etapa.desenho, etapa.paragens).noPercurso
        val i = passos.indexOfFirst { it.depois.altaPrecisao }
        val liga = passos[i]
        assertFalse(liga.antes.altaPrecisao)
        assertEquals(Fase.A_BORDO, liga.depois.fase)
        // Liga ao sair da antepenúltima (aqui antes dos 3 min do fim) e fica até ao fim da etapa.
        assertTrue(liga.depois.progresso > a[3] + 30)
        assertTrue(passos.drop(i).filter { it.depois.etapa == 0 }.all { it.depois.altaPrecisao })
        assertFalse(passos.first { it.depois.etapa == 1 }.depois.altaPrecisao)
        // No Metro sem GPS liga-se aos 3 min do fim (360 s), antes de sair da antepenúltima.
        val metro = Tracos.correr(Viagem(itinerario(metro())), t0, emptyList())
        assertEquals(aos(180.0), metro.first { it.depois.altaPrecisao }.entrada.hora)
    }

    @Test fun guardaAChegadaMaisMeiaHora() {
        val etapa = aPe(0.0, 0.0 to 0.0, 0.0 to 1000.0)
        val viagem = Viagem(itinerario(etapa))
        val limite = etapa.chegada.plus(Duration.ofMinutes(30))
        val (lat, lon) = ponto(0.0, 100.0)
        val parado = viagem.passo(viagem.inicio(t0), Entrada.Gps(lat, lon, 8f, limite.minusSeconds(30))).first
        val (aindaNao, _) = viagem.passo(parado, Entrada.Relogio(limite))
        assertFalse(aindaNao.terminada)
        val (fim, eventos) = viagem.passo(aindaNao, Entrada.Relogio(limite.plusSeconds(1)))
        assertTrue(fim.terminada)
        assertEquals(Fase.TERMINADA, fim.fase)
        assertTrue(eventos.isEmpty())
        assertEquals(fim to emptyList<Evento>(), viagem.passo(fim, Entrada.Relogio(limite.plusSeconds(60))))
    }

    @Test fun gravacoesReais() {
        // Paragem Certa/gravacoes, fora do repositório: só existe no PC do autor.
        val pasta = File(System.getProperty("user.dir"), "../../gravacoes").canonicalFile
        val csvs = pasta.listFiles { f -> f.extension == "csv" }.orEmpty().sortedBy { it.name }
        Assume.assumeTrue("sem gravações em $pasta", csvs.isNotEmpty())
        for (csv in csvs) {
            val plano = Json.decodeFromString<PlanoViagem>(File(pasta, "${csv.nameWithoutExtension}-plano.json").readText())
            val (inicio, posicoes) = Tracos.ler(csv)
            val viagem = Viagem(plano.itinerario)
            verificarAvisos(viagem, Tracos.correr(viagem, inicio, posicoes), inicio, posicoes, csv.name)
        }
    }

    /**
     * O que a spec pede em cada etapa em veículo a que a viagem chegou: Prepara-te e depois Sai agora, uma vez cada;
     * Prepara-te antes da primeira posição a 30 m da penúltima paragem e Sai agora antes da primeira a 20 m do
     * destino (contando só as posições depois do embarque).
     */
    private fun verificarAvisos(viagem: Viagem, passos: List<Passo>, inicio: Instant, posicoes: List<Posicao>, nome: String = "") {
        viagem.plano.etapas.forEachIndexed { i, etapa ->
            val daEtapa = passos.filter { it.antes.etapa == i }
            if (etapa.aPe || daEtapa.isEmpty()) return@forEachIndexed
            val avisos = daEtapa.flatMap { p -> p.eventos.filter { it == PREPARA_TE || it == SAI_AGORA }.map { it to p.entrada.hora } }
            assertEquals("$nome etapa $i", listOf(PREPARA_TE, SAI_AGORA), avisos.map { it.first })
            val (prepara, sai) = avisos.map { it.second }
            val embarque = daEtapa.first { it.depois.fase == Fase.A_BORDO || it.depois.etapa != i }.entrada.hora
            val depois = posicoes.map { inicio.plusMillis((it.s * 1000).roundToLong()) to it }.filter { it.first >= embarque }
            fun primeiraA(metros: Double, p: ParagemEtapa) =
                depois.firstOrNull { (_, x) -> distanciaMetros(x.lat, x.lon, p.lat, p.lon) <= metros }?.first
            val paragens = Tracos.paragensDe(etapa)
            primeiraA(30.0, paragens[paragens.lastIndex - 1])?.let { assertTrue("$nome etapa $i: Prepara-te tarde", prepara <= it) }
            primeiraA(20.0, paragens.last())?.let { assertTrue("$nome etapa $i: Sai agora tarde", sai <= it) }
        }
    }
}
