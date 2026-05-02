package com.halqa.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
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

    @GET("settings")
    suspend fun getSettings(): SettingsDto

    @POST("settings")
    suspend fun updateSettings(@Body body: SettingsDto): SettingsDto

    @GET("kyc/status")
    suspend fun getKycStatus(): KycStatusDto

    @POST("kyc/submit")
    suspend fun submitKyc(@Body body: KycSubmitRequest): KycStatusDto

    @GET("streams/live")
    suspend fun getLiveStreams(): LiveStreamsResponse

    @POST("streams/end")
    suspend fun endStream(@Body body: EndStreamRequest): SimpleOk

    @POST("livekit/token")
    suspend fun livekitToken(@Body body: LiveKitTokenRequest): LiveKitTokenResponse

    @GET("audit/{uid}")
    suspend fun audit(@Path("uid") uid: String): AuditResponse
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
data class LiveStreamsResponse(val streams: List<LiveStreamDto> = emptyList())

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
