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

package android.bluetooth.cts;

import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.provider.Settings;

import junit.framework.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for Bluetooth CTS test.
 */
class TestUtils {

    /**
     * Utility method to call hidden ScanRecord.parseFromBytes method.
     */
    static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass.getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }

    /**
     * Assert two byte arrays are equal.
     */
    static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            Assert.fail("expected:<" + Arrays.toString(expected) +
                    "> but was:<" + Arrays.toString(actual) + ">");
        }
    }

    /**
     * Get current location mode settings.
     */
    static int getLocationMode(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Set location settings mode.
     */
    static void setLocationMode(Context context, int mode) {
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                mode);
    }

    /**
     * Return true if location is on.
     */
    static boolean isLocationOn(Context context) {
        return getLocationMode(context) != Settings.Secure.LOCATION_MODE_OFF;
    }

    /**
     * Enable location and set the mode to GPS only.
     */
    static void enableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
    }

    /**
     * Disable location.
     */
    static void disableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_OFF);
    }
}