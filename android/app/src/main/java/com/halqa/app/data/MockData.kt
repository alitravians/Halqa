package com.halqa.app.data

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

object MockData {
    val streams: List<StreamPreview> = listOf(
        StreamPreview("s1", "حلقة الترفيه المسائية 🎤", "عبدالله الفنان", "abdulla.fnan", 1, 12_400, "ترفيه", "🔥 رائج", 280),
        StreamPreview("s2", "حلقة دردشة ودية", "نورة الكاتبة", "noura.kateb", 2, 3_240, "دردشة", "💬 دردشة", 320, isPk = true),
        StreamPreview("s3", "موسيقى وعود مباشر", "خالد العود", "khalid.oud", 3, 8_120, "موسيقى", "🎵 موسيقى", 200),
        StreamPreview("s4", "ألعاب فيفا — تحدي PK", "فهد جيمر", "fahad.gamer", 4, 5_460, "ألعاب", "🎮 PK", 30, isPk = true),
        StreamPreview("s5", "ركن الشعر النبطي", "محمد الشاعر", "m.shaer", 5, 1_180, "ثقافة", "📜 شعر", 250),
        StreamPreview("s6", "قهوة الصباح ☕", "ريم الخبيرة", "reem.k", 6, 940, "صباح", "☕ صباح", 350),
        StreamPreview("s7", "تعلم الإنجليزية مباشر", "د.هند", "dr.hind", 7, 2_330, "تعليم", "🎓 تعليم", 180),
        StreamPreview("s8", "حلقة طبخ خليجي", "أم سارة", "om.sara", 8, 4_120, "طبخ", "👩‍🍳 طبخ", 20),
        StreamPreview("s9", "تحدي الرياضيات السريع", "علي العالم", "ali.alalim", 9, 760, "تعليم", "🧠 تحدي", 220),
        StreamPreview("s10", "كاريوكي عربي PK", "سارة المغنية", "sara.sing", 10, 9_870, "موسيقى", "🎤 PK", 300, isPk = true),
        StreamPreview("s11", "حلقة استشارات تقنية", "م. يزيد", "yazeed.dev", 11, 480, "تقنية", "💻 تقنية", 210),
        StreamPreview("s12", "بث عائلي عام", "بيت الذكريات", "byt.zik", 12, 1_640, "عائلي", "👨‍👩‍👧 عائلي", 340),
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

    fun chatMessages(): List<ChatMsg> = listOf(
        ChatMsg("c1", "سعد", "بث جميل 🔥", isVip = true),
        ChatMsg("c2", "فاطمة", "❤️❤️"),
        ChatMsg("c3", "أحمد", "مذيع رهيب"),
        ChatMsg("c4", "ليلى", "أرسلت لك وردة"),
        ChatMsg("c5", "خالد", "ابدع كالعادة!", isMod = true),
        ChatMsg("c6", "نوف", "متابعة جديدة 👋"),
        ChatMsg("c7", "محمد", "الصوت واضح"),
        ChatMsg("c8", "ريم", "جاي PK جديد؟", isVip = true),
        ChatMsg("c9", "عبدالعزيز", "🌹🌹🌹"),
        ChatMsg("c10", "هند", "صار وقت طويل ما حضرت"),
    )
}
