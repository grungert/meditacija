# ONNX Runtime is reached from native code and by reflection.
-keep class ai.onnxruntime.** { *; }
# The whisper JNI bridge: native methods are looked up by name.
-keep class com.grungert.nebo.ml.WhisperNative { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Receivers and the activity are named in the manifest.
-keep class com.grungert.nebo.esm.** { *; }
