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

# NewPipeExtractor uses reflection-driven JSON parsing and Rhino-evaluated JS
# to defeat YouTube's signatureCipher; both must survive R8 shrinking.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**

# nanojson + jsoup are pulled in transitively
-dontwarn com.grack.nanojson.**
-dontwarn org.jsoup.**
