#!/usr/bin/env bash
# Measures Free My TV's home screen on one TV: frame smoothness while moving around, and memory.
# Usage: ./perf-check.sh 192.168.1.20 [label]
# Wakes the TV to the home screen, presses the same keys every run, then prints:
#   frames, janky %, 50/90/95/99th percentile frame times (from gfxinfo) and the home process's memory (PSS).
# Run it before and after a change, with the TV otherwise idle, and compare.
set -u
ip=${1:?TV address}; label=${2:-run}
T=$ip:5555; P=io.freemytv
adb connect "$T" >/dev/null 2>&1
k() { adb -s "$T" shell input keyevent "$@"; }
k KEYCODE_WAKEUP; k KEYCODE_HOME; sleep 4
k KEYCODE_HOME; sleep 3                      # settle on the home screen, cards loaded
pid=$(adb -s "$T" shell pidof $P:home | tr -d "\r"); adb -s "$T" shell dumpsys gfxinfo $pid reset >/dev/null
# Cards: right across and back, then down to the shelf, across and back, then up again.
for i in 1 2 3 4 5; do k KEYCODE_DPAD_RIGHT; sleep 0.6; done
for i in 1 2 3 4 5; do k KEYCODE_DPAD_LEFT; sleep 0.6; done
k KEYCODE_DPAD_DOWN; sleep 1
for i in 1 2 3 4 5 6; do k KEYCODE_DPAD_RIGHT; sleep 0.5; done
for i in 1 2 3 4 5 6; do k KEYCODE_DPAD_LEFT; sleep 0.5; done
k KEYCODE_DPAD_UP; sleep 2
g=$(adb -s "$T" shell dumpsys gfxinfo $pid)
m=$(adb -s "$T" shell dumpsys meminfo -s $P:home | grep -m1 'TOTAL PSS' | awk '{print $3}')
echo "== $label ($ip)"
printf '%s\n' "$g" | grep -m1 -E 'Total frames rendered'
printf '%s\n' "$g" | grep -m1 -E 'Janky frames:'
printf '%s\n' "$g" | grep -m4 -E '^(50|90|95|99)th percentile'
echo "home PSS: $((m / 1024)) MB"
