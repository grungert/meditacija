# ONNX Runtime is reached from native code and by reflection.
-keep class ai.onnxruntime.** { *; }
# sherpa-onnx reads its config objects' fields from native code.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# The whisper JNI bridge: native methods are looked up by name.
-keep class com.grungert.nebo.ml.WhisperNative { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Receivers and the activity are named in the manifest.
-keep class com.grungert.nebo.esm.** { *; }
