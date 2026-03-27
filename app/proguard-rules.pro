# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# YoutubeDL
-keep class com.yausername.youtubedl_android.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# JAudioTagger
-keep class org.jaudiotagger.** { *; }

# Coil
-keep class coil.** { *; }

# Modelos de datos
-keep class com.example.zyncwave2.data.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Evitar problemas con reflexión
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Missing classes (auto-generado)
-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.swing.filechooser.FileFilter

# YoutubeDL - keep everything
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-dontwarn com.yausername.**

# Python (usado internamente por YoutubeDL)
-keep class org.python.** { *; }
-keepclassmembers class org.python.** { *; }
-dontwarn org.python.**

# Evitar que R8 elimine clases abstractas/interfaces usadas por reflexión
-keepattributes InnerClasses
-keep class * extends java.lang.Object {
    *;
}