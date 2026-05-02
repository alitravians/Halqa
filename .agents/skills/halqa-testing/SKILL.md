# Halqa Android — Testing & Release Skill

This skill captures everything needed to build, test, and ship the Halqa Android app (Kotlin 2.0 + Jetpack Compose + LiveKit). Read this before starting any QA / release work on the repo.

## Repository

- **Repo:** `alitravians/Halqa`
- **Active feature branch:** `devin/1777683540-halqa-android-mvp` (PR #1)
- **Layout:** monorepo with `android/` (Kotlin app) and `backend/` (Next.js placeholder).
- **App package:** `com.halqa.app` (debug variant `com.halqa.app.debug`).

## Toolchain

- **JDK:** Java 17 — `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- **Android SDK:** `ANDROID_HOME=/home/ubuntu/Android/Sdk`.
- **Gradle:** 8.10.2, AGP 8.5.2, Kotlin 2.0.21.
- **DI:** Hilt 2.53.1 with **KSP1** mode (KSP2 is incompatible with Kotlin 2.0; do not switch).

## Build commands

From `android/`:

- Debug APK (Appetize / direct install): `./gradlew assembleDebug`
  - Output: `app/build/outputs/apk/debug/app-debug.apk`
- Release AAB (Google Play): `./gradlew bundleRelease`
  - Output: `app/build/outputs/bundle/release/app-release.aab`
- Both in one shot: `./gradlew assembleDebug bundleRelease`

**Always copy artifacts** to `/home/ubuntu/halqa-artifacts/halqa-v<version>-debug.apk` (and `-release.aab`) for clean tracking.

## Versioning rule (mandatory)

Every time you push a build that changes user-visible behaviour (or fixes a Devin Review issue), bump both fields in `android/app/build.gradle.kts`:

```kotlin
versionCode = N + 1
versionName = "0.x.y+1"
```

Google Play rejects duplicate `versionCode` uploads.

## Testing on Appetize.io

- URL: https://appetize.io/upload
- **Login required** — Appetize no longer accepts anonymous uploads. Auth is preserved across the live desktop session; if logged out, ask the user to sign in via the live desktop.
- **Device:** Pixel 7, Android 13.0.
- **Free-tier limit:** 3 minutes per session — plan tightly. Re-uploads must be done after each timeout.
- **Always enable** in Developer Tools panel: Network Logs (On), Debug Logs (On). Capture stack traces from Debug Logs as primary crash evidence.
- **Upload flow:** Apps tab → Halqa app card → Upload New Build → choose APK from `/home/ubuntu/halqa-artifacts/`.
- **Launch flow:** Apps tab → Halqa → click ▶ play arrow on the desired build row.

## Test plan template

Reuse / extend `/home/ubuntu/halqa-test-plan.md`. The plan must:

- List every Devin Review fix as a discrete step with concrete pass/fail criteria.
- Mark UI-untestable fixes (e.g. branches that the current screen never triggers, paste flows blocked by numeric keypads) as `inconclusive UI · verified by code` with file:line citations.

## Devin advisory council (9 sessions)

This app is coordinated by 9 specialist Devin sessions. Bring relevant ones into the loop on substantial decisions:

| Role | Owner | Session |
|---|---|---|
| UX/UI | Sara Al-Otaibi | https://app.devin.ai/sessions/ff50c9822f1f4892ae8634d2c2706610 |
| Architecture | Khalid Al-Mansour | https://app.devin.ai/sessions/cf3f2a4cd7124d99b67fe27393849f9b |
| Product / Business | Noura Al-Shamri | https://app.devin.ai/sessions/2d9061a79f9a4ede811d87e0ac025524 |
| Game Design (PK Arena) | Yasser Al-Dosari | https://app.devin.ai/sessions/208303251f20430ebcf674a9d72ea64c |
| Trust & Safety | Layla Al-Harbi | https://app.devin.ai/sessions/b9ecb2ee227242489750a51733175f8b |
| Stream Moderation | Mohammed Al-Qahtani | https://app.devin.ai/sessions/c6e9353660d84045b075478745a3c35f |
| Violation Scout | Faisal Al-Ghamdi | https://app.devin.ai/sessions/5fabd795726740d0b606a30b7ea76483 |
| Play Store Ops | Reem Al-Otaibi | https://app.devin.ai/sessions/6e36fb767a5d48d585dce5685ddc77c8 |
| Growth Marketing | Lina Al-Saud | https://app.devin.ai/sessions/a03fa7dc00a740f884f8e65c7320acef |

The Devin in this repo acts as **General Manager** — coordinates, executes, and is the only role with shell/file/git permissions. Specialists are advisory only.

## Devin Review iteration

Devin Review has surfaced 5 rounds of issues on PR #1 so far. The expected loop is:

1. Read each comment, confirm it is a real behavioural bug (not stylistic).
2. Fix it, bump versionCode/versionName, rebuild APK + AAB.
3. Reply **per-comment** with the suggestion-style code block + commit SHA.
4. Re-test the affected area on Appetize.

## Final report cadence

For each shippable build:

- Post **one** consolidated GitHub comment on PR #1 with `<details>` sections, screenshot evidence (uploaded via `upload_attachment`), and link to the Devin session.
- Send the user a markdown report attachment summarising council outputs and what's still pending.
