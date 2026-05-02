package com.halqa.app.domain

/**
 * Inbox messages sent by Halqa itself — distinct from peer-to-peer chat. These
 * carry an explicit [kind] so the inbox UI can render them with a "نظام
 * المراقبة الذاتي" / "نظام Halqa" sender chip and route taps to the right
 * destination (under-review screen, review-result screen, etc.).
 *
 * Every system message is generated server-side (Phase E) and pushed via
 * FCM-style notification + persisted to the user's inbox. The Android client
 * simply renders what the server sent — no client-side decisioning.
 */
enum class SystemMessageKind {
    /** Welcome / onboarding-style notes from the platform. */
    Generic,

    /**
     * Stream was auto-flagged. The 10-minute review window is open. User can
     * tap to see the countdown screen ([streamReviewId] is non-null).
     */
    AutoReviewOpened,

    /**
     * 10-minute window closed. The system has determined a category + penalty.
     * Tap routes to the result screen with the verdict + appeal button.
     */
    AutoReviewResult,

    /** Manual moderator action: warning, ban, restoration. */
    ModeratorAction,

    /** Admin announcements (release notes, policy changes). */
    Announcement,
}

data class SystemMessage(
    val id: String,
    val kind: SystemMessageKind,
    val senderLabelAr: String,
    val titleAr: String,
    val bodyAr: String,
    val timeLabelAr: String,
    val emoji: String,
    val unread: Boolean,
    /** When [kind] is review-related, the [StreamReview.id] this message refers to. */
    val streamReviewId: String? = null,
    /** When [kind] is [SystemMessageKind.AutoReviewResult]. */
    val verdictCategory: ViolationCategory? = null,
    val verdictPenalty: PenaltyTier? = null,
)
