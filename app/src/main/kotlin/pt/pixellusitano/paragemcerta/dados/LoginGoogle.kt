package pt.pixellusitano.paragemcerta.dados

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Pede à Google (pelo Credential Manager) o token de quem entra, ligado ao [nonce] que o servidor emitiu.
 * Nulo = sem serviços Google neste telemóvel, sem contas ou a pessoa cancelou: o ecrã sugere o código por email.
 */
suspend fun pedirTokenGoogle(atividade: Activity, webClientId: String, nonce: String): String? {
    val gestor = CredentialManager.create(atividade)
    suspend fun pedir(soContasJaAutorizadas: Boolean): String? {
        val opcao = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(soContasJaAutorizadas)
            .setNonce(nonce)
            .build()
        val credencial = gestor.getCredential(atividade, GetCredentialRequest.Builder().addCredentialOption(opcao).build()).credential
        if (credencial !is CustomCredential || credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        return GoogleIdTokenCredential.createFrom(credencial.data).idToken
    }
    return try {
        // Primeiro só as contas que já usaram a app; sem nenhuma, deixa escolher qualquer conta do telemóvel.
        try {
            pedir(soContasJaAutorizadas = true)
        } catch (e: NoCredentialException) {
            pedir(soContasJaAutorizadas = false)
        }
    } catch (e: GetCredentialException) {
        null
    } catch (e: GoogleIdTokenParsingException) {
        null
    }
}
