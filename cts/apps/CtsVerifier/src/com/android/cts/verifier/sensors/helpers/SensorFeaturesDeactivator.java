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
import com.android.cts.verifier.sensors.base.ISensorTestStateContainer;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Field;

/**
 * A helper class that provides a mechanism to:
 * - prompt users to activate/deactivate features that are known to register for sensor data.
 * - turn on/off certain components of the device on behalf of the test (described as 'runtime
 *   features')
 * - keep track of the initial state for each sensor feature, so it can be restored at will
 */
public class SensorFeaturesDeactivator {

    private final ISensorTestStateContainer mStateContainer;

    private final SensorSettingContainer mAirplaneMode = new AirplaneModeSettingContainer();
    private final SensorSettingContainer mScreenBrightnessMode =
            new ScreenBrightnessModeSettingContainer();
    private final SensorSettingContainer mAmbientDisplayMode = new AmbientDisplaySettingContainer();
    private final SensorSettingContainer mAutoRotateScreenMode =
            new AutoRotateScreenModeSettingContainer();
    private final SensorSettingContainer mKeepScreenOnMode = new KeepScreenOnModeSettingContainer();
    private final SensorSettingContainer mLocationMode = new LocationModeSettingContainer();

    public SensorFeaturesDeactivator(ISensorTestStateContainer stateContainer) {
        mStateContainer = stateContainer;
    }

    public synchronized void requestDeactivationOfFeatures() throws InterruptedException {
        captureInitialState();

        mAirplaneMode.requestToSetMode(mStateContainer, true);
        mScreenBrightnessMode.requestToSetMode(mStateContainer, false);
        mAmbientDisplayMode.requestToSetMode(mStateContainer, false);
        mAutoRotateScreenMode.requestToSetMode(mStateContainer, false);
        mKeepScreenOnMode.requestToSetMode(mStateContainer, false);
        mLocationMode.requestToSetMode(mStateContainer, false);

        // TODO: find a way to find out if there are clients still registered at this time
        mStateContainer.getTestLogger()
                .logInstructions(R.string.snsr_sensor_feature_deactivation);
        mStateContainer.waitForUserToContinue();
    }

    public synchronized void requestToRestoreFeatures() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            // TODO: in the future, if the thread is interrupted, we might need to serialize the
            //       intermediate state we acquired so we can restore when we have a chance
            return;
        }

        mAirplaneMode.requestToResetMode(mStateContainer);
        mScreenBrightnessMode.requestToResetMode(mStateContainer);
        mAmbientDisplayMode.requestToResetMode(mStateContainer);
        mAutoRotateScreenMode.requestToResetMode(mStateContainer);
        mKeepScreenOnMode.requestToResetMode(mStateContainer);
        mLocationMode.requestToResetMode(mStateContainer);
    }

    private void captureInitialState() {
        mAirplaneMode.captureInitialState();
        mScreenBrightnessMode.captureInitialState();
        mAmbientDisplayMode.captureInitialState();
        mAutoRotateScreenMode.captureInitialState();
        mLocationMode.captureInitialState();
        mKeepScreenOnMode.captureInitialState();
    }

    private class AirplaneModeSettingContainer extends SensorSettingContainer {
        public AirplaneModeSettingContainer() {
            super(Settings.ACTION_AIRPLANE_MODE_SETTINGS, R.string.snsr_setting_airplane_mode);
        }

        @Override
        protected int getSettingMode(int defaultValue) {
            ContentResolver contentResolver = mStateContainer.getContentResolver();
            // Settings.System.AIRPLANE_MODE_ON is deprecated in API 17
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return Settings.System
                        .getInt(contentResolver, Settings.System.AIRPLANE_MODE_ON, defaultValue);
            } else {
                return Settings.Global
                        .getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, defaultValue);
            }
        }

        @Override
        protected boolean isSettingAvailable() {
            // call base first, lean back UI device does not have airplane mode
            return super.isSettingAvailable() &&
                    !(mStateContainer.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
        }
    }

    private class ScreenBrightnessModeSettingContainer extends SensorSettingContainer {
        public ScreenBrightnessModeSettingContainer() {
            super(Settings.ACTION_DISPLAY_SETTINGS, R.string.snsr_setting_screen_brightness_mode);
        }

        @Override
        public int getSettingMode(int defaultValue) {
            return Settings.System.getInt(
                    mStateContainer.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    defaultValue);
        }
    }

    private class AmbientDisplaySettingContainer extends SensorSettingContainer {
        public AmbientDisplaySettingContainer() {
            super(Settings.ACTION_DISPLAY_SETTINGS, R.string.snsr_setting_ambient_display);
        }

        @Override
        protected int getSettingMode(int defaultValue) {
            // TODO: replace the use of reflection with Settings.Secure.DOZE_ENABLED when the
            //       static field is not hidden anymore
            Class<?> secureSettingsClass = Settings.Secure.class;
            Field dozeEnabledField;
            try {
                dozeEnabledField = secureSettingsClass.getField("DOZE_ENABLED");
            } catch (NoSuchFieldException e) {
                return defaultValue;
            }

            String settingName;
            try {
                settingName = (String) dozeEnabledField.get(null /* obj */);
            } catch (IllegalAccessException e) {
                return defaultValue;
            }

            return Settings.Secure.getInt(
                    mStateContainer.getContentResolver(),
                    settingName,
                    defaultValue);
        }
    }

    private class AutoRotateScreenModeSettingContainer extends SensorSettingContainer {
        public AutoRotateScreenModeSettingContainer() {
            super(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                    R.string.snsr_setting_auto_rotate_screen_mode);
        }

        @Override
        protected int getSettingMode(int defaultValue) {
            return Settings.System.getInt(
                    mStateContainer.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION,
                    defaultValue);
        }
    }

    private class KeepScreenOnModeSettingContainer extends SensorSettingContainer {
        public KeepScreenOnModeSettingContainer() {
            super(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                    R.string.snsr_setting_keep_screen_on);
        }

        @Override
        protected int getSettingMode(int defaultValue) {
            return Settings.Global.getInt(
                    mStateContainer.getContentResolver(),
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    defaultValue);
        }
    }

    private class LocationModeSettingContainer extends SensorSettingContainer {
        public LocationModeSettingContainer() {
            super(Settings.ACTION_LOCATION_SOURCE_SETTINGS, R.string.snsr_setting_location_mode);
        }

        @Override
        protected int getSettingMode(int defaultValue) {
            return Settings.Secure.getInt(
                    mStateContainer.getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    defaultValue);
        }
    }
}
