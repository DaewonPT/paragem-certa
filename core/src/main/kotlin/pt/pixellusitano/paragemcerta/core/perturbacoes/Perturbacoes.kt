package pt.pixellusitano.paragemcerta.core.perturbacoes

import pt.pixellusitano.paragemcerta.core.aovivo.nomeLinhaMetro
import pt.pixellusitano.paragemcerta.core.comunidade.chaveLinha
import pt.pixellusitano.paragemcerta.core.fontes.servidor.PerturbacaoRemota
import pt.pixellusitano.paragemcerta.core.modelo.Alerta
import java.time.Instant

enum class FontePerturbacao { CM, METRO }

/**
 * Uma perturbação oficial: um aviso da Carris Metropolitana (lido pelo telemóvel) ou o estado de uma linha do Metro (lido pelo
 * servidor). [id] é o que fica nas perturbações já vistas: "cm:<id do alerta>" ou "metro:<AZ|AM|VD|VM>:<8 hex do texto>".
 * [linhas] são chaves de linha ([chaveLinha]), as mesmas dos relatos.
 */
data class Perturbacao(
    val id: String, val fonte: FontePerturbacao, val linhas: Set<String>, val titulo: String, val texto: String,
    val inicio: Instant?, val fim: Instant?,
)

fun daCm(a: Alerta): Perturbacao = Perturbacao(
    id = "cm:" + a.id, fonte = FontePerturbacao.CM, linhas = a.linhaIds.mapTo(mutableSetOf(), ::chaveLinha),
    titulo = a.titulo, texto = a.texto, inicio = a.inicio, fim = a.fim,
)

/** O servidor só manda as linhas que não estão normais; não dá datas, por isso ficam null. */
fun doMetro(p: PerturbacaoRemota): Perturbacao = Perturbacao(
    id = p.id, fonte = FontePerturbacao.METRO, linhas = setOf(p.linha),
    titulo = nomeLinhaMetro(p.linha)?.let { "Linha $it" } ?: "Metro de Lisboa", texto = p.texto, inicio = null, fim = null,
)

/** As que tocam numa das [chaves] (as linhas de quem pergunta), pela ordem em que vieram; uma sem linhas (só de paragens) não é de nenhuma. */
fun dasLinhas(todas: List<Perturbacao>, chaves: Set<String>): List<Perturbacao> = todas.filter { p -> p.linhas.any { it in chaves } }

/** As que ainda não foram vistas. */
fun novas(lista: List<Perturbacao>, vistas: Set<String>): List<Perturbacao> = lista.filter { it.id !in vistas }
