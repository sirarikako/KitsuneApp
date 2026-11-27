#!/usr/bin/env bash
set -u

output=$1
mkdir -p "$output"
timeout 20s adb devices -l > "$output/adb-devices.txt" 2>&1
timeout 20s adb logcat -d -b all -v threadtime > "$output/logcat.txt" 2>&1
timeout 20s adb shell dumpsys window > "$output/window.txt" 2>&1
timeout 20s adb shell dumpsys input_method > "$output/input-method.txt" 2>&1
timeout 20s adb shell dumpsys activity activities > "$output/activities.txt" 2>&1
if ! timeout 20s adb exec-out screencap -p > "$output/device.png" || [[ ! -s "$output/device.png" ]]; then
  rm -f "$output/device.png"
fi
