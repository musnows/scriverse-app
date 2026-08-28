-keep class com.scriverse.app.core.runtime.IRuntimeControl$Stub { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn java.lang.management.**
