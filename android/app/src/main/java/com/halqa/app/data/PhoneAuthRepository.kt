package com.halqa.app.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * Phone-OTP sign-in wrapper around Firebase Auth.
 *
 * The previous incarnation of this flow (PR #8 + the original M0 scaffolding,
 * deleted in PR #16) had three separate problems that together made it
 * unshippable:
 *
 *  1. **Phantom-guest bug** — the screen called
 *     `navController.navigate(Routes.Main)` directly from the "send OTP" button
 *     without ever calling [PhoneAuthProvider.verifyPhoneNumber] or writing a
 *     `/users/{uid}` doc. Firebase Auth was never engaged at all, so the user
 *     landed on Main with `FirebaseAuth.currentUser == null` and every
 *     subsequent backend call 401'd. From the user's perspective they were
 *     "signed in" but the app silently broke.
 *
 *  2. **No code-entry step** — the original UI had a single phone-entry
 *     screen and zero OTP input UI, so even if the verify call had been
 *     wired it would have stalled at the SMS step. The fix is the two-step
 *     [com.halqa.app.ui.screens.auth.PhoneAuthScreen] (phone → OTP).
 *
 *  3. **No `/users/{uid}` write on first sign-in** — same root as bug 1.
 *     Email sign-up writes via [UserDocBootstrap.ensureUserDoc] before
 *     navigating, so the same handler must run synchronously for phone
 *     sign-up too, otherwise the listener-based UI
 *     ([UserRepository.observeProfile]) shows null forever and the role
 *     gate ([AuthRepository.resolveRoleForUid]) defaults to `Guest` until
 *     the user happens to trigger any backend `requireUser` write — which
 *     on the post-OTP "land on Main" path may never happen at all.
 *
 * This repo handles the Firebase side only. The screen owns the UI state
 * machine (phone → code-sent → verifying → success/failure) and is
 * responsible for calling [signInWithCredentialAndBootstrap] which
 * internally guarantees the `/users/{uid}` doc exists before resolving.
 */
object PhoneAuthRepository {

    /**
     * Result of a [requestVerification] call.
     *
     *  - [InstantVerification] — Firebase auto-resolved the SMS code (Play
     *    Services SMS Retriever). The credential is ready to sign in with;
     *    no user-visible OTP entry is necessary.
     *  - [CodeSent] — the SMS was sent. The screen must show the OTP entry
     *    UI and call [confirmVerificationCode] when the user types the code.
     *  - [Failed] — verifyPhoneNumber rejected the request (bad number,
     *    quota exceeded, SafetyNet missing, etc.). Surface to the user.
     */
    sealed class VerificationResult {
        data class InstantVerification(val credential: PhoneAuthCredential) : VerificationResult()
        data class CodeSent(val verificationId: String) : VerificationResult()
        data class Failed(val reason: PhoneAuthFailure, val cause: Throwable?) : VerificationResult()
    }

    /**
     * Reasons a phone-auth call can fail. Mirrors the taxonomy of
     * [com.halqa.app.domain.AuthFailure] used by email sign-in so the screen
     * can render the same "InvalidCredentials / Network / Unknown" Arabic
     * strings.
     */
    enum class PhoneAuthFailure {
        InvalidPhoneNumber,
        InvalidCode,
        QuotaExceeded,
        Network,
        Unknown,
    }

    /**
     * Send the OTP. The Firebase callback API is bridged into a coroutine via
     * [suspendCancellableCoroutine]; the suspension resolves on whichever of
     * the three terminal callbacks (Completed / CodeSent / Failed) fires
     * first. Timeout is the Firebase default (60s).
     *
     * The Activity reference is required by Firebase Phone Auth — it uses
     * Play Integrity (or the legacy SafetyNet) attestation, which needs an
     * Activity context, not a generic [android.content.Context]. Compose
     * exposes the host activity via `LocalContext.current as ComponentActivity`.
     */
    suspend fun requestVerification(
        activity: Activity,
        e164PhoneNumber: String,
    ): VerificationResult = suspendCancellableCoroutine { cont ->
        val auth = FirebaseAuthRepository.auth
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (cont.isActive) cont.resume(VerificationResult.InstantVerification(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                val reason = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> PhoneAuthFailure.InvalidPhoneNumber
                    is com.google.firebase.FirebaseTooManyRequestsException -> PhoneAuthFailure.QuotaExceeded
                    is com.google.firebase.FirebaseNetworkException -> PhoneAuthFailure.Network
                    else -> PhoneAuthFailure.Unknown
                }
                if (cont.isActive) cont.resume(VerificationResult.Failed(reason, e))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                if (cont.isActive) cont.resume(VerificationResult.CodeSent(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164PhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Build a [PhoneAuthCredential] from a verification id (returned by
     * [VerificationResult.CodeSent]) and the 6-digit code the user typed.
     * Pure: just a [PhoneAuthProvider.getCredential] call. The screen then
     * passes the credential to [signInWithCredentialAndBootstrap].
     */
    fun buildCredential(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    /**
     * Sign in with the supplied credential and (synchronously, on the same
     * suspend) write the `/users/{uid}` doc if it does not yet exist. The
     * screen is expected to navigate to Main only **after** this call
     * resolves successfully.
     *
     * Returns the resolved Firebase `uid` on success. On failure, throws —
     * the screen catches and maps. [FirebaseAuthInvalidCredentialsException]
     * means the user typed the wrong OTP.
     */
    suspend fun signInWithCredentialAndBootstrap(
        credential: PhoneAuthCredential,
        e164PhoneNumber: String,
    ): String {
        val result = FirebaseAuthRepository.auth.signInWithCredential(credential).await()
        val uid = result.user?.uid ?: error("Firebase phone sign-in returned null user")
        // Bootstrap the /users/{uid} doc BEFORE we return. The screen relies
        // on this contract to avoid the phantom-guest bug — Main must not
        // load until /users/{uid}.role is queryable.
        val bootstrapResult = UserDocBootstrap.ensureUserDoc(
            uid = uid,
            phoneNumber = e164PhoneNumber,
            email = null,
            bypassReason = UserDocBootstrap.BypassReason.PhoneOtp,
        )
        // Layla's GR5: fire the per-day signup heartbeat ONLY when this
        // call actually created the user doc. Return sign-ins (Patched /
        // Skipped) and read failures must not double-count toward the
        // closed-beta cap. The heartbeat throws SignupCapReachedException
        // on HTTP 423; the screen catches and refuses to nav to Main.
        if (bootstrapResult == UserDocBootstrap.Result.Created) {
            SignupTelemetry.heartbeat(extractCountryCode(e164PhoneNumber))
        }
        return uid
    }

    /**
     * Pull the dial code prefix out of an E.164 number (e.g. `+966501234567`
     * → `+966`). Returns `null` when the number isn't E.164-shaped, which
     * makes the server bucket the signup under `unknown` — preferable to
     * misattributing a malformed number to the wrong carrier.
     *
     * Country dial codes are 1–3 digits; we capture up to 3 to keep the
     * regex bounded. The canonical Saudi prefix is `+966`, the canonical
     * Egyptian prefix is `+20`, etc. The country picker in
     * [com.halqa.app.ui.screens.auth.PhoneAuthScreen] always normalises
     * to this shape before calling [requestVerification].
     */
    private fun extractCountryCode(e164: String): String? {
        val m = Regex("""^(\+\d{1,3})\d+$""").matchEntire(e164.trim())
        return m?.groupValues?.get(1)
    }
}
