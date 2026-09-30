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

# ----- Print Informations -----

-printseeds seeds.txt
-printusage usage.txt
-printmapping mapping.txt
-printconfiguration configuration.txt

# ----- CHelper Native Core -----

-keep class yancey.chelper.core.Suggestion{ *; }
-keep class yancey.chelper.core.ErrorReason{ *; }
-keep class yancey.chelper.core.ClickSuggestionResult{ *; }

# ----- CHelper Server -----

-keep class yancey.chelper.network.chelper.service.**{ *; }

# ----- Command Lab -----

-keep class yancey.chelper.network.library.service.**{ *; }

# Retrofit reads DTO types from generic signatures at runtime, including nested
# BaseResult<T> in suspend methods. Keep their class identities even when callers
# only consume status/message; otherwise R8 can replace T with java.lang.Object.
# Members remain eligible for optimization and obfuscation. Serialization's
# bundled rules retain the companions and serializers required for JSON decoding.
-keepattributes Signature
-keep,allowoptimization,allowobfuscation @kotlinx.serialization.Serializable class yancey.chelper.network.library.data.**

# ----- umeng -----

-keep class com.umeng.** { *; }

-keep class com.uc.** { *; }

-keep class com.efs.** { *; }

-dontwarn com.umeng.**
-dontwarn com.uc.**
-dontwarn com.efs.**

-keepclassmembers class *{
    public <init>(org.json.JSONObject);
}
-keepclassmembers enum *{
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class yancey.chelper.R$*{
    public static final int *;
}

# ----- kotlinx.serialization -----

# kotlinx.serialization uses java.lang.ClassValue (API 34+) via ClassValueReferences
# for caching serializer lookups. On minSdk < 34, R8 may merge ClassValueReferences
# into Platform_commonKt, causing verification to fail universally. This keep rule
# prevents such merging/inlining so the class loads inside the library's own
# NoClassDefFoundError catch and falls back to ConcurrentHashMap.
-keep class kotlinx.serialization.internal.ClassValueReferences { *; }
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# ----- end -----
