# Add project specific ProGuard rules here.

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep accessibility service
-keep class com.voiceflow.app.service.TextPasteAccessibilityService { *; }
