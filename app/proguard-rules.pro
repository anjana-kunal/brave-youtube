# Brave YouTube ProGuard Rules

# Keep ad-block engine
-keep class com.aspect.braveyoutube.adblock.** { *; }

# Keep data classes (Gson serialization)
-keep class com.aspect.braveyoutube.data.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**
