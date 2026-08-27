#!/usr/bin/env bash
set -euo pipefail

readonly ABI="${1:-arm64-v8a}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly REPO_ROOT="$(cd "$ANDROID_DIR/.." && pwd)"
readonly ADB="${ANDROID_HOME:-$REPO_ROOT/.tooling/android-sdk}/platform-tools/adb"
readonly NODE_BINARY="$SCRIPT_DIR/prebuilt/$ABI/libnode.so"
readonly LIBCXX="$SCRIPT_DIR/prebuilt/$ABI/libc++_shared.so"

test -x "$ADB"
test -f "$NODE_BINARY"
test -f "$LIBCXX"
"$ADB" wait-for-device
"$ADB" push "$NODE_BINARY" /data/local/tmp/scriverse-node >/dev/null
"$ADB" push "$LIBCXX" /data/local/tmp/libc++_shared.so >/dev/null
"$ADB" push "$SCRIPT_DIR/probe/runtime-probe.mjs" /data/local/tmp/runtime-probe.mjs >/dev/null
"$ADB" shell chmod 700 /data/local/tmp/scriverse-node
"$ADB" shell "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/scriverse-node /data/local/tmp/runtime-probe.mjs"
