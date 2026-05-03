package com.halqa.app.ui.components

/**
 * Returns a single-character initial that is safe to render as the
 * letter inside an avatar circle.
 *
 * The display string is sourced from a remote document (e.g.
 * `users/{uid}.displayName`, `streams/{id}.title`) so we cannot
 * assume it is non-empty: a Firestore doc may be partially-written,
 * an admin can blank a field from the console, an old document may
 * have predated a default-fallback that was added later, or a
 * `substringBefore(' ')` of a leading-space string may collapse to
 * `""`. Calling [String.first] on any of those throws
 * [NoSuchElementException], which surfaces as a runtime crash that
 * trips the `LiveData` exception handler and tears down whatever
 * screen was rendering the avatar.
 *
 * The replacement glyph is the Arabic-locale-appropriate "؟" (U+061F)
 * so RTL screens don't inherit a Latin question mark out of context.
 */
const val AVATAR_INITIAL_FALLBACK: String = "؟"

fun avatarInitial(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    val ch = trimmed.firstOrNull() ?: return AVATAR_INITIAL_FALLBACK
    return ch.toString()
}
