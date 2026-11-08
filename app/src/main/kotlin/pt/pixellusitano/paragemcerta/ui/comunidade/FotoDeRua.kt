package pt.pixellusitano.paragemcerta.ui.comunidade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fontes.servidor.FotoRemota
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.fotos.anoDaFoto
import pt.pixellusitano.paragemcerta.core.fotos.paginaDaFoto
import pt.pixellusitano.paragemcerta.core.fotos.ruaGanha
import pt.pixellusitano.paragemcerta.core.rede.Resultado
import pt.pixellusitano.paragemcerta.dados.Comunidade
import pt.pixellusitano.paragemcerta.dados.Configuracoes
import pt.pixellusitano.paragemcerta.dados.EstadoFicheiro
import pt.pixellusitano.paragemcerta.dados.FicheiroDoServidor
import pt.pixellusitano.paragemcerta.dados.Preferencias

/** A foto que se mostra numa paragem: só uma, a da comunidade ou a de rua. */
internal sealed interface FotoEscolhida {
    data class DaRua(val foto: FotoDeRua) : FotoEscolhida
    data class DaComunidade(val foto: FotoRemota) : FotoEscolhida
}

/** A mais recente das duas (spec 4.4); em empate, ou sem data da comunidade, a da comunidade ([ruaGanha]). null sem nenhuma. */
internal fun escolherFoto(rua: FotoDeRua?, comunidade: FotoRemota?): FotoEscolhida? = when {
    rua != null && (comunidade == null || ruaGanha(rua, comunidade.data)) -> FotoEscolhida.DaRua(rua)
    comunidade != null -> FotoEscolhida.DaComunidade(comunidade)
    else -> null
}

// "foto:<id>" nas Preferencias, como as da comunidade; até elas responderem (null) conta como ocultada: uma foto que a pessoa
// ocultou não pode piscar nem pedir a miniatura entretanto.
private fun ocultada(ocultos: Set<String>?, id: String) = ocultos == null || "foto:$id" in ocultos

@Composable
private fun ocultosDaPessoa(preferencias: Preferencias): Set<String>? =
    preferencias.ocultos.collectAsStateWithLifecycle<Set<String>?>(null).value

/** [rua] sem a foto que a pessoa ocultou. */
@Composable
fun fotoDeRuaVisivel(rua: FotoDeRua?, preferencias: Preferencias): FotoDeRua? {
    val ocultos = ocultosDaPessoa(preferencias)
    return rua?.takeIf { !ocultada(ocultos, it.id) }
}

/** A foto de rua de [chave] no ficheiro do servidor, quando ele já chegou (ou estava guardado). */
@Composable
fun FicheiroDoServidor<Map<String, FotoDeRua>>.fotoDa(chave: String?): FotoDeRua? {
    val atual by estado.collectAsStateWithLifecycle()
    return chave?.let { (atual as? EstadoFicheiro.Pronto)?.c?.get(it) }
}

/**
 * A foto de rua do Panoramax, com a linha de crédito (autor, licença, ano) que abre a página da foto, e no menu só "Ocultar esta foto"
 * (com [aoOcultar] null, sem menu). [carregar] devolve os bytes da miniatura (null se não der); [aoAbrirPagina] recebe o endereço da página.
 */
@Composable
fun FotoDeRuaNaParagem(foto: FotoDeRua, carregar: suspend (String) -> ByteArray?, aoAbrirPagina: (String) -> Unit, aoOcultar: (() -> Unit)?) {
    val carga = carregarImagem(foto.miniatura, carregar)
    FotoDeRuaConteudo(
        foto, (carga as? Carga.Pronta)?.imagem, falhou = carga == Carga.Falhou,
        aoAbrirPagina = { aoAbrirPagina(paginaDaFoto(foto)) }, aoOcultar = aoOcultar,
    )
}

/**
 * A foto e o crédito, sem pedidos (as capturas de ecrã compõem-no com uma imagem já feita). A imagem inteira, sem recortes nem nada
 * por cima: o crédito (autor, licença e ligação à fonte) é o que a CC BY-SA 4.0 pede.
 */
@Composable
internal fun FotoDeRuaConteudo(foto: FotoDeRua, imagem: ImageBitmap?, falhou: Boolean, aoAbrirPagina: () -> Unit, aoOcultar: (() -> Unit)?) {
    Column(Modifier.padding(top = 16.dp).fillMaxWidth()) {
        ImagemDaFoto(imagem, falhou, stringResource(R.string.foto_rua_descricao))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.foto_rua_credito, foto.autor, foto.licenca, anoDaFoto(foto)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = aoAbrirPagina)
                    .heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            )
            MenuDaFoto(aoOcultar, aoDenunciar = null)
        }
    }
}

/**
 * A foto da paragem [chave] por baixo do alvo da bússola, sem menu: a de [rua] (a do ficheiro, para esta paragem) ou a da comunidade,
 * a mais recente (a mesma regra do ecrã da paragem). Lê a comunidade num só pedido, e só com as fotos ligadas: a de rua não
 * depende de nenhum interruptor. Sem [chave] (a viagem não sabe qual é a paragem) não mostra nem pede nada. As que a pessoa
 * ocultou no ecrã da paragem ficam ocultas.
 */
@Composable
fun FotoNaBussola(
    chave: String?,
    rua: FotoDeRua?,
    comunidade: Comunidade,
    configuracoes: Configuracoes,
    preferencias: Preferencias,
    carregar: suspend (String) -> ByteArray?,
    aoAbrirPagina: (String) -> Unit,
) {
    val config by configuracoes.atual.collectAsStateWithLifecycle()
    val ocultos = ocultosDaPessoa(preferencias)
    if (chave == null) return
    val fotos by produceState(emptyList<FotoRemota>(), chave, config) {
        value = if (!config.interruptores.fotos) emptyList() else (comunidade.daParagem(chave) as? Resultado.Ok)?.valor?.fotos.orEmpty()
    }
    val daRua = rua?.takeIf { !ocultada(ocultos, it.id) }
    val daComunidade = fotos.firstOrNull { !ocultada(ocultos, it.id) }
    when (val escolhida = escolherFoto(daRua, daComunidade)) {
        is FotoEscolhida.DaRua -> FotoDeRuaNaParagem(escolhida.foto, carregar, aoAbrirPagina, aoOcultar = null)
        is FotoEscolhida.DaComunidade -> FotoDaParagem(escolhida.foto, carregar, aoOcultar = null, aoDenunciar = null)
        null -> Unit
    }
}
