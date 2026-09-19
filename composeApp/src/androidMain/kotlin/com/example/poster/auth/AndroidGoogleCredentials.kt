package com.example.poster.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Google sign-in through Credential Manager.
 *
 * Credential Manager rather than the older Google Sign-In client: that one is
 * deprecated, and this is the sheet people already know from every other app on
 * the phone — it lists the accounts on the device instead of starting a
 * browser.
 *
 * [serverClientId] is the *Web* client id, not the Android one. This is the
 * detail that costs everybody an afternoon: the Android client exists so Google
 * recognises the app by its signing certificate, and the token has to be
 * addressed to the Web client, because that is what the server checks it
 * against.
 */
class AndroidGoogleCredentials(
    // The foreground Activity, resolved at call time — Credential Manager must
    // launch its sheet from an Activity context, not the application one (that
    // fails on MIUI: "ensure the 'context' parameter is an Activity-based
    // context"). Null when nothing is on screen, which is not a state a sign-in
    // tap can actually reach.
    private val activityProvider: () -> Context?,
    private val serverClientId: String,
) : GoogleCredentials {

    override val available: Boolean get() = serverClientId.isNotBlank()

    override suspend fun requestIdToken(): String? {
        if (!available) return null

        val context = activityProvider()
            ?: throw GoogleCredentialsException("No active screen to start Google sign-in from")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    // False, so the sheet offers every account on the device
                    // rather than only ones that have used this app before.
                    // Filtering to previous users shows an empty sheet to
                    // everybody signing in for the first time, which is
                    // everybody.
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (cancelled: GetCredentialCancellationException) {
            // Closing the sheet is an answer, not an error.
            Log.i(TAG, "google sign-in cancelled")
            return null
        } catch (none: NoCredentialException) {
            // No Google account on the device, or none the sheet would offer.
            Log.w(TAG, "google sign-in: no credential available", none)
            throw GoogleCredentialsException("No Google account available on this device", none)
        } catch (failed: GetCredentialException) {
            // Everything else: a client id that does not match this app's
            // signing certificate, Play Services out of date, a device with no
            // Google at all. These are all things somebody can act on, and all
            // of them used to arrive here as silence.
            Log.w(TAG, "google sign-in failed: ${failed.type}", failed)
            throw GoogleCredentialsException(failed.message ?: failed.type, failed)
        }

        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Log.w(TAG, "google sign-in returned an unexpected credential: ${credential.type}")
            throw GoogleCredentialsException("Unexpected credential type ${credential.type}")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}
