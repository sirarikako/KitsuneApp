#!/usr/bin/env bash
set -euo pipefail

gestural_ime=false
if [[ "${1:-}" == "--gestural-ime" ]]; then
  gestural_ime=true
fi

adb wait-for-device
for _ in $(seq 1 60); do
  booted=$(adb shell getprop sys.boot_completed | tr -d '\r[:space:]')
  bootanim=$(adb shell getprop init.svc.bootanim | tr -d '\r[:space:]')
  if [[ "$booted" == "1" && ( -z "$bootanim" || "$bootanim" == "stopped" ) ]]; then
    break
  fi
  sleep 1
done

# Suppress background/launcher ANR or crash dialogs during CI instrumentation runs.
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0
adb shell wm dismiss-keyguard || true

if [[ "$gestural_ime" == "true" ]]; then
  adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  adb shell settings put secure show_ime_with_hard_keyboard 1
fi

# Clean up any system ANR/crash modal (e.g. cold-boot Pixel Launcher ANR) that appeared before hide_error_dialogs=1.
for attempt in $(seq 1 20); do
  state=$(adb shell dumpsys window windows | python3 -c '
import re, sys
text = sys.stdin.read()
dialogs = re.findall(r"Window #\d+ Window\{[^}]*Application (?:Not Responding|Error): ([^\s:}]+)\}", text)
focused = bool(re.search(r"mCurrentFocus=Window\{", text))
if dialogs:
    print("DIALOG:" + dialogs[0])
elif focused:
    print("READY")
else:
    print("WAIT_FOCUS")
')
  if [[ "$state" == "READY" ]]; then
    break
  fi
  if [[ "$state" == DIALOG:* ]]; then
    pkg=${state#DIALOG:}
    echo "Warning: dismissing boot system dialog for $pkg (attempt $attempt)" >&2
    tapped=false
    if (( attempt == 1 )) && adb shell uiautomator dump /data/local/tmp/boot-dialog.xml >/dev/null 2>&1; then
      coords=$(adb exec-out cat /data/local/tmp/boot-dialog.xml | python3 -c '
import re, sys, xml.etree.ElementTree as ET
data = sys.stdin.read()
if "<hierarchy" in data:
    root = ET.fromstring(data[data.index("<hierarchy"):])
    for rid in ("android:id/aerr_wait", "android:id/aerr_close"):
        node = root.find(f".//*[@resource-id=\"{rid}\"]")
        if node is not None:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
                break
' || true)
      adb shell rm -f /data/local/tmp/boot-dialog.xml || true
      if [[ -n "$coords" ]]; then
        adb shell input tap $coords
        tapped=true
      fi
    fi
    if [[ "$tapped" == "false" ]]; then
      adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
      adb shell am force-stop "$pkg" || true
    fi
  fi
  sleep 1
done

adb shell dumpsys window windows | python3 -c '
import re, sys
text = sys.stdin.read()
dialogs = re.findall(r"Window #\d+ Window\{[^}]*Application (?:Not Responding|Error): ([^\s:}]+)\}", text)
if dialogs:
    sys.stderr.write(f"Active system error dialog remained before tests: {dialogs}\n")
    sys.exit(1)
'
