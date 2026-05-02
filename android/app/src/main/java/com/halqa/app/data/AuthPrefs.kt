package com.halqa.app.data

import android.content.Context
import android.content.SharedPreferences
import com.halqa.app.domain.StaffAccount
import com.halqa.app.domain.UserRole

/**
 * Thin SharedPreferences-backed cache for the authenticated staff session.
 *
 * This is *not* a credential store — sign-in itself runs through
 * [AuthRepository] and (in production) hits the backend with a real password.
 * Once authentication succeeds we cache the resulting [StaffAccount] here so
 * the user does not have to re-enter credentials every cold start. Saudi /
 * Arabic users in particular expect their session (and especially staff-level
 * tools) to persist across restarts; resetting on launch would force a
 * re-login on every cold-start which is unacceptable.
 *
 * The schema is deliberately key-per-field rather than a serialised JSON
 * blob so future migrations (adding fields, dropping fields) don't break
 * older installs — missing keys just fall back to defaults.
 */
object AuthPrefs {
    private const val FILE = "halqa_auth_prefs"
    private const val KEY_ACCOUNT_ID = "account_id"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_ACCOUNT_ROLE = "account_role"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("AuthPrefs.init(context) must be called before use")

    fun saveAccount(account: StaffAccount) {
        requirePrefs().edit()
            .putString(KEY_ACCOUNT_ID, account.id)
            .putString(KEY_ACCOUNT_EMAIL, account.email)
            .putString(KEY_ACCOUNT_NAME, account.displayName)
            .putString(KEY_ACCOUNT_ROLE, account.role.name)
            .apply()
    }

    fun loadAccount(): StaffAccount? {
        val p = requirePrefs()
        val id = p.getString(KEY_ACCOUNT_ID, null) ?: return null
        val email = p.getString(KEY_ACCOUNT_EMAIL, null) ?: return null
        val name = p.getString(KEY_ACCOUNT_NAME, null) ?: return null
        val roleName = p.getString(KEY_ACCOUNT_ROLE, null) ?: return null
        val role = runCatching { UserRole.valueOf(roleName) }.getOrNull() ?: return null
        return StaffAccount(id = id, email = email, displayName = name, role = role)
    }

    fun clearAccount() {
        requirePrefs().edit()
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_ROLE)
            .apply()
    }
}
