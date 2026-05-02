package com.halqa.app.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.halqa.app.ui.theme.HalqaColors

/**
 * Identity / role badges shown next to a user's name on Profile, Feed cards, and
 * the Live Watch header. The set is intentionally small (8 types) and ordered by
 * priority so [BadgeRow] can deterministically pick the most-important badges
 * when display space is limited.
 */
enum class BadgeType(
    val labelAr: String,
    val descriptionAr: String,
    val icon: ImageVector,
    val tint: Color,
    val priority: Int,
) {
    /** Top-level platform admin. Trumps everything. */
    Admin(
        labelAr = "مسؤول",
        descriptionAr = "مسؤول إداري في Halqa",
        icon = Icons.Filled.AdminPanelSettings,
        tint = HalqaColors.Gold,
        priority = 100,
    ),

    /** Halqa employee — non-admin staff (HR, design, growth, etc.). */
    Staff(
        labelAr = "موظف Halqa",
        descriptionAr = "موظف رسمي في Halqa",
        icon = Icons.Filled.WorkspacePremium,
        tint = HalqaColors.Gold,
        priority = 90,
    ),

    /** Stream-moderation team (Mohammed). Can issue 60s warnings + temp bans. */
    Moderator(
        labelAr = "مراقب",
        descriptionAr = "فريق مراقبة البثوث",
        icon = Icons.Filled.Shield,
        tint = HalqaColors.BrandLight,
        priority = 80,
    ),

    /** Violation-scout team (Faisal). Captures evidence clips. */
    Scout(
        labelAr = "صياد المخالفين",
        descriptionAr = "فريق رصد المخالفات",
        icon = Icons.Filled.Visibility,
        tint = HalqaColors.Pink,
        priority = 70,
    ),

    /** Verified by an agency partnership — the equivalent of Twitter blue. */
    VerifiedAgency(
        labelAr = "موثَّق بوكالة",
        descriptionAr = "حساب موثَّق عبر وكالة شريكة",
        icon = Icons.Filled.Verified,
        tint = HalqaColors.BrandLight,
        priority = 60,
    ),

    /** KYC-completed creator (eligible for payouts). */
    KycVerified(
        labelAr = "هوية مُتحقَّقة",
        descriptionAr = "اجتاز التحقق من الهوية (KYC)",
        icon = Icons.Filled.CheckCircle,
        tint = HalqaColors.Success,
        priority = 50,
    ),

    /** Pre-launch / first 90-day creator. */
    FoundingCreator(
        labelAr = "مُنشئ مؤسس",
        descriptionAr = "من أوائل المنشئين على Halqa",
        icon = Icons.Filled.RocketLaunch,
        tint = HalqaColors.Pink,
        priority = 40,
    ),

    /** Top-1% creator by gifts received in last 30 days. */
    TopCreator(
        labelAr = "نجم Halqa",
        descriptionAr = "ضمن أفضل 1% منشئ هذا الشهر",
        icon = Icons.Filled.Whatshot,
        tint = HalqaColors.GoldLight,
        priority = 30,
    ),
}

/**
 * Sorts badges by priority (highest first) and returns at most [limit] of them.
 * Used by compact placements (feed cards, live-watch header) where we can't
 * realistically render all of a user's badges.
 */
fun List<BadgeType>.topPriority(limit: Int): List<BadgeType> =
    sortedByDescending { it.priority }.take(limit)
