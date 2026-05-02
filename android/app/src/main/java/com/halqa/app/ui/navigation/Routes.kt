package com.halqa.app.ui.navigation

object Routes {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Auth = "auth"
    const val PhoneAuth = "phone_auth"

    const val Main = "main"
    const val Feed = "feed"
    const val Arena = "arena"
    const val GoLive = "go_live"
    const val Inbox = "inbox"
    const val Profile = "profile"

    const val LiveWatch = "live/{streamId}"
    fun liveWatch(streamId: String) = "live/$streamId"

    const val Wallet = "wallet"
    const val TopUp = "topup"

    const val PkArena = "pk_arena"
    const val AvatarBattle = "avatar_battle/{matchId}"
    fun avatarBattle(matchId: String) = "avatar_battle/$matchId"

    const val Settings = "settings"
    const val Kyc = "kyc"
    const val Terms = "terms"
    const val Privacy = "privacy"
    const val Community = "community"

    // Trust & Safety
    const val AgeGate = "safety/age_gate"
    const val UnderReview = "safety/under_review"
    const val ReviewResult = "safety/review_result"
}
