# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ClipVault data models
-keep class com.clipvault.app.ClipEntry { *; }
-keep enum com.clipvault.app.ClipType { *; }

# Service and receivers
-keep class com.clipvault.app.ClipVaultService { *; }
-keep class com.clipvault.app.BootReceiver { *; }
-keep class com.clipvault.app.ScreenshotObserver { *; }
