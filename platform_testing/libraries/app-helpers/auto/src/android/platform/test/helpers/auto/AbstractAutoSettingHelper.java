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
 * limitations under the License.
 */

package android.platform.test.helpers;

import android.app.Instrumentation;

/**
 * Helper class for functional tests of Settings facet
 */
public abstract class AbstractAutoSettingHelper extends AbstractStandardAppHelper {

    public AbstractAutoSettingHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: The app is open and the settings facet is open
     *
     * @param setting  option to open.
     */
    public abstract void openSetting(String setting);

    /**
     * Setup expectations: The app is open and wifi setting options is selected
     *
     * Turns on/off wifi
     */
    public abstract void turnOnOffWifi(boolean turnOn);

    /**
     * Setup expectations: The app is open and bluetooth setting options is selected
     *
     * Turns on/off bluetooth
     */
    public abstract void turnOnOffBluetooth(boolean turnOn);

    /**
     * Setup expectations: The app is open.
     *
     * Checks if the wifi is enabled.
     */
    public abstract boolean isWifiOn();

    /**
     * Setup expectations: The app is open.
     *
     * Checks if the bluetooth is enabled.
     */
    public abstract boolean isBluetoothOn();

    /**
     * Setup expectations: The app is open and the settings facet is open
     */
    public abstract void goBackToSettingsScreen();

    /**
     * Force stops the settings application
     */
    public abstract void stopSettingsApplication();

}
