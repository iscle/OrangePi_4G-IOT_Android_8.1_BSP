# Native Tests

As mentioned earlier, native tests are typically used for exercising HAL or
interacting directly with lower level system services, and to leverage
continuous testing service, native tests should be built with
[gtest](https://github.com/google/googletests) framework.

Here are some general instructions:

1. See sample native test module setup at: `libs/hwui/unit_tests`
1. Test module makefile should use `BUILD_NATIVE_TEST` build rule so that
gtest dependencies are included automatically
1. Write a [test config](../test-config.md)
1. Build the test module with `mmm` or `mma` (depends on if it's an
incremental or full build), e.g.:

   ```shell
   make hwui_unit_tests -j
   ```
1.  Automatic installation and run with the TradeFederation test harness:

    ```
    make tradefed-all -j
    tradefed.sh run template/local_min --template:map test=hwui_unit_tests
    ```
1. Manually Install and Run:
   1. Push the generated test binary onto device:

      ```shell
      adb push ${OUT}/data/nativetest/hwui_unit_tests/hwui_unit_tests \
        /data/nativetest/hwui_unit_tests/hwui_unit_tests
      ```
   1. Execute the test by invoking test binary on device:

      ```shell
      adb shell /data/nativetest/hwui_unit_tests/hwui_unit_tests
   ```

   This launches the native test. You can also add `--help` parameter to your test
   binary to find out more about the different ways to customize test execution.
   You can also check out the gtest advanced guide for more parameters usage:

   *   https://github.com/google/googletest/blob/master/googletest/docs/AdvancedGuide.md
