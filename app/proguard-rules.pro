# Project-specific ProGuard rules. Libraries ship consumer rules.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.abanana.abattery.ABatteryApplication { *; }
-keep class com.abanana.abattery.presentation.main.MainActivity { *; }
-keep class com.abanana.abattery.diagnostics.PrivilegedReadService { *; }
