#!/usr/bin/env bash
# Refreshes the copy of the shared spec vectors in src/test/resources/vectors and records where it came from.
#
#   scripts/refresh-vectors.sh            re-copy at the tag recorded in SPEC_VERSION
#   scripts/refresh-vectors.sh v0.2.0     copy at a new tag (updates SPEC_VERSION)
#
# Needs git, and read access to https://github.com/selcom-developers/selcom-spec.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="selcom-developers/selcom-spec"
tag="${1:-$(sed -n 's/^tag=//p' "$root/SPEC_VERSION")}"
[ -n "$tag" ] || { echo "no tag given and none in SPEC_VERSION" >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

git clone --quiet --depth 1 --branch "$tag" "https://github.com/$repo.git" "$tmp/spec"
commit="$(git -C "$tmp/spec" rev-parse HEAD)"

dest="$root/src/test/resources/vectors"
rm -rf "$dest"
mkdir -p "$dest"
cp -R "$tmp/spec/vectors/." "$dest/"

printf 'repo=%s\ntag=%s\ncommit=%s\n' "$repo" "$tag" "$commit" > "$root/SPEC_VERSION"
echo "vectors refreshed from $repo $tag ($commit)"
