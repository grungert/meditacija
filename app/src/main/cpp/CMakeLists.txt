cmake_minimum_required(VERSION 3.22)
project(nebo_whisper)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

# Build whisper.cpp and its bundled ggml as static libraries, CPU backend only.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# armv8.2 with fp16 and dot product: every phone that runs Android 13 has these
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper-build EXCLUDE_FROM_ALL)

add_library(nebo_whisper SHARED whisper_jni.cpp)
target_compile_options(nebo_whisper PRIVATE -O3)
target_link_libraries(nebo_whisper PRIVATE whisper android log)
