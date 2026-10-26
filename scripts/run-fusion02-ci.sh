#!/usr/bin/env bash
# Only GitHub's ephemeral, verified emulator. Preserve the actual Gradle exit status.
set -eu
case "${1-}" in 24|25|26|35) qualification_api="$1" ;; *) exit 2 ;; esac
if [ "$qualification_api" = 35 ]; then
  mkdir -p build
  "$ANDROID_HOME/emulator/emulator" -accel-check > build/fusion02-accel.log 2>&1
fi
python3 scripts/android-lab-readiness.py --api "$qualification_api" --output build/fusion02-readiness
set +e
./gradlew -PwithAndroid :services-saf-fixture:installDebug :services-android-adapter:connectedDebugAndroidTest --no-daemon --max-workers=2
qualification_status=$?
python3 scripts/android-lab-readiness.py --api "$qualification_api" --output build/fusion02-final-ui --capture-only
qualification_diagnostic_status=$?
set -e
python3 - "$qualification_status" "$qualification_diagnostic_status" <<'PY'
import json,sys
from pathlib import Path
Path('build/fusion02-command.json').write_text(json.dumps({'gradle_exit':int(sys.argv[1]),'diagnostic_exit':int(sys.argv[2]),'diagnostic_cannot_override_test_result':True},indent=2)+'\n')
PY
exit "$qualification_status"
