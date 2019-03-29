/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcfsHostTests extends DeviceTestCase implements IBuildReceiver {
  // We need a running test app to test /proc/[PID]/* files.
  private static final String TEST_APP_PACKAGE = "android.os.procfs";
  private static final String TEST_APP_CLASS = "ProcfsTest";
  private static final String APK_NAME = "CtsHostProcfsTestApp.apk";
  private static final String START_TEST_APP_COMMAND =
      String.format(
          "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
          TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_APP_CLASS);
  private static final String TEST_APP_LOG_REGEXP = "PID is (\\d+)";
  private static final Pattern TEST_APP_LOG_PATTERN = Pattern.compile(TEST_APP_LOG_REGEXP);

  private static final String PROC_STAT_PATH = "/proc/stat";
  private static final String PROC_STAT_READ_COMMAND = "head -1 ";
  // Verfies the first line of /proc/stat includes 'cpu' followed by 10 numbers.
  // The 10th column was introduced in kernel version 2.6.33.
  private static final String PROC_STAT_REGEXP = "cpu ( \\d+){10,10}";
  private static final Pattern PROC_STAT_PATTERN = Pattern.compile(PROC_STAT_REGEXP);

  // In Linux, a process's stat file (/proc/[PID]/stat) and a thread's (/proc/[PID]/task/[TID]/stat)
  // share the same format. We want to verify these stat files include pid (a number), file name
  // (a string in parentheses), and state (a character), followed by 41 or more numbers.
  // The 44th column was introduced in kernel version 2.6.24.
  private static final String PID_TID_STAT_REGEXP = "\\d+ \\(.*\\) [A-Za-z]( [\\d-]+){41,}";
  private static final Pattern PID_TID_STAT_PATTERN = Pattern.compile(PID_TID_STAT_REGEXP);

  // Interval in milliseconds between two sequential reads when checking whether a file is being
  // updated.
  private static final long UPDATE_READ_INTERVAL_MS = 100;
  // Max time in milliseconds waiting for a file being update. If a file's content does not change
  // during the period, it is not considered being actively updated.
  private static final long UPDATE_MAX_WAIT_TIME_MS = 5000;

  // A reference to the device under test, which gives us a handle to run commands.
  private ITestDevice mDevice;

  private int mTestAppPid = -1;

  private IBuildInfo mBuild;

  @Override
  public void setBuild(IBuildInfo buildInfo) {
      mBuild = buildInfo;
  }

  @Override
  protected synchronized void setUp() throws Exception {
    super.setUp();
    mDevice = getDevice();
    mTestAppPid = startTestApp();
  }

  /**
   * Tests that host, as the shell user, can read /proc/stat file, the file is in a reasonable
   * shape, and the file is being updated.
   *
   * @throws Exception
   */
  public void testProcStat() throws Exception {
    testFile(PROC_STAT_PATH, PROC_STAT_READ_COMMAND, PROC_STAT_PATTERN);
  }

  /**
   * Tests that host, as the shell user, can read /proc/[PID]/stat file, the file is in a reasonable
   * shape, and the file is being updated.
   *
   * @throws Exception
   */
  public void testProcPidStat() throws Exception {
    testFile("/proc/" + mTestAppPid + "/stat", "cat ", PID_TID_STAT_PATTERN);
  }

  /**
   * Tests that host, as the shell user, can read /proc/[PID]/task/[TID]/stat files, and the files
   * are in a reasonable shape. Also verifies there are more than one such files (a typical Android
   * app easily has 10+ threads including those from Android runtime).
   *
   * <p>Note we are not testing whether these files are being updated because some Android runtime
   * threads may be idling for a while so it is hard to test whether they are being updated within a
   * limited time window (such as 'Profile Saver' thread in art/runtime/jit/profile_saver.h and
   * 'JDWP' thread).
   *
   * @throws Exception
   */
  public void testProcTidStat() throws Exception {
    int[] tids = lookForTidsInProcess(mTestAppPid);
    assertTrue("/proc/" + mTestAppPid + "/task/ includes < 2 threads", tids.length >= 2);
    for (int tid : tids) {
      readAndCheckFile(
          "/proc/" + mTestAppPid + "/task/" + tid + "/stat", "cat ", PID_TID_STAT_PATTERN);
    }
  }

  /**
   * Tests that host, as the shell user, can read the file at the given absolute path by using the
   * given read command, the file is in the expected format pattern, and the file is being updated.
   *
   * @throws Exception
   */
  private void testFile(String absolutePath, String readCommand, Pattern pattern) throws Exception {
    String content = readAndCheckFile(absolutePath, readCommand, pattern);

    // Check the file is being updated.
    long waitTime = 0;
    while (waitTime < UPDATE_MAX_WAIT_TIME_MS) {
      java.lang.Thread.sleep(UPDATE_READ_INTERVAL_MS);
      waitTime += UPDATE_READ_INTERVAL_MS;
      String newContent = readAndCheckFile(absolutePath, readCommand, pattern);
      if (!newContent.equals(content)) {
        return;
      }
    }
    assertTrue(absolutePath + " not actively updated. Content: \"" + content + "\"", false);
  }

  /**
   * Starts the test app and returns its process ID.
   *
   * @throws Exception
   */
  private int startTestApp() throws Exception {

    // Uninstall+install the app
    mDevice.uninstallPackage(TEST_APP_PACKAGE);
    CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
    File app = buildHelper.getTestFile(APK_NAME);
    String[] options = {};
    mDevice.installPackage(app, false, options);

    // Clear logcat.
    mDevice.executeAdbCommand("logcat", "-c");
    // Start the app activity and wait for it to complete.
    String results = mDevice.executeShellCommand(START_TEST_APP_COMMAND);
    // Dump logcat.
    String logs =
        mDevice.executeAdbCommand("logcat", "-v", "brief", "-d", TEST_APP_CLASS + ":I", "*:S");
    // Search for string contianing the process ID.
    int pid = -1;
    Scanner in = new Scanner(logs);
    while (in.hasNextLine()) {
      String line = in.nextLine();
      if (line.startsWith("I/" + TEST_APP_CLASS)) {
        Matcher m = TEST_APP_LOG_PATTERN.matcher(line.split(":")[1].trim());
        if (m.matches()) {
          pid = Integer.parseInt(m.group(1));
        }
      }
    }
    in.close();
    // Assert test app's pid is captured from log.
    assertTrue(
        "Test app PID not captured. results = \"" + results + "\"; logs = \"" + logs + "\"",
        pid > 0);
    return pid;
  }

  /**
   * Reads and returns the file content at the given absolute path by using the given read command,
   * after ensuring it is in the expected pattern.
   *
   * @throws Exception
   */
  private String readAndCheckFile(String absolutePath, String readCommand, Pattern pattern)
      throws Exception {
    String readResult = getDevice().executeShellCommand(readCommand + absolutePath);
    assertNotNull("Unexpected empty file " + absolutePath, readResult);
    readResult = readResult.trim();
    assertTrue(
        "Unexpected format of " + absolutePath + ": \"" + readResult + "\"",
        pattern.matcher(readResult).matches());
    return readResult;
  }

  /**
   * Returns the thread IDs in a given process.
   *
   * @throws Exception
   */
  private int[] lookForTidsInProcess(int pid) throws Exception {
    String taskPath = "/proc/" + pid + "/task";
    // Explicitly pass -1 to 'ls' to get one per line rather than relying on adb not allocating a
    // tty.
    String lsOutput = getDevice().executeShellCommand("ls -1 " + taskPath);
    assertNotNull("Unexpected empty directory " + taskPath, lsOutput);

    String[] threads = lsOutput.split("\\s+");
    int[] tids = new int[threads.length];
    for (int i = 0; i < threads.length; i++) {
      tids[i] = Integer.parseInt(threads[i]);
    }
    return tids;
  }
}
