package com.halqa.app.data

import com.halqa.app.domain.BadgeType

data class StreamPreview(
    val id: String,
    val title: String,
    val hostName: String,
    val hostHandle: String,
    val avatarSeed: Int,
    val viewers: Int,
    val category: String,
    val tag: String,
    val coverHue: Int,
    val isPk: Boolean = false,
    val hostBadges: List<BadgeType> = emptyList(),
)

/**
 * The signed-in user's identity surface (consumed by the Profile screen and
 * other "who am I" reads). Real instances now come from [UserRepository] via
 * Firebase Auth + Firestore — this data class is the in-memory shape only.
 */
data class CurrentUser(
    val displayName: String,
    val handle: String,
    val bio: String,
    val followers: Int,
    val following: Int,
    val level: Int,
    val streamsHosted: Int,
    val badges: List<BadgeType>,
)

data class ChatMsg(
    val id: String,
    val user: String,
    val message: String,
    val isMod: Boolean = false,
    val isVip: Boolean = false,
)

data class Gift(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val tier: GiftTier,
)

enum class GiftTier { Basic, Rare, Epic, Legendary }

data class CoinPackage(
    val id: String,
    val name: String,
    val coins: Long,
    val priceSar: Double,
    val bonusPercent: Int,
    val highlight: Boolean = false,
)

data class PkMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val available: Boolean,
)

/**
 * Static catalog content (gifts, coin packages, PK modes). Live, user-generated
 * data — streams, profiles, audit logs, KYC submissions — is now sourced
 * exclusively from Firestore + the Halqa backend (no mock fallbacks). Do not
 * reintroduce mock streams or mock current-user data here.
 */
object MockData {
    /**
     * Empty by design: the live feed is sourced from
     * [com.halqa.app.data.StreamsRepository.liveStreams] (Firestore) so the
     * UI shows whatever real broadcasters are publishing right now.
     */
    val streams: List<StreamPreview> = emptyList()

    /**
     * Empty default for cold-start renders before Firebase Auth + Firestore
     * resolve. Real values come from [com.halqa.app.data.UserRepository.observeProfile].
     */
    val currentUser: CurrentUser = CurrentUser(
        displayName = "",
        handle = "",
        bio = "",
        followers = 0,
        following = 0,
        level = 1,
        streamsHosted = 0,
        badges = emptyList(),
    )

    val gifts: List<Gift> = listOf(
        Gift("g1", "وردة", "🌹", 1, GiftTier.Basic),
        Gift("g2", "قلب", "❤️", 5, GiftTier.Basic),
        Gift("g3", "نار", "🔥", 10, GiftTier.Basic),
        Gift("g4", "تاج", "👑", 99, GiftTier.Rare),
        Gift("g5", "عود", "🪘", 199, GiftTier.Rare),
        Gift("g6", "أسد", "🦁", 499, GiftTier.Epic),
        Gift("g7", "صقر", "🦅", 999, GiftTier.Epic),
        Gift("g8", "ماسة", "💎", 1999, GiftTier.Epic),
        Gift("g9", "صاروخ", "🚀", 4999, GiftTier.Legendary),
        Gift("g10", "قلعة", "🏰", 9999, GiftTier.Legendary),
        Gift("g11", "تنين ذهبي", "🐉", 19999, GiftTier.Legendary),
        Gift("g12", "كوكب", "🪐", 49999, GiftTier.Legendary),
    )

    val coinPackages: List<CoinPackage> = listOf(
        CoinPackage("p1", "Starter", 70, 4.99, 0),
        CoinPackage("p2", "Casual", 380, 24.99, 8),
        CoinPackage("p3", "Fan", 800, 49.99, 14),
        CoinPackage("p4", "Supporter", 1_700, 99.0, 20, highlight = true),
        CoinPackage("p5", "VIP", 9_500, 499.0, 33),
        CoinPackage("p6", "Whale", 20_500, 999.0, 45),
        CoinPackage("p7", "Legend", 44_000, 1_999.0, 57),
    )

    val pkModes: List<PkMode> = listOf(
        PkMode("avatar", "معركة Avatar 3D", "أفاتار يتقاتل بصرياً بين الشاشتين", "⚔️", true),
        PkMode("minigames", "ألعاب الجمهور", "تحديات سريعة لكل المشاهدين", "🎮", true),
        PkMode("wheel", "عجلة العقوبات", "الخاسر يدور والجمهور يقترح", "🎡", true),
        PkMode("skill", "تحدي المواهب", "الجمهور يصوت + AI يقيم", "🎤", false),
        PkMode("factions", "حرب الفصائل", "نار، ماء، أرض، هواء — أسبوعي", "🔥", false),
        PkMode("boss", "معركة الزعيم", "حدث يومي تعاوني للمنصة كلها", "🐉", false),
    )

    /**
     * Empty by design: real chat is delivered through the LiveKit data
     * channel + Firestore once the stream pipeline is wired end-to-end.
     */
    fun chatMessages(): List<ChatMsg> = emptyList()
}
