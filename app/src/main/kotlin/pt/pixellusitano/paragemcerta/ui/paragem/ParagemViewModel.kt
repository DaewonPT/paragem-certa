package pt.pixellusitano.paragemcerta.ui.paragem

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import pt.pixellusitano.paragemcerta.dados.EstadoChegadas
import pt.pixellusitano.paragemcerta.ui.sobre.EMAIL_CONTACTO

class ParagemViewModel(chegadas: Flow<EstadoChegadas>) : ViewModel() {
    /** Consulta só com o ecrã visível; pára 5 s depois de deixar de estar. */
    val estado: StateFlow<EstadoChegadas?> = chegadas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _filtro = MutableStateFlow<String?>(null)
    val filtro: StateFlow<String?> = _filtro.asStateFlow()

    fun escolherFiltro(linhaId: String?) {
        _filtro.value = linhaId
    }

    /** (id, nome) de cada linha presente, pelo nome que se vê (no Transitous o id é "pt-Carris_60_0" e o nome "201"). */
    fun linhasPresentes(e: EstadoChegadas): List<Pair<String, String>> =
        e.chegadas.distinctBy { it.linhaId }.map { it.linhaId to it.linhaNome.ifBlank { it.linhaId } }
            .sortedWith(compareBy({ it.second.length }, { it.second }))

    fun aplicarFiltro(e: EstadoChegadas): EstadoChegadas {
        val f = _filtro.value ?: return e
        return e.copy(chegadas = e.chegadas.filter { it.linhaId == f })
    }
}

fun intentReportarErro(versao: String, chave: String, nome: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_CONTACTO"))
        .putExtra(Intent.EXTRA_SUBJECT, "Paragem Certa — erro na paragem $nome")
        .putExtra(Intent.EXTRA_TEXT, "Versão da app: $versao\nParagem: $chave\n\nDescreve o erro:\n")
