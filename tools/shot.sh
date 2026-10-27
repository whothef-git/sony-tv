#!/usr/bin/env bash
# Press remote keys over ADB, wait, and save a screenshot of the TV to state/shots/<name>.png.
# Usage: tools/shot.sh <name> [KEYCODE ...]
#   tools/shot.sh home
#   tools/shot.sh grid KEYCODE_DPAD_DOWN
# Live TV and HDMI inputs come back black or empty: that is the TV's content protection, not a bug.
set -uo pipefail
source "$(dirname "$0")/env.sh"
[ $# -ge 1 ] || { echo "Usage: $0 <name> [KEYCODE ...]" >&2; exit 2; }
name="$1"; shift
adb_connect
mkdir -p "$STATE/shots"
for k in "$@"; do adb_tv shell input keyevent "$k"; sleep 0.6; done
sleep 1.5
out="$STATE/shots/$name.png"
adb_tv exec-out screencap -p > "$out"
echo "$out $(wc -c < "$out") bytes"
tv_sh dumpsys activity activities | grep -m1 topResumedActivity
