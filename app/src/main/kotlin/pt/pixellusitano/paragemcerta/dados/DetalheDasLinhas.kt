package pt.pixellusitano.paragemcerta.dados

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.fontes.cm.PadraoCm
import pt.pixellusitano.paragemcerta.core.fontes.transitous.Transitous
import pt.pixellusitano.paragemcerta.core.linhas.RefLinha
import pt.pixellusitano.paragemcerta.core.linhas.SentidoCatalogo
import pt.pixellusitano.paragemcerta.core.linhas.daLinhaNoSentido
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.regras.maisProximas
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Um sentido da linha. [padroes]: os percursos da CM que o compõem (os outros operadores não os têm); [formaId]: o `shape_id` do percurso
 * principal (CM); [porDestino]: o sentido foi inventado pelo gerador do catálogo, só o destino decide as chegadas (ver [SentidoCatalogo]).
 */
data class SentidoLinha(
    val sentido: Int, val destino: String, val destinos: Set<String>, val paragens: List<Paragem>, val padroes: Set<String>,
    val formaId: String? = null, val porDestino: Boolean = false,
)

/** Uma linha com os sentidos e as paragens, sem a forma no mapa (essa carrega à parte: [DetalheDasLinhas.forma]). [rotas]: os route_id do Transitous. */
data class DetalheLinha(
    val ref: RefLinha, val nome: String, val nomeLongo: String, val operador: String, val modo: Modo,
    val cor: String?, val corTexto: String?, val rotas: Set<String>, val sentidos: List<SentidoLinha>,
)

private const val OPERADOR_CM = "Carris Metropolitana"
private val LISBOA = ZoneId.of("Europe/Lisbon")

/**
 * A paragem que o ecrã da linha escolhe ao abrir: a habitual ([habitual], uma `RefParagem.chave`, só se for deste sentido), senão a
 * mais perto de ti (a menos de 800 m), senão a primeira.
 */
fun paragemInicial(s: SentidoLinha, habitual: String?, posicao: Pair<Double, Double>?): Paragem =
    s.paragens.firstOrNull { it.ref.chave == habitual }
        ?: posicao?.let { (lat, lon) -> maisProximas(s.paragens, lat, lon, limite = 1).firstOrNull() }
        ?: s.paragens.first()

/**
 * O que o ecrã de uma linha precisa das fontes. A CM vem da rede guardada no Room mais o `patterns/{id}` de cada percurso; as outras
 * linhas, do catálogo (`linhas.json`). As chegadas só se pedem para a paragem escolhida.
 */
class DetalheDasLinhas(
    private val cm: CarrisMetropolitana,
    private val transitous: Transitous,
    private val catalogo: CatalogoLinhas,
    private val repositorio: Repositorio,
    private val relogio: () -> Instant = Instant::now,
) {
    // Os percursos completos da última linha da CM aberta: o horário de cada paragem sai deles, sem outro pedido (têm centenas de viagens).
    private var padroesCm = emptyMap<String, PadraoCm>()

    // Só as últimas formas (170 KB de JSON cada) para a sessão não crescer com cada linha vista.
    private val formas = object : LinkedHashMap<String, List<Pair<Double, Double>>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Pair<Double, Double>>>) = size > MAX_FORMAS
    }

    // Os horários do dia, até ao fim do dia: uma paragem grande da Carris dá centenas de KB no pedido, e o que se guarda é só a lista de horas.
    private val horarios = mutableMapOf<String, List<LocalTime>>()
    private var diaDosHorarios: LocalDate? = null

    /**
     * Falha(404): a linha não existe (catálogo ou rede da CM carregados e sem ela). Falha(outro): não se sabe (sem rede, formato estranho).
     * Um catálogo que ainda carrega espera pelo resultado em vez de dizer que a linha não existe.
     */
    suspend fun detalhe(ref: RefLinha): Resultado<DetalheLinha> = when (ref.fonte) {
        Fonte.CM -> detalheCm(ref)
        Fonte.TRANSITOUS -> detalheDoCatalogo(ref)
    }

    private suspend fun detalheDoCatalogo(ref: RefLinha): Resultado<DetalheLinha> {
        val c = (catalogo.estado.first { it !is EstadoCatalogo.ACarregar } as? EstadoCatalogo.Pronto)?.c ?: return Resultado.Falha(null, limite = false)
        val l = c.linhas.firstOrNull { it.ref == ref } ?: return Resultado.Falha(404, limite = false)
        // Um sentido sem paragens não tem por onde abrir (o catálogo vem de fora): fica de fora, e sem nenhum não há ecrã.
        val sentidos = l.sentidos.filter { it.paragens.isNotEmpty() }
            .map { SentidoLinha(it.sentido, it.destino, it.destinos, it.paragens, emptySet(), porDestino = it.porDestino) }
        if (sentidos.isEmpty()) return Resultado.Falha(null, limite = false)
        return Resultado.Ok(DetalheLinha(l.ref, l.nome, l.nomeLongo, c.operadores[l.operador] ?: l.operador, l.modo, l.cor, l.corTexto, l.rotas, sentidos))
    }

    private suspend fun detalheCm(ref: RefLinha): Resultado<DetalheLinha> {
        val linha = repositorio.linhaCm(ref.id) ?: run {
            // Telemóvel novo: sem a rede da CM não se sabe se a linha existe. Pede-a antes de dizer que não.
            if (!repositorio.redeEmFalta()) return Resultado.Falha(404, limite = false)
            if (!repositorio.atualizarRede()) return Resultado.Falha(null, limite = false)
            repositorio.linhaCm(ref.id) ?: return Resultado.Falha(404, limite = false)
        }
        val respostas = coroutineScope { repositorio.padroesDaLinhaCm(ref.id).map { async { cm.padraoCompleto(it) } }.awaitAll() }
        respostas.firstOrNull { it is Resultado.Falha }?.let { return it as Resultado.Falha }
        val padroes = respostas.map { (it as Resultado.Ok).valor }
        padroesCm = padroes.associateBy { it.id }
        val paragens = repositorio.paragensCm(padroes.flatMap { it.paragens }.distinct())
        val hoje = relogio().atZone(LISBOA).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
        val sentidos = padroes.groupBy { it.sentido }.toSortedMap().mapNotNull { (sentido, deste) ->
            // O percurso com mais viagens hoje dá o destino, as paragens e a forma; as chegadas e o horário juntam os de todos.
            val principal = deste.maxByOrNull { p -> p.horarios.count { hoje in it.dias } }
            val lista = principal?.paragens?.mapNotNull { paragens[it] }.orEmpty()
            principal?.takeIf { lista.isNotEmpty() }?.let {
                SentidoLinha(sentido, it.destino, deste.mapTo(LinkedHashSet()) { p -> p.destino }, lista, deste.mapTo(LinkedHashSet()) { p -> p.id }, it.formaId)
            }
        }
        if (sentidos.isEmpty()) return Resultado.Falha(null, limite = false)
        return Resultado.Ok(DetalheLinha(ref, linha.nomeCurto, linha.nomeLongo, OPERADOR_CM, Modo.AUTOCARRO, linha.cor, linha.corTexto, emptySet(), sentidos))
    }

    /** O percurso da linha no mapa, num pedido à parte do detalhe: se falhar fica vazio e o ecrã mostra só as paragens. */
    suspend fun forma(d: DetalheLinha, sentido: Int): List<Pair<Double, Double>> {
        if (d.ref.fonte != Fonte.CM) {
            return catalogo.atual?.linhas?.firstOrNull { it.ref == d.ref }?.sentidos?.firstOrNull { it.sentido == sentido }?.forma.orEmpty()
        }
        val id = d.sentidos.firstOrNull { it.sentido == sentido }?.formaId ?: return emptyList()
        formas[id]?.let { return it }
        return (cm.forma(id) as? Resultado.Ok)?.valor?.also { formas[id] = it }.orEmpty()
    }

    /**
     * As chegadas da linha no sentido, na [paragem]. Pede as da paragem (com o ritmo, a pausa e as últimas guardadas do ecrã da paragem) e
     * fica com as do sentido: na CM os percursos dele (o `padrao`; um terminal parte e chega na mesma paragem), nas outras `daLinhaNoSentido`.
     */
    fun chegadas(d: DetalheLinha, sentido: Int, paragem: Paragem): Flow<EstadoChegadas> {
        val s = d.sentidos.first { it.sentido == sentido }
        return repositorio.chegadas(paragem.ref).map { e ->
            e.copy(chegadas = e.chegadas.filter { c -> if (d.ref.fonte == Fonte.CM) c.padrao in s.padroes else daLinhaNoSentido(c, d.rotas, s.paraCatalogo()) })
        }
    }

    /** As horas a que a linha parte da paragem no [dia], pela ordem do dia de serviço; guardadas até ao fim do dia. */
    suspend fun horario(d: DetalheLinha, sentido: Int, paragem: Paragem, dia: LocalDate): Resultado<List<LocalTime>> {
        if (dia != diaDosHorarios) {
            horarios.clear()
            diaDosHorarios = dia
        }
        val chave = "${d.ref.chave}|$sentido|${paragem.ref.chave}"
        horarios[chave]?.let { return Resultado.Ok(it) }
        val s = d.sentidos.first { it.sentido == sentido }
        val r = if (d.ref.fonte == Fonte.CM) horarioCm(s, paragem, dia) else transitous.horarioDoDia(paragem.ref.id, d.modo, d.rotas, s.paraCatalogo(), dia)
        if (r is Resultado.Ok) horarios[chave] = r.valor
        return r
    }

    private suspend fun horarioCm(s: SentidoLinha, paragem: Paragem, dia: LocalDate): Resultado<List<LocalTime>> {
        val padroes = s.padroes.map { id ->
            padroesCm[id] ?: when (val r = cm.padraoCompleto(id)) {
                is Resultado.Ok -> r.valor.also { padroesCm = padroesCm + (id to it) }
                is Resultado.Falha -> return r
            }
        }
        // Os grupos de viagens de todos os percursos do sentido juntos, para as horas virem pela ordem do dia de serviço.
        return Resultado.Ok(cm.horarioCmDoDia(padroes.first().copy(horarios = padroes.flatMap { it.horarios }), paragem.ref.id, dia))
    }

    private fun SentidoLinha.paraCatalogo() = SentidoCatalogo(sentido, destino, destinos, paragens, emptyList(), porDestino)

    private companion object {
        const val MAX_FORMAS = 8
    }
}
