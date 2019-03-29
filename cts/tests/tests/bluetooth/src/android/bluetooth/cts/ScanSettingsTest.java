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

import android.bluetooth.le.ScanSettings;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test for Bluetooth LE {@link ScanSettings}.
 */
public class ScanSettingsTest extends AndroidTestCase {

    @SmallTest
    public void testDefaultSettings() {
        ScanSettings settings = new ScanSettings.Builder().build();
        assertEquals(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, settings.getCallbackType());
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, settings.getScanMode());
        assertEquals(0, settings.getScanResultType());
        assertEquals(0, settings.getReportDelayMillis());
    }

    @SmallTest
    public void testDescribeContents() {
        ScanSettings settings = new ScanSettings.Builder().build();
        assertEquals(0, settings.describeContents());
    }

    @SmallTest
    public void testReadWriteParcel() {
        final long reportDelayMillis = 60 * 1000;
        Parcel parcel = Parcel.obtain();
        ScanSettings settings = new ScanSettings.Builder()
                .setReportDelay(reportDelayMillis)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .build();
        settings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ScanSettings settingsFromParcel = ScanSettings.CREATOR.createFromParcel(parcel);
        assertEquals(reportDelayMillis, settingsFromParcel.getReportDelayMillis());
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, settings.getScanMode());
    }
}
