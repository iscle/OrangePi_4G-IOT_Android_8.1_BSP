# Instrumentation Targeting an Application: A Complete Example

[TOC]

If you are new to Android platform development, you might find this complete
example of adding a brand new instrumentation test from scratch useful to
demonstrate the typical workflow involved.

Note that this guide assumes that you already have some knowledge in the
platform source tree workflow. If not, please refer to
https://source.android.com/source/requirements. The example
covered here is writing an new instrumentation test with target package set at
its own test application package. If you are unfamiliar with the concept, please
read through the [testing basics](../basics/index.md) page.

This guide uses the follow test to serve as an sample:

*   frameworks/base/packages/Shell/tests

It's recommended to browse through the code first to get a rough impression
before proceeding.

## Deciding on a Source Location

Because the instrumentation test will be targeting an application, the convention
is to place the test source code in a `tests` directory under the root of your
component source directory in platform source tree.

See more discussions about source location in the [end-to-end example for
self-instrumenting tests](instr-self-e2e.md#location).

## Makefile

Each new test module must have a makefile to direct the build system with module
metadata, compile time depdencies and packaging instructions.

frameworks/base/packages/Shell/tests/Android.mk

A snapshot is included here for convenience:

```makefile
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_STATIC_JAVA_LIBRARIES := ub-uiautomator junit legacy-android-test

LOCAL_PACKAGE_NAME := ShellTests
LOCAL_INSTRUMENTATION_FOR := Shell

LOCAL_COMPATIBILITY_SUITE := device-tests

LOCAL_CERTIFICATE := platform
include $(BUILD_PACKAGE)
```

Some select remarks on the makefile:

```makefile
LOCAL_MODULE_TAGS := tests
```

This setting declares the module as a test module, which will instruct the build
system to automatically skip proguard stripping, since that's typically
problematic for tests.

```makefile
LOCAL_CERTIFICATE := platform
```

This setting instructs the build system to sign the test application package
with the platform certificate. This is because for a test application package to
be able to instrument on the targeted application package, these two packages
must be signed with the same certificate; otherwise allowing packages to be
instrumented on arbitrarily would be a security concern. To find out the signing
certificate of the application packge you are testing, look for
`LOCAL_CERTIFICATE` in its `Android.mk`; and if there isn't one, simply skip
this field in your test application makefile as well.

```makefile
LOCAL_JAVA_LIBRARIES := android.test.runner
```

This setting tells the build system to put Java library `android.test.runner` on
classpath during compilation, as opposed to statically incorporating the library
into the current package. This is typically done for Java code that is
referenced by the code in current package, and will be automatically placed on
package classpath at runtime. In the context of tests for application, strictly
speaking, both framework APIs and code in application under test fall into this
category, however, the former is done via implicit rules by build system at
compile time and by framework at runtime, and the latter is done via
`LOCAL_INSTRUMENTATION_FOR` (see below) at compile time and via
`android:targetPackage` (see below) in manifest by instrumentation framework at
runtime.

```makefile
LOCAL_STATIC_JAVA_LIBRARIES := ub-uiautomator junit legacy-android-test
```

This setting instructs the build system to incorporate the contents of the named
modules into the resulting apk of current module. This means that each named
module is expected to produce a `.jar` file, and its content will be used for
resolving classpath references during compile time, as well as incorporated into
the resulting apk.


The platform source tree also included other useful testing frameworks such as
`ub-uiautomator`, `easymock` and so on.

```makefile
LOCAL_PACKAGE_NAME := ShellTests
```

This setting is required if `BUILD_PACKAGE` when used later: it gives a name to
your module, and the resulting apk will be named the same and with a `.apk`
suffix, e.g. in this case, resulting test apk is named as
`ShellTests.apk`. In addition, this also defines a make target name
for your module, so that you can use `make [options] <LOCAL_PACKAGE_NAME>` to
build your test module and all its dependencies.

```makefile {# LOCAL_INSTRUMENTATION_FOR}
LOCAL_INSTRUMENTATION_FOR := Shell
```

As mentioned, during execution of an instrumentation test, the application under
test is restarted with the instrumentation code injected for execution. The test
can reference any classes and its instances of the application under test. This
means that the test code may contain references to classes defined by the
application under test, so during compile time, the build system needs to
properly resolve such references. This setting provides the module name of
application under test, which should match the `LOCAL_PACKAGE_NAME` of in the
makefile for your application. At compile time, the build system will try to
look up the intermediate files for the named module, and use them on the
classpath for the Java compiler.

```makefile
LOCAL_COMPATIBILITY_SUITE := device-tests
```

This line builds the testcase as part of the device-tests suite, which is
meant to target a specific device and not a general ABI. If only the ABI
needs to be targetted, it can be swapped with 'general-tests'.

```makefile
include $(BUILD_PACKAGE)
```

This includes a core makefile in build system that performs the necessary steps
to generate an apk based on the settings provided by the preceding variables.
The generated apk will be named after `LOCAL_PACKAGE_NAME`, e.g.
`SettingsGoogleUnitTests.apk`. And if `tests` is used as `LOCAL_MODULE_TAGS` and
there are no other customizations, you should be able to find your test apk in:

*   `${OUT}/data/app/<LOCAL_PACKAGE_NAME>/<LOCAL_PACKAGE_NAME>.apk`

e.g. `${OUT}/data/app/ShellTests/ShellTests.apk`

## Manifest file

Just like a regular application, each instrumentation test module needs a
manifest file. If you name the file as `AndroidManifest.xml` and provide it next
to `Android.mk` for your test tmodule, it will get included automatically by the
`BUILD_PACKAGE` core makefile.

Before proceeding further, it's highly recommended to go through the external
[documentation on manifest file](https://developer.android.com/guide/topics/manifest/manifest-intro.html)
first.

This gives an overview of basic components of a manifest file and their
functionalities.

Latest version of the manifest file for the sample gerrit change can be accessed
at:
https://android.googlesource.com/platform/frameworks/base/+/master/packages/Shell/tests/AndroidManifest.xml

A snapshot is included here for convenience:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.android.shell.tests">

    <application>
        <uses-library android:name="android.test.runner" />

        <activity
            android:name="com.android.shell.ActionSendMultipleConsumerActivity"
            android:label="ActionSendMultipleConsumer"
            android:theme="@android:style/Theme.NoDisplay"
            android:noHistory="true"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
        </activity>
    </application>

    <instrumentation android:name="android.support.test.runner.AndroidJUnitRunner"
        android:targetPackage="com.android.shell"
        android:label="Tests for Shell" />

</manifest>
```

Some select remarks on the manifest file:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.android.shell.tests">
```

The `package` attribute is the application package name: this is the unique
identifier that the Android application framework uses to identify an
application (or in this context: your test application). Each user in the system
can only install one application with that package name.

Since this is a test application package, independent from the application
package under test, a different package name must be used: one common convention
is to add a suffix `.test`.

Furthermore, this `package` attribute is the same as what
[`ComponentName#getPackageName()`](https://developer.android.com/reference/android/content/ComponentName.html#getPackageName\(\))
returns, and also the same you would use to interact with various `pm` sub
commands via `adb shell`.

Please also note that although the package name is typically in the same style
as a Java package name, it actually has very few things to do with it. In other
words, your application (or test) package may contain classes with any package
names, though on the other hand, you could opt for simplicity and have your top
level Java package name in your application or test identical to the application
package name.

```xml
<uses-library android:name="android.test.runner" />
```

This is required for all Instrumentation tests since the related classes are
packaged in a separate framework jar library file, therefore requires additional
classpath entries when the test package is invoked by application framework.

```xml
android:targetPackage="com.android.shell"
```

This sets the target package of the instrumentation to `com.android.shell.tests`.
When the instrumentation is invoked via `am instrument` command, the framework
restarts `com.android.shell.tests` process, and injects instrumentation code into
the process for test execution. This also means that the test code will have
access to all the class instances running in the application under test and may
be able to manipulate state depends on the test hooks exposed.

## Test Configuration File

In order to simplify test execution, you also need write a test configuration
file for Android's test harness, [TradeFederation](https://source.android.com/devices/tech/test_infra/tradefed/).

The test configuration can specify special device setup options and default
arguments to supply the test class.

The config can be found:
frameworks/base/packages/Shell/tests/src/com/android/shell/BugreportReceiverTest.javast.java

A snapshot is included here for convenience:

```xml
<configuration description="Runs Tests for Shell.">
    <target_preparer class="com.android.tradefed.targetprep.TestAppInstallSetup">
        <option name="test-file-name" value="ShellTests.apk" />
    </target_preparer>

    <option name="test-suite-tag" value="apct" />
    <option name="test-tag" value="ShellTests" />
    <test class="com.android.tradefed.testtype.AndroidJUnitTest" >
        <option name="package" value="com.android.shell.tests" />
        <option name="runner" value="android.support.test.runner.AndroidJUnitRunner" />
    </test>
</configuration>
```

Some select remarks on the test configuration file:

```xml
<target_preparer class="com.android.tradefed.targetprep.TestAppInstallSetup">
  <option name="test-file-name" value="ShellTests.apk"/>
</target_preparer>
```
This tells TradeFederation to install the ShellTests.apk onto the target
device using a specified target_preparer. There are many target preparers
available to developers in TradeFederation and these can be used to ensure
the device is setup properly prior to test execution.

```xml
<test class="com.android.tradefed.testtype.AndroidJUnitTest">
  <option name="package" value="com.android.shell.tests"/>
  <option name="runner" value="android.support.test.runner.AndroidJUnitRunner"/>
</test>
```
This specifies the TradeFederation test class to use to execute the test and
passes in the package on the device to be executed and the test runner
framework which is JUnit in this case.

Look here for more information on [Test Module Configs](../test-config.md)

## JUnit4 Features

Using `android-support-test` library as test runner enables adoptation of new
JUnit4 style test classes, and the sample gerrit change contains some very basic
use of its features.

Latest source code for the sample gerrit change can be accessed at:
frameworks/base/packages/Shell/tests/src/com/android/shell/BugreportReceiverTest.javast.java

While testing patterns are usually specific to component teams, there are some
generally useful usage patterns.

```java
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class FeatureFactoryImplTest {
```

A significant difference in JUnit4 is that tests are no longer required to
inherit from a common base test class; instead, you write tests in plain Java
classes and use annotation to indicate certain test setup and constraints. In
this example, we are instructing that this class should be run as an Android
JUnit4 test.

The `@SmallTest` annotation specified a test size for the entire test class: all
test methods added into this test class inherit this test size annotation.
pre test class setup, post test tear down, and post test class tear down:
similar to `setUp` and `tearDown` methods in JUnit4.
`Test` annotation is used for annotating the actual test.

**Important**: the test methods themselves are annotated with `@Test`
annotation; and note that for tests to be executed via APCT, they must be
annotated with test sizes. Such annotation may be applied at method scope, or
class scope.

```java
    @Before
    public void setup() {
    ...
    @Test
    public void testGetProvider_shouldCacheProvider() {
    ...
```

The `@Before` annotation is used on methods by JUnit4 to perform pre test setup.
Although not used in this example, there's also `@After` for post test teardown.
Similarly, the `@BeforeClass` and `@AfterClass` annotations are can be used on
methods by JUnit4 to perform setup before executing all tests in a test class,
and teardown afterwards. Note that the class-scope setup and teardown methods
must be static.

As for the test methods, unlike in earlier version of JUnit, they no longer need
to start the method name with `test`, instead, each of them must be annotated
with `@Test`. As usual, test methods must be public, declare no return value,
take no parameters, and may throw exceptions.

```java
        Context context = InstrumentationRegistry.getTargetContext();
```

Because the JUnit4 tests no longer require a common base class, it's no longer
necessary to obtain `Context` instances via `getContext()` or
`getTargetContext()` via base class methods; instead, the new test runner
manages them via [`InstrumentationRegistry`](https://developer.android.com/reference/android/support/test/InstrumentationRegistry.html)
where contextual and environmental setup created by instrumentation framework is
stored. Through this class, you can also call:

*   `getInstrumentation()`: the instance to the `Instrumentation` class
*   `getArguments()`: the command line arguments passed to `am instrument` via
    `-e <key> <value>`

## Build & Test Locally

Follow these [Instructions](../instrumentation.md)
