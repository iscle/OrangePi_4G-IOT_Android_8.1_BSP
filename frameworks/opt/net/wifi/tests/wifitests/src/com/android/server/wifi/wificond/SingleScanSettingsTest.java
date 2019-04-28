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
 * Unit tests for {@link com.android.server.wifi.wificond.SingleScanSettingsResult}.
 */
@SmallTest
public class SingleScanSettingsTest {

    private static final byte[] TEST_SSID_1 =
            new byte[] {'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_SSID_2 =
            new byte[] {'A', 'n', 'd', 'r', 'o', 'i', 'd', 'T', 'e', 's', 't'};
    private static final int TEST_FREQUENCY_1 = 2456;
    private static final int TEST_FREQUENCY_2 = 5215;

    /**
     *  SingleScanSettings object can be serialized and deserialized, while keeping the
     *  values unchanged.
     */
    @Test
    public void canSerializeAndDeserialize() throws Exception {
        ChannelSettings channelSettings1 = new ChannelSettings();
        channelSettings1.frequency = TEST_FREQUENCY_1;
        ChannelSettings channelSettings2 = new ChannelSettings();
        channelSettings2.frequency = TEST_FREQUENCY_2;

        HiddenNetwork hiddenNetwork1 = new HiddenNetwork();
        hiddenNetwork1.ssid = TEST_SSID_1;
        HiddenNetwork hiddenNetwork2 = new HiddenNetwork();
        hiddenNetwork2.ssid = TEST_SSID_2;

        SingleScanSettings scanSettings = new SingleScanSettings();
        scanSettings.channelSettings =
                new ArrayList(Arrays.asList(channelSettings1, channelSettings2));
        scanSettings.hiddenNetworks = new ArrayList(Arrays.asList(hiddenNetwork1, hiddenNetwork2));

        Parcel parcel = Parcel.obtain();
        scanSettings.writeToParcel(parcel, 0);
        // Rewind the pointer to the head of the parcel.
        parcel.setDataPosition(0);
        SingleScanSettings scanSettingsDeserialized =
                SingleScanSettings.CREATOR.createFromParcel(parcel);

        assertNotNull(scanSettingsDeserialized);
        assertEquals(scanSettings, scanSettingsDeserialized);
    }
}
