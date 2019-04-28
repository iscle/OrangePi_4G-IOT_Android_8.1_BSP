# Instrumentation Tests

1.  Below are common destinations for hermetic tests against framework services:

    ```
    frameworks/base/core/tests/coretests
    frameworks/base/services/tests/servicestests
    ```

    If you are adding a brand new instrumentation module for your component, see

    *   [Self-Instrumenting Tests: A Complete Example](instr-self-e2e.md)
    *   [Instrumentation Targeting an Application: A Complete Example]
        (instr-app-e2e.md)

1.  Following the existing convention if you are adding tests into one of the
    locations above. If you are setting up a new test module, please follow the
    setup of `AndroidManifest.xml` and `Android.mk` in one of the locations
    above

1.  See https://android.googlesource.com/platform/frameworks/base.git/+/master/core/tests/coretests/ for an example

1.  Note: do not forget to mark your test as `@SmallTest`, `@MediumTest` or
    `@LargeTest`

1.  Build the test module with make, e.g.:

    ```
    make FrameworksCoreTests -j
    ```

1.  Automatic installation and run with the TradeFederation test harness:

    ```
    make tradefed-all -j
    tradefed.sh run template/local_min --template:map test=FrameworksCoreTests
    ```

1.  Manually Install and Run:
    1. Install the generated apk:

    ```
    adb install -r ${OUT}/data/app/FrameworksCoreTests/FrameworksCoreTests.apk
    ```

    Tip: you use `adb shell pm list instrumentation` to find the
    instrumentations inside the apk just installed

    1.  Run the tests with various options:

        1.  all tests in the apk

            ```
            adb shell am instrument -w com.android.frameworks.coretests\
              /android.support.test.runner.AndroidJUnitRunner
            ```

        1.  all tests under a specific Java package

            ```
            adb shell am instrument -w -e package android.animation \
              com.android.frameworks.coretests\
              /android.support.test.runner.AndroidJUnitRunner
            ```

        1.  all tests under a specific class

            ```
            adb shell am instrument -w -e class \
              android.animation.AnimatorSetEventsTest \
              com.android.frameworks.coretests\
              /android.support.test.runner.AndroidJUnitRunner
            ```

        1.  a specific test method

            ```
            adb shell am instrument -w -e class \
              android.animation.AnimatorSetEventsTest#testCancel \
              com.android.frameworks.coretests\
              /android.support.test.runner.AndroidJUnitRunner
            ```

Your test can make an explicit assertion on pass or fail using `JUnit` APIs; in
addition, any uncaught exceptions will also cause a functional failure.

To emit performance metrics, your test code can call
[`Instrumentation#sendStatus`](http://developer.android.com/reference/android/app/Instrumentation.html#sendStatus\(int, android.os.Bundle\))
to send out a list of key-value pairs. It's important to note that:

1.  metrics can be integer or floating point
1.  any non-numerical values will be discarded
1.  your test apk can be either functional tests or metrics tests, however
    mixing both are not currently supported
