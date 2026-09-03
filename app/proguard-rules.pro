# AttendWise ProGuard Rules

# 1. General Optimization Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# 2. Your Data Models
-keep class com.ankit.attendwise.data.** { *; }
-keep class com.ankit.attendwise.models.** { *; }

# 3. Room
# Room handles its own ProGuard rules via the compiler.
-dontwarn androidx.room.**

# 4. Compose
-dontwarn androidx.compose.**

# 5. Kizitonwose Calendar
-keep class com.kizitonwose.calendar.** { *; }

# 6. Common R8/ProGuard Fixes
-ignorewarnings
-keep class androidx.lifecycle.DefaultLifecycleObserver
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses
