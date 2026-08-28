#!/usr/bin/env bash
set -euo pipefail

readonly BINARY="${1:?Usage: verify-elf.sh <binary>}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
readonly READELF="${ANDROID_HOME:-$REPO_ROOT/.tooling/android-sdk}/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"

test -x "$READELF"
test -f "$BINARY"

if "$READELF" -lW "$BINARY" | perl -ane '
  if ($F[0] eq "LOAD") {
    $seen += 1;
    $bad = 1 if hex($F[-1]) < 0x4000;
  }
  END { exit(($seen && !$bad) ? 0 : 1); }
'; then
  echo "16 KB ELF alignment verified: $BINARY"
else
  echo "ELF alignment is below 16 KB: $BINARY" >&2
  exit 1
fi
