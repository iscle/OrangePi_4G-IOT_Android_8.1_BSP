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

package android.security.cts;

import com.android.ddmlib.NullOutputReceiver;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.log.LogUtil.CLog;

import android.platform.test.annotations.RootPermissionTest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class AdbUtils {

    /** Runs a commandline on the specified device
     *
     * @param command the command to be ran
     * @param device device for the command to be ran on
     * @return the console output from running the command
     */
    public static String runCommandLine(String command, ITestDevice device) throws Exception {
        return device.executeShellCommand(command);
    }

    /**
     * Pushes and runs a binary to the selected device
     *
     * @param pathToPoc a string path to poc from the /res folder
     * @param device device to be ran on
     * @return the console output from the binary
     */
    public static String runPoc(String pocName, ITestDevice device) throws Exception {
        device.executeShellCommand("chmod +x /data/local/tmp/" + pocName);
        return device.executeShellCommand("/data/local/tmp/" + pocName);
    }

    /**
     * Pushes and runs a binary to the selected device
     *
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     * @return the console output from the binary
     */
    public static String runPoc(String pocName, ITestDevice device, int timeout) throws Exception {
        device.executeShellCommand("chmod +x /data/local/tmp/" + pocName);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand("/data/local/tmp/" + pocName, receiver, timeout, TimeUnit.SECONDS, 0);
        String output = receiver.getOutput();
        return output;
    }

    /**
     * Pushes and runs a binary to the selected device and ignores any of its output.
     *
     * @param pocName a string path to poc from the /res folder
     * @param device device to be ran on
     * @param timeout time to wait for output in seconds
     */
    public static void runPocNoOutput(String pocName, ITestDevice device, int timeout)
            throws Exception {
        device.executeShellCommand("chmod +x /data/local/tmp/" + pocName);
        NullOutputReceiver receiver = new NullOutputReceiver();
        device.executeShellCommand("/data/local/tmp/" + pocName, receiver, timeout,
                TimeUnit.SECONDS, 0);
    }

    /**
     * Enables malloc debug on a given process.
     *
     * @param processName the name of the process to run with libc malloc debug
     * @param device the device to use
     * @return true if enabling malloc debug succeeded
     */
    public static boolean enableLibcMallocDebug(String processName, ITestDevice device) throws Exception {
        device.executeShellCommand("setprop libc.debug.malloc.program " + processName);
        device.executeShellCommand("setprop libc.debug.malloc.options \"backtrace guard\"");
        /**
         * The pidof command is being avoided because it does not exist on versions before M, and
         * it behaves differently between M and N.
         * Also considered was the ps -AoPID,CMDLINE command, but ps does not support options on
         * versions before O.
         * The [^]] prefix is being used for the grep command to avoid the case where the output of
         * ps includes the grep command itself.
         */
        String cmdOut = device.executeShellCommand("ps -A | grep '[^]]" + processName + "'");
        /**
         * .hasNextInt() checks if the next token can be parsed as an integer, not if any remaining
         * token is an integer.
         * Example command: $ ps | fgrep mediaserver
         * Out: media     269   1     77016  24416 binder_thr 00f35142ec S /system/bin/mediaserver
         * The second field of the output is the PID, which is needed to restart the process.
         */
        Scanner s = new Scanner(cmdOut).useDelimiter("\\D+");
        if(!s.hasNextInt()) {
            CLog.w("Could not find pid for process: " + processName);
            return false;
        }

        String result = device.executeShellCommand("kill -9 " + s.nextInt());
        if(!result.equals("")) {
            CLog.w("Could not restart process: " + processName);
            return false;
        }

        TimeUnit.SECONDS.sleep(1);
        return true;
    }

    /**
     * Pushes and installs an apk to the selected device
     *
     * @param pathToApk a string path to apk from the /res folder
     * @param device device to be ran on
     * @return the output from attempting to install the apk
     */
    public static String installApk(String pathToApk, ITestDevice device) throws Exception {

        String fullResourceName = pathToApk;
        File apkFile = File.createTempFile("apkFile", ".apk");
        try {
            apkFile = extractResource(fullResourceName, apkFile);
            return device.installPackage(apkFile, true);
        } finally {
            apkFile.delete();
        }
    }

   /**
     * Extracts the binary data from a resource and writes it to a temp file
     */
    private static File extractResource(String fullResourceName, File file) throws Exception {
        try (InputStream in = AdbUtils.class.getResourceAsStream(fullResourceName);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fullResourceName);
            }
            byte[] buf = new byte[65536];
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
            return file;
        }

    }
}
