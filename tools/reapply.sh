#!/usr/bin/env bash
# After a firmware update (or whenever the old home screen comes back): re-disable everything in
# state/disabled.txt that the update re-enabled, make the launcher HOME again and check it.
# Safe to run any number of times; a second run changes nothing.
#
# Usage: tools/reapply.sh
#
# Skipped on purpose:
#   - lines starting with # in state/disabled.txt
#   - packages on the "# firmware-reenables: a b c" line (the firmware turns them back on at every
#     boot; they are hidden in the launcher instead)
#   - packages no longer installed
#   - setupwraith while it owns a saved Wi-Fi network (disabling it would delete that network and
#     drop the TV off Wi-Fi and ADB)
set -uo pipefail
source "$(dirname "$0")/env.sh"

LOG="$STATE/disabled.txt"
[ -f "$LOG" ] || { echo "No $LOG: nothing to reapply. (It is written by tools/disable.sh.)" >&2; exit 1; }
adb_connect

skip=" $(grep -m1 '^# *firmware-reenables:' "$LOG" | cut -d: -f2- | tr -d '\r' | tr ',' ' ') "
wanted="$(grep -v '^#' "$LOG" | tr -d '\r' | sed '/^[[:space:]]*$/d' | sort -u)"
installed="$(tv_sh pm list packages --user 0 | sed 's/^package://')"
disabled="$(tv_sh pm list packages -d --user 0 | sed 's/^package://')"

redisabled=0; fine=0; skipped=0; failed=0; gone=0
for p in $wanted; do
  if [[ "$skip" == *" $p "* ]]; then skipped=$((skipped + 1)); continue; fi
  if ! printf '%s\n' "$installed" | grep -qx "$p"; then gone=$((gone + 1)); continue; fi
  if printf '%s\n' "$disabled" | grep -qx "$p"; then fine=$((fine + 1)); continue; fi

  case "$p" in
    com.google.android.apps.tv.launcherx)
      echo "WARNING: the update re-enabled the stock Google TV home ($p); it outranks this launcher until disabled." ;;
    com.google.android.tungsten.setupwraith)
      owners="$(tv_sh dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u)"
      if printf '%s\n' "$owners" | grep -q setupwraith; then
        echo "WARNING: $p owns a saved Wi-Fi network; NOT disabling it (that would drop Wi-Fi and ADB)."
        echo "         On the TV: Settings > Network, forget your network and join it again, then rerun."
        skipped=$((skipped + 1)); continue
      fi
      echo "note: re-disabling $p (Wi-Fi owners: $(printf '%s ' $owners))" ;;
  esac

  out="$(tv_sh pm disable-user --user 0 "$p")"
  if [[ "$out" == *"new state: disabled"* ]]; then
    echo "re-disabled $p"; redisabled=$((redisabled + 1))
  else
    echo "FAILED $p: $out"; failed=$((failed + 1))
  fi
done

home="NOT OK"
if printf '%s\n' "$installed" | grep -qx "$PKG"; then
  tv_sh cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null
  adb_tv shell input keyevent KEYCODE_HOME
  sleep 2
  focused="$(tv_sh dumpsys activity activities | grep -m1 mFocusedApp)"
  echo "$focused"
  [[ "$focused" == *"$PKG"* ]] && home="OK"
else
  echo "The launcher ($PKG) is not installed (factory reset?). Run tools/build-install.sh." >&2
fi

echo
echo "re-disabled $redisabled, already fine $fine, skipped $skipped, not installed $gone, failed $failed"
echo "home: $home"
[ "$home" = OK ] && [ "$failed" = 0 ] || { echo "See docs/UPDATES.md and docs/TROUBLESHOOTING.md#home-takeover" >&2; exit 1; }
