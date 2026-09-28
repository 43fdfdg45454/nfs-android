#!/usr/bin/env bash
# A GitHub release v<version> of this commit with the APK, noting the commits since the last one.
# Usage: ci/release.sh <version> <apk>
set -euo pipefail
version="$1" apk="$2"
# Only with the release key: an APK signed with another one does not install over the last.
[ "${SIGNED:-}" = true ] || { echo "No release key (NFS_KEYSTORE_BASE64): no release"; exit 1; }
# Run again (a re-run of the job): it is there already.
gh release view "v$version" > /dev/null 2>&1 && { echo "v$version exists"; exit 0; }
previous=$(git describe --tags --abbrev=0 --match 'v*' HEAD^ 2>/dev/null || true)
{
  echo "Commits since ${previous:-the start}:"
  echo
  git log --no-merges --format='- %s' ${previous:+"$previous"..}HEAD
} > notes.md
gh release create "v$version" "$apk" --target "$GITHUB_SHA" --title "NFS $version" --notes-file notes.md
echo "::notice title=Release::v$version"
