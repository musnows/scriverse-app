#!/usr/bin/env bash
set -euo pipefail

readonly NODE_VERSION="22.23.2"
readonly NDK_VERSION="28.2.13676358"
readonly ANDROID_API="29"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly REPO_ROOT="$(cd "$ANDROID_DIR/.." && pwd)"
readonly SDK_ROOT="${ANDROID_HOME:-$REPO_ROOT/.tooling/android-sdk}"
readonly NDK_ROOT="$SDK_ROOT/ndk/$NDK_VERSION"
readonly ABI="${1:-}"

case "$ABI" in
  arm64-v8a)
    NODE_ARCH="arm64"
    HOST_CC="/usr/bin/clang"
    HOST_CXX="/usr/bin/clang++"
    ;;
  x86_64)
    NODE_ARCH="x86_64"
    HOST_CC="/usr/bin/clang -arch x86_64"
    HOST_CXX="/usr/bin/clang++ -arch x86_64"
    export NODE_ANDROID_HOST_ARCH="x64"
    ;;
  *) echo "Usage: $0 <arm64-v8a|x86_64>" >&2; exit 2 ;;
esac

test -d "$NDK_ROOT"
command -v uv >/dev/null

readonly WORK_DIR="$SCRIPT_DIR/work/node-v$NODE_VERSION-$ABI"
readonly ARCHIVE="$SCRIPT_DIR/work/node-v$NODE_VERSION.tar.gz"
readonly SHASUMS="$SCRIPT_DIR/work/SHASUMS256.txt"
readonly JNI_DIR="$ANDROID_DIR/core/runtime/src/main/jniLibs/$ABI"
mkdir -p "$SCRIPT_DIR/work" "$SCRIPT_DIR/prebuilt/$ABI" "$JNI_DIR"

if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 3 "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION.tar.gz" --output "$ARCHIVE"
  curl --fail --location --retry 3 "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt" --output "$SHASUMS"
  expected="$(awk '/ node-v'"$NODE_VERSION"'\.tar\.gz$/ {print $1}' "$SHASUMS")"
  printf '%s  %s\n' "$expected" "$ARCHIVE" | shasum -a 256 -c -
fi

if [[ ! -d "$WORK_DIR" ]]; then
  mkdir -p "$WORK_DIR"
  tar -xzf "$ARCHIVE" -C "$WORK_DIR" --strip-components=1
  patch -d "$WORK_DIR" -p1 < "$SCRIPT_DIR/patches/node-v$NODE_VERSION-darwin-android.patch"
  cp "$NDK_ROOT/sources/android/cpufeatures/cpu-features.c" "$WORK_DIR/deps/zlib/android-cpu-features.c"
fi

pushd "$WORK_DIR" >/dev/null
uv run --no-project python ./android_configure.py "$NDK_ROOT" "$ANDROID_API" "$NODE_ARCH"
readonly LLVM_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin"
make -s -j"$(sysctl -n hw.ncpu)" \
  CC.host="$HOST_CC" \
  CXX.host="$HOST_CXX" \
  LINK.host="$HOST_CXX" \
  AR.host="$LLVM_BIN/llvm-ar" \
  AR.target="$LLVM_BIN/llvm-ar"
cp out/Release/node "$SCRIPT_DIR/prebuilt/$ABI/libnode.so"
cp out/Release/node "$JNI_DIR/libnode.so"
case "$ABI" in
  arm64-v8a) NDK_TRIPLE="aarch64-linux-android" ;;
  x86_64) NDK_TRIPLE="x86_64-linux-android" ;;
esac
readonly LIBCXX="$LLVM_BIN/../sysroot/usr/lib/$NDK_TRIPLE/libc++_shared.so"
cp "$LIBCXX" "$SCRIPT_DIR/prebuilt/$ABI/libc++_shared.so"
cp "$LIBCXX" "$JNI_DIR/libc++_shared.so"
popd >/dev/null

echo "Built Node v$NODE_VERSION for $ABI"
