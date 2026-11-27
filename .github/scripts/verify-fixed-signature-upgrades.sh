#!/usr/bin/env bash
set -euo pipefail

mkdir -p upgrade-reports
on_exit() {
  local status=$?
  if (( status != 0 )); then
    bash .github/scripts/collect-android-diagnostics.sh upgrade-reports/failure || true
  fi
  exit "$status"
}
trap on_exit EXIT

run_upgrade_test() {
  local phase=$1 report=$2
  adb shell am instrument -w -e class com.kitsuneapp.FixedSigningUpgradeTest \
    -e kitsuneUpgradePhase "$phase" com.kitsuneapp.test/androidx.test.runner.AndroidJUnitRunner |
    tee "upgrade-reports/$report.log"
  grep -Eq '^OK \(1 test\)' "upgrade-reports/$report.log"
}

adb install apk-signed/app-debug.apk
adb install -t apk-signed/app-debug-androidTest.apk
run_upgrade_test seed debug-seed
adb install -r apk-signed/app-release.apk
run_upgrade_test verify release-verify
adb install -r apk-signed/app-debug.apk
run_upgrade_test verify debug-verify
