@file:UseSerializers(InstanteSerializer::class)

package pt.pixellusitano.paragemcerta.core.viagem

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pt.pixellusitano.paragemcerta.core.modelo.Etapa
import pt.pixellusitano.paragemcerta.core.modelo.InstanteSerializer
import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.ParagemEtapa
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

// Posições aceites
private const val PRECISAO_MAX_M = 75f
private const val AFASTAMENTO_MIN_M = 50.0
private const val RECUO_M = 50.0
private const val AVANCO_M = 100.0
private const val VMAX_AUTOCARRO = 25.0
private const val VMAX_CARRIL = 35.0
private val SEM_GPS = Duration.ofSeconds(60)

// Paragens e embarque
private const val SAIU_M = 30.0
private const val EMBARQUE_M = 60.0
private const val EMBARQUE_MS = 3.0
private const val CHEGOU_A_PE_M = 50.0

// O veículo da etapa (vehicles da Carris Metropolitana)
/** Na paragem k, o veículo já saiu da k − 1: P passa a A[k − 1] + 31, um metro além do saiu(). */
private const val VEICULO_SAIU_M = SAIU_M + 1
/** Com GPS recente, o veículo não pode ir mais à frente da pessoa do que isto mais a velocidade × a idade do GPS. */
private const val VEICULO_PERTO_M = 150.0

// Avisos
/** "Prepara-te" ao sair da paragem duas antes do destino (a antepenúltima); decisão do autor de 01/11/2026. */
const val PREPARA_ANTES = 2
private val PREPARA_TEMPO = Duration.ofMinutes(2)
private const val SAI_M = 150.0
private const val SAI_S = 15.0
private const val SAI_MARGEM_M = PRECISAO_MAX_M.toDouble()
private val SAI_TEMPO = Duration.ofSeconds(60)
private const val ALTA_PARAGENS = 2
private val ALTA_TEMPO = Duration.ofMinutes(3)
private const val FIM_M = 30.0
private val FIM_HORARIO = Duration.ofSeconds(60)
private val GUARDA = Duration.ofMinutes(30)
/** O atraso que a guarda conta no máximo: o mesmo teto do wake lock do serviço. */
private val ATRASO_MAX_S = Duration.ofHours(2).seconds

/** O que chega ao motor; cada entrada traz a sua hora, por isso o motor não lê o relógio. */
sealed interface Entrada {
    val hora: Instant
    data class Gps(val lat: Double, val lon: Double, val precisaoM: Float, override val hora: Instant) : Entrada
    data class Relogio(override val hora: Instant) : Entrada
    /** A hora a que o veículo da etapa vai partir da paragem de embarque, segundo o tempo real. */
    data class Partida(val etapa: Int, val prevista: Instant, override val hora: Instant) : Entrada
    data class JaSai(override val hora: Instant) : Entrada
    data class Silenciar(override val hora: Instant) : Entrada
    /**
     * O veículo da etapa (Carris Metropolitana), já escolhido pelo serviço: a caminho, a chegar ou parado na paragem de
     * índice paragemIndice da etapa; aChegar se a chegar ou parado. lat, lon: onde ele está (o mapa da viagem desenha-o).
     */
    data class Veiculo(
        val etapa: Int, val paragemIndice: Int, val aChegar: Boolean, override val hora: Instant,
        val lat: Double? = null, val lon: Double? = null,
    ) : Entrada
}

enum class Fase { A_PE, ESPERA, A_BORDO, TERMINADA }
enum class Origem { GPS, HORARIO, VEICULO }
enum class Evento { PREPARA_TE, SAI_AGORA, ETAPA_SEGUINTE, CHEGOU }

/**
 * O estado da viagem, tal e qual o trip-state.json.
 * progresso: numa etapa em veículo, metros ao longo da linha (nunca anda para trás); a pé, metros em linha reta
 * que faltam até ao fim da etapa. velocidade: m/s ao longo da linha (a pé, a aproximar-se ou afastar-se do fim).
 * ultimoGps: a hora da última posição aceite; no início, a da viagem (o GPS tem 60 s para arrancar).
 * avisados: os avisos já dados na etapa atual.
 * avancou: à espera, a última posição aceite levou P para a frente e para lá de A[0] + 60 (meio embarque).
 * ultimoVeiculo: a hora da última leitura aceite do veículo da etapa (Carris Metropolitana).
 * origem: VEICULO quando, sem GPS há 60 s, as leituras do veículo continuam a chegar.
 * veiculoLat, veiculoLon: onde o veículo da etapa estava na última leitura aceite (o mapa desenha-o); null sem leitura
 * há 60 s e ao mudar de etapa.
 * relatosAvisados: os ids dos relatos da comunidade de que a viagem já avisou (o motor não lhes toca).
 */
@Serializable
data class EstadoViagem(
    val etapa: Int, val fase: Fase, val progresso: Double, val atrasoS: Long,
    val velocidade: Double, val origem: Origem, val ultimoGps: Instant?, val avisados: Set<Evento>,
    val altaPrecisao: Boolean, val silenciado: Boolean, val terminada: Boolean, val atualizado: Instant,
    val avancou: Boolean = false, val ultimoVeiculo: Instant? = null,
    val veiculoLat: Double? = null, val veiculoLon: Double? = null,
    val relatosAvisados: Set<String> = emptySet(),
)

/**
 * O trip-plan.json: a rota escolhida e as definições do aviso (o processo :trip não lê as definições da app).
 * avisosComunidade: avisar dos relatos confirmados nas linhas da viagem; ocultos: os relatos que a pessoa escondeu ("relato:<id>").
 * Os dois têm omissão: o plano de uma viagem da 0.3.x lê-se sem eles.
 */
@Serializable
data class PlanoViagem(
    val itinerario: Itinerario, val som: Boolean, val vibrar: Boolean,
    val avisosComunidade: Boolean = false, val ocultos: Set<String> = emptySet(),
)

/**
 * O motor da viagem: recebe posições e o relógio, devolve o estado novo e os avisos a dar. Puro, sem Android.
 * Na etapa em veículo, d é a paragem de saída, A[k] os metros da paragem k na linha e P o progresso.
 */
class Viagem(val plano: Itinerario) {
    init {
        require(plano.etapas.isNotEmpty()) { "uma viagem precisa de pelo menos uma etapa" }
    }

    private val paragens: List<List<ParagemEtapa>> = plano.etapas.map(::paragensDe)
    private val percursos: List<Percurso?> = plano.etapas.mapIndexed { i, etapa -> if (etapa.aPe) null else Percurso(etapa.desenho, paragens[i]) }

    fun inicio(agora: Instant): EstadoViagem = naEtapa(
        EstadoViagem(0, Fase.A_PE, 0.0, 0, 0.0, Origem.GPS, agora, emptySet(), false, false, false, agora), 0, agora,
    )

    fun passo(e: EstadoViagem, x: Entrada): Pair<EstadoViagem, List<Evento>> {
        if (e.terminada) return e to emptyList()
        if (x.hora > limiteDaViagem(plano, e)) return e.copy(fase = Fase.TERMINADA, terminada = true, atualizado = x.hora) to emptyList()
        if (x is Entrada.Silenciar) return e.copy(silenciado = true) to emptyList()
        val s = e.copy(atualizado = x.hora)
        return if (percursos[s.etapa] == null) passoAPe(s, x) else passoVeiculo(s, x)
    }

    /** A última paragem de que o veículo já saiu (0 enquanto não sai da primeira, e a pé). */
    fun paragemPassada(e: EstadoViagem): Int {
        val a = percursos[e.etapa]?.noPercurso ?: return 0
        return a.indices.lastOrNull { e.progresso > a[it] + SAIU_M } ?: 0
    }

    /** As paragens que faltam até à de saída (0 a pé). */
    fun faltam(e: EstadoViagem): Int = if (percursos[e.etapa] == null) 0 else paragens[e.etapa].lastIndex - paragemPassada(e)

    /** A hora prevista de chegar ao fim da etapa: em veículo, o horário da paragem de saída mais o atraso. */
    fun eta(e: EstadoViagem): Instant =
        if (percursos[e.etapa] == null) plano.etapas[e.etapa].chegada else paragens[e.etapa].last().hora.plusSeconds(e.atrasoS)

    private fun passoAPe(e: EstadoViagem, x: Entrada): Pair<EstadoViagem, List<Evento>> {
        val etapa = plano.etapas[e.etapa]
        if (x is Entrada.Gps && x.precisaoM <= PRECISAO_MAX_M && maisNova(x, e)) {
            val dt = segundos(e.ultimoGps, x.hora)
            val falta = distanciaMetros(x.lat, x.lon, etapa.para.lat, etapa.para.lon)
            val s = e.copy(progresso = falta, velocidade = media(e.velocidade, abs(e.progresso - falta), dt), ultimoGps = x.hora, origem = Origem.GPS)
            if (falta <= CHEGOU_A_PE_M) return fimDeEtapa(s, x.hora, emptyList())
            // Já vai no veículo da etapa seguinte sem ter passado a 50 m da paragem: salta a espera. A velocidade
            // separa-o de quem chega à paragem a pé pela rua da linha, no sentido contrário.
            val seguinte = percursos.getOrNull(e.etapa + 1) ?: return s to emptyList()
            val a0 = seguinte.noPercurso[0]
            val p = projetar(seguinte, plano.etapas[e.etapa + 1].modo, a0, x, dt)
            if (p == null || p.ao <= a0 + EMBARQUE_M || s.velocidade <= EMBARQUE_MS) return s to emptyList()
            val (novaEtapa, eventos) = fimDeEtapa(s, x.hora, emptyList())
            val aBordo = novaEtapa.copy(fase = Fase.A_BORDO, progresso = p.ao, atrasoS = segundosEntre(seguinte.horaEm(p.ao), x.hora))
            return avaliar(aBordo, x.hora, jaSai = false, eventos)
        }
        if (semGps(e, x.hora) && x.hora >= etapa.chegada.plusSeconds(e.atrasoS)) return fimDeEtapa(e, x.hora, emptyList())
        return e to emptyList()
    }

    private fun passoVeiculo(e: EstadoViagem, x: Entrada): Pair<EstadoViagem, List<Evento>> {
        val percurso = percursos[e.etapa]!!
        val a = percurso.noPercurso
        val hora0 = paragens[e.etapa][0].hora
        var s = e
        var aceite = false
        var chega = false
        when (x) {
            is Entrada.Gps -> aceitar(s, x, percurso)?.let { s = it; aceite = true }
            is Entrada.Partida -> if (x.etapa == s.etapa && s.fase == Fase.ESPERA) s = s.copy(atrasoS = segundosEntre(hora0, x.prevista))
            is Entrada.Veiculo -> lerVeiculo(s, x, a)?.let { s = it; chega = x.paragemIndice == a.lastIndex && x.aChegar }
            else -> {}
        }
        if (!aceite && semGps(s, x.hora)) s = s.copy(origem = if (semVeiculo(s, x.hora)) Origem.HORARIO else Origem.VEICULO)
        // O veículo que deixou de chegar sai do mapa.
        if (s.veiculoLat != null && semVeiculo(s, x.hora)) s = s.copy(veiculoLat = null, veiculoLon = null)
        if (s.fase == Fase.ESPERA && s.origem == Origem.HORARIO && x.hora >= hora0.plusSeconds(s.atrasoS)) s = s.copy(fase = Fase.A_BORDO)
        if (x is Entrada.Relogio && s.fase == Fase.A_BORDO && s.origem == Origem.HORARIO) {
            s = s.copy(progresso = max(s.progresso, percurso.progressoEm(x.hora.minusSeconds(s.atrasoS))))
        }
        if (aceite && s.fase == Fase.ESPERA) {
            // Embarca com duas posições aceites seguidas a levar P para a frente, para lá de A[0] + 60: um salto
            // do ruído de quem espera parado na paragem não chega.
            val avancou = s.progresso > e.progresso && s.progresso > a[0] + EMBARQUE_M
            s = s.copy(avancou = avancou)
            if (avancou && e.avancou && s.velocidade > EMBARQUE_MS) {
                s = s.copy(fase = Fase.A_BORDO, atrasoS = segundosEntre(percurso.horaEm(s.progresso), x.hora))
            }
        }
        return avaliar(s, x.hora, jaSai = x is Entrada.JaSai, emptyList(), chega)
    }

    /**
     * Uma leitura do veículo da etapa, a bordo: na paragem k (a caminho, a chegar ou parado) já saiu da k − 1, e o P só
     * anda para a frente. À espera não conta: o autocarro pode partir sem a pessoa. Com GPS recente, um veículo que já
     * saiu de uma paragem mais à frente do que a pessoa pode estar é outro (o da viagem que ela perdeu, ou um mal
     * escolhido) e também não conta.
     */
    private fun lerVeiculo(e: EstadoViagem, x: Entrada.Veiculo, a: DoubleArray): EstadoViagem? {
        val k = x.paragemIndice
        if (x.etapa != e.etapa || e.fase != Fase.A_BORDO || k !in 1..a.lastIndex) return null
        if (!semGps(e, x.hora) && a[k - 1] > e.progresso + VEICULO_PERTO_M + e.velocidade * segundos(e.ultimoGps, x.hora)) return null
        return e.copy(progresso = max(e.progresso, a[k - 1] + VEICULO_SAIU_M), ultimoVeiculo = x.hora, veiculoLat = x.lat, veiculoLon = x.lon)
    }

    /** A posição na linha, se for de confiança; o novo P, a velocidade e o atraso. */
    private fun aceitar(e: EstadoViagem, x: Entrada.Gps, percurso: Percurso): EstadoViagem? {
        if (x.precisaoM > PRECISAO_MAX_M || !maisNova(x, e)) return null
        val dt = segundos(e.ultimoGps, x.hora)
        val p = projetar(percurso, plano.etapas[e.etapa].modo, e.progresso, x, dt) ?: return null
        val novo = max(e.progresso, p.ao)
        val atraso = segundosEntre(percurso.horaEm(novo), x.hora)
        return e.copy(
            progresso = novo, velocidade = media(e.velocidade, novo - e.progresso, dt), ultimoGps = x.hora, origem = Origem.GPS,
            // À espera, uma posição só pode dizer que o veículo ainda não partiu: o atraso só sobe.
            atrasoS = if (e.fase == Fase.A_BORDO) atraso else max(e.atrasoS, segundosEntre(paragens[e.etapa][0].hora, x.hora)),
        )
    }

    /** Onde cai x na janela [P − 50, P + 100 + VMAX·Δt] da linha; null se ficar longe dela. */
    private fun projetar(percurso: Percurso, modo: Modo, p: Double, x: Entrada.Gps, dt: Double): Projecao? {
        val vmax = if (modo == Modo.METRO || modo == Modo.COMBOIO) VMAX_CARRIL else VMAX_AUTOCARRO
        val proj = percurso.projetar(x.lat, x.lon, p - RECUO_M, p + AVANCO_M + vmax * dt) ?: return null
        return proj.takeIf { it.afastamento <= max(AFASTAMENTO_MIN_M, x.precisaoM.toDouble()) }
    }

    /** Os avisos e o fim da etapa em veículo, pela posição (P) e pela hora. */
    private fun avaliar(e: EstadoViagem, agora: Instant, jaSai: Boolean, antes: List<Evento>, chega: Boolean = false): Pair<EstadoViagem, List<Evento>> {
        val a = percursos[e.etapa]!!.noPercurso
        val d = a.lastIndex
        val eta = eta(e)
        val falta = Duration.between(agora, eta)
        fun saiu(k: Int) = e.progresso > a[k.coerceAtLeast(0)] + SAIU_M
        val horario = e.origem == Origem.HORARIO
        var s = e.copy(altaPrecisao = e.altaPrecisao || saiu(d - ALTA_PARAGENS) || falta <= ALTA_TEMPO || d <= ALTA_PARAGENS)
        val eventos = antes.toMutableList()
        // Depois de "Já saí" não se toca nada: só se muda de etapa.
        if (s.fase == Fase.A_BORDO && !jaSai) {
            if (Evento.PREPARA_TE !in s.avisados && (saiu(d - PREPARA_ANTES) || falta <= PREPARA_TEMPO)) eventos += Evento.PREPARA_TE
            // Mais margem que o saiu(): parado na penúltima, o ruído leva o P (que só anda para a frente) além de A[d − 1] + 30.
            val perto = s.progresso > a[(d - 1).coerceAtLeast(0)] + SAI_MARGEM_M && a[d] - s.progresso <= max(SAI_M, s.velocidade * SAI_S)
            // chega: o veículo da etapa diz que está a chegar ao destino, ou parado nele.
            if (Evento.SAI_AGORA !in s.avisados && (perto || chega || (horario && falta <= SAI_TEMPO))) eventos += Evento.SAI_AGORA
        }
        s = s.copy(avisados = s.avisados + eventos.filter { it == Evento.PREPARA_TE || it == Evento.SAI_AGORA })
        // Pelo horário a etapa fica aberta até 60 s depois da eta: o P estimado chega ao destino antes do veículo.
        val fim = jaSai || (s.fase == Fase.A_BORDO &&
            ((!horario && s.progresso >= a[d] - FIM_M) || (horario && agora >= eta.plus(FIM_HORARIO))))
        if (!fim) return s to eventos
        if (!jaSai && Evento.SAI_AGORA !in s.avisados) eventos += Evento.SAI_AGORA
        return fimDeEtapa(s, agora, eventos)
    }

    private fun fimDeEtapa(e: EstadoViagem, agora: Instant, eventos: List<Evento>): Pair<EstadoViagem, List<Evento>> {
        if (e.etapa == plano.etapas.lastIndex) return e.copy(fase = Fase.TERMINADA, terminada = true) to eventos + Evento.CHEGOU
        return naEtapa(e, e.etapa + 1, agora) to eventos + Evento.ETAPA_SEGUINTE
    }

    /** O estado no começo da etapa i: a pé, ou à espera do veículo (que não pode ter partido antes de lá chegares). */
    private fun naEtapa(e: EstadoViagem, i: Int, agora: Instant): EstadoViagem {
        val etapa = plano.etapas[i]
        val percurso = percursos[i]
        val base = e.copy(etapa = i, velocidade = 0.0, avisados = emptySet(), altaPrecisao = false, avancou = false, ultimoVeiculo = null, veiculoLat = null, veiculoLon = null)
        if (percurso == null) {
            // A pé o atraso que trazia continua: a caminhada começa (e acaba) tão tarde como a etapa anterior.
            return base.copy(fase = Fase.A_PE, atrasoS = max(0, e.atrasoS), progresso = distanciaMetros(etapa.de.lat, etapa.de.lon, etapa.para.lat, etapa.para.lon))
        }
        return base.copy(
            fase = Fase.ESPERA, progresso = percurso.noPercurso[0], atrasoS = max(0, segundosEntre(paragens[i][0].hora, agora)),
            altaPrecisao = paragens[i].lastIndex <= ALTA_PARAGENS,
        )
    }

    private fun semGps(e: EstadoViagem, agora: Instant) = e.ultimoGps == null || Duration.between(e.ultimoGps, agora) >= SEM_GPS

    private fun semVeiculo(e: EstadoViagem, agora: Instant) = e.ultimoVeiculo == null || Duration.between(e.ultimoVeiculo, agora) >= SEM_GPS
}

/**
 * Até quando a viagem pode durar: a chegada prevista, mais o atraso conhecido (o adiantamento não conta, e no máximo 2 h:
 * parado em cima da linha o atraso sobe sem fim), mais 30 min.
 */
fun limiteDaViagem(plano: Itinerario, e: EstadoViagem): Instant = plano.chegada.plusSeconds(e.atrasoS.coerceIn(0, ATRASO_MAX_S)) + GUARDA

/** As paragens da etapa; sem elas (a pé, ou a fonte não as deu), a origem e o destino. Pública: o ecrã lista as mesmas que o motor conta. */
fun paragensDe(etapa: Etapa): List<ParagemEtapa> = etapa.paragens.takeIf { it.size >= 2 } ?: listOf(
    ParagemEtapa(null, etapa.de.nome, etapa.de.lat, etapa.de.lon, etapa.partida),
    ParagemEtapa(null, etapa.para.nome, etapa.para.lat, etapa.para.lon, etapa.chegada),
)

private fun maisNova(x: Entrada.Gps, e: EstadoViagem) = e.ultimoGps == null || x.hora > e.ultimoGps

private fun segundos(de: Instant?, ate: Instant): Double = if (de == null) 0.0 else Duration.between(de, ate).toMillis() / 1000.0

private fun segundosEntre(de: Instant, ate: Instant): Long = Duration.between(de, ate).seconds

/** Média corrida da velocidade: metade a anterior, metade a desta posição. */
private fun media(anterior: Double, metros: Double, dt: Double) = if (dt <= 0.0) anterior else (anterior + metros / dt) / 2
