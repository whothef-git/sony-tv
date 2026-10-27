#!/usr/bin/env bash
# Undo the debloat.
#
#   tools/restore.sh                      re-enable every package in state/disabled.txt, give HOME back
#                                         to the stock launcher, restore Private DNS and the screensaver
#   tools/restore.sh pkg1 [pkg2 ...]      re-enable just these packages
#   tools/restore.sh --uninstall-launcher everything above, then uninstall the launcher too
#
# Settings come back from the oldest state/backup-*/ folder when there is one (taken before any change);
# without a backup, Private DNS goes back to Android's default (automatic) and the screensaver is left alone.
set -uo pipefail
source "$(dirname "$0")/env.sh"
adb_connect

uninstall=0
if [ "${1:-}" = "--uninstall-launcher" ]; then uninstall=1; shift; fi
LOG="$STATE/disabled.txt"

if [ $# -gt 0 ]; then
  pkgs="$*"
elif [ -f "$LOG" ]; then
  pkgs="$(grep -v '^#' "$LOG" | tr -d '\r' | sed '/^[[:space:]]*$/d')"
else
  echo "No $LOG and no packages given: nothing to re-enable." >&2
  pkgs=""
fi

for p in $pkgs; do
  echo "$p -> $(tv_sh pm enable --user 0 "$p")"
done
[ $# -gt 0 ] && exit 0

# --- HOME: hand it back to the stock launcher, whichever one this TV has.
homes="$(tv_sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | grep '/' | sed 's/^[[:space:]]*//')"
stock=""
for preferred in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher; do
  stock="$(printf '%s\n' "$homes" | grep "^$preferred/" | head -1)"
  [ -n "$stock" ] && break
done
# Any other HOME app that is not us and not a FallbackHome/setup screen.
[ -n "$stock" ] || stock="$(printf '%s\n' "$homes" | grep -v "^$PKG/" | grep -viE 'fallbackhome|setupwraith|setupwizard' | head -1)"
if [ -n "$stock" ]; then
  echo "home -> $stock: $(tv_sh cmd package set-home-activity "$stock")"
else
  echo "No stock launcher found among HOME apps:" >&2
  printf '%s\n' "$homes" >&2
fi

# --- Settings: from the first backup if there is one.
backup="$(ls -d "$STATE"/backup-*/ 2>/dev/null | head -1)"
get_setting() { # <namespace> <key>: value from the backup's settings-<namespace>.txt, empty if absent
  [ -n "$backup" ] && [ -f "$backup/settings-$1.txt" ] && grep -m1 "^$2=" "$backup/settings-$1.txt" | cut -d= -f2- | tr -d '\r'
}
restore_setting() { # <namespace> <key>
  local v; v="$(get_setting "$1" "$2")"
  if [ -n "$v" ] && [ "$v" != "null" ]; then tv_sh settings put "$1" "$2" "$v" >/dev/null; echo "settings $1 $2 = $v"
  else tv_sh settings delete "$1" "$2" >/dev/null; echo "settings $1 $2 deleted (default)"; fi
}

if [ -n "$backup" ]; then
  echo "restoring settings from $backup"
  restore_setting global private_dns_mode
  restore_setting global private_dns_specifier
  restore_setting secure screensaver_enabled
  restore_setting secure screensaver_components
  restore_setting system screen_off_timeout
else
  tv_sh settings put global private_dns_mode opportunistic >/dev/null
  tv_sh settings delete global private_dns_specifier >/dev/null
  echo "no backup found: Private DNS back to automatic; screensaver and screen-off timeout unchanged"
fi

if [ "$uninstall" = 1 ]; then
  echo "uninstall $PKG -> $(tv_sh pm uninstall "$PKG")"
fi

adb_tv shell input keyevent KEYCODE_HOME
sleep 2
echo "$(tv_sh dumpsys activity activities | grep -m1 mFocusedApp)"
