# Self-Instrumenting Tests: A Complete Example

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

*   [Hello World Instrumentation Test](../../tests/example/instrumentation)

It's recommended to browse through the code first to get a rough impression
before proceeding.

## Deciding on a Source Location {#location}

Typically your team will already have an established pattern of places to check
in code, and places to add tests. Most team owns a single git repository, or
share one with other teams but have a dedicated sub directory that contains
component source code.

Assuming the root location for your component source is at `<component source
root>`, most components have `src` and `tests` folders under it, and some
additional files such as `Android.mk` (or broken up into additional `.mk` files),
the manifest file `AndroidManifest.xml`, and the test configuration file
'AndroidTest.xml'.

Since you are adding a brand new test, you'll probably need to create the
`tests` directory next to your component `src`, and populate it with content.

In some cases, your team might have further directory structures under `tests`
due to the need to package different suites of tests into individual apks. And
in this case, you'll need to create a new sub directory under `tests`.

Regardless of the structure, you'll end up populating the `tests` directory or
the newly created sub directory with files similar to what's in
`instrumentation` directory in the sample gerrit change. The sections below will
explain in further details of each file.

## Makefile

Each new test module must have a makefile to direct the build system with module
metadata, compile time depdencies and packaging instructions.

[Latest version of the makefile](../../tests/example/instrumentation/Android.mk)


A snapshot is included here for convenience:

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_MODULE_TAGS := tests
LOCAL_PACKAGE_NAME := HelloWorldTests

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_CERTIFICATE := platform

LOCAL_COMPATIBILITY_SUITE := device-tests

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
LOCAL_PACKAGE_NAME := HelloWorldTests
```

This setting is required when `BUILD_PACKAGE` is used later: it gives a name to
your module, and the resulting apk will be named the same and with a `.apk`
suffix, e.g. in this case, resulting test apk is named as `HelloWorldTests.apk`.
In addition, this also defines a make target name for your module, so that you
can use `make [options] <LOCAL_PACKAGE_NAME>` to build your test module and all
its dependencies.

```makefile
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
```

This setting instructs the build system to incorporate the contents of the named
modules into the resulting apk of current module. This means that each named
module is expected to produce a `.jar` file, and its content will be used for
resolving classpath references during compile time, as well as incorporated into
the resulting apk.

In this example, things that might be generally useful for tests:

*   `android-support-test` is the prebuilt for Android Test Support Library,
    which included the new test runner `AndroidJUnitRunner`: a replacement for
    the now deprecated built-in `InstrumentationTestRunner`, with support for
    JUnit4 testing framework. Find out more at:

    *   https://google.github.io/android-testing-support-library/

    If you are building a new instrumentation module, you should always start
    with this library as your test runner.

The platform source tree also included other useful testing frameworks such as
`ub-uiautomator`, `mockito-target`, `easymock` and so on.

```makefile
LOCAL_CERTIFICATE := platform
```

This setting instructs the build system to sign the apk with the same
certificate as the core platform. This is needed if your test uses a signature
protected permission or API. Note that this is suitable for platform continuous
testing, but should *not* be used in CTS test modules. Note that this example
uses this certificat setting only for the purpose of illustration: the test code
of the example does not actually need for the test apk to be signed with the
special platform certificate.

If you are writing an instrumentation for your component that lives outside of
system server, that is, it's packaged more or less like a regular app apk,
except that it's built into system image and may be a priveleged app, chances
are that your instrumentation will be targeting the app package (see below
section about manifest) of your component. In this case, your applicaiton
makefile may have its own `LOCAL_CERTIFICATE` setting, and your instrumentation
module should retain the same setting. This is because to target your
instrumentation on the app under test, your test apk and app apk must be signed
with the same certificate.

In other cases, you don't need to have this setting at all: the build system
will simply sign it with a default built-in certificate, based on the build
variant, and it's typically called the `dev-keys`.

```makefile
LOCAL_COMPATIBILITY_SUITE := device-tests
```

This sets up the test to be easily discoverable by the TradeFederation test
harness. Other suites can be added here such as CTS so that this test may be
shared.

```makefile
include $(BUILD_PACKAGE)
```

This includes a core makefile in build system that performs the necessary steps
to generate an apk based on the settings provided by the preceding variables.
The generated apk will be named after `LOCAL_PACKAGE_NAME`, e.g.
`HelloWorldTests.apk`. And if `tests` is used as `LOCAL_MODULE_TAGS` and there
are no other customizations, you should be able to find your test apk in:

*   `${OUT}/data/app/<LOCAL_PACKAGE_NAME>/<LOCAL_PACKAGE_NAME>.apk`

e.g. `${OUT}/data/app/HelloWorldTests/HelloWorldTests.apk`

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

[Latest Manifest File](../../tests/example/instrumentation/AndroidManifest.xml)

A snapshot is included here for convenience:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="android.test.example.helloworld"
    android:sharedUserId="android.uid.system" >

    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="21" />

    <application>
        <uses-library android:name="android.test.runner" />
    </application>

    <instrumentation android:name="android.support.test.runner.AndroidJUnitRunner"
                     android:targetPackage="android.test.example.helloworld"
                     android:label="Hello World Test"/>

</manifest>
```

Some select remarks on the manifest file:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="android.test.example.helloworld"
```

The `package` attribute is the application package name: this is the unique
identifier that the Android application framework uses to identify an
application (or in this context: your test application). Each user in the system
can only install one application with that package name.

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
android:sharedUserId="android.uid.system"
```

This declares that at installation time, this apk should be granted the same
user id, i.e. runtime identity, as the core platform. Note that this is
dependent on the apk being signed with same certificate as the core platform
(see `LOCAL_CERTIFICATE` in above section), yet they are different concepts:

*   some permissions or APIs are signature protected, which requires same
    signing certificate
*   some permissions or APIs requires the `system` user identity of the caller,
    which requires the calling package to share user id with `system`, if it's a
    separate package from core platform itself

```xml
<uses-library android:name="android.test.runner" />
```

This is required for all Instrumentation tests since the related classes are
packaged in a separate framework jar library file, therefore requires additional
classpath entries when the test package is invoked by application framework.

```xml
android:targetPackage="android.test.example.helloworld"
```

You might have noticed that the `targetPackage` here is declared the same as the
`package` attribute declared in the `manifest` tag of this file. As mentioned in
[testing basics](../basics/index.md), this category of instrumentation test are
typically intended for testing framework APIs, so it's not very meaningful for
them to have a specific targeted application package, other then itself.

## Test Configuration File

In order to simplify test execution, you also need write a test configuration
file for Android's test harness, [TradeFederation](https://source.android.com/devices/tech/test_infra/tradefed/).

The test configuration can specify special device setup options and default
arguments to supply the test class.

[Latest Test Config File](../../tests/example/instrumentation/AndroidTest.xml)

A snapshot is included here for convenience:

```xml
<configuration description="Runs sample instrumentation test.">
  <target_preparer class="com.android.tradefed.targetprep.TestFilePushSetup"/>
  <target_preparer class="com.android.tradefed.targetprep.TestAppInstallSetup">
    <option name="test-file-name" value="HelloWorldTests.apk"/>
  </target_preparer>
  <target_preparer class="com.android.tradefed.targetprep.PushFilePreparer"/>
  <target_preparer class="com.android.tradefed.targetprep.RunCommandTargetPreparer"/>
  <option name="test-suite-tag" value="apct"/>
  <option name="test-tag" value="SampleInstrumentationTest"/>

  <test class="com.android.tradefed.testtype.AndroidJUnitTest">
    <option name="package" value="android.test.example.helloworld"/>
    <option name="runner" value="android.support.test.runner.AndroidJUnitRunner"/>
  </test>
</configuration>
```

Some select remarks on the test configuration file:

```xml
<target_preparer class="com.android.tradefed.targetprep.TestAppInstallSetup">
  <option name="test-file-name" value="HelloWorldTests.apk"/>
</target_preparer>
```
This tells TradeFederation to install the HelloWorldTests.apk onto the target
device using a specified target_preparer. There are many target preparers
available to developers in TradeFederation and these can be used to ensure
the device is setup properly prior to test execution.

```xml
<test class="com.android.tradefed.testtype.AndroidJUnitTest">
  <option name="package" value="android.test.example.helloworld"/>
  <option name="runner" value="android.support.test.runner.AndroidJUnitRunner"/>
</test>
```
This specifies the TradeFederation test class to use to execute the test and
passes in the package on the device to be executed and the test runner
framework which is JUnit in this case.

Look here for more information on [Test Module Configs](test-config.md)

## JUnit4 Features

Using `android-support-test` library as test runner enables adoptation of new
JUnit4 style test classes, and the sample gerrit change contains some very basic
use of its features.

[Latest source code](../../tests/example/instrumentation/src/android/test/example/helloworld/HelloWorldTest.java)

While testing patterns are usually specific to component teams, there are some
generally useful usage patterns.

```java
@RunWith(JUnit4.class)
public class HelloWorldTest {
```

A significant difference in JUnit4 is that tests are no longer required to
inherit from a common base test class; instead, you write tests in plain Java
classes and use annotation to indicate certain test setup and constraints. In
this example, we are instructing that this class should be run as a JUnit4 test.

```java
    @BeforeClass
    public static void beforeClass() {
    ...
    @AfterClass
    public static void afterClass() {
    ...
    @Before
    public void before() {
    ...
    @After
    public void after() {
    ...
    @Test
    @SmallTest
    public void testHelloWorld() {
    ...
```

The `@Before` and `@After` annotations are used on methods by JUnit4 to perform
pre test setup and post test teardown. Similarly, the `@BeforeClass` and
`@AfterClass` annotations are used on methods by JUnit4 to perform setup before
executing all tests in a test class, and teardown afterwards. Note that the
class-scope setup and teardown methods must be static. As for the test methods,
unlike in earlier version of JUnit, they no longer need to start the method name
with `test`, instead, each of them must be annotated with `@Test`. As usual,
test methods must be public, declare no return value, take no parameters, and
may throw exceptions.

**Important**: the test methods themselves are annotated with `@Test`
annotation; and note that for tests to be executed via APCT, they must be
annotated with test sizes: the example annotated method `testHelloWorld` as
`@SmallTest`. The annotation may be applied at method scope, or class scope.

## Accessing `Instrumentation`

Although not covered in the basic hello world example, it's fairly common for an
Android test to require access `Instrumentation` instance: this is the core API
interface that provides access to application contexts, activity lifecycle
related test APIs and more.

Because the JUnit4 tests no longer require a common base class, it's no longer
necessary to obtain `Instrumentation` instance via
`InstrumentationTestCase#getInstrumentation()`, instead, the new test runner
manages it via [`InstrumentationRegistry`](https://developer.android.com/reference/android/support/test/InstrumentationRegistry.html)
where contextual and environmental setup created by instrumentation framework is
stored.

To access the instance of `Instrumentation` class, simply call static method
`getInstrumentation()` on `InstrumentationRegistry` class:

```java
Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation()
```

## Build & Test Locally:

Follow these [Instructions](instrumentation.md)
