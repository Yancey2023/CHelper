# ----- CHelper Native Core -----

# CHelperJvm.cpp resolves these classes and fields by name through JNI.
-keep class yancey.chelper.core.Suggestion { *; }
-keep class yancey.chelper.core.ErrorReason { *; }
-keep class yancey.chelper.core.ClickSuggestionResult { *; }

# ----- umeng -----

# apm's consumer rules already keep com.umeng.** and com.efs.**.
# Keep the remaining SDK reflection and optional-dependency rules here.
-dontwarn com.umeng.**
-dontwarn com.efs.**

-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

-keep public class yancey.chelper.R$* {
    public static final int *;
}

# ----- end -----
