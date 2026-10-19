The C API headers of ONNX Runtime 1.30 (MIT licence, © Microsoft Corporation), taken unchanged from the
`onnxruntime-android` 1.30.0 package. They are used to call the ONNX Runtime 1.28.2 that ships with sherpa-onnx:
the C API only ever appends to the `OrtApi` table, so `ort_jni.cpp` asks for API version 28 and uses nothing newer.
