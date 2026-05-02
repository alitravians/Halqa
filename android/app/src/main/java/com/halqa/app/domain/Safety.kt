package com.halqa.app.domain

/**
 * Trust & Safety primitives — the data shapes the Live Watch UI, the future
 * Moderator screen (Phase C), and the Web Admin panel (Phase E) all read from.
 *
 * The state machine for an automated child-safety review is:
 *
 *   ACTIVE
 *     └─ (auto-detector flags potential minor / banned content)
 *        ▼
 *   FLAGGED_AUTO
 *     └─ system opens a 10-minute review window
 *        ▼
 *   UNDER_REVIEW  ──── (after auto-window expires OR mod manual decision)
 *        ├──▶ VIOLATION_CONFIRMED  ─▶ apply [PenaltyTier]
 *        └──▶ CLEARED              ─▶ stream resumes, no penalty recorded
 *
 * SUSPENDED is the terminal state when a permanent or long ban is enforced.
 */
enum class StreamReviewState {
    Active,
    FlaggedAuto,
    UnderReview,
    ViolationConfirmed,
    Cleared,
    Suspended,
}

enum class ViolationCategory(val labelAr: String, val isCriticalChildSafety: Boolean = false) {
    MinorHost(labelAr = "بث من قِبَل قاصر (دون 18)", isCriticalChildSafety = true),
    MinorAppearance(labelAr = "ظهور قاصر في البث", isCriticalChildSafety = true),
    AdultContent(labelAr = "محتوى إباحي/موحٍ"),
    Violence(labelAr = "عنف/تحريض"),
    Harassment(labelAr = "تنمر/إهانة/خطاب كراهية"),
    Fraud(labelAr = "احتيال/كوينز مسروقة"),
    Other(labelAr = "أخرى"),
}

/**
 * Standard escalation ladder. Time fields are durations measured from the
 * moment the penalty is applied. PERMANENT has no end.
 */
enum class PenaltyTier(val labelAr: String, val durationHours: Long?) {
    Warning(labelAr = "تحذير", durationHours = 0L),
    Ban24h(labelAr = "حظر 24 ساعة", durationHours = 24L),
    Ban7d(labelAr = "حظر 7 أيام", durationHours = 7L * 24L),
    Ban30d(labelAr = "حظر 30 يوم", durationHours = 30L * 24L),
    BanPermanent(labelAr = "حظر دائم + إحالة قانونية", durationHours = null),
}

/**
 * Maps a violation category to the recommended starting penalty per Layla's
 * Trust & Safety guidance. Mods can escalate (never reduce below this) and
 * Admin can override either way. Critical child-safety violations skip
 * straight to permanent ban + escalation.
 */
fun ViolationCategory.recommendedPenalty(priorOffenses: Int): PenaltyTier = when (this) {
    ViolationCategory.MinorHost,
    ViolationCategory.MinorAppearance -> PenaltyTier.BanPermanent

    ViolationCategory.AdultContent -> when (priorOffenses) {
        0 -> PenaltyTier.Ban7d
        1 -> PenaltyTier.Ban30d
        else -> PenaltyTier.BanPermanent
    }

    ViolationCategory.Violence,
    ViolationCategory.Harassment -> when (priorOffenses) {
        0 -> PenaltyTier.Warning
        1 -> PenaltyTier.Ban24h
        2 -> PenaltyTier.Ban7d
        else -> PenaltyTier.Ban30d
    }

    ViolationCategory.Fraud -> PenaltyTier.Ban30d
    ViolationCategory.Other -> PenaltyTier.Warning
}

/**
 * One open auto-review case. The 10-minute window is a hard deadline: when
 * [autoCloseAtEpochMs] is reached, the backend moderator-bot service must
 * either escalate to a human moderator or auto-resolve to a default penalty
 * derived from [suspectedCategory] (see Phase E worker).
 */
data class StreamReview(
    val id: String,
    val streamId: String,
    val hostName: String,
    val openedAtEpochMs: Long,
    val autoCloseAtEpochMs: Long,
    val suspectedCategory: ViolationCategory,
    val state: StreamReviewState,
    val notesAr: String,
)

/** Default review-window length per ali's policy: 10 minutes. */
const val AUTO_REVIEW_WINDOW_MS: Long = 10L * 60L * 1000L
