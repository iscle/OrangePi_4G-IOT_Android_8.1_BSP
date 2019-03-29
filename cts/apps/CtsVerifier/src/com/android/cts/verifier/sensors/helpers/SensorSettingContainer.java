/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.sensors.helpers;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.BaseSensorTestActivity;
import com.android.cts.verifier.sensors.base.ISensorTestStateContainer;

/**
 * A helper class for {@link SensorFeaturesDeactivator}. It abstracts the responsibility of handling
 * device settings that affect sensors.
 *
 * This class is meant to be used only by {@link SensorFeaturesDeactivator}.
 * To keep things simple, this class synchronizes access to its internal state on public methods.
 * This approach is fine, because there is no need for concurrent access.
 */
abstract class SensorSettingContainer {
    private static final int DEFAULT_SETTING_VALUE = -1;

    private final String mAction;
    private final int mSettingNameResId;

    private boolean mInitialized;
    private boolean mSettingAvailable;
    private boolean mCapturedModeOn;

    public SensorSettingContainer(String action, int settingNameResId) {
        mAction = action;
        mSettingNameResId = settingNameResId;
    }

    public synchronized void captureInitialState() {
        if (mInitialized) {
            return;
        }
        mSettingAvailable = getSettingMode(DEFAULT_SETTING_VALUE) != DEFAULT_SETTING_VALUE;
        mCapturedModeOn = getCurrentSettingMode();
        mInitialized = true;
    }

    public synchronized void requestToSetMode(
            ISensorTestStateContainer stateContainer,
            boolean modeOn) throws InterruptedException {
        if (!isSettingAvailable() || !isSettingUiAvailable(stateContainer)) {
            return;
        }
        trySetMode(stateContainer, modeOn);
        if (getCurrentSettingMode() != modeOn) {
            String message = stateContainer.getString(
                    R.string.snsr_setting_mode_not_set,
                    getSettingName(stateContainer),
                    modeOn);
            throw new IllegalStateException(message);
        }
    }

    public synchronized void requestToResetMode(ISensorTestStateContainer stateContainer)
            throws InterruptedException {
        if (!isSettingAvailable() || !isSettingUiAvailable(stateContainer)) {
            return;
        }
        trySetMode(stateContainer, mCapturedModeOn);
    }

    private void trySetMode(ISensorTestStateContainer stateContainer, boolean modeOn)
            throws InterruptedException {
        BaseSensorTestActivity.SensorTestLogger logger = stateContainer.getTestLogger();
        String settingName = getSettingName(stateContainer);
        if (getCurrentSettingMode() == modeOn) {
            logger.logMessage(R.string.snsr_setting_mode_set, settingName, modeOn);
            return;
        }

        logger.logInstructions(R.string.snsr_setting_mode_request, settingName, modeOn);
        logger.logInstructions(R.string.snsr_on_complete_return);
        stateContainer.waitForUserToContinue();
        stateContainer.executeActivity(mAction);
    }

    private boolean getCurrentSettingMode() {
        return getSettingMode(DEFAULT_SETTING_VALUE) != 0;
    }

    private String getSettingName(ISensorTestStateContainer stateContainer) {
        return stateContainer.getString(mSettingNameResId);
    }

    protected boolean isSettingAvailable() {
        if (!mInitialized) {
            throw new IllegalStateException(
                    "Object must be initialized first by invoking #captureInitialState.");
        }
        return mSettingAvailable;
    }

    protected boolean isSettingUiAvailable(ISensorTestStateContainer stateContainer) {
        return stateContainer.hasActivity(mAction);
    }

    protected abstract int getSettingMode(int defaultValue);
}
