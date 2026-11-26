#!/usr/bin/env bash
set -euo pipefail

rounds=${DEVICE_TEST_ROUNDS:-1}
case "$rounds" in 1|3) ;; *) exit 2 ;; esac

report_dir=device-reports/setup
on_exit() {
  local status=$?
  if (( status != 0 )); then
    bash .github/scripts/collect-android-diagnostics.sh "$report_dir/failure" || true
  fi
  exit "$status"
}
trap on_exit EXIT

mkdir -p "$report_dir"
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell getprop ro.build.fingerprint | tee "$report_dir/build-fingerprint.txt"

run_connected_tests() {
  mkdir -p "$report_dir"
  local status=0
  ./gradlew :app:connectedDebugAndroidTest --rerun --stacktrace --console=plain "$@" \
    2>&1 | tee "$report_dir/gradle.log" || status=$?
  for path in app/build/reports/androidTests app/build/outputs/androidTest-results \
    app/build/outputs/connected_android_test_additional_output; do
    if [[ -d "$path" ]]; then
      cp -a "$path" "$report_dir/"
    fi
  done
  return "$status"
}

for ((round = 1; round <= rounds; round++)); do
  report_dir="device-reports/round-$round"
  echo "::group::Complete device suite ($round/$rounds)"
  run_connected_tests
  echo '::endgroup::'
done

# Keep the calculator's short-window assertions on each API at 320 × 450 dp and 1.5× font scale.
report_dir=device-reports/calculator-short
adb shell wm density 320
adb shell wm size 640x900
adb shell settings put system font_scale 1.5
run_connected_tests '-Pandroid.testInstrumentationRunnerArguments.class=com.kitsuneapp.LedgerLayoutTest#calculatorUsesKeypadAndRestoresExpressionBeforeApplying'
adb shell wm size reset
adb shell wm density reset
adb shell settings delete system font_scale

if [[ "${CAPTURE_SELECTION_SHEETS:-false}" == true ]]; then
  report_dir=device-reports/selection-sheets
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  bash .github/scripts/capture-selection-sheets.sh "$report_dir"
fi
