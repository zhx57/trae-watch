# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.trae.watch.**$$serializer { *; }
-keepclassmembers class com.trae.watch.** { *** Companion; }
-keepclasseswithmembers class com.trae.watch.** { kotlinx.serialization.KSerializer serializer(...); }
