#!/usr/bin/env bash
# Called by semantic-release (@semantic-release/exec prepareCmd) BEFORE the
# release commit. Writes the next version everywhere it is displayed so the
# README, the Claude plugin and the skill docs follow the library release.
# The rewritten files are committed by @semantic-release/git (.releaserc.json).
#
# Usage: bumpversion.sh <current version> <next version> <release type>
set -euo pipefail

log_info() { >&2 echo -e "[\\e[1;94mINFO\\e[0m] $*"; }
log_error() { >&2 echo -e "[\\e[1;91mERROR\\e[0m] $*"; }

if [[ "$#" -lt 3 ]]; then
  log_error "Usage: $0 <current version> <next version> <release type>"
  exit 1
fi

curVer=$1
nextVer=$2
relType=$3

log_info "Bumping \\e[33;1m${curVer:-<none>}\\e[0m -> \\e[33;1m${nextVer}\\e[0m (release type: $relType)"

# Version catalog snippets `redux-kit = "x.y.z"` and pinned skill URLs `kotlin-redux-kit/tree/vx.y.z`
for f in README.md skills/*/references/*.md; do
  [[ -f "$f" ]] || continue
  sed -i.bak -E \
    -e "s#(redux-kit = \")[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?(\")#\1${nextVer}\3#g" \
    -e "s#(kotlin-redux-kit/tree/v)[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?#\1${nextVer}#g" \
    "$f"
  rm -f "$f.bak"
  log_info "  updated $f"
done

# Claude plugin manifest: "version": "x.y.z"
f=.claude-plugin/plugin.json
sed -i.bak -E "s#(\"version\": \")[^\"]+(\")#\1${nextVer}\2#" "$f"
rm -f "$f.bak"
log_info "  updated $f"

grep -q "\"version\": \"${nextVer}\"" "$f" || { log_error "plugin.json was not updated"; exit 1; }
grep -q "redux-kit = \"${nextVer}\"" README.md || { log_error "README.md was not updated"; exit 1; }
