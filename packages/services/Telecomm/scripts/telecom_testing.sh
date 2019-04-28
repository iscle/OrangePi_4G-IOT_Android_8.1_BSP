_lite_test_general() {
  usage="
  Usage: $0 [-c CLASSNAME] [-d] [-a | -i] [-e], where

  -c CLASSNAME          Run tests only for the specified class/method. CLASSNAME
                          should be of the form SomeClassTest or SomeClassTest#testMethod.
  -d                    Waits for a debugger to attach before starting to run tests.
  -i                    Rebuild and reinstall the test apk before running tests (mmm).
  -a                    Rebuild all dependencies and reinstall the test apk before/
                          running tests (mmma).
  -e                    Run code coverage. Coverage will be output into the coverage/
                          directory in the repo root.
  -g                    Run build commands with USE_GOMA=true
  -h                    This help message.
  "

  local OPTIND=1
  local class=
  local project=
  local install=false
  local installwdep=false
  local debug=false
  local coverage=false
  local goma=false

  while getopts "c:p:hadieg" opt; do
    case "$opt" in
      h)
        echo "$usage"
        return 0;;
      \?)
        echo "$usage"
        return 0;;
      c)
        class=$OPTARG;;
      d)
        debug=true;;
      i)
        install=true;;
      a)
        install=true
        installwdep=true;;
      e)
        coverage=true;;
      g)
        goma=true;;
      p)
        project=$OPTARG;;
    esac
  done

  local build_dir=
  local apk_loc=
  local package_prefix=
  local instrumentation=
  case "$project" in
    "telecom")
      build_dir="packages/services/Telecomm/tests"
      apk_loc="data/app/TelecomUnitTests/TelecomUnitTests.apk"
      package_prefix="com.android.server.telecom.tests"
      instrumentation="android.test.InstrumentationTestRunner";;
    "telephony")
      build_dir="frameworks/opt/telephony/tests/"
      apk_loc="data/app/FrameworksTelephonyTests/FrameworksTelephonyTests.apk"
      package_prefix="com.android.frameworks.telephonytests"
      instrumentation="android.support.test.runner.AndroidJUnitRunner";;
  esac

  local T=$(gettop)

  if [ $install = true ] ; then
    local olddir=$(pwd)
    local emma_opt=
    local goma_opt=

    cd $T
    # Build and exit script early if build fails

    if [ $coverage = true ] ; then
      emma_opt="EMMA_INSTRUMENT=true LOCAL_EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true"
    else
      emma_opt="EMMA_INSTRUMENT=false"
    fi

    if [ $goma = true ] ; then
        goma_opt="USE_GOMA=true"
    fi

    if [ $installwdep = true ] ; then
      (export ${emma_opt}; mmma ${goma_opt} -j40 "$build_dir")
    else
      (export ${emma_opt}; mmm ${goma_opt} "$build_dir")
    fi
    if [ $? -ne 0 ] ; then
      echo "Make failed! try using -a instead of -i if building with coverage"
      return
    fi

    # Strip off any possible aosp_ prefix from the target product
    local canonical_product=$(sed 's/^aosp_//' <<< "$TARGET_PRODUCT")

    adb install -r -t "out/target/product/$canonical_product/$apk_loc"
    if [ $? -ne 0 ] ; then
      cd "$olddir"
      return $?
    fi
    cd "$olddir"
  fi

  local e_options=""
  if [ -n "$class" ] ; then
    if [[ "$class" =~ "\." ]] ; then
      e_options="${e_options} -e class ${class}"
    else
      e_options="${e_options} -e class ${package_prefix}.${class}"
    fi
  fi
  if [ $debug = true ] ; then
    e_options="${e_options} -e debug 'true'"
  fi
  if [ $coverage = true ] && [ $project =~ "telecom" ] ; then
    e_options="${e_options} -e coverage 'true'"
  fi
  adb shell am instrument ${e_options} -w "$package_prefix/$instrumentation"

  # Code coverage only enabled for Telecom.
  if [ $coverage = true ] && [ $project =~ "telecom" ] ; then
    adb root
    adb wait-for-device
    adb pull /data/user/0/com.android.server.telecom.tests/files/coverage.ec /tmp/
    if [ ! -d "$T/coverage" ] ; then
      mkdir -p "$T/coverage"
    fi
    java -jar "$T/prebuilts/sdk/tools/jack-jacoco-reporter.jar" \
      --report-dir "$T/coverage/" \
      --metadata-file "$T/out/target/common/obj/APPS/TelecomUnitTests_intermediates/coverage.em" \
      --coverage-file "/tmp/coverage.ec" \
      --source-dir "$T/packages/services/Telecomm/src/"
  fi
}

lite_test_telecom() {
  _lite_test_general -p telecom $@
}

lite_test_telephony() {
  _lite_test_general -p telephony $@
}
