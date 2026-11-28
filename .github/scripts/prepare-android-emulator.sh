#!/usr/bin/env bash
set -euo pipefail

gestural_ime=false
case "${1:-}" in
  "") ;;
  --gestural-ime) gestural_ime=true ;;
  *)
    echo "Usage: $0 [--gestural-ime]" >&2
    exit 2
    ;;
esac
if (( $# > 1 )); then
  echo "Usage: $0 [--gestural-ime]" >&2
  exit 2
fi

boot_timeout_sec=${EMULATOR_BOOT_TIMEOUT_SEC:-60}
focus_timeout_sec=${EMULATOR_FOCUS_TIMEOUT_SEC:-30}

if ! timeout "${boot_timeout_sec}s" adb wait-for-device; then
  echo "ERROR: Timed out waiting for adb device." >&2
  exit 1
fi

boot_ready=false
booted=""
bootanim=""
for _ in $(seq 1 "$boot_timeout_sec"); do
  booted=$(timeout 10s adb shell getprop sys.boot_completed | tr -d '\r[:space:]' || true)
  bootanim=$(timeout 10s adb shell getprop init.svc.bootanim | tr -d '\r[:space:]' || true)
  if [[ "$booted" == "1" && ( -z "$bootanim" || "$bootanim" == "stopped" ) ]]; then
    boot_ready=true
    break
  fi
  sleep 1
done

if [[ "$boot_ready" != "true" ]]; then
  echo "ERROR: Timed out waiting for emulator boot completion (sys.boot_completed=${booted:-<empty>}, init.svc.bootanim=${bootanim:-<empty>})." >&2
  exit 1
fi

read_setting() {
  local val
  val=$(timeout 10s adb shell settings get "$1" "$2" | tr -d '\r[:space:]')
  if [[ -z "$val" || "$val" == "null" ]]; then
    printf 'null'
  else
    printf '%s' "$val"
  fi
}

restore_setting() {
  local namespace=$1 key=$2 orig=$3
  if [[ "$orig" == "null" ]]; then
    timeout 10s adb shell settings delete "$namespace" "$key" >/dev/null
  else
    timeout 10s adb shell settings put "$namespace" "$key" "$orig" >/dev/null
  fi
}

orig_hide_error_dialogs=$(read_setting global hide_error_dialogs)
orig_anr_show_background=$(read_setting secure anr_show_background)
settings_modified=false

restore_error_dialog_settings() {
  if [[ "$settings_modified" == "true" ]]; then
    restore_setting global hide_error_dialogs "$orig_hide_error_dialogs"
    restore_setting secure anr_show_background "$orig_anr_show_background"
    settings_modified=false
  fi
}

cleanup() {
  local status=$?
  timeout 10s adb shell rm -f /data/local/tmp/boot-dialog.xml >/dev/null 2>&1 || true
  restore_error_dialog_settings || true
  exit "$status"
}
trap cleanup EXIT

# Temporarily suppress background ANR dialogs only during post-boot system UI initialization.
timeout 10s adb shell settings put global hide_error_dialogs 1 >/dev/null
timeout 10s adb shell settings put secure anr_show_background 0 >/dev/null
settings_modified=true

timeout 10s adb shell wm dismiss-keyguard >/dev/null 2>&1 || true

if [[ "$gestural_ime" == "true" ]]; then
  timeout 15s adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  timeout 10s adb shell settings put secure show_ime_with_hard_keyboard 1
fi

inspect_windows() {
  timeout 15s adb shell dumpsys window | python3 -c '
import re, sys

LAUNCHER_ANR_TITLE = "Application Not Responding: com.google.android.apps.nexuslauncher"
ERROR_PREFIXES = (
    "Application Not Responding:",
    "Application Error:",
    "Strict Mode Violation:",
    "Factory Error",
)

full_text = sys.stdin.read()
sections = {}
section_re = re.compile(r"(?m)^WINDOW MANAGER ([A-Z ]+?)(?: \([^)]*\))?:?\s*$")
sec_matches = list(section_re.finditer(full_text))
for idx, match in enumerate(sec_matches):
    name = match.group(1).strip()
    start = match.end()
    end = sec_matches[idx + 1].start() if idx + 1 < len(sec_matches) else len(full_text)
    sections[name] = full_text[start:end]

displays_text = sections.get("DISPLAY CONTENTS", full_text)
windows_text = sections.get("WINDOWS", full_text)

header_re = re.compile(r"(?m)^  Window #\d+ Window\{[0-9a-f]+ u\d+ ([^}]+)\}:")
matches = list(header_re.finditer(windows_text))

launcher_anr_active = False
launcher_anr_exiting = False
unexpected = []

for idx, match in enumerate(matches):
    raw_title = match.group(1).strip()
    end = matches[idx + 1].start() if idx + 1 < len(matches) else len(windows_text)
    block = windows_text[match.start():end]
    exiting = raw_title.endswith(" EXITING")
    title = raw_title[:-len(" EXITING")].strip() if exiting else raw_title
    is_error = title.startswith(ERROR_PREFIXES) or bool(re.search(r"\bSYSTEM_ERROR\b", block))
    if not is_error:
        continue
    if title == LAUNCHER_ANR_TITLE:
        if exiting:
            launcher_anr_exiting = True
        else:
            launcher_anr_active = True
    else:
        unexpected.append(title)

focus_match = re.search(r"(?m)^\s*mCurrentFocus=Window\{[0-9a-f]+ u\d+ ([^}]+)\}", displays_text)
focus_raw = focus_match.group(1).strip() if focus_match else ""
focus_exiting = focus_raw.endswith(" EXITING")
focus_title = focus_raw[:-len(" EXITING")].strip() if focus_exiting else focus_raw

if focus_title.startswith(ERROR_PREFIXES) and focus_title != LAUNCHER_ANR_TITLE:
    if focus_title not in unexpected:
        unexpected.append(focus_title)

if unexpected:
    print("UNEXPECTED_DIALOG:" + unexpected[0])
elif launcher_anr_active or focus_title == LAUNCHER_ANR_TITLE:
    if launcher_anr_exiting and not launcher_anr_active and focus_exiting:
        print("WAIT_EXITING")
    else:
        print("LAUNCHER_ANR")
elif launcher_anr_exiting:
    print("WAIT_EXITING")
elif focus_title and not focus_exiting:
    print("READY:" + focus_title)
else:
    print("WAIT_FOCUS")
'
}

window_ready=false
launcher_anr_attempts=0
launcher_force_stopped=false
state=""
for _ in $(seq 1 "$focus_timeout_sec"); do
  state=$(inspect_windows)
  case "$state" in
    READY:*)
      window_ready=true
      break
      ;;
    UNEXPECTED_DIALOG:*)
      echo "ERROR: Unexpected system error/ANR dialog during emulator setup: ${state#UNEXPECTED_DIALOG:}" >&2
      exit 1
      ;;
    LAUNCHER_ANR)
      launcher_anr_attempts=$((launcher_anr_attempts + 1))
      echo "Warning: dismissing cold-boot Pixel Launcher ANR dialog (attempt $launcher_anr_attempts)" >&2
      tapped=false
      coords=""
      if (( launcher_anr_attempts <= 2 )) && timeout 10s adb shell uiautomator dump /data/local/tmp/boot-dialog.xml >/dev/null 2>&1; then
        coords=$(timeout 10s adb exec-out cat /data/local/tmp/boot-dialog.xml | python3 -c '
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
        timeout 10s adb shell rm -f /data/local/tmp/boot-dialog.xml >/dev/null 2>&1 || true
      fi
      recheck=$(inspect_windows)
      if [[ "$recheck" == UNEXPECTED_DIALOG:* ]]; then
        echo "ERROR: Unexpected system error/ANR dialog during emulator setup: ${recheck#UNEXPECTED_DIALOG:}" >&2
        exit 1
      fi
      if [[ "$recheck" == "LAUNCHER_ANR" ]]; then
        if [[ -n "$coords" ]]; then
          timeout 10s adb shell input tap $coords
          tapped=true
        fi
        if [[ "$tapped" == "false" && "$launcher_force_stopped" == "false" ]] && (( launcher_anr_attempts >= 2 )); then
          launcher_force_stopped=true
          timeout 10s adb shell am force-stop com.google.android.apps.nexuslauncher
          timeout 15s adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
        fi
      fi
      ;;
    WAIT_EXITING|WAIT_FOCUS)
      ;;
    *)
      echo "ERROR: Unrecognized window inspection state: ${state:-<empty>}" >&2
      exit 1
      ;;
  esac
  sleep 1
done

if [[ "$window_ready" != "true" ]]; then
  echo "ERROR: Timed out waiting for emulator window focus and Pixel Launcher ANR cleanup (last state: ${state:-<empty>})." >&2
  exit 1
fi

# Restore original error-dialog settings so subsequent tests surface any real ANRs.
restore_error_dialog_settings

restored_hide=$(read_setting global hide_error_dialogs)
restored_anr=$(read_setting secure anr_show_background)
if [[ "$restored_hide" != "$orig_hide_error_dialogs" || "$restored_anr" != "$orig_anr_show_background" ]]; then
  echo "ERROR: Failed to restore error-dialog settings (hide_error_dialogs=$restored_hide expected $orig_hide_error_dialogs, anr_show_background=$restored_anr expected $orig_anr_show_background)." >&2
  exit 1
fi

post_restore_ready=false
final_state=""
for _ in $(seq 1 5); do
  final_state=$(inspect_windows)
  case "$final_state" in
    READY:*)
      post_restore_ready=true
      break
      ;;
    UNEXPECTED_DIALOG:*|LAUNCHER_ANR)
      echo "ERROR: System error/ANR dialog present after emulator setup: $final_state" >&2
      exit 1
      ;;
    WAIT_EXITING|WAIT_FOCUS)
      sleep 1
      ;;
    *)
      echo "ERROR: Unrecognized post-restore window state: ${final_state:-<empty>}" >&2
      exit 1
      ;;
  esac
done

if [[ "$post_restore_ready" != "true" ]]; then
  echo "ERROR: Emulator lacked a focused window after setup completed (state: ${final_state:-<empty>})." >&2
  exit 1
fi
