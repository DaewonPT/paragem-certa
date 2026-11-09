package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import pt.pixellusitano.paragemcerta.ui.tema.Azulejo
import pt.pixellusitano.paragemcerta.ui.tema.Icones

/** O campo branco de pesquisa para pôr sobre a faixa; as cores são fixas para não ficar branco sobre branco no escuro. */
@Composable
fun CampoAzulejo(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    acoes: KeyboardActions = KeyboardActions.Default,
    dica: String? = null,
) {
    TextField(
        value = valor, onValueChange = aoMudar, modifier = modifier,
        singleLine = true, shape = MaterialTheme.shapes.medium,
        label = { Text(rotulo) },
        placeholder = dica?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = { Icon(Icones.Pesquisa, contentDescription = null, tint = Azulejo.Cinza) },
        keyboardOptions = teclado, keyboardActions = acoes,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White,
            focusedTextColor = Azulejo.Tinta, unfocusedTextColor = Azulejo.Tinta,
            focusedLabelColor = Azulejo.Cobalto, unfocusedLabelColor = Azulejo.Cinza,
            focusedPlaceholderColor = Azulejo.Cinza, unfocusedPlaceholderColor = Azulejo.Cinza,
            cursorColor = Azulejo.Cobalto,
            // A seleção também é fixa: no escuro o esquema daria um azul-claro sobre o branco.
            selectionColors = TextSelectionColors(handleColor = Azulejo.Cobalto, backgroundColor = Azulejo.Cobalto.copy(alpha = 0.25f)),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}
