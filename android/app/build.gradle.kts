plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

android {
    namespace = "com.halqa.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.halqa.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 25
        versionName = "0.1.24"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("ar", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"https://halqa-backend.vercel.app/api/\"")
        buildConfigField("String", "LIVEKIT_WS_URL", "\"wss://halqa.livekit.cloud\"")

        // Mirrors the backend Vercel env var of the same name. While this is
        // `true` (closed-beta builds: v0.1.x), every brand-new sign-in is
        // grandfathered past KYC by the backend (BYPASS_KYC_FOR_BETA=true on
        // Vercel). The Android client treats that grant as an audit-worthy
        // event and stamps `bypass_grant` on the freshly created `/users/{uid}`
        // doc + writes a separate `/audit/{uid}/events/` entry, so when the
        // bypass flag is eventually flipped off we can:
        //   1. find every grandfathered user via a server-side query on
        //      `bypass_grant.will_reverify == true`, and
        //   2. block their withdrawals until they pass real KYC review
        //      (see backend/src/app/api/wallet/withdraw/route.ts).
        // When public launch ships, this constant flips to `false` in the
        // SAME PR that flips the Vercel env var. Don't flip in isolation.
        // Source of truth for the runtime flag is the backend; this constant
        // exists only so the client knows whether to emit the audit fields.
        buildConfigField("Boolean", "BYPASS_KYC_FOR_BETA", "true")
    }

    // Release signing config — env-driven. CI provides:
    //   HALQA_KEYSTORE_PATH      — absolute path to the release keystore on the runner
    //   HALQA_KEYSTORE_PASSWORD  — keystore password
    //   HALQA_KEY_ALIAS          — key alias inside the keystore
    //   HALQA_KEY_PASSWORD       — alias password
    // When any of these are absent, the release block falls back to the debug
    // signing config so `assembleRelease` still produces a buildable (but
    // dev-signed) artifact for local smoke-tests. Play Console submissions
    // MUST be built on CI where all 4 vars are populated.
    val releaseKeystorePath = System.getenv("HALQA_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("HALQA_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("HALQA_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("HALQA_KEY_PASSWORD")
    val releaseSigningReady = !releaseKeystorePath.isNullOrBlank() &&
            !releaseKeystorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank() &&
            file(releaseKeystorePath!!).exists()

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                // Fallback for local builds without keystore secrets. Logs
                // a clear warning so devs aren't surprised at upload time.
                println("[halqa] release signing env-vars not set — falling back to DEBUG signing. Play Console uploads will be rejected. Set HALQA_KEYSTORE_PATH/PASSWORD/KEY_ALIAS/KEY_PASSWORD on CI.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Arabic-only strings (the app is RTL-first; English is partial). The
        // ExtraTranslation noise comes from `values-ar/strings.xml` and
        // `values-en/strings.xml` having keys not present in default
        // `values/strings.xml` — that's intentional, not a regression.
        disable += setOf(
            "ExtraTranslation",
            "MissingTranslation",
        )
        // Only fail builds on real correctness issues, not warnings.
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.collections.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt removed in v0.1.19 — was wired (@HiltAndroidApp on
    // HalqaApplication, @AndroidEntryPoint on MainActivity) but 100%
    // unused (zero @Inject / @Module / @Provides / @HiltViewModel
    // anywhere in src/main). Re-add only when a real consumer ships;
    // restoring takes:
    //   - alias(libs.plugins.hilt) in this file's plugins block
    //   - alias(libs.plugins.hilt) apply false in android/build.gradle.kts
    //   - the [versions] / [libraries] / [plugins] entries for hilt +
    //     hilt-navigation in gradle/libs.versions.toml
    //   - implementation(libs.hilt.android) + ksp(libs.hilt.compiler)
    //     + implementation(libs.hilt.navigation.compose) here
    //   - @HiltAndroidApp on HalqaApplication, @AndroidEntryPoint on
    //     consuming Activities / Fragments / Services
    //   - the proguard-rules.pro Hilt block
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.lottie.compose)

    implementation(libs.livekit.android)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.common)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.ui.tooling)
}
