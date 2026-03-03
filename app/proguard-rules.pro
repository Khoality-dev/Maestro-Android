# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.maestro.android.**$$serializer { *; }
-keepclassmembers class com.maestro.android.** { *** Companion; }
-keepclasseswithmembers class com.maestro.android.** { kotlinx.serialization.KSerializer serializer(...); }
