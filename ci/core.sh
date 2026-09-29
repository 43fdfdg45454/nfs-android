#!/usr/bin/env bash
# The client core, one part per call so that the parts are built at once on separate runners:
# libnfscore.so for one ABI (arm64-v8a for phones, x86_64 for the emulator) with cargo-ndk, or
# the Kotlin bindings, which UniFFI reads from a host build (the android profile strips the
# metadata they come from).
# Usage: ci/core.sh <arm64-v8a | x86_64 | bindings>
set -Eeuo pipefail
trap 'echo "failed at $BASH_SOURCE:$LINENO: $BASH_COMMAND"' ERR
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_NDK_LATEST_HOME}"
cd rust
if [ "$1" = bindings ]; then
  cargo build --release --locked
  cargo run --release --locked --bin uniffi-bindgen -- generate --no-format \
    --library target/release/libnfscore.so --language kotlin --out-dir ../core/src/main/kotlin
else
  cargo ndk -t "$1" -P 33 -o ../core/src/main/jniLibs build --profile android --locked
fi
