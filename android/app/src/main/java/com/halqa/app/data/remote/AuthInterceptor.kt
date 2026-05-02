package com.halqa.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches a fresh Firebase ID token to every outbound request.
 *
 * Backend routes (except /api/health) require Authorization: Bearer <ID token>.
 * If no Firebase user is signed in, the request goes through unauthenticated and
 * the server will return 401 — handled at the call site as an "auth required"
 * error.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        // Skip token attachment for the health probe — it's intentionally public.
        if (req.url.encodedPath.endsWith("/api/health")) {
            return chain.proceed(req)
        }
        val user = FirebaseAuth.getInstance().currentUser ?: return chain.proceed(req)
        val token = try {
            runBlocking { user.getIdToken(false).await().token }
        } catch (_: Throwable) {
            null
        } ?: return chain.proceed(req)

        val authed = req.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
