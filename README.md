# Halqa (حلقة)

> Arabic-first live streaming with an innovative PK Arena. Stream, watch, battle, earn.
>
> **حلقتك تبدأ هنا** — *Where moments gather*

Halqa is a native **Android** live streaming app combined with a **Next.js** backend &
admin panel. It is the Arabic-world answer to BIGO LIVE / TikTok LIVE / Super Live —
with PK features that go far beyond "send the most gifts to win".

## 📁 Repo layout

```
.
├── android/            Native Android app  (Kotlin 2.0 + Jetpack Compose + Hilt)
└── backend/            Next.js 14 API + Admin Dashboard (Vercel-ready)
```

## 📱 Android app

* **Stack**: Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Retrofit, Room,
  DataStore, Coil, Lottie, LiveKit Android SDK 2.18.
* **Architecture**: single-activity MVVM, `StateFlow` for UI state, NavGraph for
  routing, full RTL/Arabic support from day 1.
* **Min SDK**: 24 (Android 7.0). **Target SDK**: 34.

### Build

```bash
cd android
./gradlew :app:assembleDebug          # debug APK for Appetize.io / sideload
./gradlew :app:bundleRelease          # release AAB for Google Play
```

* APK output: `android/app/build/outputs/apk/debug/app-debug.apk`
* AAB output: `android/app/build/outputs/bundle/release/app-release.aab`

### Screens (MVP)

Splash → Onboarding (3 pages) → Auth (Phone / Google / Email / Guest) →
Main scaffold (Feed · Arena · Go-Live · Inbox · Profile) →
PK Arena (Avatar Battle 3D, Mini-Games, Wheel of Penalty) →
Live Watch (chat overlay, gift panel, PK button) →
Wallet (coin packages, top-up, earnings) →
Profile · Legal (Terms / Privacy / Community).

## 🌐 Backend

Next.js 14 (App Router) + Prisma + Postgres, deployable to Vercel. Hosts:

* Public marketing site (`/`)
* Future API routes for streams, gifts, wallet, PK matches
* Admin panel for stream moderation, KYC review, payouts

## 🛡️ Trust & Safety (per advisory council)

* New broadcasters start as **Pending KYC**.
* KYC required when earnings ≥ 500 SAR.
* Minimum withdrawal threshold: **375 SAR (~$100)**.
* Age gate: **18+ broadcasters**, 13+ viewers (parental consent).
* Stream moderation team workflow:
  * **3 in-stream warnings (60s each)** before account ban.
  * **Violation Scout** records 30-40s clip → Monitoring team reviews → 1h / 1d / 30d ban
    based on severity.

## 👥 Devin advisory council

The product is co-designed with a council of dedicated Devin sub-sessions:

| # | Role                       | Focus                                         |
|---|----------------------------|-----------------------------------------------|
| 1 | UX/UI Designer             | RTL, Material 3, Arabic typography            |
| 2 | Principal Engineer         | Architecture, scalability, LiveKit, security  |
| 3 | Product / Business         | Coins pricing, retention, MENA market         |
| 4 | Game Designer              | PK Arena modes, anti-whale balance            |
| 5 | Trust & Safety             | KYC, content moderation, CITC compliance      |
| 6 | Stream Moderation lead     | Warning flow, ban tiers, in-app admin account |
| 7 | Violation Scout            | Clip-evidence pipeline                        |
| 8 | Play Store Ops             | AAB releases, ASO, performance KPIs           |
| 9 | Growth / Ad Campaigns      | MENA-wide marketing & user acquisition        |

The Devin running this repo acts as **General Manager (PM)** — coordinating between
the agents and translating their decisions into code, policy, and PRs.
