package pt.pixellusitano.paragemcerta.core.fontes.servidor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import pt.pixellusitano.paragemcerta.core.comunidade.Correcao
import pt.pixellusitano.paragemcerta.core.comunidade.MotivoDenuncia
import pt.pixellusitano.paragemcerta.core.comunidade.TipoRelato
import pt.pixellusitano.paragemcerta.core.rede.Http
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.core.rede.converterResposta
import pt.pixellusitano.paragemcerta.core.rede.emPausa
import pt.pixellusitano.paragemcerta.core.rede.pedir
import pt.pixellusitano.paragemcerta.core.rede.pedirCom
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset

// Campos desconhecidos ignoram-se: uma versão nova do servidor não parte a app.
private val JSON = Json { ignoreUnknownKeys = true }

/** O `config.json` do servidor: interruptores e valores que mudam sem sair uma versão nova da app. */
@Serializable data class Configuracao(
    val versao: Int,
    val interruptores: Interruptores,
    val emails: Emails,
    val canaisReclamacao: List<CanalReclamacaoTexto>,
    /** Os termos em vigor: quem relata, vota ou envia fotos tem de os ter aceitado (senão 403 `termos_por_aceitar`). */
    val versaoTermos: String = "1",
)

/** Tudo desligado por omissão: um interruptor que o servidor não conhece fica desligado. */
@Serializable data class Interruptores(
    val conta: Boolean = false,
    val comunidade: Boolean = false,
    val fotos: Boolean = false,
    val correcoes: Boolean = false,
)

@Serializable data class Emails(val codigosPorDia: Int)

@Serializable data class CanalReclamacaoTexto(val nome: String, val url: String)

@Serializable data class Sessao(val token: String, val email: String)

/** Um favorito como o servidor o guarda. `revisao` só vem do servidor: nunca se envia. */
@Serializable data class FavoritoRemoto(
    val chave: String,
    val tipo: String,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val ordem: Int,
    val apagado: Boolean,
    val revisao: Long? = null,
)

@Serializable data class Sincronizacao(val revisao: Long, val alteracoes: List<FavoritoRemoto>)

@Serializable private class PedidoSincronizar(val desde: Long, val alteracoes: List<FavoritoRemoto>)

@Serializable private class NonceJson(val nonce: String)

/** Um relato como o servidor o mostra a toda a gente: nada de quem o fez. `confirmado` = pelo menos 2 confirmações que contam. */
@Serializable data class RelatoRemoto(
    val id: String,
    val tipo: String,
    val paragem: String,
    val linha: String = "",
    val criadoEm: Long,
    val expiraEm: Long,
    val confirmacoes: Int,
    val confirmado: Boolean,
)

@Serializable data class FotoRemota(val paragem: String, val id: String, val url: String)

/** Só os campos do tipo vêm preenchidos: `posicao` traz lat e lon, `nome` traz o nome, `ja_nao_existe` nenhum. */
@Serializable data class CorrecaoRemota(val paragem: String, val tipo: String, val lat: Double? = null, val lon: Double? = null, val nome: String? = null)

/** O que o servidor sabe das paragens pedidas; `emRevisao` são as paragens com uma foto à espera do autor. */
@Serializable data class DadosComunidade(
    val relatos: List<RelatoRemoto> = emptyList(),
    val fotos: List<FotoRemota> = emptyList(),
    val emRevisao: List<String> = emptyList(),
    val correcoes: List<CorrecaoRemota> = emptyList(),
)

/** `novo` é false quando já havia um relato ativo igual: o pedido contou como "Ainda está" dessa conta. */
@Serializable data class RelatoCriado(val id: String, val novo: Boolean)

@Serializable data class Voto(val confirmacoes: Int, val confirmado: Boolean, val ativo: Boolean)

@Serializable private class RelatosJson(val relatos: List<RelatoRemoto> = emptyList())

@Serializable private class FotoEnviada(val id: String)

/**
 * O servidor da Paragem Certa. Falhas: o código HTTP (401 código errado ou sessão recusada, 410 código expirado,
 * 429 demasiados pedidos com `limite = true`) ou `Falha(null)` sem rede; `erro` traz o código que o servidor dá
 * (`codigo_errado`, `sem_sessao`, `desligado`, `termos_por_aceitar`…).
 *
 * Quando a quota grátis do dia acaba (503 `em_pausa`, ou o 429 sem JSON da página 1027 da Cloudflare) os pedidos à API
 * param até à próxima 00:00 UTC e devolvem `Falha(null, true, "em_pausa")` sem pedir (spec 5.4). Ficam de fora o `config()`
 * (um ficheiro estático, não gasta quota) e as rotas de `/api/auth/` (entrar, sair), onde o 429 é da regra WAF de 10 s e não da quota.
 */
class ServidorParagemCerta(
    private val http: Http,
    private val base: String = "https://paragemcerta.pixellusitano.com",
    private val relogio: () -> Instant = Instant::now,
) {
    @Volatile private var pausaAte: Instant = Instant.MIN

    suspend fun config(): Resultado<Configuracao> = http.pedir("$base/dados/config.json") { JSON.decodeFromString<Configuracao>(it) }

    suspend fun nonceGoogle(): Resultado<String> =
        enviar("POST", "/api/auth/google/nonce", null) { JSON.decodeFromString<NonceJson>(it).nonce }

    suspend fun entrarComGoogle(idToken: String): Resultado<Sessao> =
        enviar("POST", "/api/auth/google", buildJsonObject { put("idToken", idToken) }, converter = ::sessao)

    suspend fun pedirCodigo(email: String): Resultado<Unit> =
        enviar("POST", "/api/auth/email/pedir", buildJsonObject { put("email", email) }) { }

    suspend fun confirmarCodigo(email: String, codigo: String): Resultado<Sessao> =
        enviar("POST", "/api/auth/email/confirmar", buildJsonObject { put("email", email); put("codigo", codigo) }, converter = ::sessao)

    suspend fun sair(token: String): Resultado<Unit> = enviar("POST", "/api/auth/sair", null, token) { }

    suspend fun apagarConta(token: String): Resultado<Unit> = enviar("DELETE", "/api/conta", null, token) { }

    suspend fun sincronizar(token: String, desde: Long, alteracoes: List<FavoritoRemoto>): Resultado<Sincronizacao> =
        enviar("POST", "/api/sincronizar", JSON.encodeToJsonElement(PedidoSincronizar(desde, alteracoes)), token) {
            JSON.decodeFromString<Sincronizacao>(it)
        }

    /** Leitura pública (sem sessão): os relatos, fotos e correções das paragens pedidas (até 100). */
    suspend fun comunidade(paragens: List<String>): Resultado<DadosComunidade> =
        enviar("GET", "/api/comunidade?paragens=${juntar(paragens)}", null) { JSON.decodeFromString<DadosComunidade>(it) }

    /** Leitura pública: os relatos confirmados e ativos das linhas pedidas (até 20, pelo nome visível em maiúsculas). */
    suspend fun avisos(linhas: List<String>): Resultado<List<RelatoRemoto>> =
        enviar("GET", "/api/avisos?linhas=${juntar(linhas)}", null) { JSON.decodeFromString<RelatosJson>(it).relatos }

    suspend fun relatar(token: String, tipo: TipoRelato, paragem: String, linha: String?): Resultado<RelatoCriado> =
        enviar("POST", "/api/relatos", buildJsonObject {
            put("tipo", tipo.chave)
            put("paragem", paragem)
            // O servidor recusa uma linha num relato da paragem.
            if (tipo.deLinha && linha != null) put("linha", linha)
        }, token) { JSON.decodeFromString<RelatoCriado>(it) }

    suspend fun votar(token: String, relato: String, aindaEsta: Boolean): Resultado<Voto> =
        enviar("POST", "/api/votos", buildJsonObject {
            put("relato", relato)
            put("voto", if (aindaEsta) "ainda_esta" else "ja_nao_esta")
        }, token) { JSON.decodeFromString<Voto>(it) }

    /** O JPEG vai em bruto (já sem metadados). Devolve o id da foto, que fica "em revisão" até o autor a ver. */
    suspend fun enviarFoto(token: String, paragem: String, jpeg: ByteArray): Resultado<String> {
        val caminho = "/api/fotos?paragem=${URLEncoder.encode(paragem, "UTF-8")}"
        return comPausa(caminho) {
            converterResposta({ http.enviarBytes("POST", "$base$caminho", jpeg, "image/jpeg", cabecalhos(token)) }, lerErro = true) {
                JSON.decodeFromString<FotoEnviada>(it).id
            }
        }
    }

    suspend fun corrigir(token: String, paragem: String, correcao: Correcao): Resultado<Unit> =
        enviar("POST", "/api/correcoes", buildJsonObject {
            put("paragem", paragem)
            when (correcao) {
                is Correcao.Posicao -> { put("tipo", "posicao"); put("lat", correcao.lat); put("lon", correcao.lon) }
                is Correcao.Nome -> { put("tipo", "nome"); put("nome", correcao.nome) }
                Correcao.JaNaoExiste -> put("tipo", "ja_nao_existe")
            }
        }, token) { }

    /** `conteudo` é "relato:<id>" ou "foto:<id>". A caixa "acredito de boa-fé" é do ecrã: só se chama com ela marcada. */
    suspend fun denunciar(token: String, conteudo: String, motivo: MotivoDenuncia, nome: String, explicacao: String): Resultado<Unit> =
        enviar("POST", "/api/denuncias", buildJsonObject {
            put("conteudo", conteudo)
            put("motivo", motivo.chave)
            put("nome", nome)
            put("explicacao", explicacao)
            put("boaFe", true)
        }, token) { }

    suspend fun aceitarTermos(token: String, versao: String): Resultado<Unit> =
        enviar("POST", "/api/termos", buildJsonObject { put("versao", versao) }, token) { }

    private fun sessao(corpo: String) = JSON.decodeFromString<Sessao>(corpo)

    // As chaves vão separadas por vírgulas, cada uma codificada (têm ":" e, no Transitous, "(" e ")").
    private fun juntar(chaves: List<String>) = chaves.joinToString(",") { URLEncoder.encode(it, "UTF-8") }

    private fun cabecalhos(token: String?) = if (token == null) emptyMap() else mapOf("Authorization" to "Bearer $token")

    private suspend fun <T> enviar(
        metodo: String, caminho: String, corpo: JsonElement?, token: String? = null, converter: (String) -> T,
    ): Resultado<T> = comPausa(caminho) {
        http.pedirCom(metodo, "$base$caminho", corpo?.toString(), cabecalhos(token), lerErro = true, converter = converter)
    }

    /** Não pede durante a pausa; e uma falha de quota (ver a classe) começa a pausa até à próxima 00:00 UTC. */
    private suspend fun <T> comPausa(caminho: String, pedir: suspend () -> Resultado<T>): Resultado<T> {
        val entrada = caminho.startsWith("/api/auth/")
        val agora = relogio()
        if (!entrada && agora < pausaAte) return Resultado.Falha(null, limite = true, erro = "em_pausa")
        val r = pedir()
        // Um 503 sem erro (um 1102 passageiro, por exemplo) é uma falha como as outras: não pausa nada.
        if (entrada || r !is Resultado.Falha || !(r.emPausa || (r.codigo == 429 && r.erro == null))) return r
        pausaAte = agora.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return r.copy(limite = true, erro = "em_pausa")
    }
}
