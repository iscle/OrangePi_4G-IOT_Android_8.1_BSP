# Test Development Workflow

To integrate tests into platform continuous testing service, they should meet
the following guidelines.

## Test Types

Supported test types are:

*   standard [instrumentation](http://developer.android.com/tools/testing/testing_android.html) tests
    *   supports both functional and metrics tests
*   native tests
    *   functional: [gtest](https://github.com/google/googletest) framework
    *   metrics: native benchmark tests using [google-benchmark](https://github.com/google/benchmark)

Functional tests make assertions of pass or fail on test cases, while metrics
tests generally performs an action repeatedly to collect timing metrics.

With standardized input/output format, the need for customized result parsing
and post-processing per test is eliminated, and generic test harnesses can be
used for all tests that fit into the convention.

### Test Case Guidelines

Test cases executed via continuous testing service are expected to be
**hermetic**:

* no Google account sign-in
* no connectivity setup (telephony/wifi/bluetooth/NFC)
* no test parameters passed in
* no setup or tear down performed by test harness for a specific test case

### Building Tests

If you are new to the workflow of adding and executing tests, please see:

*   [Instrumentation Tests](instrumentation.md) (supports both functional and
    metrics tests)
*   [Native Tests](native.md)
*   [Native Metric Tests](metrics.md)
