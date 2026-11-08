package pt.pixellusitano.paragemcerta.dados

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * O pedido do botão "Iniciar sessão com o Google", ligado ao [nonce] que o servidor emitiu. É o pedido próprio de um botão: o
 * GetGoogleIdOption às vezes nem mostra a janela da Google (no Android 14 e seguintes, com várias contas no telemóvel).
 */
internal fun opcaoGoogle(webClientId: String, nonce: String): GetSignInWithGoogleOption =
    GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()

/**
 * Pede à Google (pelo Credential Manager) o token de quem entra.
 * Nulo = sem serviços Google neste telemóvel, sem contas ou a pessoa cancelou: o ecrã sugere o código por email.
 */
suspend fun pedirTokenGoogle(atividade: Activity, webClientId: String, nonce: String): String? = try {
    val pedido = GetCredentialRequest.Builder().addCredentialOption(opcaoGoogle(webClientId, nonce)).build()
    val credencial = CredentialManager.create(atividade).getCredential(atividade, pedido).credential
    if (credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        GoogleIdTokenCredential.createFrom(credencial.data).idToken
    } else {
        null
    }
} catch (e: NoCredentialException) {
    null // sem contas Google no telemóvel
} catch (e: GetCredentialException) {
    null
} catch (e: GoogleIdTokenParsingException) {
    null
}
