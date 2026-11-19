# R8 rules for Jellio TV.

# Moshi: KotlinJsonAdapterFactory reads Kotlin metadata, constructors and
# field names by reflection for every class without generated adapters,
# so the JSON-shaped classes keep their names and members.
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-keep class com.jellio.android.data.model.** { *; }
-keep class com.jellio.android.data.playback.** { *; }
-keep class com.jellio.android.data.books.** { *; }
-keep class com.jellio.android.data.offline.** { *; }
-keep class com.jellio.android.data.cache.** { *; }
-keep class com.jellio.android.data.prefs.** { *; }
-keep class com.jellio.android.data.session.** { *; }
-keep class com.jellio.android.ui.home.HomeCustomization* { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class **JsonAdapter { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.**
-dontwarn com.squareup.moshi.**

# Retrofit: service interfaces are proxied and suspend functions read the
# generic return type through Continuation (the 0.54.4 breakage).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.jellio.android.data.network.** { *; }
-keep interface com.jellio.android.**Api { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
