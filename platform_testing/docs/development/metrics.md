# Native Metric Tests

As mentioned earlier, native metric tests are typically used for exercising HAL
or interacting directly with lower level system services, and to leverage
continuous testing service, native metric tests should be built with
[google-benchmark](https://github.com/google/benchmark) framework.

Here are some general instructions:

1. See sample native test module setup at: `bionic/benchmarks/bionic-benchmarks`
1. Test module makefile should use `BUILD_NATIVE_BENCHMARK` build rule so that
google-benchmark dependencies are included automatically
1. Build the test module with make:

   ```shell
   make -j40 bionic-benchmarks
   ```
1.  Automatic installation and run with the TradeFederation test harness:

    ```
    make tradefed-all -j
    tradefed.sh run template/local_min --template:map test=bionic-benchmarks
1. Manually Install and Run:
   1. Push the generated test binary onto device:

      ```shell
      adb push ${OUT}/data/benchmarktest/bionic-benchmarks/bionic-benchmarks32 \
        /data/benchmarktest/bionic-benchmarks/bionic-benchmarks32
      ```
   1. Execute the test by invoking test binary on device:

      ```shell
      adb shell /data/benchmarktest/bionic-benchmarks/bionic-benchmarks32
      ```
