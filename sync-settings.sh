#!/usr/bin/env bash
# [custom] Copies one TV's on-screen Free My TV settings to others (Backup provider, adb only).
# Usage: ./sync-settings.sh FROM_IP TO_IP [TO_IP...]      e.g. ./sync-settings.sh 192.168.1.20 192.168.1.21
# The target's home screen restarts with them. A copy is kept in ../settings-sync/<from>.json.
set -eu
from=${1:?from IP}; shift
dir="$(cd "$(dirname "$0")/.." && pwd)/settings-sync"; mkdir -p "$dir"
adb connect "$from:5555" >/dev/null
adb -s "$from:5555" shell content query --uri content://io.freemytv.backup/settings | sed 's/^Row: 0 json=//' > "$dir/$from.json"
for to in "$@"; do
  adb connect "$to:5555" >/dev/null
  for try in 1 2 3; do   # a write can arrive empty: try again
    adb -s "$to:5555" exec-in content write --uri content://io.freemytv.backup/settings < "$dir/$from.json"
    sleep 1
    out=$(adb -s "$to:5555" shell content call --uri content://io.freemytv.backup --method apply)
    echo "$to: $out"
    case "$out" in *applied*) break ;; esac
  done
done
