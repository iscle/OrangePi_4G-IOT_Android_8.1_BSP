#!/usr/bin/env bash

# This script was copied from vendor/google/apps/SetupWizard/tools/coverage.sh

##### App specific parameters #####

PACKAGE_NAME='com.android.managedprovisioning'
MODULE_NAME='ManagedProvisioning'
MODULE_PATH='packages/apps/ManagedProvisioning'
MODULE_INSTALL_PATH='system/priv-app/ManagedProvisioning/ManagedProvisioning.apk'

TEST_MODULE_PATH='packages/apps/ManagedProvisioning/tests/instrumentation'
TEST_MODULE_INSTALL_PATH='data/app/ManagedProvisioningTests/ManagedProvisioningTests.apk'
TEST_RUNNER='com.android.managedprovisioning.tests/com.android.managedprovisioning.TestInstrumentationRunner'

##### End app specific parameters #####

if [[ $# != 0 && ! ($# == 1 && ($1 == "HTML" || $1 == "XML" || $1 == "CSV")) ]]; then
  echo "$0: usage: coverage.sh [REPORT_TYPE]"
  echo "REPORT_TYPE [HTML | XML | CSV] : the type of the report (default is HTML)"
  exit 1
fi

REPORT_TYPE=${1:-HTML}

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

REPORTER_JAR="$ANDROID_BUILD_TOP/prebuilts/sdk/tools/jack-jacoco-reporter.jar"

OUTPUT_DIR="$ANDROID_BUILD_TOP/out/coverage/$MODULE_NAME"

echo "Running tests and generating coverage report"
echo "Output dir: $OUTPUT_DIR"
echo "Report type: $REPORT_TYPE"

REMOTE_COVERAGE_OUTPUT_FILE="/data/data/$PACKAGE_NAME/files/coverage.ec"
COVERAGE_OUTPUT_FILE="$ANDROID_BUILD_TOP/out/$PACKAGE_NAME.ec"
OUT_COMMON="$ANDROID_BUILD_TOP/out/target/common"
COVERAGE_METADATA_FILE="$OUT_COMMON/obj/APPS/${MODULE_NAME}_intermediates/coverage.em"

source $ANDROID_BUILD_TOP/build/envsetup.sh

set -e # fail early

(cd "$ANDROID_BUILD_TOP/$MODULE_PATH" && EMMA_INSTRUMENT_STATIC=true mma -j32)
(cd "$ANDROID_BUILD_TOP/$TEST_MODULE_PATH" && EMMA_INSTRUMENT_STATIC=true mma -j32)

set -x # print commands

adb root
adb wait-for-device

adb shell rm -f "$REMOTE_COVERAGE_OUTPUT_FILE"

adb install -r -g "$OUT/$TEST_MODULE_INSTALL_PATH"
adb install -r -g "$OUT/$MODULE_INSTALL_PATH"

adb shell am instrument -e coverage true -e size small -w "$TEST_RUNNER"

mkdir -p "$OUTPUT_DIR"

adb pull "$REMOTE_COVERAGE_OUTPUT_FILE" "$COVERAGE_OUTPUT_FILE"

java -jar "$REPORTER_JAR" \
  --report-dir "$OUTPUT_DIR" \
  --metadata-file "$COVERAGE_METADATA_FILE" \
  --coverage-file "$COVERAGE_OUTPUT_FILE" \
  --report-type "$REPORT_TYPE" \
  --source-dir "$ANDROID_BUILD_TOP/$MODULE_PATH/src"

set +x

# Echo the file as URI to quickly open the result using ctrl-click in terminal
echo "file://$OUTPUT_DIR/index.html"
