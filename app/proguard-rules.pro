# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep names of all Jellyfin classes
-keepnames class org.jellyfin.mobile.**.* { *; }
-keepnames interface org.jellyfin.mobile.**.* { *; }

# Keep WebView JS interfaces
-keepclassmembers class org.jellyfin.mobile.bridge.* {
    @android.webkit.JavascriptInterface public *;
}

# Keep Chromecast methods
-keepclassmembers class org.jellyfin.mobile.player.cast.Chromecast {
    public *;
}

# Keep file names/line numbers
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep AndroidX ComponentFactory
-keep class androidx.core.app.CoreComponentFactory { *; }

# Assume SDK >= 21 to remove unnecessary compat code
-assumevalues class android.os.Build$VERSION {
  int SDK_INT return 21..2147483647;
}

# kotlinx.serialization: generated serializers and companions, looked up at runtime
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class org.jellyfin.mobile.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.jellyfin.mobile.**$$serializer { *; }
-keepclasseswithmembers class org.jellyfin.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin resolves by class; keep constructors of classes it builds
-keepclassmembers class org.jellyfin.mobile.** { public <init>(...); }

# Room: generated database and DAO implementations are created through reflection
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class org.jellyfin.mobile.data.**_Impl { *; }
