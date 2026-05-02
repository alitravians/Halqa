package com.halqa.app.domain

/**
 * The authenticated principal — a single source of truth for the current
 * session. Mirrors what the server will eventually return on a successful
 * sign-in, but lives here so the UI can be written against the final shape
 * before the backend is wired up.
 */
data class StaffAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
)

/** Reason the latest sign-in attempt failed. */
enum class AuthFailure {
    InvalidCredentials,
    AccountDisabled,
    Network,
    Unknown,
}

/**
 * Result of a sign-in call. Modelled as a sealed class instead of `Result<T>`
 * so the failure side carries a typed reason without throwing.
 */
sealed interface AuthResult {
    data class Success(val account: StaffAccount) : AuthResult
    data class Failure(val reason: AuthFailure) : AuthResult
}
