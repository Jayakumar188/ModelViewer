# Filament / gltfio ship native code reached only via JNI + reflection in a
# few spots; keep their classes intact if minification is ever turned on.
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
