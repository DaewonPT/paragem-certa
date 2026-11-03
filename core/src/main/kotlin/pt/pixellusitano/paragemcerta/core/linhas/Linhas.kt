package pt.pixellusitano.paragemcerta.core.linhas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import pt.pixellusitano.paragemcerta.core.fontes.transitous.chaveDoNome
import pt.pixellusitano.paragemcerta.core.fontes.transitous.espacosUnicos
import pt.pixellusitano.paragemcerta.core.modelo.Chegada
import pt.pixellusitano.paragemcerta.core.modelo.Fonte
import pt.pixellusitano.paragemcerta.core.modelo.Modo
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.RefParagem
import pt.pixellusitano.paragemcerta.core.rotas.descodificarPolilinha
import java.time.LocalDate

/** Uma linha: da CM ("CM:1009", id "1009") ou de um operador do Transitous ("TRANSITOUS:pt-Carris:728", id "pt-Carris:728"). */
data class RefLinha(val fonte: Fonte, val id: String) {
    val chave get() = "${fonte.name}:$id"

    companion object {
        // O id do Transitous leva ":" dentro: só o primeiro separa a fonte.
        fun daChave(c: String): RefLinha? {
            val (fonte, id) = c.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return RefLinha(Fonte.entries.firstOrNull { it.name == fonte } ?: return null, id)
        }
    }
}

/**
 * Uma linha do `linhas.json`: [operador] é o id do operador ("pt-Carris"; o nome para mostrar está em [Catalogo.operadores]) e
 * [rotas] os route_id do Transitous que a compõem ("pt-Carris_204_0"). [nomeLongo] vem vazio se o operador não o tem.
 */
data class LinhaCatalogo(
    val ref: RefLinha, val operador: String, val nome: String, val nomeLongo: String, val modo: Modo,
    val cor: String?, val corTexto: String?, val rotas: Set<String>, val sentidos: List<SentidoCatalogo>,
)

/**
 * Um sentido da linha: o [destino] mais frequente, os [destinos] de todas as viagens dele, as [paragens] do percurso mais
 * frequente e a [forma] no mapa. [porDestino]: o sentido foi inventado pelo gerador (a linha não tem direction_id no GTFS); o
 * Transitous manda "directionId": "0" nessas viagens (a CP, 03/11/2026), por isso o número não se compara e só o destino decide.
 */
data class SentidoCatalogo(
    val sentido: Int, val destino: String, val destinos: Set<String>, val paragens: List<Paragem>, val forma: List<Pair<Double, Double>>,
    val porDestino: Boolean = false,
)

data class Catalogo(val geradoEm: LocalDate, val operadores: Map<String, String>, val linhas: List<LinhaCatalogo>) {
    private val porRota by lazy { linhas.flatMap { l -> l.rotas.map { it to l } }.toMap() }

    /** A linha a que a rota ("pt-Carris_204_1") pertence. */
    fun daRota(routeId: String): LinhaCatalogo? = porRota[routeId]

    /** Pelo nome curto (a começar pelo texto, depois a conter) e só depois pelo nome longo; sem acentos nem maiúsculas. */
    fun procurar(texto: String): List<LinhaCatalogo> {
        val q = chaveDoNome(texto)
        if (q.isEmpty()) return emptyList()
        fun nivel(l: LinhaCatalogo): Int? {
            val nome = chaveDoNome(l.nome)
            return when {
                nome.startsWith(q) -> 0
                q in nome -> 1
                q in chaveDoNome(l.nomeLongo) -> 2
                else -> null
            }
        }
        return linhas.mapNotNull { l -> nivel(l)?.let { it to l } }.sortedBy { it.first }.map { it.second }
    }
}

@Serializable private class OperadorJson(val id: String, val nome: String)

@Serializable private class SentidoJson(
    val sentido: Int,
    val destino: String = "",
    val destinos: List<String> = emptyList(),
    val paragens: List<String> = emptyList(),
    val forma: String = "",
    val porDestino: Boolean = false,
)

@Serializable private class LinhaJson(
    val operador: String,
    val nome: String,
    val nomeLongo: String = "",
    val modo: String = "",
    val cor: String? = null,
    val corTexto: String? = null,
    val rotas: List<String> = emptyList(),
    val sentidos: List<SentidoJson> = emptyList(),
)

// "paragens": { "pt-Carris_3803": ["Cais Sodré", 38.7166, -9.1357] }
@Serializable private class CatalogoJson(
    val geradoEm: String,
    val operadores: List<OperadorJson> = emptyList(),
    val paragens: Map<String, JsonArray> = emptyMap(),
    val linhas: List<LinhaJson> = emptyList(),
)

private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** O `linhas.json` gerado no GitHub. Lança num ficheiro estragado (cortado, sem uma paragem que uma linha usa…): o chamador fica com o antigo. */
fun lerCatalogo(json: String): Catalogo {
    val c = JSON.decodeFromString<CatalogoJson>(json)
    val paragens = c.paragens.mapValues { (id, p) ->
        Paragem(RefParagem(Fonte.TRANSITOUS, id), p[0].jsonPrimitive.content.espacosUnicos(), p[1].jsonPrimitive.double, p[2].jsonPrimitive.double, null)
    }
    return Catalogo(
        geradoEm = LocalDate.parse(c.geradoEm),
        operadores = c.operadores.associate { it.id to it.nome },
        linhas = c.linhas.map { l ->
            LinhaCatalogo(
                ref = RefLinha(Fonte.TRANSITOUS, "${l.operador}:${l.nome}"),
                operador = l.operador, nome = l.nome, nomeLongo = l.nomeLongo,
                modo = Modo.entries.firstOrNull { it.name == l.modo } ?: Modo.OUTRO,
                cor = l.cor, corTexto = l.corTexto, rotas = l.rotas.toSet(),
                sentidos = l.sentidos.map { s ->
                    SentidoCatalogo(
                        s.sentido, s.destino, s.destinos.toSet(), s.paragens.map { paragens.getValue(it) },
                        descodificarPolilinha(s.forma, 5), s.porDestino,
                    )
                },
            )
        },
    )
}

/**
 * A chegada é da linha ([rotas]) e do sentido [s]: pelo sentido que a partida traz; sem ele, ou num sentido inventado pelo
 * gerador ([SentidoCatalogo.porDestino]), pelo destino.
 */
fun daLinhaNoSentido(c: Chegada, rotas: Set<String>, s: SentidoCatalogo): Boolean =
    c.linhaId in rotas &&
        if (s.porDestino || c.sentido == null) s.destinos.any { it.equals(c.destino, ignoreCase = true) } else c.sentido == s.sentido
