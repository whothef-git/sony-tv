#!/usr/bin/env bash
# Disable packages for user 0, one pm call per package, and log each success to state/disabled.txt,
# which restore.sh and reapply.sh read. Nothing is uninstalled.
#
# Usage: tools/disable.sh <batch-name> pkg1 [pkg2 ...]
#   tools/disable.sh 1-telemetry com.example.analytics com.example.feedback
#
# One call per package on purpose: chained pm commands in a single adb shell can stop silently after
# the first. Take a screenshot (tools/shot.sh) between batches.
set -uo pipefail
source "$(dirname "$0")/env.sh"

if [ $# -lt 2 ]; then
  echo "Usage: $0 <batch-name> pkg1 [pkg2 ...]" >&2
  exit 2
fi
batch="$1"; shift
LOG="$STATE/disabled.txt"
[ -f "$LOG" ] || printf '# Packages disabled by tools/disable.sh (restore.sh re-enables them, reapply.sh re-disables them).\n# firmware-reenables:\n' > "$LOG"

adb_connect
ok=0; failed=0
for p in "$@"; do
  out="$(tv_sh pm disable-user --user 0 "$p")"
  echo "$p -> $out"
  if [[ "$out" == *"new state: disabled"* ]]; then
    grep -qx "$p" "$LOG" || echo "$p" >> "$LOG"
    ok=$((ok + 1))
  else
    failed=$((failed + 1))
  fi
done
echo "# batch $batch: disabled $ok, failed $failed ($(date '+%F %T'))"
