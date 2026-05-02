package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.SettingsDto
import com.halqa.app.data.remote.UpdateProfileRequest
import com.halqa.app.data.remote.UserDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * User profile + settings facade.
 *
 *  - Reads use Firestore listeners (real-time updates across devices).
 *  - Writes go through the backend API so server-side audit logging fires.
 */
object UserRepository {

    private fun firestore() = FirebaseFirestore.getInstance()

    /** Real-time profile feed for [uid]. Emits null until the first snapshot lands. */
    fun observeProfile(uid: String): Flow<UserDto?> = callbackFlow {
        val reg = firestore().collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val data = snap?.data
                if (data == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    UserDto(
                        uid = data["uid"] as? String ?: uid,
                        email = data["email"] as? String,
                        phoneNumber = data["phoneNumber"] as? String,
                        displayName = (data["displayName"] as? String).orEmpty(),
                        handle = (data["handle"] as? String).orEmpty(),
                        bio = (data["bio"] as? String).orEmpty(),
                        avatar = (data["avatar"] as? String).orEmpty(),
                        role = (data["role"] as? String).orEmpty().ifEmpty { "user" },
                        createdAt = data["createdAt"] as? String,
                        updatedAt = data["updatedAt"] as? String,
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchMe(): UserDto = ApiClient.api.getMe()

    suspend fun updateMe(req: UpdateProfileRequest): UserDto = ApiClient.api.updateMe(req)

    /** Real-time settings feed for [uid]. Emits a default DTO if the doc doesn't exist. */
    fun observeSettings(uid: String): Flow<SettingsDto> = callbackFlow {
        val reg = firestore().collection("users").document(uid)
            .collection("settings").document("default")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(SettingsDto())
                    return@addSnapshotListener
                }
                val data = snap?.data
                if (data == null) {
                    trySend(SettingsDto())
                    return@addSnapshotListener
                }
                trySend(
                    SettingsDto(
                        language = (data["language"] as? String) ?: "ar",
                        theme = (data["theme"] as? String) ?: "auto",
                        notificationsPush = (data["notificationsPush"] as? Boolean) ?: true,
                        notificationsEmail = (data["notificationsEmail"] as? Boolean) ?: true,
                        privacyShowOnline = (data["privacyShowOnline"] as? Boolean) ?: true,
                        privacyAllowMessages = (data["privacyAllowMessages"] as? String) ?: "everyone",
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateSettings(s: SettingsDto): SettingsDto = ApiClient.api.updateSettings(s)
}
