package pt.pixellusitano.paragemcerta.core.viagem

import pt.pixellusitano.paragemcerta.core.atraso.aindaPorChegar
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import java.time.Duration
import java.time.Instant

/** Quanto tempo o aviso de chegada vigia, no máximo. */
private val VIGIA_MAX = Duration.ofMinutes(90)

/** "Avisa-me quando estiver a [minutos] min": as linhas (pelo nome que a pessoa vê) vigiadas numa paragem desde [desde]. */
data class PedidoAviso(val ref: RefParagem, val nomeParagem: String, val linhas: Set<String>, val minutos: Int, val desde: Instant)

/** peloHorario: a linha não tem tempo real e o aviso vai pela hora do horário. */
data class AvisoChegada(val linhaNome: String, val destino: String, val minutos: Long, val peloHorario: Boolean)

/**
 * Os avisos a dar agora: por cada linha pedida e ainda não avisada, a primeira chegada por chegar (a prevista, ou
 * a programada sem tempo real) a [PedidoAviso.minutos] ou menos. Os minutos arredondam para baixo, como a lista da paragem.
 */
fun aDisparar(p: PedidoAviso, chegadas: List<Chegada>, avisadas: Set<String>, agora: Instant): List<AvisoChegada> =
    chegadas
        .filter { it.linhaNome in p.linhas && it.linhaNome !in avisadas && aindaPorChegar(it, agora) }
        .groupBy { it.linhaNome }
        .map { (_, daLinha) -> daLinha.minBy { it.prevista ?: it.programada } }
        .map { AvisoChegada(it.linhaNome, it.destino, Duration.between(agora, it.prevista ?: it.programada).seconds / 60, it.prevista == null) }
        .filter { it.minutos <= p.minutos }
        .sortedBy { it.minutos }

/** Todas as linhas pedidas já avisaram, ou passaram mais de 90 min desde o pedido. */
fun vigiaAcabou(p: PedidoAviso, avisadas: Set<String>, agora: Instant): Boolean =
    avisadas.containsAll(p.linhas) || agora.isAfter(p.desde.plus(VIGIA_MAX))
