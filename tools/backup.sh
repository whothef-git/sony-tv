#!/usr/bin/env bash
# Snapshot the TV before changing anything: package lists, HOME apps, getprop and all settings,
# into state/backup-<date>-<time>/. restore.sh reads the settings back from the oldest backup.
# Usage: tools/backup.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"
adb_connect

dir="$STATE/backup-$(date '+%Y%m%d-%H%M')"
mkdir -p "$dir"
tv_sh pm list packages -f --user 0          > "$dir/packages-all.txt"
tv_sh pm list packages -d --user 0          > "$dir/packages-disabled.txt"
tv_sh pm list packages -s --user 0          > "$dir/packages-system.txt"
tv_sh pm list packages -3 --user 0          > "$dir/packages-third-party.txt"
tv_sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$dir/home-activities.txt"
tv_sh getprop                               > "$dir/getprop.txt"
for ns in global secure system; do
  tv_sh settings list "$ns"                 > "$dir/settings-$ns.txt"
done
tv_sh dumpsys activity activities | grep -m1 mFocusedApp > "$dir/focused-app.txt"

echo "backup: $dir"
wc -l "$dir"/*.txt | sed "s#$dir/##"
