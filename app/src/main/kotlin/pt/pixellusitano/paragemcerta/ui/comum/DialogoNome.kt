package pt.pixellusitano.paragemcerta.ui.comum

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pt.pixellusitano.paragemcerta.R

/** Pede um nome (guardar um local, mudar-lhe o nome); não deixa confirmar em branco. */
@Composable
fun DialogoNome(titulo: String, inicial: String, aoConfirmar: (String) -> Unit, aoCancelar: () -> Unit) {
    var nome by rememberSaveable { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(nome, { nome = it }, shape = MaterialTheme.shapes.medium, singleLine = true, label = { Text(stringResource(R.string.dialogo_nome)) })
        },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(nome.trim()) }, enabled = nome.isNotBlank()) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}
