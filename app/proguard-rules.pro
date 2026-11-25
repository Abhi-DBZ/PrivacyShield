# ML Kit finds its parts by class name (manifest meta-data + reflection); R8 full mode can
# strip or rename them, which only breaks release builds. Keep them whole.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common** { *; }
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.**
