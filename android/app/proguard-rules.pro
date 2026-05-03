# Halqa - Proguard rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Kotlin Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.halqa.app.**$$serializer { *; }
-keepclassmembers class com.halqa.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.halqa.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# LiveKit
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }

# Reem Al-Otaibi (Play Store Ops & Performance Lead) — keep rules
# session: 6e36fb767a5d48d585dce5685ddc77c8
# Without these, R8 in release builds rewrites Firestore POJO field
# names and breaks deserialization at runtime — silent crashes that
# only surface in production. M2/M1 added StreamSnapshot + the
# wallets/{uid}, streams/{id}/gifts/{txnId} POJOs that need this.
-keep class com.halqa.app.data.** { *; }
-keep class com.halqa.app.domain.** { *; }
-keepattributes *Annotation*, Signature

# Firebase Crashlytics — keep stack traces readable post-R8.
-keep class com.google.firebase.crashlytics.** { *; }
-keepattributes SourceFile, LineNumberTable

# Hilt removed in v0.1.19 — no longer present in deps. If Hilt is
# re-introduced (see tombstone in app/build.gradle.kts dependencies
# block), restore:
#   -keep,allowobfuscation,allowshrinking class dagger.hilt.android.lifecycle.HiltViewModel
