# R8 rules for the launcher. The defaults plus Compose's own rules cover almost everything.
# The accessibility service and activity are referenced from the manifest, which R8 keeps automatically.
-keep class com.example.onnlauncher.HomeOverrideService { *; }
-keep class com.example.onnlauncher.MainActivity { *; }
-dontwarn org.jetbrains.annotations.**
