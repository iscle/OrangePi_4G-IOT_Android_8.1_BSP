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

package com.android.server.wifi.wificond;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.wifi.wificond.PnoSettingsResult}.
 */
@SmallTest
public class PnoSettingsTest {

    private static final byte[] TEST_SSID_1 =
            new byte[] {'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_SSID_2 =
            new byte[] {'A', 'n', 'd', 'r', 'o', 'i', 'd', 'T', 'e', 's', 't'};
    private static final int TEST_INTERVAL_MS = 30000;
    private static final int TEST_MIN_2G_RSSI = -60;
    private static final int TEST_MIN_5G_RSSI = -65;

    /**
     *  PnoSettings object can be serialized and deserialized, while keeping the
     *  values unchanged.
     */
    @Test
    public void canSerializeAndDeserialize() throws Exception {

        PnoSettings pnoSettings = new PnoSettings();

        PnoNetwork pnoNetwork1 = new PnoNetwork();
        pnoNetwork1.ssid = TEST_SSID_1;
        pnoNetwork1.isHidden = true;

        PnoNetwork pnoNetwork2 = new PnoNetwork();
        pnoNetwork2.ssid = TEST_SSID_2;
        pnoNetwork2.isHidden = false;

        pnoSettings.pnoNetworks = new ArrayList(Arrays.asList(pnoNetwork1, pnoNetwork2));

        pnoSettings.intervalMs = TEST_INTERVAL_MS;
        pnoSettings.min2gRssi = TEST_MIN_2G_RSSI;
        pnoSettings.min5gRssi = TEST_MIN_5G_RSSI;

        Parcel parcel = Parcel.obtain();
        pnoSettings.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        PnoSettings pnoSettingsDeserialized =
                pnoSettings.CREATOR.createFromParcel(parcel);

        assertNotNull(pnoSettingsDeserialized);
        assertEquals(pnoSettings, pnoSettingsDeserialized);
    }
}
