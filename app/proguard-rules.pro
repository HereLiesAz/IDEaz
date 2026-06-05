# IDEaz R8 / ProGuard rules.
#
# Strategy: keep the (small) app code intact so reflection-driven machinery
# (Compose, kotlinx.serialization, Retrofit interfaces, AIDL) keeps working, and
# keep the libraries that resolve types reflectively. R8 then strips the large
# unused remainder of pulled-in libraries — most importantly bcprov, of which
# only the X25519 / Salsa20 / Poly1305 / Blake2b primitives are reached.

# Preserve metadata reflection relies on.
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# ---- App code -------------------------------------------------------------
# The app is small; keep it whole rather than risk shrinking/obfuscating a
# class that is only referenced reflectively (serializers, ViewModels via
# Compose, Retrofit service interfaces, Parcelables, AIDL stubs).
-keep class com.hereliesaz.ideaz.** { *; }

# ---- kotlinx.serialization ------------------------------------------------
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn kotlinx.serialization.**

# ---- Retrofit / OkHttp / Okio ---------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- google-genai + Jackson (reflective serialization of genai models) ----
-keep class com.google.genai.** { *; }
-dontwarn com.google.genai.**
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---- BouncyCastle (let R8 keep only what GithubSecretBox reaches) ----------
# The lightweight crypto API classes we use are referenced directly, so R8
# retains them automatically; everything else is dead and gets stripped.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# ---- MediaPipe LLM Inference (on-device runtime; JNI/native) ----------------
# MediaPipe resolves Java classes from native code by name, so keep it whole.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ---- JGit -----------------------------------------------------------------
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**

# ---- Android / WebView JS bridge ------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class * implements android.os.Parcelable { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
