/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.jdwpsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test to check non-zygote apps do not have an active JDWP connection.
 */
public class JdwpSecurityHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final String DEVICE_LOCATION = "/data/local/tmp/jdwpsecurity";
    private static final String DEVICE_SCRIPT_FILENAME = "jdwptest";
    private static final String DEVICE_JAR_FILENAME = "CtsJdwpApp.jar";
    private static final String JAR_MAIN_CLASS_NAME = "com.android.cts.jdwpsecurity.JdwpTest";

    private IBuildInfo mBuildInfo;

    private static String getDeviceScriptFilepath() {
        return DEVICE_LOCATION + File.separator + DEVICE_SCRIPT_FILENAME;
    }

    private static String getDeviceJarFilepath() {
        return DEVICE_LOCATION + File.separator + DEVICE_JAR_FILENAME;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Create test directory on the device.
        createRemoteDir(DEVICE_LOCATION);

        // Also create the dalvik-cache directory. It needs to exist before the runtime starts.
        createRemoteDir(DEVICE_LOCATION + File.separator + "dalvik-cache");

        // Create and push script on the device.
        File tempFile = createScriptTempFile();
        try {
            boolean success = getDevice().pushFile(tempFile, getDeviceScriptFilepath());
            assertTrue("Failed to push script to " + getDeviceScriptFilepath(), success);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }

        // Make the script executable.
        getDevice().executeShellCommand("chmod 755 " + getDeviceScriptFilepath());

        // Push jar file.
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        File jarFile = buildHelper.getTestFile(DEVICE_JAR_FILENAME);
        boolean success = getDevice().pushFile(jarFile, getDeviceJarFilepath());
        assertTrue("Failed to push jar file to " + getDeviceScriptFilepath(), success);
    }

    @Override
    protected void tearDown() throws Exception {
        // Delete the whole test directory on the device.
        getDevice().executeShellCommand(String.format("rm -r %s", DEVICE_LOCATION));

        super.tearDown();
    }

    /**
     * Tests a non-zygote app does not have a JDWP connection, thus not being
     * debuggable.
     *
     * Runs a script executing a Java app (jar file) with app_process,
     * without forking from zygote. Then checks its pid is not returned
     * by 'adb jdwp', meaning it has no JDWP connection and cannot be
     * debugged.
     *
     * @throws Exception
     */
    public void testNonZygoteProgramIsNotDebuggable() throws Exception {
        String scriptFilepath = getDeviceScriptFilepath();
        Process scriptProcess = null;
        String scriptPid = null;
        List<String> activeJdwpPids = null;
        try {
            // Run the script on the background so it's running when we collect the list of
            // pids with a JDWP connection using 'adb jdwp'.
            // command.
            scriptProcess = runScriptInBackground(scriptFilepath);

            // On startup, the script will print its pid on its output.
            scriptPid = readScriptPid(scriptProcess);

            // Collect the list of pids with a JDWP connection.
            activeJdwpPids = getJdwpPids();
        } finally {
            // Stop the script.
            if (scriptProcess != null) {
                scriptProcess.destroy();
            }
        }

        assertNotNull("Failed to get script pid", scriptPid);
        assertNotNull("Failed to get active JDWP pids", activeJdwpPids);
        assertFalse("Test app should not have an active JDWP connection" +
                " (pid " + scriptPid + " is returned by 'adb jdwp')",
                activeJdwpPids.contains(scriptPid));
    }

    private Process runScriptInBackground(String scriptFilepath) throws IOException {
        String[] shellScriptCommand = buildAdbCommand("shell", scriptFilepath);
        return RunUtil.getDefault().runCmdInBackground(shellScriptCommand);
    }

    private String readScriptPid(Process scriptProcess) throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(scriptProcess.getInputStream()));
            // We only expect to read one line containing the pid.
            return br.readLine();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    private List<String> getJdwpPids() throws Exception {
        return new AdbJdwpOutputReader().listPidsWithAdbJdwp();
    }

    /**
     * Creates the script file on the host so it can be pushed onto the device.
     *
     * @return the script file
     * @throws IOException
     */
    private static File createScriptTempFile() throws IOException {
        File tempFile = File.createTempFile("jdwptest", ".tmp");

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(tempFile);

            // We need a dalvik-cache in /data/local/tmp so we have read-write access.
            // Note: this will cause the runtime to optimize the DEX file (contained in
            // the jar file) before executing it.
            pw.println(String.format("export ANDROID_DATA=%s", DEVICE_LOCATION));
            pw.println(String.format("export CLASSPATH=%s", getDeviceJarFilepath()));
            pw.println(String.format("exec app_process /system/bin %s \"$@\"",
                    JAR_MAIN_CLASS_NAME));
        } finally {
            if (pw != null) {
                pw.close();
            }
        }

        return tempFile;
    }

    /**
     * Helper class collecting all pids returned by 'adb jdwp' command.
     */
    private class AdbJdwpOutputReader implements Runnable {
        /**
         * A list of all pids with a JDWP connection returned by 'adb jdwp'.
         */
        private final List<String> lines = new ArrayList<String>();

        /**
         * The input stream of the process running 'adb jdwp'.
         */
        private InputStream in;

        public List<String> listPidsWithAdbJdwp() throws Exception {
            // The 'adb jdwp' command does not return normally, it only terminates with Ctrl^C.
            // Therefore we cannot use ITestDevice.executeAdbCommand but need to run that command
            // in the background. Since we know the tested app is already running, we only need to
            // capture the output for a short amount of time before stopping the 'adb jdwp'
            // command.
            String[] adbJdwpCommand = buildAdbCommand("jdwp");
            Process adbProcess = RunUtil.getDefault().runCmdInBackground(adbJdwpCommand);
            in = adbProcess.getInputStream();

            // Read the output for 5s in a separate thread before stopping the command.
            Thread t = new Thread(this);
            t.start();
            Thread.sleep(5000);

            // Kill the 'adb jdwp' process and wait for the thread to stop.
            adbProcess.destroy();
            t.join();

            return lines;
        }

        @Override
        public void run() {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = readLineIgnoreException(br)) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                CLog.e(e);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                        // Ignore it.
                    }
                }
            }
        }

        private String readLineIgnoreException(BufferedReader reader) throws IOException {
            try {
                return reader.readLine();
            } catch (IOException e) {
                if (e instanceof EOFException) {
                    // This is expected when the process's input stream is closed.
                    return null;
                } else {
                    throw e;
                }
            }
        }
    }

    private String[] buildAdbCommand(String... args) {
        return ArrayUtil.buildArray(new String[] {"adb", "-s", getDevice().getSerialNumber()},
                args);
    }

    private boolean createRemoteDir(String remoteFilePath) throws DeviceNotAvailableException {
        if (getDevice().doesFileExist(remoteFilePath)) {
            return true;
        }
        File remoteFile = new File(remoteFilePath);
        String parentPath = remoteFile.getParent();
        if (parentPath != null) {
            if (!createRemoteDir(parentPath)) {
                return false;
            }
        }
        getDevice().executeShellCommand(String.format("mkdir %s", remoteFilePath));
        return getDevice().doesFileExist(remoteFilePath);
    }
}
