package com.halqa.app.data

/**
 * Authoritative snapshot of a `streams/{streamId}` Firestore document.
 *
 * This is the single source of truth for stream-level state. The Android
 * client *reads* it (via [StreamsRepository.observe]); only the backend
 * writes — server-side audit logging stays the only place where stream
 * lifecycle changes (start, end, viewer-count delta from LiveKit webhook,
 * gift accumulation in M2) are recorded.
 *
 * Mapping to Firestore fields:
 *
 *   streamId      <- doc.id (or `streamId` field, kept for compatibility)
 *   ownerUid      <- ownerUid
 *   roomName      <- roomName (LiveKit room identifier)
 *   title         <- title
 *   status        <- status      ("live" | "ended")
 *   viewerCount   <- viewerCount (driven by LiveKit webhook)
 *   startTime     <- startTime   (ISO 8601, server timestamp)
 *   endTime       <- endTime     (ISO 8601, server timestamp; null while live)
 *   giftTotal     <- giftTotal   (cumulative diamonds, lands in M2)
 *
 * Reserved for v0.2 (PK Mode):
 *   pkSelfScore, pkOpponentScore, pkOpponentStreamId
 *
 * Missing optional fields fall back to safe defaults so the UI never
 * crashes on an early/incomplete snapshot.
 */
data class StreamSnapshot(
    val streamId: String,
    val ownerUid: String,
    val roomName: String,
    val title: String = "",
    val status: String = "live",
    val viewerCount: Int = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val giftTotal: Long = 0L,
) {
    val isLive: Boolean get() = status == "live"
    val isEnded: Boolean get() = status == "ended"
}
