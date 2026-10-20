#!/usr/bin/env bash
# ==============================================================================
# Offline Android Sandbox: Test Verification Runner (Bash)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=========================================================="
echo " [TEST VERIFICATION] Running Sandbox Test Suites"
echo " Project Directory: ${PROJECT_ROOT}"
echo "=========================================================="

cd "${PROJECT_ROOT}"

# Step 1: Toolchain asset verification
echo "--> Step 1/2: Preparing Toolchain Assets..."
chmod +x "${SCRIPT_DIR}/setup-toolchain-assets.sh"
"${SCRIPT_DIR}/setup-toolchain-assets.sh"

# Step 2: Executing Gradle tests
echo ""
echo "--> Step 2/2: Executing Gradle Test Suites..."
chmod +x "${PROJECT_ROOT}/gradlew"

TARGET_SUITE="${1:-}"

if [ -n "${TARGET_SUITE}" ]; then
    ./gradlew test --tests "com.android.sandbox.compiler.${TARGET_SUITE}" --info
else
    ./gradlew test --info
fi

REPORT_PATH="${PROJECT_ROOT}/app/build/reports/tests/testDebugUnitTest/index.html"
echo "=========================================================="
echo " [SUCCESS] All Test Suites Completed!"
if [ -f "${REPORT_PATH}" ]; then
    echo " Test Report HTML: file://${REPORT_PATH}"
fi
echo "=========================================================="
