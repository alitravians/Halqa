package com.halqa.app.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.halqa.app.R
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In wrapper. Mirrors [PhoneAuthRepository] in shape: the screen
 * gets back a Firebase `uid` only after `/users/{uid}` is bootstrapped, which
 * closes the same phantom-guest path documented on [UserDocBootstrap].
 *
 * Flow
 * ----
 * 1. The screen calls [signInIntent] to get a one-shot `Intent` to launch
 *    via [androidx.activity.compose.rememberLauncherForActivityResult].
 * 2. The Activity result contains either an `idToken` we can hand to
 *    Firebase, or an [ApiException] (user cancelled, no Play Services,
 *    etc.).
 * 3. The screen calls [signInWithIdTokenAndBootstrap] which awaits
 *    `FirebaseAuth.signInWithCredential` AND
 *    [UserDocBootstrap.ensureUserDoc] before resolving. The
 *    `displayName` / `email` / `avatar` from the Google account are
 *    written to the doc on first sign-in.
 *
 * Web client id
 * -------------
 * `requestIdToken` requires the **Web** OAuth client id (not the Android
 * one), exposed by the google-services Gradle plugin as
 * `R.string.default_web_client_id`. The plugin generates this string
 * resource directly from `oauth_client[].client_type == 3` in
 * `android/app/google-services.json`. If the file is missing the entry
 * the resource is absent and this class fails to compile — that's the
 * intended fail-loud signal that the file needs to be refreshed after
 * the Google provider is enabled in Firebase Console.
 */
object GoogleAuthRepository {

    /**
     * Build (or rebuild) the [GoogleSignInClient] used to launch the
     * Google chooser. Cheap to call — Google Sign-In API memoises
     * internally — so the screen does not need to cache the result.
     */
    fun client(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context.applicationContext, options)
    }

    /**
     * Pulls the [GoogleSignInAccount] out of the result intent, throwing
     * [ApiException] on the cancelled / failed paths so the screen's
     * `runCatching` can surface a friendly Arabic string.
     */
    fun accountFromIntent(data: Intent?): GoogleSignInAccount {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return task.getResult(ApiException::class.java)
    }

    /**
     * Sign in to Firebase with the Google ID token, then bootstrap the
     * `/users/{uid}` doc. Returns the resolved Firebase `uid`.
     *
     * The bootstrap call is awaited inline (same suspend, same
     * coroutine) — the screen MUST NOT navigate to Main until this
     * resolves. This is the phantom-guest fix.
     */
    suspend fun signInWithIdTokenAndBootstrap(account: GoogleSignInAccount): String {
        val idToken = account.idToken
            ?: error("Google account did not return an idToken — check google-services.json oauth_client[].client_type=3")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = FirebaseAuthRepository.auth.signInWithCredential(credential).await()
        val uid = result.user?.uid
            ?: error("Firebase Google sign-in returned null user")
        val bootstrapResult = UserDocBootstrap.ensureUserDoc(
            uid = uid,
            phoneNumber = null,
            email = account.email,
            displayName = account.displayName,
            avatar = account.photoUrl?.toString(),
        )
        // Layla's GR5: same daily signup heartbeat as the Phone OTP path.
        // Google sign-ins don't carry a phone country code, so the
        // server buckets them under `unknown` — that's a meaningful
        // signal in its own right (a sudden burst of unknown-carrier
        // signups suggests Google flow abuse, separate from the SIM-farm
        // class of attack the phone breakdown surfaces).
        if (bootstrapResult == UserDocBootstrap.Result.Created) {
            SignupTelemetry.heartbeat(phoneCountryCode = null)
        }
        return uid
    }
}
