# Adding a New Native Test: A Complete Example

[TOC]

If you are new to Android platform development, you might find this complete
example of adding a brand new native test from scratch useful to demonstrate the
typical workflow involved.

Note that this guide assumes that you already have some knowledge in the
platform source tree workflow. If not, please refer to
https://source.android.com/source/requirements.

In addition, if you are also unfamiliar with the gtest framework for C++, please
check out its project page first:

*   https://github.com/google/googletest

This guide uses the follow test to serve as an sample:

*   [Hello World Native Test](../../tests/example/native)

It's recommended to browse through the code first to get a rough impression
before proceeding.

## Deciding on a Source Location

Typically your team will already have an established pattern of places to check
in code, and places to add tests. Most team owns a single git repository, or
share one with other teams but have a dedicated sub directory that contains
component source code.

Assuming the root location for your component source is at `<component source
root>`, most components have `src` and `tests` folders under it, and some
additional files such as `Android.mk` (or broken up into additional `.mk`
files).

Since you are adding a brand new test, you'll probably need to create the
`tests` directory next to your component `src`, and populate it with content.

In some cases, your team might have further directory structures under `tests`
due to the need to package different suites of tests into individual binaries.
And in this case, you'll need to create a new sub directory under `tests`.

To illustrate, here's a typical directory outline for components with a single
`tests` folder:

```
\
 <component source root>
  \-- Android.mk (component makefile)
  \-- AndroidTest.mk (test config file)
  \-- src (component source)
  |    \-- foo.cpp
  |    \-- ...
  \-- tests (test source root)
      \-- Android.mk (test makefile)
      \-- src (test source)
          \-- foo_test.cpp
          \-- ...
```

and here's a typical directory outline for components with multiple test source
directories:

```
\
 <component source root>
  \-- Android.mk (component makefile)
  \-- AndroidTest.mk (test config file)
  \-- src (component source)
  |    \-- foo.cpp
  |    \-- ...
  \-- tests (test source root)
      \-- Android.mk (test makefile)
      \-- testFoo (sub test source root)
      |   \-- Android.mk (sub test makefile)
      |   \-- src (sub test source)
      |       \-- test_foo.cpp
      |       \-- ...
      \-- testBar
      |   \-- Android.mk
      |   \-- src
      |       \-- test_bar.cpp
      |       \-- ...
      \-- ...
```

Regardless of the structure, you'll end up populating the `tests` directory or
the newly created sub directory with files similar to what's in `native`
directory in the sample gerrit change. The sections below will explain in
further details of each file.

## Makefile

Each new test module must have a makefile to direct the build system with module
metadata, compile time dependencies and packaging instructions.

[Latest version of the makefile](../../tests/example/native/Android.mk)

A snapshot is included here for convenience:

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
  HelloWorldTest.cpp

LOCAL_MODULE := hello_world_test
LOCAL_MODULE_TAGS := tests

LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_NATIVE_TEST)
```

Some select remarks on the makefile:

```makefile
LOCAL_MODULE := hello_world_test
```

This setting declares the module name, which must be unique in the entire build
tree. It will also be used as the name as the binary executable of your test, as
well as a make target name, so that you can use `make [options] <LOCAL_MODULE>`
to build your test binary and all its dependencies.

```makefile
LOCAL_MODULE_TAGS := tests
```

This setting declares the module as a test module, which will instruct the build
system to generate the native test binaries under "data" output directory, so
that they can be packaged into test artifact file bundle.

```makefile
LOCAL_COMPATIBILITY_SUITE := device-tests
```

This line builds the testcase as part of the device-tests suite, which is
meant to target a specific device and not a general ABI.

```makefile
include $(BUILD_NATIVE_TEST)
```

This includes a core makefile in build system that performs the necessary steps
to compile your test, together with gtest framework under `external/gtest`, into
a native test binary. The generated binary will have the same name as
`LOCAL_MODULE`. And if `tests` is used as `LOCAL_MODULE_TAGS` and there are no
other customizations, you should be able to find your test binary in:

*   `${OUT}/data/nativetest[64]/<LOCAL_MODULE>/<LOCAL_MODULE>`

e.g. `${OUT}/data/nativetest[64]/hello_world_test/hello_world_test`

And you will also find it:
*    ${OUT}/target/product/<target>/testcases/<LOCAL_MODULE>/<arch>/<LOCAL_MODULE>

e.g. ${OUT}/target/product/arm64-generic/testcases/hello_world_test/arm/hello_world_test
&    ${OUT}/target/product/arm64-generic/testcases/hello_world_test/arm64/hello_world_test

Note: if the native ABI type of device is 64bit, such as angler, bullhead etc,
the directory name will be suffixed with `64`.

Please also note that currently the native tests in APCT does not support use of
dynamically linked libraries, which means that the dependencies needs to be
statically linked into the test binary.

## Source code

[Latest source code](../../tests/example/native/HelloWorldTest.cpp)

Annotated source code is listed below:

```c++
#include <gtest/gtest.h>
```

Header file include for gtest. Note that the include file dependency is
automatically resolved by using `BUILD_NATIVE_TEST` in the makefile

```c++
#include <stdio.h>

TEST(HelloWorldTest, PrintHelloWorld) {
    printf("Hello, World!");
}
```

gtests are written by using `TEST` macro: the first parameter is the test case
name, and the second is test name; together with test binary name, they form the
hierarchy below when visualized in result dashboard:

```
<test binary 1>
| \-- <test case 1>
| |   \-- <test 1>
| |   \-- <test 2>
| |   \-- ...
| \-- <test case 2>
| |   \-- <test 1>
| |   \-- ...
| \-- ...
<test binary 2>
|
...
```

For more information on writing tests with gtest, see its documentation:

*   https://github.com/google/googletest/blob/master/googletest/docs/Primer.md

## Test Config

In order to simplify test execution, you also need write a test configuration
file for Android's test harness, [TradeFederation](https://source.android.com/devices/tech/test_infra/tradefed/).

The test configuration can specify special device setup options and default
arguments to supply the test class.

[LATEST TEST CONFIG](../../tests/example/native/AndroidTest.xml)

A snapshot is included here for convenience:
```xml
<configuration description="Config for APCT native hello world test cases">
    <target_preparer class="com.android.tradefed.targetprep.PushFilePreparer">
        <option name="cleanup" value="true" />
        <option name="push" value="hello_world_test->/data/local/tmp/hello_world_test" />
    </target_preparer>
    <test class="com.android.tradefed.testtype.GTest" >
        <option name="native-test-device-path" value="/data/local/tmp" />
        <option name="module-name" value="hello_world_test" />
        <option name="runtime-hint" value="8m" />
    </test>
</configuration>
```

Some select remarks on the test configuration file:

```xml
<target_preparer class="com.android.tradefed.targetprep.PushFilePreparer">
    <option name="cleanup" value="true" />
    <option name="push" value="hello_world_test->/data/local/tmp/hello_world_test" />
</target_preparer>
```

This tells TradeFederation to install the hello_world_test binary onto the target
device using a specified target_preparer. There are many target preparers
available to developers in TradeFederation and these can be used to ensure
the device is setup properly prior to test execution.

```xml
<test class="com.android.tradefed.testtype.GTest" >
    <option name="native-test-device-path" value="/data/local/tmp" />
    <option name="module-name" value="hello_world_test" />
    <option name="runtime-hint" value="8m" />
</test>
```

This specifies the TradeFederation test class to use to execute the test and
passes in the native test location that it was installed.

Look here for more information on [Test Module Configs](../test-config.md)

## Build & Test Locally

Follow these [Instructions](../native.md) to build and execute your test
