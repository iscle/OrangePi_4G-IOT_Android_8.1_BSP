/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package android.server.displayservice;

import static junit.framework.Assert.assertTrue;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DisplayHelper {
    private static final String VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay";
    private static final String VIRTUAL_DISPLAY_SERVICE =
            "android.server.displayservice/.VirtualDisplayService";
    private static final Pattern mDisplayDevicePattern = Pattern.compile(
            ".*DisplayDeviceInfo\\{\"([^\"]+)\":.*, state (\\S+),.*\\}.*");

    private boolean mCreated;
    private final ITestDevice mDevice;

    public DisplayHelper(ITestDevice device) {
        mDevice = device;
    }

    public void createAndWaitForDisplay(boolean external, boolean requestShowWhenLocked)
            throws DeviceNotAvailableException {
        StringBuilder command =
                new StringBuilder("am startfgservice -n " + VIRTUAL_DISPLAY_SERVICE);
        command.append(" --es command create");
        if (external) {
            command.append(" --ez external_display true");
        }
        if (requestShowWhenLocked) {
            command.append(" --ez show_content_when_locked true");
        }
        mDevice.executeShellCommand(command.toString());

        waitForDisplayState(mDevice, false /* default */, true /* exists */, true /* on */);
        mCreated = true;
    }

    public void turnDisplayOff() throws DeviceNotAvailableException {
        mDevice.executeShellCommand(
                "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command off");
        waitForDisplayState(mDevice, false /* default */, true /* exists */, false /* on */);
    }

    public void turnDisplayOn() throws DeviceNotAvailableException {
        mDevice.executeShellCommand(
                "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command on");
        waitForDisplayState(mDevice, false /* default */, true /* exists */, true /* on */);
    }

    public void releaseDisplay() throws DeviceNotAvailableException {
        if (mCreated) {
            mDevice.executeShellCommand(
                    "am start-service -n " + VIRTUAL_DISPLAY_SERVICE + " --es command destroy");
            waitForDisplayState(mDevice, false /* default */, false /* exists */, true /* on */);
        }
        mCreated = false;
    }

    public static void waitForDefaultDisplayState(ITestDevice device, boolean wantOn)
            throws DeviceNotAvailableException {
        waitForDisplayState(device, true /* default */, true /* exists */, wantOn);
    }

    public static boolean getDefaultDisplayState(ITestDevice device)
            throws DeviceNotAvailableException {
        return getDisplayState(device, true);
    }

    private static void waitForDisplayState(
            ITestDevice device, boolean defaultDisplay, boolean wantExists, boolean wantOn)
            throws DeviceNotAvailableException {
        int tries = 0;
        boolean done = false;
        do {
            if (tries > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Oh well
                }
            }

            Boolean state = getDisplayState(device, defaultDisplay);
            done = (!wantExists && state == null)
                    || (wantExists && state != null && state == wantOn);

            tries++;
        } while (tries < 10 && !done);

        assertTrue(done);
    }

    private static Boolean getDisplayState(ITestDevice device, boolean defaultDisplay)
            throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        device.executeShellCommand("dumpsys display", outputReceiver);
        String dump = outputReceiver.getOutput();

        boolean displayExists = false;
        boolean displayOn = false;
        for (String line : dump.split("\\n")) {
            Matcher matcher = mDisplayDevicePattern.matcher(line);
            if (matcher.matches()) {
                if ((defaultDisplay && line.contains("FLAG_DEFAULT_DISPLAY"))
                        || (!defaultDisplay && VIRTUAL_DISPLAY_NAME.equals(matcher.group(1)))) {
                    return "ON".equals(matcher.group(2));
                }
            }
        }
        return null;
    }
}
