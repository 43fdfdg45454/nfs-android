#!/usr/bin/env bash
# Runs inside the emulator's session: installs the app and runs its tests against nfsd.
# On failure, the tests' failures (JUnit XML) are printed: the HTML report cannot be read.
set -Eeuo pipefail
adb logcat -c
report() { # the tests' results (logcat tag nfs-test) as the CI's annotation
  local lines
  lines="$(adb logcat -d -s nfs-test:I | sed -n 's/.*RESULT //p' | awk '{printf "%s%%0A", $0}')"
  [ -n "$lines" ] && echo "::notice title=Emulator (reported: its CPU and the file proxy set the times)::$lines"
  true
}
trap report EXIT
if ! ./gradlew --no-daemon --quiet connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.nfsServer=10.0.2.2; then
  python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for path in glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        for failure in list(case.iter("failure")) + list(case.iter("error")):
            text = (failure.text or failure.get("message") or "").strip().splitlines()
            print(f"FAILED {case.get('name')}: " + " | ".join(text[:12]))
PY
  exit 1
fi
