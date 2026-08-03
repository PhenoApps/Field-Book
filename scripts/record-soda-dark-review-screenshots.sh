#!/usr/bin/env bash
# Record PR #1477 soda-dark review screenshots into trait-tree/screenshots/soda-dark-pr1477/
#
# Gallery stems (01–15): traits icons, summary/nav, collect audio, export/delete/person/sort
# dialogs, data grid, field stats, BrAPI sync/chips/checker, attach media, crop mask,
# collect photo settings (CameraTraitSettings / view_trait_photo_settings).
#
# Theme is controlled by ScreenshotThemeConfig.FORCE_SODA_DARK in
# app/src/test/.../screenshots/ScreenshotAppTheme.kt (true = soda dark).
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-/root/development/Field-Book/.android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
# Prefer the machine Gradle cache when a sandbox remaps GRADLE_USER_HOME.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

REVIEW_TEST='com.fieldbook.tracker.screenshots.SodaDarkReviewScreenshotTest'
# --offline: use local Gradle cache only (no Maven DNS). Override with GRADLE_ONLINE=1.
OFFLINE_ARGS=()
if [[ "${GRADLE_ONLINE:-}" != "1" ]]; then
  OFFLINE_ARGS=(--offline)
fi
GRADLE=(./gradlew "${OFFLINE_ARGS[@]}" :app:testDebugUnitTest
  -PincludeSodaDarkScreenshots
  -Proborazzi.test.record=true
  --tests "$REVIEW_TEST")

if [[ "${1:-}" == "--force" ]]; then
  "${GRADLE[@]}" --rerun-tasks
else
  "${GRADLE[@]}"
fi

./gradlew "${OFFLINE_ARGS[@]}" :app:publishSodaDarkReviewScreenshots

echo
echo "Soda-dark PR #1477 gallery: trait-tree/screenshots/soda-dark-pr1477/"
ls -1 trait-tree/screenshots/soda-dark-pr1477/*.png 2>/dev/null || true
echo
echo "Reports:"
echo "  app/build/reports/roborazzi/index.html"
