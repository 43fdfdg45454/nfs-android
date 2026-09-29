#!/usr/bin/env bash
# Runs inside the emulator's session: installs the APKs built in the background (apks.done holds
# Gradle's status) and runs one group of test classes (ci/shards.txt) against nfsd. Without Gradle:
# `am instrument` straight away. On failure, the failures without the framework's stack frames.
# Usage: ci/emulator.sh <group>
set -Eeuo pipefail
classes=$(awk -v g="$1" '$1 == g { for (i = 2; i <= NF; i++) printf "%sio.github.nfsandroid.%s", (i > 2 ? "," : ""), $i }' ci/shards.txt)
[ -n "$classes" ] || { echo "no group $1 in ci/shards.txt"; exit 1; }
for _ in $(seq 900); do [ -f apks.done ] && break; sleep 1; done
[ "$(cat apks.done 2> /dev/null)" = 0 ] || { tail -n 40 apks.log; exit 1; }
apk=app/build/outputs/apk
adb install -r -t -g "$apk/debug/app-debug.apk" > /dev/null
adb install -r -t -g "$apk/androidTest/debug/app-debug-androidTest.apk" > /dev/null
adb logcat -c
report() { # the tests' results (logcat tag nfs-test) as the CI's annotation
  local lines
  lines="$(adb logcat -d -s nfs-test:I | sed -n 's/.*RESULT //p' | awk '{printf "%s%%0A", $0}')"
  [ -n "$lines" ] && echo "::notice title=Emulator $1 (reported: its CPU and the file proxy set the times)::$lines"
  true
}
trap 'report "$1"' EXIT
out=$(adb shell am instrument -w -e nfsServer 10.0.2.2 -e class "$classes" \
  io.github.nfsandroid.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r')
grep -q '^OK (' <<< "$out" && exit 0
grep -vE '^\s+at (androidx|org\.junit|java|kotlin|android|com\.android|dalvik|jdk)\.' <<< "$out"
exit 1
