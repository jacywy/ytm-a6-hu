# OkHttp & Conscrypt
-dontwarn org.conscrypt.**
-keep class org.conscrypt.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# NewPipeExtractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.carytm.music.model.** { *; }

# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
