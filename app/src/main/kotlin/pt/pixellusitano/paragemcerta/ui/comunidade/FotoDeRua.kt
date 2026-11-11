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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.pixellusitano.paragemcerta.R
import pt.pixellusitano.paragemcerta.core.fotos.FotoDeRua
import pt.pixellusitano.paragemcerta.core.fotos.anoDaFoto
import pt.pixellusitano.paragemcerta.core.fotos.paginaDaFoto
import pt.pixellusitano.paragemcerta.dados.EstadoFicheiro
import pt.pixellusitano.paragemcerta.dados.FicheiroDoServidor
import pt.pixellusitano.paragemcerta.dados.Preferencias

// "foto:<id>" nas Preferencias; até elas responderem (null) conta como ocultada: uma foto que a pessoa
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
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = aoAbrirPagina)
                    .heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            )
            MenuDaFoto(aoOcultar, aoDenunciar = null)
        }
    }
}

/**
 * A foto de rua da paragem por baixo do alvo da bússola, sem menu: a de [rua] (a do ficheiro, para esta paragem), menos a que a
 * pessoa ocultou no ecrã da paragem. Não pede nada ao servidor; sem [rua] (a viagem não sabe qual é a paragem) não mostra nada.
 */
@Composable
fun FotoNaBussola(rua: FotoDeRua?, preferencias: Preferencias, carregar: suspend (String) -> ByteArray?, aoAbrirPagina: (String) -> Unit) {
    fotoDeRuaVisivel(rua, preferencias)?.let { FotoDeRuaNaParagem(it, carregar, aoAbrirPagina, aoOcultar = null) }
}
