#!/usr/bin/env bash
# Every emulator test class (a file under app/src/androidTest with @Test) is in exactly one group of
# ci/shards.txt, every class named there exists and the groups are the emulator job's matrix.
set -euo pipefail
tests=$(grep -l '@Test' app/src/androidTest/kotlin/io/github/nfsandroid/*.kt | xargs -n1 basename | sed 's/\.kt$//' | sort)
listed=$(grep -v '^#' ci/shards.txt | cut -d' ' -f2- | tr ' ' '\n' | grep . | sort)
twice=$(uniq -d <<< "$listed")
[ -z "$twice" ] || { echo "in more than one group: $twice"; exit 1; }
[ "$tests" = "$listed" ] || { echo "classes and groups differ:"; diff <(echo "$tests") <(echo "$listed"); exit 1; }
matrix=$(sed -n 's/^ *group: \[\(.*\)\]$/\1/p' .github/workflows/ci.yml | tr -d ' ' | tr ',' '\n' | sort)
[ "$matrix" = "$(grep -v '^#' ci/shards.txt | cut -d' ' -f1 | sort)" ] || { echo "ci.yml's groups differ from ci/shards.txt"; exit 1; }
echo "$(wc -l <<< "$tests") test classes in $(grep -vc '^#' ci/shards.txt) groups"
