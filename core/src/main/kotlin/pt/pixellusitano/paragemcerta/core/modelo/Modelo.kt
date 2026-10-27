package pt.pixellusitano.paragemcerta.core.modelo

import java.time.Instant

enum class Fonte { CM, TRANSITOUS }

enum class Modo { AUTOCARRO, ELETRICO, METRO, COMBOIO, BARCO, OUTRO }

data class RefParagem(val fonte: Fonte, val id: String) {
    val chave get() = "${fonte.name}:$id"

    companion object {
        fun daChave(chave: String): RefParagem? {
            val (fonte, id) = chave.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return RefParagem(Fonte.entries.firstOrNull { it.name == fonte } ?: return null, id)
        }
    }
}

data class Paragem(val ref: RefParagem, val nome: String, val lat: Double, val lon: Double, val acessivel: Boolean?)

data class Linha(
    val fonte: Fonte, val id: String, val nomeCurto: String, val nomeLongo: String,
    val cor: String?, val corTexto: String?, val modo: Modo,
)

data class Chegada(
    val fonte: Fonte, val linhaId: String, val linhaNome: String, val destino: String,
    val programada: Instant, val prevista: Instant?, val idViagem: String?, val idVeiculo: String?,
    val modo: Modo, val cor: String?, val acessivel: Boolean?,
)

data class Veiculo(
    val id: String, val linhaId: String, val lat: Double, val lon: Double, val rumo: Int?,
    val ultimoSinal: Instant, val destino: String? = null,
)

enum class Gravidade { GRAVE, AVISO, INFO }

data class Alerta(
    val id: String, val linhaIds: Set<String>, val paragemIds: Set<String>, val inicio: Instant?, val fim: Instant?,
    val titulo: String, val texto: String, val gravidade: Gravidade, val imagemUrl: String?,
)
