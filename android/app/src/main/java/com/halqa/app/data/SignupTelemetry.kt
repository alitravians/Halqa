package com.halqa.app.data

import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.SignupHeartbeatRequest
import retrofit2.HttpException

/**
 * Layla's T&S guardrail GR5 — closed-beta daily signup cap.
 *
 * Background
 * ----------
 * Layla flagged that the closed-beta auth methods (Phone OTP, Google
 * Sign-In) ship with `BYPASS_KYC_FOR_BETA=true`, which means anyone
 * downloading the APK can grandfather past KYC. Without a daily
 * counter + cap there is no fence preventing the closed beta from
 * silently drifting from "5 trusted users" to "200 soft-launched
 * users" — there is no Vercel signup endpoint for the phone path
 * (signups happen client-side through Firebase Phone Auth + the
 * client-side [UserDocBootstrap]) so a server-side telemetry hook
 * has to be ADDED rather than tapped from existing logs.
 *
 * This helper is the client-side half of GR5. It's invoked from
 * [PhoneAuthRepository.signInWithCredentialAndBootstrap] and
 * [GoogleAuthRepository.signInWithIdTokenAndBootstrap] after
 * [UserDocBootstrap.ensureUserDoc] reports
 * [UserDocBootstrap.Result.Created] — never on a return sign-in,
 * never when the bootstrap read failed (we don't double-count
 * across transient errors).
 *
 * Failure semantics
 * -----------------
 *  - HTTP 423 → throw [SignupCapReachedException]. The auth screens
 *    catch this and refuse to nav to Main, showing a non-dismissible
 *    Arabic dialog. The user's `/users/{uid}` doc was already created
 *    by [UserDocBootstrap] (it had to be, since `Result.Created`
 *    triggered this call) — that orphaned doc is harmless: when the
 *    user retries after staff unlocks the day, ensureUserDoc takes
 *    the existing-doc branch and patches whatever's missing.
 *  - HTTP 200 → fire and forget, the count is the staff dashboard's
 *    business, not the user's.
 *  - Anything else (4xx/5xx, IOException, JSON parse error) → log
 *    nothing and let the sign-in continue. The cap is a soft alert,
 *    not a hard fence; a transient error must NOT block sign-in.
 *
 * Why a separate object instead of inlining the call?
 * ---------------------------------------------------
 * Two places need this (Phone, Google), and a third path (GR3 DOB
 * gate fallback) may also need it. Centralising the HttpException →
 * SignupCapReachedException adapter keeps the failure semantics
 * consistent across call sites.
 */
object SignupTelemetry {

    /**
     * Fire the per-day signup heartbeat against the backend. Suspends
     * until the server responds or a non-retriable error fires.
     *
     * @param phoneCountryCode E.164 country dial code (e.g. "+966").
     *                         Pass `null` for the Google path; the
     *                         server buckets unknown carriers
     *                         separately.
     * @throws SignupCapReachedException when the backend returns HTTP
     *         423 (cap hit). The caller MUST treat this as a hard
     *         signal not to navigate the user to Main.
     */
    suspend fun heartbeat(phoneCountryCode: String?) {
        try {
            ApiClient.api.signupHeartbeat(
                SignupHeartbeatRequest(phoneCountryCode = phoneCountryCode),
            )
        } catch (httpErr: HttpException) {
            if (httpErr.code() == HTTP_LOCKED) {
                throw SignupCapReachedException(httpErr)
            }
            // Other 4xx / 5xx are best-effort — the cap fence isn't a
            // hard fence (the user already has a /users/{uid} doc)
            // and we'd rather let them in than block them on a 502
            // from an unrelated outage. Telemetry-side debugging
            // happens server-side via Vercel logs anyway.
        } catch (_: Throwable) {
            // Network / serialisation failure → swallow. Same
            // rationale as above.
        }
    }

    /** RFC 4918 / WebDAV "Locked" — Layla picked this code in spec. */
    private const val HTTP_LOCKED = 423
}

/**
 * Thrown by [SignupTelemetry.heartbeat] when the closed-beta daily
 * cap has been reached. The auth screens catch this specifically and
 * render the locked dialog instead of navigating to Main; any other
 * exception is treated as best-effort and ignored.
 */
class SignupCapReachedException(cause: Throwable? = null) :
    Exception("SIGNUP_DAILY_CAP_REACHED", cause)
