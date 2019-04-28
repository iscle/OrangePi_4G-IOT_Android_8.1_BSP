## Test Module Config

Some test modules may require customized setup and tear down steps that cannot
be performed within test case itself. Typical examples may include:

*   install other apks (in addition to the test apk)
*   push some files to the device
*   run commands (e.g. adb shell pm ...)

In the past, component teams usually resort to writing a host side test to
perform such tasks, which requires understanding of TradeFederation harness
and typically increases the complexity of a test module .

Borrowing from CTS, we introduced the concept of test module config to support
such tasks, the common tasks list above can be achieved by just a few lines of
config. For maximum flexibility, you can even implement your own target
preparer, as defined by [ITargetPreparer]
(https://source.android.com/reference/com/android/tradefed/targetprep/ITargetPreparer.html)
or [ITargetCleaner]
(https://source.android.com/reference/com/android/tradefed/targetprep/ITargetCleaner.html),
and configure them to use in your own test module config.

A test module config for a test module is a required XML file added to the top
level module source folder, named ‘AndroidTest.xml’. The XML follows the format
of a configuration file used by TradeFederation test automation harness.
Currently the main tags handled via the test module configs are the “target_preparer” and
"test" tags.

## Target Preparers
A “target_preparer” tag, as the name suggests, defines a target preparer
(see [ITargetPreparer](https://source.android.com/reference/com/android/tradefed/targetprep/ITargetPreparer.html))
that offers a setup method, which gets called before the test module is executed
for testing; and if the class referenced in the “target_preparer” tag also
implements
[ITargetCleaner](https://source.android.com/reference/com/android/tradefed/targetprep/ITargetCleaner.html),
its teardown method will be invoked after the test module has finished.

To use the built-in common module config, add a new file ‘AndroidTest.xml’ at
the top level folder for your test module, and populate it with the following
content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- [insert standard AOSP copyright here] -->
<configuration description="Test module config for Foo">
<!-- insert options here -->
</configuration>
```

As an example, we can add the following option tags (at the “insert” comment
above):

```xml
    <target_preparer class="com.android.tradefed.targetprep.RunCommandTargetPreparer">
        <option name="run-command" value="settings put secure accessibility_enabled 1" />
        <option name="teardown-command" value="settings put secure accessibility_enabled 0" />
    </target_preparer>
```

The options will configure the test harness to:

1.  before test module is invoked, execute shell command “settings put secure
    accessibility_enabled 1” on device
2.  after test module is finished, execute shell command “settings put secure
    accessibility_enabled 0”

In this particular example, accessibility is enabled/disabled before/after the
test module execution, respectively. With a simple example demonstrated, it’s
necessary to cover more details on how the “option” tag is used. As shown above,
the tag can have two attributes: name, value. The name attribute indicated the
name of the option, and is further broken down into two parts separated by a
colon: short name for the preparer, and the actual option name offered by the
preparer. The exact purpose of value field is dependent on how preparer defined
the option: it can be a string, a number, a boolean, or even a file path etc. In
the example above, name “run-command:run-command” means that we are setting
value for the option “run-command” defined by a target preparer with short name
“run-command”; and name “run-command:teardown-command” means that we are setting
value for the option “teardown-command” also defined by the same target preparer
with short name “run-command”. Here's a summary of the 3 common target
preparers:

*   class name: [PushFilePreparer](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/targetprep/PushFilePreparer.java)

    *   **short name**: push-file
    *   **function**: pushes arbitrary files under test case folder into
        destination on device
    *   **notes**:
        *   this preparer can push from folder to folder, or file to file; that
            is, you cannot push a file under a folder on device: you must
            specify the destination filename under that folder as well
    *   **options**:
        *   **push:** A push-spec, formatted as
            '`/path/to/srcfile.txt->/path/to/destfile.txt`' or
            '`/path/to/srcfile.txt->/path/to/destdir/`'. May be repeated
            This path may be relative to the test module directory or the out
            directory itself.
        *   **post-push: **A command to run on the device (with \``adb shell
            <your command>`\`) after all pushes have been attempted. Typical use
            case would be using chmod for permissions

*   class name: [InstallApkSetup](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/targetprep/InstallApkSetup.java)

    *   **short name:**install-apk
    *   **function:** pushes arbitrary apk files under into destination on
        device
    *   **options:**
        *   **test-file-name:** the name of the apk to be installed on to
            device.
        *   **install-arg:** Additional arguments to be passed to the pm install
            command, including leading dash, e.g. “-d". May be repeated

*   class name: [RunCommandTargetPreparer](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/targetprep/RunCommandTargetPreparer.java)

    *   **short name:** run-command
    *   **function:** executes arbitrary shell commands before or after test
        module execution
    *   **options:**
        *   **run-command:**adb shell command to run. May be repeated
        *   **teardown-command:**adb shell command to run during teardown phase.
            May be repeated

## Test Class
A test class is the TradeFederation class to use to execute the test.

```xml
<test class="com.android.tradefed.testtype.AndroidJUnitTest">
  <option name="package" value="android.test.example.helloworld"/>
  <option name="runner" value="android.support.test.runner.AndroidJUnitRunner"/>
</test>
```

Here are 3 common test classes:

*   class name: [GTest](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/testtype/GTest.java)

    *   **short name:** gtest
    *   **function:** A Test that runs a native test package on given device.
    *   **options:**
        *   **native-test-device-path:**The path on the device where native tests are located.

*   class name: [InstrumentationTest](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/testtype/InstrumentationTest.java)

    *   **short name:** instrumentation
    *   **function:** A Test that runs an instrumentation test package on given device
    *   **options:**
        *   **package:**The manifest package name of the Android test application to run.
        *   **class:**The test class name to run.
        *   **method:**The test method name to run.

*   class name: [AndroidJUnitTest](https://android.googlesource.com/platform/tools/tradefederation/+/master/src/com/android/tradefed/testtype/AndroidJUnitTest.java)

    *   **function:** A Test that runs an instrumentation test package on given
                      device using the android.support.test.runner.AndroidJUnitRunner
                      This is the main way to execute an instrumentation test.

