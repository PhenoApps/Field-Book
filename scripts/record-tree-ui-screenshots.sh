#!/usr/bin/env bash
# Record integrated tree UI PNGs into trait-tree/screenshots/integrated/
set -euo pipefail
cd "$(dirname "$0")/.."

INTEGRATED_TEST='com.fieldbook.tracker.screenshots.TreeIntegratedScreenshotTest'
GRADLE=(./gradlew :app:testDebugUnitTest -PincludeTreeScreenshots -Proborazzi.test.record=true --tests "$INTEGRATED_TEST")

if [[ "${1:-}" == "--force" ]]; then
  "${GRADLE[@]}" --rerun-tasks
else
  "${GRADLE[@]}"
fi

./gradlew :app:publishTreeIntegratedScreenshots

echo
echo "Integrated gallery: trait-tree/screenshots/integrated/"
ls -1 trait-tree/screenshots/integrated/*.png 2>/dev/null || true
echo
echo "Reports:"
echo "  app/build/reports/roborazzi/index.html"
