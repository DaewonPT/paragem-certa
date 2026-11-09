package pt.pixellusitano.paragemcerta.widget

import pt.pixellusitano.paragemcerta.dados.LocalGuardado
import pt.pixellusitano.paragemcerta.ui.comum.LISBOA_FUSO
import pt.pixellusitano.paragemcerta.ui.inicio.EstadoLocal
import pt.pixellusitano.paragemcerta.ui.inicio.PosicaoComHora
import pt.pixellusitano.paragemcerta.ui.inicio.estadoDoLocal
import pt.pixellusitano.paragemcerta.ui.rotas.paraRota
import java.time.Instant
import java.time.format.DateTimeFormatter

/** O que se lê num destino do widget "Casa e Trabalho"; [nome] é o do sítio guardado. */
sealed interface Destino {
    /** "Toca para escolher a morada". */
    data object SemMorada : Destino
    /** O selo da linha (ou a pé) e "30 min · sai às 18:42". */
    data class Ligacao(val nome: String, val ligacao: LigacaoGuardada) : Destino
    /** Como a [Ligacao], a cinzento: a hora de saída já passou. */
    data class Passada(val nome: String, val ligacao: LigacaoGuardada) : Destino
    /** A 500 m ou menos: "Estás perto". */
    data class Perto(val nome: String) : Destino
    /** Há morada e posição, mas nada calculado: "Toca em ↻". */
    data class TocaAtualizar(val nome: String) : Destino
    /** Só durante o ↻. */
    data class ACalcular(val nome: String) : Destino
    data class SemTransportes(val nome: String) : Destino
    /** O último pedido falhou: "Sem ligação — toca em ↻". */
    data class SemLigacao(val nome: String) : Destino
    /** Nunca houve posição: "Abre a app para saber onde estás". */
    data class SemPosicao(val nome: String) : Destino
}

/** "atualizado às [hora]" ou, quando se calculou a partir da posição guardada, "posição das [hora]". */
data class Rodape(val hora: String, val daPosicaoGuardada: Boolean)

/** [rotaCasa] e [rotaTrabalho]: o destino das Rotas ("nome|lat|lon") que o toque abre; null = sem morada. */
data class ConteudoCasaTrabalho(val casa: Destino, val trabalho: Destino, val rodape: Rodape?, val rotaCasa: String?, val rotaTrabalho: String?)

private val HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(LISBOA_FUSO)

// Limite: o cinzento da ligação que já saiu só se vê num desenho (ao abrir ou sair da app, ou no ↻); nada acorda o widget para isso.
fun conteudoCasaTrabalho(
    casa: LocalGuardado?, trabalho: LocalGuardado?, ligacoes: Map<String, LigacaoGuardada>, posicao: PosicaoComHora?,
    posicaoGuardada: Boolean, agora: Instant, aCalcular: Boolean,
): ConteudoCasaTrabalho {
    val aqui = posicao?.let { it.lat to it.lon }
    fun perto(l: LocalGuardado) = aqui != null && estadoDoLocal(l, aqui) == EstadoLocal.PERTO

    fun destino(local: LocalGuardado?): Destino {
        if (local == null) return Destino.SemMorada
        val nome = local.nome
        if (perto(local)) return Destino.Perto(nome)
        if (aCalcular) return Destino.ACalcular(nome)
        val l = ligacoes[local.tipo] ?: return if (aqui == null) Destino.SemPosicao(nome) else Destino.TocaAtualizar(nome)
        return when (l.estado) {
            EstadoLigacao.SEM_TRANSPORTES -> Destino.SemTransportes(nome)
            EstadoLigacao.FALHOU -> Destino.SemLigacao(nome)
            EstadoLigacao.OK -> if (l.partida < agora.toEpochMilli()) Destino.Passada(nome, l) else Destino.Ligacao(nome, l)
        }
    }

    val calculadaEm = listOfNotNull(casa, trabalho).filterNot(::perto).mapNotNull { ligacoes[it.tipo]?.em }.maxOrNull()
    val rodape = when {
        posicaoGuardada && posicao != null && posicao.hora > 0 -> Rodape(HORA.format(Instant.ofEpochMilli(posicao.hora)), daPosicaoGuardada = true)
        calculadaEm != null -> Rodape(HORA.format(Instant.ofEpochMilli(calculadaEm)), daPosicaoGuardada = false)
        else -> null
    }
    return ConteudoCasaTrabalho(destino(casa), destino(trabalho), rodape, casa?.lugar?.paraRota(), trabalho?.lugar?.paraRota())
}
