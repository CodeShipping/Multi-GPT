# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Security: Obfuscate credential-related classes and methods
-keep class com.matrix.multigpt.util.SecureCredentialManager {
    public <methods>;
}

# Security: Remove debug information and obfuscate sensitive classes
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Security: Obfuscate AWS-related classes and credential handling
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# Security: Remove source file names and line numbers in production
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable

# Security: Aggressive obfuscation for sensitive classes
-keep class com.matrix.multigpt.util.AwsSignatureV4** { *; }
-keep class com.matrix.multigpt.data.dto.bedrock.** { *; }
-keep class com.matrix.multigpt.data.network.BedrockAPI** { *; }

# Security: Encrypt string constants (requires additional obfuscation tools in CI/CD)
-adaptclassstrings com.matrix.multigpt.**

# Keep serialization classes
-keep class kotlinx.serialization.** { *; }
-keep class * implements kotlinx.serialization.KSerializer

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep necessary Android classes
-keep class androidx.** { *; }
-keep class com.google.android.** { *; }

# =====================================================
# LocalInference Dynamic Feature Module - Reflection Access
# =====================================================
# The main app accesses these classes via reflection when the dynamic feature is installed
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

# Keep presentation classes
-keep class com.matrix.multigpt.localinference.presentation.** {
    public *;
}

# Keep all classes in localinference module for reflection
-keep class com.matrix.multigpt.localinference.** {
    *;
}
-keepclassmembers class com.matrix.multigpt.localinference.** {
    *;
}

# Keep Kotlin Companion objects (needed for reflection to find Companion field)
-keepclassmembers class * {
    public static ** Companion;
}
-keepnames class * {
    public static ** Companion;
}
