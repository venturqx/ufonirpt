#!/usr/bin/env bash
# Same as `ubf` (build + install debug, launch on phone), output saved to output.txt.
# -q and dropping "w: " lines keep output.txt to errors only, so it's cheap to paste to an AI.
set -o pipefail
cd ~/prog/ufonirpt || exit 1
out=output.txt

{
  env JAVA_HOME=/opt/android-studio/jbr PATH=/opt/android-studio/jbr/bin:$PATH \
    ./gradlew :app:installDebug -q --console=plain 2>&1 | grep -v '^w: '
  rc=${PIPESTATUS[0]}
  if [ "$rc" -ne 0 ]; then
    echo "BUILD FAILED (exit $rc)"
    exit "$rc"
  fi
  echo "BUILD OK"
  pkg=$(adb shell pm list packages | grep -o 'dev\.ufonirpt\.ufonirpt[^[:space:]]*' | head -1)
  adb shell am start -n "$pkg/org.schabi.newpipe.MainActivity" 2>&1
} | tee "$out"
exit "${PIPESTATUS[0]}"
