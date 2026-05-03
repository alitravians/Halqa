package com.halqa.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that recovers from a 401 by force-refreshing the
 * Firebase ID token and retrying the request once with the fresh token.
 *
 * Why a separate component from [AuthInterceptor]:
 *
 *   - [AuthInterceptor] uses `getIdToken(forceRefresh = false)` so the
 *     request thread isn't blocked on a Firebase round-trip on every
 *     call. That's the right default — Firebase tokens are valid for an
 *     hour and the cached value is fast.
 *   - But cached tokens can be revoked server-side BEFORE they expire on
 *     the client (admin force-signout, password reset, security event,
 *     Firebase rotating its signing keys). When that happens, every
 *     subsequent call returns 401 and the user is silently locked out
 *     until they restart the app.
 *
 * OkHttp [Authenticator] is the standard hook for this: it fires only
 * after a 401 response and is allowed to block (it's invoked on the
 * connection pool's worker thread, not the caller's coroutine), so the
 * `runBlocking { getIdToken(true) }` here doesn't block any UI / call
 * site that wasn't already going to wait for the network response.
 *
 * Retry policy:
 *   - We only retry requests that already had an Authorization header.
 *     A 401 on an unauthenticated probe means "you must sign in", not
 *     "your token rotted".
 *   - We retry at most once. If the second attempt also 401s, the
 *     refresh token itself is dead and the user must sign in again. The
 *     caller surfaces the 401 as "auth required".
 */
class AuthAuthenticator : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        if (response.request.header("Authorization") == null) return null

        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val freshToken = try {
            runBlocking { user.getIdToken(/* forceRefresh = */ true).await().token }
        } catch (_: Throwable) {
            null
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
