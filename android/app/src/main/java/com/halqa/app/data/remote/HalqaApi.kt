package com.halqa.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Halqa backend Retrofit interface.
 *
 * Base URL: BuildConfig.API_BASE_URL ("https://halqa-backend.vercel.app/api/").
 *
 * All routes (except `/health`) require an Authorization: Bearer <Firebase ID
 * token> header which the [AuthInterceptor] attaches automatically.
 */
interface HalqaApi {

    @GET("health")
    suspend fun health(): HealthResponse

    @GET("users/me")
    suspend fun getMe(): UserDto

    @POST("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): UserDto

    /**
     * Reem — Play 2024 in-app account-deletion requirement. The route
     * performs the cascade server-side (revoke Firebase Auth, zero out
     * /users/{uid}, soft-delete /wallets/{uid}, and write a
     * `account_deleted` row to `/audit/{uid}/events`) so the Android
     * client just needs to call this once, then sign out locally and
     * route back to [Routes.Auth].
     *
     * Returns [SimpleOk] on success. Khalid owns the backend; this
     * Retrofit method is the wire contract.
     */
    @DELETE("users/me")
    suspend fun deleteMe(): SimpleOk

    @GET("settings")
    suspend fun getSettings(): SettingsDto

    @POST("settings")
    suspend fun updateSettings(@Body body: SettingsDto): SettingsDto

    @GET("kyc/status")
    suspend fun getKycStatus(): KycStatusDto

    @POST("kyc/submit")
    suspend fun submitKyc(@Body body: KycSubmitRequest): KycStatusDto

    @POST("streams/end")
    suspend fun endStream(@Body body: EndStreamRequest): SimpleOk

    @POST("livekit/token")
    suspend fun livekitToken(@Body body: LiveKitTokenRequest): LiveKitTokenResponse

    @GET("audit/{uid}")
    suspend fun audit(@Path("uid") uid: String): AuditResponse

    @GET("gifts/catalog")
    suspend fun giftCatalog(): GiftCatalogResponse

    @POST("gifts/send")
    suspend fun sendGift(@Body body: SendGiftRequest): SendGiftResponse

    @GET("wallet/me")
    suspend fun getWallet(): WalletDto

    @POST("wallet/topup")
    suspend fun topupWallet(): TopupResponse

    /**
     * Layla's T&S guardrail GR5. Fired exactly once per first-time
     * sign-in (Phone OTP and Google paths) immediately after
     * [com.halqa.app.data.UserDocBootstrap] reports
     * [com.halqa.app.data.UserDocBootstrap.Result.Created]. The backend
     * increments a daily counter at `/metrics/signups/days/{YYYY-MM-DD}`
     * and returns HTTP 423 once the closed-beta cap is reached so the
     * Android client can show "السقف اليومي للتسجيل" + refuse to nav.
     * The backend route doc explains the fallback rationale (Firestore
     * rules can't compute the current date so client + route is the
     * enforcement, per Layla's pre-approved fallback path).
     *
     * Body: `{ phoneCountryCode: "+966" | … }` — the canonical dial
     * code; the route falls back to "unknown" when omitted.
     */
    @POST("signup/heartbeat")
    suspend fun signupHeartbeat(@Body body: SignupHeartbeatRequest): SignupHeartbeatResponse
}

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val service: String? = null,
    val time: String? = null,
    val hasFirebase: Boolean? = null,
    val hasLivekit: Boolean? = null,
)

@Serializable
data class UserDto(
    val uid: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val displayName: String = "",
    val handle: String = "",
    val bio: String = "",
    val avatar: String = "",
    val role: String = "user",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val handle: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
)

@Serializable
data class SettingsDto(
    val language: String = "ar",
    val theme: String = "auto",
    val notificationsPush: Boolean = true,
    val notificationsEmail: Boolean = true,
    val privacyShowOnline: Boolean = true,
    val privacyAllowMessages: String = "everyone",
)

@Serializable
data class KycStatusDto(
    val status: String = "none",
    val submittedAt: String? = null,
    val approvedAt: String? = null,
    val reason: String? = null,
    val identityType: String? = null,
)

@Serializable
data class KycSubmitRequest(
    val identityType: String,
    val fullName: String,
    val documentNumber: String,
    val images: List<String>,
)

@Serializable
data class LiveStreamDto(
    val streamId: String,
    val ownerUid: String,
    val title: String,
    val startTime: String? = null,
    val viewerCount: Int = 0,
    val roomName: String,
)

@Serializable
data class EndStreamRequest(val streamId: String)

@Serializable
data class SimpleOk(val ok: Boolean = true)

@Serializable
data class LiveKitTokenRequest(
    val roomName: String,
    val role: String,
    val streamTitle: String? = null,
)

@Serializable
data class LiveKitTokenResponse(val token: String, val url: String)

@Serializable
data class AuditResponse(val entries: List<AuditEntryDto> = emptyList())

@Serializable
data class AuditEntryDto(
    val id: String,
    val action: String,
    val timestamp: String,
    @SerialName("metadata") val metadata: JsonElement? = null,
)

@Serializable
data class GiftCatalogResponse(val gifts: List<GiftDto> = emptyList())

@Serializable
data class GiftDto(
    val id: String,
    val name: String,
    val emoji: String,
    val priceCoins: Int,
    val yieldDiamonds: Int,
    val tier: String = "basic",
)

@Serializable
data class SendGiftRequest(
    val streamId: String,
    val giftId: String,
    val count: Int = 1,
)

@Serializable
data class SendGiftResponse(
    val ok: Boolean = false,
    val txnId: String? = null,
    val balance: WalletBalanceDto? = null,
    val gift: GiftEchoDto? = null,
    val total: GiftTotalDto? = null,
)

@Serializable
data class GiftEchoDto(val id: String, val name: String, val emoji: String)

@Serializable
data class GiftTotalDto(val coins: Int, val diamonds: Int, val count: Int)

@Serializable
data class WalletDto(
    val uid: String? = null,
    val coins: Long = 0L,
    val diamonds: Long = 0L,
    val coinsSpent: Long = 0L,
    val diamondsEarned: Long = 0L,
)

@Serializable
data class WalletBalanceDto(val coins: Long = 0L, val diamonds: Long = 0L)

@Serializable
data class SignupHeartbeatRequest(
    /**
     * E.164 country dial code, e.g. `+966` for Saudi Arabia. The
     * backend normalises this to a stable Firestore field name and
     * uses it for the per-day carrier breakdown a staff member sees
     * on the GR5 admin dashboard. Null / unparseable values are
     * accepted by the server and bucketed under `unknown`.
     */
    val phoneCountryCode: String? = null,
)

@Serializable
data class SignupHeartbeatResponse(
    val ok: Boolean = false,
    /** Total signups (across all carriers) recorded for today's UTC bucket after this call. */
    val count: Int = 0,
    /**
     * `true` when this signup tipped the day over the closed-beta cap.
     * The signup itself succeeded; the NEXT signup will be rejected
     * with HTTP 423 until staff manually unlock or the day rolls.
     */
    val locked: Boolean = false,
)

@Serializable
data class TopupResponse(
    val ok: Boolean = false,
    val pack: TopupPackDto? = null,
    val balance: WalletBalanceDto? = null,
)

@Serializable
data class TopupPackDto(val id: String, val coins: Long, val priceLabel: String)
