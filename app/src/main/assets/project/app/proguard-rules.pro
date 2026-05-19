# Add project specific ProGuard rules here.
# By default the flags in this file are appended to flags specified in
# proguard-android-optimize.txt (consumed by AGP).

# Keep generated R class fields.
-keepclassmembers class **.R$* {
    public static <fields>;
}
