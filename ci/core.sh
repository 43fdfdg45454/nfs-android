#!/usr/bin/env bash
# The client core: libnfscore.so for arm64-v8a (phones) and x86_64 (the emulator) with cargo-ndk,
# and its Kotlin bindings, which UniFFI reads from a host build (the android profile strips the
# metadata they come from).
set -Eeuo pipefail
trap 'echo "failed at $BASH_SOURCE:$LINENO: $BASH_COMMAND"' ERR
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_NDK_LATEST_HOME}"
cd rust
cargo ndk -t arm64-v8a -t x86_64 -P 33 -o ../core/src/main/jniLibs build --profile android --locked
cargo build --release --locked
cargo run --release --locked --bin uniffi-bindgen -- generate --no-format \
  --library target/release/libnfscore.so --language kotlin --out-dir ../core/src/main/kotlin
