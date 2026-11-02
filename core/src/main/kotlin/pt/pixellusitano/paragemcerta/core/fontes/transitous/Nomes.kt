package pt.pixellusitano.paragemcerta.core.fontes.transitous

import pt.pixellusitano.paragemcerta.core.modelo.Itinerario
import pt.pixellusitano.paragemcerta.core.modelo.Lugar
import pt.pixellusitano.paragemcerta.core.modelo.Paragem
import pt.pixellusitano.paragemcerta.core.modelo.TipoLugar
import pt.pixellusitano.paragemcerta.core.regras.distanciaMetros
import java.text.Normalizer

private val ESPACOS = Regex("\\s+")
private val MARCAS = Regex("\\p{M}+")
private const val RAIO_NOME_METROS = 300.0

/** Os operadores põem espaços a mais nos nomes ("Alcantara -  Mar"). */
fun String.espacosUnicos(): String = trim().replace(ESPACOS, " ")

/** "Alcântara -  Mar" → "alcantara - mar": o que se compara quando os nomes só diferem em acentos, maiúsculas ou espaços. */
fun chaveDoNome(nome: String): String = Normalizer.normalize(nome, Normalizer.Form.NFD).replace(MARCAS, "").lowercase().espacosUnicos()

private fun acentos(nome: String) = Normalizer.normalize(nome, Normalizer.Form.NFD).count { it.category == CharCategory.NON_SPACING_MARK }

/**
 * Sítios já conhecidos (da pesquisa, da base de paragens, da própria rota), para devolver os acentos que alguns operadores
 * tiram: "Cais do Sodre" passa a "Cais do Sodré" se a menos de 300 m há um sítio que só difere por ter mais acentos.
 * Sem correspondência fica o nome como vem; nunca troca por um nome com menos acentos.
 */
class NomesConhecidos(conhecidos: Iterable<Lugar>) {
    private val porChave = conhecidos.groupBy { chaveDoNome(it.nome) }

    fun comAcentos(nome: String, lat: Double, lon: Double): String {
        val meus = acentos(nome)
        return porChave[chaveDoNome(nome)].orEmpty()
            .filter { acentos(it.nome) > meus && distanciaMetros(lat, lon, it.lat, it.lon) < RAIO_NOME_METROS }
            .maxByOrNull { acentos(it.nome) }?.nome?.espacosUnicos() ?: nome
    }
}

/** Os resultados de uma pesquisa corrigem-se uns aos outros ("Cais do Sodre" da CP junto de "Cais do Sodré" do Transtejo). */
internal fun List<Paragem>.paragensComAcentos(): List<Paragem> {
    val nomes = NomesConhecidos(map { Lugar(it.nome, it.lat, it.lon, TipoLugar.PARAGEM) })
    return map { it.copy(nome = nomes.comAcentos(it.nome, it.lat, it.lon)) }
}

internal fun List<Lugar>.lugaresComAcentos(): List<Lugar> {
    val nomes = NomesConhecidos(this)
    return map { it.copy(nome = nomes.comAcentos(it.nome, it.lat, it.lon)) }
}

private fun Itinerario.lugares() = etapas.flatMap { e -> listOf(e.de, e.para) + e.paragens.map { Lugar(it.nome, it.lat, it.lon, TipoLugar.PARAGEM) } }

/** As chaves dos nomes de todos os sítios das rotas: o que procurar na base de paragens para os corrigir. */
fun List<Itinerario>.chavesDosNomes(): Set<String> = flatMap { it.lugares() }.mapTo(mutableSetOf()) { chaveDoNome(it.nome) }

/** As rotas com os nomes certos: [conhecidas] mais os sítios das próprias rotas dão os acentos que faltam. */
fun List<Itinerario>.comNomesAcentuados(conhecidas: List<Lugar> = emptyList()): List<Itinerario> {
    val nomes = NomesConhecidos(conhecidas + flatMap { it.lugares() })
    fun Lugar.certo() = copy(nome = nomes.comAcentos(nome, lat, lon))
    return map { rota ->
        rota.copy(
            etapas = rota.etapas.map { e ->
                e.copy(de = e.de.certo(), para = e.para.certo(), paragens = e.paragens.map { it.copy(nome = nomes.comAcentos(it.nome, it.lat, it.lon)) })
            },
        )
    }
}
