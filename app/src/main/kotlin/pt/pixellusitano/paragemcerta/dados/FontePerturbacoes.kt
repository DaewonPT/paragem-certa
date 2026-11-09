package pt.pixellusitano.paragemcerta.dados

import pt.pixellusitano.paragemcerta.core.fontes.cm.CarrisMetropolitana
import pt.pixellusitano.paragemcerta.core.perturbacoes.Perturbacao
import pt.pixellusitano.paragemcerta.core.perturbacoes.daCm
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import java.time.Duration
import java.time.Instant
import pt.pixellusitano.paragemcerta.core.perturbacoes.dasLinhas as tocamEm

// As linhas da Carris Metropolitana têm 4 algarismos (1001 a 4xxx); um nome assim noutro operador só custa um pedido a mais.
private val LINHA_CM = Regex("""\d{4}""")
// A faixa, o separador Linhas e o ecrã das perturbações abrem quase juntos: um só pedido serve aos três.
private val GUARDAR = Duration.ofMinutes(1)

/**
 * As perturbações oficiais, pedidas pelo telemóvel à Carris Metropolitana: não dependem do servidor nem da comunidade, por
 * isso continuam com o servidor em pausa. Uma falha da CM dá null, que não é "sem perturbações" (a falha não fica guardada: quem
 * pergunta de novo tenta outra vez). Corre nos dois processos (a viagem cria a sua).
 */
class FontePerturbacoes(private val cm: CarrisMetropolitana) {
    private var guardadas: Pair<Instant, List<Perturbacao>>? = null

    // Limite: só as da CM; as do Metro viriam das perturbacoes do /api/avisos, mas o Metro está fora da 0.4 (a porta 8243
    // dá 526, sem o certificado intermédio): juntá-las quando o Metro mandar a cadeia completa.
    suspend fun todas(agora: Instant): List<Perturbacao>? {
        guardadas?.takeIf { Duration.between(it.first, agora) < GUARDAR }?.let { return it.second }
        val r = cm.alertas(agora) as? Resultado.Ok ?: return null
        return r.valor.map(::daCm).also { guardadas = agora to it }
    }

    /** As que tocam nas [chaves] (chaveLinha); só pede à CM se uma delas for uma linha da CM. Uma falha da CM conta como nenhuma. */
    suspend fun dasLinhas(chaves: Set<String>, agora: Instant): List<Perturbacao> =
        if (chaves.none(LINHA_CM::matches)) emptyList() else tocamEm(todas(agora).orEmpty(), chaves)
}
