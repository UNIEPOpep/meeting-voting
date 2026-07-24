# ProGuard rules for HeimaVote
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.heima.vote.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
