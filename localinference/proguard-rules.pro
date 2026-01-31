# Keep LocalInference Provider for reflection access from main app
-keep class com.matrix.multigpt.localinference.LocalInferenceProvider {
    public static ** Companion;
    public *;
}
-keep class com.matrix.multigpt.localinference.LocalInferenceProvider$Companion {
    public *;
}

# Keep repository classes accessed via reflection
-keep class com.matrix.multigpt.localinference.data.repository.** {
    public *;
}

# Keep model data classes
-keep class com.matrix.multigpt.localinference.data.model.** {
    *;
}

# Keep download manager classes
-keep class com.matrix.multigpt.localinference.service.** {
    public *;
}

# Keep presentation classes that might be accessed
-keep class com.matrix.multigpt.localinference.presentation.** {
    public *;
}

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Kotlin Companion objects
-keepclassmembers class ** {
    public static ** Companion;
}

# Keep enum classes
-keepclassmembers enum com.matrix.multigpt.localinference.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
