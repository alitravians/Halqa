package com.halqa.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * User-facing error humanization for any [Throwable] caught around an
 * `ApiClient.api.*` call.
 *
 * ## Why this exists
 *
 * Every screen that catches an API failure used to do:
 *
 *     catch (t: Throwable) { showError(t.message ?: "تعذّر …") }
 *
 * For a Retrofit [HttpException] `t.message` returns the HTTP status
 * line, e.g. `"HTTP 403 Forbidden"`, NOT the JSON body. The backend
 * returns rich actionable strings inside the body —
 * `{"error": "Account is banned."}`, `{"error": "Insufficient coins:
 * need 100, have 50"}`, `{"error": "fullName must be at least 3
 * characters."}` — and we were throwing them on the floor.
 *
 * Net effect for the user:
 *   - Banned user taps Go-Live → sees `"HTTP 403 Forbidden"` instead
 *     of `"Account is banned."` (or the Arabic version we'll add to
 *     the backend later).
 *   - Sender hits the rate-limit cooldown → sees `"HTTP 429 Too
 *     Many Requests"` instead of `"Wait 47s before sending another
 *     gift to this host."`
 *   - KYC submission validation fails → sees `"HTTP 400 Bad Request"`
 *     instead of `"fullName must be at least 3 characters."`
 *
 * Three of those cases are entire user funnels collapsing on a
 * meaningless string.
 *
 * ## Resolution order
 *
 *   1. **HttpException with a parseable `{"error": "..."}` body**
 *      → return the body string verbatim. The backend writes
 *      English today; we surface it as-is so it's at minimum
 *      actionable (and we'll localise backend strings to Arabic in
 *      a follow-up — see backend/src/lib/auth.ts for the central
 *      message catalogue). Empty / unparseable body falls through.
 *
 *   2. **HttpException without a parseable body**
 *      → status-code-based Arabic message. Each maps to the
 *      observed user-visible failure mode for that code on this
 *      backend. 401 always means "session expired, sign in again"
 *      because [AuthAuthenticator] already retried once with a
 *      force-refreshed token before bubbling the failure up.
 *
 *   3. **Network errors** (UnknownHostException, SocketTimeout,
 *      IOException) → connection-failure Arabic copy. These are
 *      "no internet" / "DNS broken" / "TLS hung" — the most
 *      common in-the-wild user complaint and the one our previous
 *      `t.message` exposure ("Failed to connect to ...") was
 *      least useful for.
 *
 *   4. **Anything else** → caller's [fallback] or `t.message`.
 *
 * The body is consumed via `errorBody().string()`. Retrofit's
 * `Response` buffers the body, so reading it once here is safe
 * (the response object is dead by the time we're in the catch).
 */
fun Throwable.humanize(fallback: String? = null): String {
    return when (val t = this) {
        is HttpException -> {
            val parsed = parseErrorBody(t)
            if (!parsed.isNullOrBlank()) parsed
            else defaultMessageForStatus(t.code(), fallback)
        }
        is UnknownHostException ->
            "تعذّر الاتصال بالشبكة. تحقق من الإنترنت وحاول مجدداً."
        is SocketTimeoutException ->
            "انتهت مهلة الاتصال. حاول مجدداً."
        is IOException ->
            "خطأ في الشبكة. ${t.message ?: ""}".trim().ifEmpty { "خطأ في الشبكة." }
        else -> fallback ?: t.message ?: "حدث خطأ غير معروف"
    }
}

@Serializable
private data class ApiErrorBody(val error: String? = null)

private val errJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun parseErrorBody(t: HttpException): String? {
    return try {
        val body = t.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) return null
        val decoded = errJson.decodeFromString(ApiErrorBody.serializer(), body)
        decoded.error?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}

private fun defaultMessageForStatus(code: Int, fallback: String?): String = when (code) {
    400 -> "طلب غير صالح."
    401 -> "انتهت الجلسة. أعد تسجيل الدخول."
    402 -> "رصيد الكوينز غير كافٍ."
    403 -> "لا توجد صلاحية لتنفيذ هذا الإجراء."
    404 -> "العنصر المطلوب غير موجود."
    409 -> "تعارض في الحالة. أعد المحاولة."
    410 -> "هذا البث انتهى."
    422 -> "البيانات غير صالحة."
    429 -> "تجاوزت الحد المسموح. انتظر قليلاً وحاول مجدداً."
    in 500..599 -> "خطأ في الخادم. حاول لاحقاً."
    else -> fallback ?: "تعذّرت العملية (HTTP $code)"
}
