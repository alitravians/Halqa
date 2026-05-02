package com.halqa.app.data

import com.halqa.app.domain.AUTO_REVIEW_WINDOW_MS
import com.halqa.app.domain.PenaltyTier
import com.halqa.app.domain.StreamReview
import com.halqa.app.domain.StreamReviewState
import com.halqa.app.domain.SystemMessage
import com.halqa.app.domain.SystemMessageKind
import com.halqa.app.domain.ViolationCategory

/**
 * Mock seed data for the inbox. Replace with `Retrofit` + push notifications
 * in Phase E once the backend exposes the inbox feed.
 *
 * The two child-safety messages illustrate the canonical flow:
 *   1) [autoReviewOpened] — sent the moment the auto-system flags a stream.
 *   2) [autoReviewResult] — sent ~10 minutes later with the final verdict.
 */
object SystemMessages {

    /** Demo open review — used by [UnderReviewScreen] for the countdown. */
    val demoOpenReview: StreamReview = run {
        val now = System.currentTimeMillis()
        StreamReview(
            id = "rev_demo_001",
            streamId = "s7",
            hostName = "د.هند",
            openedAtEpochMs = now,
            autoCloseAtEpochMs = now + AUTO_REVIEW_WINDOW_MS,
            suspectedCategory = ViolationCategory.MinorAppearance,
            state = StreamReviewState.UnderReview,
            notesAr = "اشتبه النظام بظهور قاصر في إطار الكاميرا. يتم تحليل عيّنات الفيديو الآن.",
        )
    }

    /** Demo final verdict — used by [ReviewResultScreen]. */
    val demoVerdict: Pair<ViolationCategory, PenaltyTier> =
        ViolationCategory.AdultContent to PenaltyTier.Ban7d

    val seed: List<SystemMessage> = listOf(
        SystemMessage(
            id = "msg_review_open_demo",
            kind = SystemMessageKind.AutoReviewOpened,
            senderLabelAr = "نظام المراقبة الذاتي",
            titleAr = "بثك تحت المراجعة",
            bodyAr = "رصد النظام مخالفة محتملة في بثك. تجري المراجعة الآن خلال 10 دقائق، وستصلك نتيجة المراجعة في رسالة ثانية. تم إيقاف البث مؤقتاً حتى انتهاء المراجعة.",
            timeLabelAr = "الآن",
            emoji = "🛡️",
            unread = true,
            streamReviewId = demoOpenReview.id,
        ),
        SystemMessage(
            id = "msg_review_result_demo",
            kind = SystemMessageKind.AutoReviewResult,
            senderLabelAr = "نظام المراقبة الذاتي",
            titleAr = "نتيجة المراجعة",
            bodyAr = "بعد مراجعة بثك، حُدِّدت المخالفة كـ\"محتوى مخالف\" وصدر القرار: حظر 7 أيام. يمكنك تقديم اعتراض خلال 48 ساعة.",
            timeLabelAr = "10د",
            emoji = "⚖️",
            unread = true,
            streamReviewId = "rev_prior_001",
            verdictCategory = demoVerdict.first,
            verdictPenalty = demoVerdict.second,
        ),
        SystemMessage(
            id = "msg_welcome",
            kind = SystemMessageKind.Generic,
            senderLabelAr = "نظام Halqa",
            titleAr = "أهلاً بك في حلقة!",
            bodyAr = "اكمل ملفك الشخصي للحصول على هدية ترحيبية.",
            timeLabelAr = "اليوم",
            emoji = "🎁",
            unread = false,
        ),
        SystemMessage(
            id = "msg_announcement",
            kind = SystemMessageKind.Announcement,
            senderLabelAr = "Halqa Updates",
            titleAr = "تحديث جديد: ميني‑قيمز للجمهور",
            bodyAr = "أضفنا ألعاباً مصغّرة يقدر الجمهور يلعبها مع المذيع داخل البث.",
            timeLabelAr = "البارحة",
            emoji = "✨",
            unread = false,
        ),
    )
}
