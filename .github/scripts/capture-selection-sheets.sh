#!/usr/bin/env bash
set -euo pipefail

output="${1:-captures}"
mkdir -p "$output"

restore_device() {
  adb shell wm size reset
  adb shell wm density reset
  adb shell settings delete system font_scale
  adb shell cmd uimode night auto
}
trap restore_device EXIT

runner=com.kitsuneapp.test/androidx.test.runner.AndroidJUnitRunner
suite=com.kitsuneapp.SelectionSheetsScreenshotTest
sheets=(wallet category ledger-filter wallet-range currency)

for profile in light dark narrow-large; do
  case "$profile" in
    light) adb shell cmd uimode night no ;;
    dark) adb shell cmd uimode night yes ;;
    narrow-large)
      adb shell cmd uimode night no
      adb shell wm density 320
      adb shell wm size 640x1280
      adb shell settings put system font_scale 1.5
      ;;
  esac

  for sheet in "${sheets[@]}"; do
    adb shell run-as com.kitsuneapp rm -f "cache/selection-$profile-$sheet.png"
  done
  adb shell am force-stop com.kitsuneapp
  adb shell am instrument -w -e class "$suite" -e selectionProfile "$profile" "$runner" |
    tee "$output/selection-$profile.log"
  grep -Eq '^OK \([1-9][0-9]* tests?\)' "$output/selection-$profile.log"
  for sheet in "${sheets[@]}"; do
    file="selection-$profile-$sheet.png"
    adb exec-out run-as com.kitsuneapp cat "cache/$file" > "$output/$file"
  done
done

python3 - "$output" <<'PYTHON'
import sys
from pathlib import Path

captures = list(Path(sys.argv[1]).glob("selection-*.png"))
assert len(captures) == 15, f"Expected 15 sheet captures, found {len(captures)}"
for capture in captures:
    assert capture.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"), capture
PYTHON
