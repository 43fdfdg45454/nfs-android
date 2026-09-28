#!/usr/bin/env bash
# The version from the history with GitVersion (GitVersion.yml), as outputs for the other jobs:
# name (1.0.2, or with a pre-release label off master) and code (major·10⁶ + minor·10³ + patch,
# always growing, so each release installs over the last).
set -euo pipefail
gitversion_release=6.4.0
curl -sSL "https://github.com/GitTools/GitVersion/releases/download/$gitversion_release/gitversion-linux-x64-$gitversion_release.tar.gz" | tar xz -C /tmp
name=$(/tmp/gitversion /showvariable FullSemVer)
IFS=. read -r major minor patch <<< "$(/tmp/gitversion /showvariable MajorMinorPatch)"
printf 'name=%s\ncode=%s\n' "$name" "$((major * 1000000 + minor * 1000 + patch))" >> "${GITHUB_OUTPUT:-/dev/stdout}"
echo "::notice title=Version::$name"
