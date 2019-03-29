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

package android.server.cts;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.StateLogger.log;
import static android.server.cts.StateLogger.logE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for ActivityManager display tests.
 *
 * @see ActivityManagerDisplayTests
 * @see ActivityManagerDisplayLockedKeyguardTests
 */
public class ActivityManagerDisplayTestBase extends ActivityManagerTestBase {

    static final int CUSTOM_DENSITY_DPI = 222;

    private static final String DUMPSYS_ACTIVITY_PROCESSES = "dumpsys activity processes";
    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";
    private static final int INVALID_DENSITY_DPI = -1;

    private boolean mVirtualDisplayCreated;
    private boolean mDisplaySimulated;

    /** Temp storage used for parsing. */
    final LinkedList<String> mDumpLines = new LinkedList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            destroyVirtualDisplays();
            destroySimulatedDisplays();
        } catch (DeviceNotAvailableException e) {
            logE(e.getMessage());
        }
        super.tearDown();
    }

    /** Contains the configurations applied to attached displays. */
    static final class DisplayState {
        int mDisplayId;
        String mOverrideConfig;

        private DisplayState(int displayId, String overrideConfig) {
            mDisplayId = displayId;
            mOverrideConfig = overrideConfig;
        }

        private int getWidth() {
            final String[] configParts = mOverrideConfig.split(" ");
            for (String part : configParts) {
                if (part.endsWith("dp") && part.startsWith("w")) {
                    final String widthString = part.substring(1, part.length() - 3);
                    return Integer.parseInt(widthString);
                }
            }

            return -1;
        }

        private int getHeight() {
            final String[] configParts = mOverrideConfig.split(" ");
            for (String part : configParts) {
                if (part.endsWith("dp") && part.startsWith("h")) {
                    final String heightString = part.substring(1, part.length() - 3);
                    return Integer.parseInt(heightString);
                }
            }

            return -1;
        }

        int getDpi() {
            final String[] configParts = mOverrideConfig.split(" ");
            for (String part : configParts) {
                if (part.endsWith("dpi")) {
                    final String densityDpiString = part.substring(0, part.length() - 3);
                    return Integer.parseInt(densityDpiString);
                }
            }

            return -1;
        }
    }

    /** Contains the configurations applied to attached displays. */
    static final class ReportedDisplays {
        private static final Pattern sGlobalConfigurationPattern =
                Pattern.compile("mGlobalConfiguration: (\\{.*\\})");
        private static final Pattern sDisplayOverrideConfigurationsPattern =
                Pattern.compile("Display override configurations:");
        private static final Pattern sDisplayConfigPattern =
                Pattern.compile("(\\d+): (\\{.*\\})");

        String mGlobalConfig;
        private Map<Integer, DisplayState> mDisplayStates = new HashMap<>();

        static ReportedDisplays create(LinkedList<String> dump) {
            final ReportedDisplays result = new ReportedDisplays();

            while (!dump.isEmpty()) {
                final String line = dump.pop().trim();

                Matcher matcher = sDisplayOverrideConfigurationsPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    while (ReportedDisplays.shouldContinueExtracting(dump, sDisplayConfigPattern)) {
                        final String displayOverrideConfigLine = dump.pop().trim();
                        log(displayOverrideConfigLine);
                        matcher = sDisplayConfigPattern.matcher(displayOverrideConfigLine);
                        matcher.matches();
                        final Integer displayId = Integer.valueOf(matcher.group(1));
                        result.mDisplayStates.put(displayId,
                                new DisplayState(displayId, matcher.group(2)));
                    }
                    continue;
                }

                matcher = sGlobalConfigurationPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    result.mGlobalConfig = matcher.group(1);
                }
            }

            return result;
        }

        /** Check if next line in dump matches the pattern and we should continue extracting. */
        static boolean shouldContinueExtracting(LinkedList<String> dump, Pattern matchingPattern) {
            if (dump.isEmpty()) {
                return false;
            }

            final String line = dump.peek().trim();
            return matchingPattern.matcher(line).matches();
        }

        DisplayState getDisplayState(int displayId) {
            return mDisplayStates.get(displayId);
        }

        int getNumberOfDisplays() {
            return mDisplayStates.size();
        }

        /** Return the display state with width, height, dpi */
        DisplayState getDisplayState(int width, int height, int dpi) {
            for (Map.Entry<Integer, DisplayState> entry : mDisplayStates.entrySet()) {
                final DisplayState ds = entry.getValue();
                if (ds.mDisplayId != DEFAULT_DISPLAY_ID && ds.getDpi() == dpi
                        && ds.getWidth() == width && ds.getHeight() == height) {
                    return ds;
                }
            }
            return null;
        }

        /** Check if reported state is valid. */
        boolean isValidState(int expectedDisplayCount) {
            if (mDisplayStates.size() != expectedDisplayCount) {
                return false;
            }

            for (Map.Entry<Integer, DisplayState> entry : mDisplayStates.entrySet()) {
                final DisplayState ds = entry.getValue();
                if (ds.mDisplayId != DEFAULT_DISPLAY_ID && ds.getDpi() == -1) {
                    return false;
                }
            }
            return true;
        }
    }

    ReportedDisplays getDisplaysStates() throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(DUMPSYS_ACTIVITY_PROCESSES, outputReceiver);
        String dump = outputReceiver.getOutput();
        mDumpLines.clear();

        Collections.addAll(mDumpLines, dump.split("\\n"));

        return ReportedDisplays.create(mDumpLines);
    }

    /** Find the display that was not originally reported in oldDisplays and added in newDisplays */
    protected List<ActivityManagerDisplayTests.DisplayState> findNewDisplayStates(
            ReportedDisplays oldDisplays, ReportedDisplays newDisplays) {
        final ArrayList<DisplayState> displays = new ArrayList();

        for (Integer displayId : newDisplays.mDisplayStates.keySet()) {
            if (!oldDisplays.mDisplayStates.containsKey(displayId)) {
                displays.add(newDisplays.getDisplayState(displayId));
            }
        }

        return displays;
    }

    /**
     * Create new virtual display.
     * @param densityDpi provide custom density for the display.
     * @param launchInSplitScreen start {@link VirtualDisplayActivity} to side from
     *                            {@link LaunchingActivity} on primary display.
     * @param canShowWithInsecureKeyguard allow showing content when device is showing an insecure
     *                                    keyguard.
     * @param mustBeCreated should assert if the display was or wasn't created.
     * @param publicDisplay make display public.
     * @param resizeDisplay should resize display when surface size changes.
     * @param launchActivity should launch test activity immediately after display creation.
     * @return {@link ActivityManagerDisplayTests.DisplayState} of newly created display.
     * @throws Exception
     */
    private List<ActivityManagerDisplayTests.DisplayState> createVirtualDisplays(int densityDpi,
            boolean launchInSplitScreen, boolean canShowWithInsecureKeyguard, boolean mustBeCreated,
            boolean publicDisplay, boolean resizeDisplay, String launchActivity, int displayCount)
            throws Exception {
        // Start an activity that is able to create virtual displays.
        if (launchInSplitScreen) {
            getLaunchActivityBuilder().setToSide(true)
                    .setTargetActivityName(VIRTUAL_DISPLAY_ACTIVITY).execute();
        } else {
            launchActivity(VIRTUAL_DISPLAY_ACTIVITY);
        }
        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        final ActivityManagerDisplayTests.ReportedDisplays originalDS = getDisplaysStates();

        // Create virtual display with custom density dpi.
        executeShellCommand(getCreateVirtualDisplayCommand(densityDpi, canShowWithInsecureKeyguard,
                publicDisplay, resizeDisplay, launchActivity, displayCount));
        mVirtualDisplayCreated = true;

        return assertAndGetNewDisplays(mustBeCreated ? displayCount : -1, originalDS);
    }

    /**
     * Simulate new display.
     * @param densityDpi provide custom density for the display.
     * @return {@link ActivityManagerDisplayTests.DisplayState} of newly created display.
     */
    private List<ActivityManagerDisplayTests.DisplayState> simulateDisplay(int densityDpi)
            throws Exception {
        final ActivityManagerDisplayTests.ReportedDisplays originalDs = getDisplaysStates();

        // Create virtual display with custom density dpi.
        executeShellCommand(getSimulateDisplayCommand(densityDpi));
        mDisplaySimulated = true;

        return assertAndGetNewDisplays(1, originalDs);
    }

    /**
     * Wait for desired number of displays to be created and get their properties.
     * @param newDisplayCount expected display count, -1 if display should not be created.
     * @param originalDS display states before creation of new display(s).
     */
    private List<ActivityManagerDisplayTests.DisplayState> assertAndGetNewDisplays(
            int newDisplayCount, ActivityManagerDisplayTests.ReportedDisplays originalDS)
            throws Exception {
        final int originalDisplayCount = originalDS.mDisplayStates.size();

        // Wait for the display(s) to be created and get configurations.
        final ActivityManagerDisplayTests.ReportedDisplays ds =
                getDisplayStateAfterChange(originalDisplayCount + newDisplayCount);
        if (newDisplayCount != -1) {
            assertEquals("New virtual display(s) must be created",
                    originalDisplayCount + newDisplayCount, ds.mDisplayStates.size());
        } else {
            assertEquals("New virtual display must not be created",
                    originalDisplayCount, ds.mDisplayStates.size());
            return null;
        }

        // Find the newly added display(s).
        final List<ActivityManagerDisplayTests.DisplayState> newDisplays
                = findNewDisplayStates(originalDS, ds);
        assertTrue("New virtual display must be created", newDisplayCount == newDisplays.size());

        return newDisplays;
    }

    /**
     * Destroy existing virtual display.
     */
    void destroyVirtualDisplays() throws Exception {
        if (mVirtualDisplayCreated) {
            executeShellCommand(getDestroyVirtualDisplayCommand());
            mVirtualDisplayCreated = false;
        }
    }

    /**
     * Destroy existing simulated display.
     */
    private void destroySimulatedDisplays() throws Exception {
        if (mDisplaySimulated) {
            executeShellCommand(getDestroySimulatedDisplayCommand());
            mDisplaySimulated = false;
        }
    }

    static class VirtualDisplayBuilder {
        private final ActivityManagerDisplayTestBase mTests;

        private int mDensityDpi = CUSTOM_DENSITY_DPI;
        private boolean mLaunchInSplitScreen = false;
        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mResizeDisplay = true;
        private String mLaunchActivity = null;
        private boolean mSimulateDisplay = false;
        private boolean mMustBeCreated = true;

        public VirtualDisplayBuilder(ActivityManagerDisplayTestBase tests) {
            mTests = tests;
        }

        public VirtualDisplayBuilder setDensityDpi(int densityDpi) {
            mDensityDpi = densityDpi;
            return this;
        }

        public VirtualDisplayBuilder setLaunchInSplitScreen(boolean launchInSplitScreen) {
            mLaunchInSplitScreen = launchInSplitScreen;
            return this;
        }

        public VirtualDisplayBuilder setCanShowWithInsecureKeyguard(
                boolean canShowWithInsecureKeyguard) {
            mCanShowWithInsecureKeyguard = canShowWithInsecureKeyguard;
            return this;
        }

        public VirtualDisplayBuilder setPublicDisplay(boolean publicDisplay) {
            mPublicDisplay = publicDisplay;
            return this;
        }

        public VirtualDisplayBuilder setResizeDisplay(boolean resizeDisplay) {
            mResizeDisplay = resizeDisplay;
            return this;
        }

        public VirtualDisplayBuilder setLaunchActivity(String launchActivity) {
            mLaunchActivity = launchActivity;
            return this;
        }

        public VirtualDisplayBuilder setSimulateDisplay(boolean simulateDisplay) {
            mSimulateDisplay = simulateDisplay;
            return this;
        }

        public VirtualDisplayBuilder setMustBeCreated(boolean mustBeCreated) {
            mMustBeCreated = mustBeCreated;
            return this;
        }

        public DisplayState build() throws Exception {
            final List<DisplayState> displays = build(1);
            return displays != null && !displays.isEmpty() ? displays.get(0) : null;
        }

        public List<DisplayState> build(int count) throws Exception {
            if (mSimulateDisplay) {
                return mTests.simulateDisplay(mDensityDpi);
            }

            return mTests.createVirtualDisplays(mDensityDpi, mLaunchInSplitScreen,
                    mCanShowWithInsecureKeyguard, mMustBeCreated, mPublicDisplay, mResizeDisplay,
                    mLaunchActivity, count);
        }
    }

    private static String getCreateVirtualDisplayCommand(int densityDpi,
            boolean canShowWithInsecureKeyguard, boolean publicDisplay, boolean resizeDisplay,
            String launchActivity, int displayCount) {
        final StringBuilder commandBuilder
                = new StringBuilder(getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY));
        commandBuilder.append(" -f 0x20000000");
        commandBuilder.append(" --es command create_display");
        if (densityDpi != INVALID_DENSITY_DPI) {
            commandBuilder.append(" --ei density_dpi ").append(densityDpi);
        }
        commandBuilder.append(" --ei count ").append(displayCount);
        commandBuilder.append(" --ez can_show_with_insecure_keyguard ")
                .append(canShowWithInsecureKeyguard);
        commandBuilder.append(" --ez public_display ").append(publicDisplay);
        commandBuilder.append(" --ez resize_display ").append(resizeDisplay);
        if (launchActivity != null) {
            commandBuilder.append(" --es launch_target_activity ").append(launchActivity);
        }
        return commandBuilder.toString();
    }

    private static String getDestroyVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command destroy_display";
    }

    private static String getSimulateDisplayCommand(int densityDpi) {
        return "settings put global overlay_display_devices 1024x768/" + densityDpi;
    }

    private static String getDestroySimulatedDisplayCommand() {
        return "settings delete global overlay_display_devices";
    }

    /** Wait for provided number of displays and report their configurations. */
    ReportedDisplays getDisplayStateAfterChange(int expectedDisplayCount)
            throws DeviceNotAvailableException {
        ReportedDisplays ds = getDisplaysStates();

        int retriesLeft = 5;
        while (!ds.isValidState(expectedDisplayCount) && retriesLeft-- > 0) {
            log("***Waiting for the correct number of displays...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log(e.toString());
            }
            ds = getDisplaysStates();
        }

        return ds;
    }

    /** Checks if the device supports multi-display. */
    boolean supportsMultiDisplay() throws Exception {
        return hasDeviceFeature("android.software.activities_on_secondary_displays");
    }
}
