#!/bin/sh
set -eu
# Paths are explicit to make the NDK and pinned source revision reproducible.
: "${ANDROID_NDK:?Set ANDROID_NDK to Android NDK 27.0.12077973}"
: "${CMAKE:?Set CMAKE to the Android SDK cmake binary}"
: "${NINJA:?Set NINJA to the Android SDK ninja binary}"
: "${LLAMA_SOURCE:?Set LLAMA_SOURCE to the llama.cpp checkout}"
: "${LLAMA_BUILD:?Set LLAMA_BUILD to a writable build directory}"
: "${BENCH_BUILD:?Set BENCH_BUILD to a writable build directory}"
revision=de5122d879d00d2895c36be7e0cee94105aeeabd
actual_revision=$(git -C "$LLAMA_SOURCE" rev-parse HEAD)
if [ "$actual_revision" != "$revision" ]; then
  echo "Expected llama.cpp revision $revision; got $actual_revision" >&2
  exit 1
fi
"$CMAKE" -S "$LLAMA_SOURCE" -B "$LLAMA_BUILD" -G Ninja \
 -DCMAKE_MAKE_PROGRAM="$NINJA" -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
 -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_BUILD_TYPE=Release \
 -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DLLAMA_OPENSSL=OFF \
 -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=OFF \
 -DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_MTMD=ON -DLLAMA_SUBPROCESS=OFF -DMTMD_VIDEO=OFF -DBUILD_SHARED_LIBS=OFF
"$CMAKE" --build "$LLAMA_BUILD" --target mtmd -j 8
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$CMAKE" -S "$script_dir/native" -B "$BENCH_BUILD" -G Ninja \
 -DCMAKE_MAKE_PROGRAM="$NINJA" -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
 -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_BUILD_TYPE=Release \
 -DLLAMA_SOURCE="$LLAMA_SOURCE" -DLLAMA_BUILD="$LLAMA_BUILD"
"$CMAKE" --build "$BENCH_BUILD" -j 4
