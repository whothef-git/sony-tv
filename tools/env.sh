# Shared settings for every script in tools/. Source it, don't run it.
#
# The TV's address comes from the environment or from tools/tv.conf (gitignored):
#   TV_IP=192.0.2.10 tools/reapply.sh          # one-off
#   echo 'TV_IP=192.0.2.10' > tools/tv.conf    # remembered
# Optional: TV_PORT (default 5555).
#
# Scripts that never talk to the TV (build-install.sh --build-only) set NEED_TV=0 before sourcing.

TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TOOLS/.." && pwd)"
STATE="$ROOT/state"
PKG=tv.widgetv.home
HOME_ACTIVITY="$PKG/.MainActivity"

# Git Bash on Windows rewrites arguments that look like paths (/data/local/tmp -> C:/Program Files/Git/...).
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) export MSYS_NO_PATHCONV=1 ;; esac

if [ -z "${TV_IP:-}" ] && [ -f "$TOOLS/tv.conf" ]; then
  # shellcheck disable=SC1091
  . "$TOOLS/tv.conf"
fi
TV_PORT="${TV_PORT:-5555}"

if [ -z "${TV_IP:-}" ] && [ "${NEED_TV:-1}" = 1 ]; then
  cat >&2 <<'EOF'
TV_IP is not set.
  Find the TV's address on the TV: Settings > Network & Internet > (your network) > IP address.
  Then either run:   TV_IP=<address> tools/<script>.sh ...
  or remember it:    echo 'TV_IP=<address>' > tools/tv.conf
EOF
  exit 1
fi

# adb: the repo's own platform-tools/ first (unzip Google's platform-tools there), else the one on PATH.
if [ -x "$ROOT/platform-tools/adb.exe" ]; then ADB_BIN="$ROOT/platform-tools/adb.exe"
elif [ -x "$ROOT/platform-tools/adb" ]; then ADB_BIN="$ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then ADB_BIN="$(command -v adb)"
elif [ "${NEED_TV:-1}" = 1 ]; then
  echo "adb not found. Unzip Android platform-tools into $ROOT/platform-tools/ or put adb on your PATH:" >&2
  echo "  https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1
fi

TV="${TV_IP:-}:$TV_PORT"
adb_tv() { "$ADB_BIN" -s "$TV" "$@"; }
# One shell command on the TV with Windows line endings stripped.
tv_sh() { adb_tv shell "$@" 2>&1 | tr -d '\r'; }
adb_connect() {
  local out
  out="$("$ADB_BIN" connect "$TV" 2>&1 | tr -d '\r')"
  case "$out" in
    *"connected to"*) ;;
    *) echo "adb connect $TV: $out" >&2
       echo "Is Network/ADB debugging on, and did you accept 'Allow USB debugging?' on the TV?" >&2
       exit 1 ;;
  esac
}
# Windows adb.exe needs C:/... paths for local files; elsewhere the path is used as is.
local_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }
mkdir -p "$STATE"
