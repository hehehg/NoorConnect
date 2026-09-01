# TDLib's native code (libtdjni.so) calls into org.drinkless.tdlib.* classes/fields/methods
# by exact name via JNI. R8 renaming or stripping any of them as "unused" (since nothing in
# Kotlin code references most TdApi.* subclasses by name, just by pattern matching) would
# break those JNI calls at runtime with NoSuchMethodError/NoSuchFieldError — keep the whole
# package untouched.
-keep class org.drinkless.tdlib.** { *; }
