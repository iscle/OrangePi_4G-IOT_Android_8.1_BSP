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

package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.RadioNetworkConstants.EutranBands;
import android.telephony.RadioNetworkConstants.GeranBands;
import android.telephony.RadioNetworkConstants.RadioAccessNetworks;

import org.junit.Test;

/** Unit tests for {@link NetworkScanRequest}. */

public class NetworkScanRequestTest {

    @Test
    @SmallTest
    public void testParcel() {
        int ranGsm = RadioAccessNetworks.GERAN;
        int[] gsmBands = {GeranBands.BAND_T380, GeranBands.BAND_T410};
        int[] gsmChannels = {1, 2, 3, 4};
        RadioAccessSpecifier gsm = new RadioAccessSpecifier(ranGsm, gsmBands, gsmChannels);
        int ranLte = RadioAccessNetworks.EUTRAN;
        int[] lteBands = {EutranBands.BAND_10, EutranBands.BAND_11};
        int[] lteChannels = {5, 6, 7, 8};
        RadioAccessSpecifier lte = new RadioAccessSpecifier(ranLte, lteBands, lteChannels);
        RadioAccessSpecifier[] ras = {gsm, lte};
        NetworkScanRequest nsq = new NetworkScanRequest(NetworkScanRequest.SCAN_TYPE_ONE_SHOT, ras);

        Parcel p = Parcel.obtain();
        nsq.writeToParcel(p, 0);
        p.setDataPosition(0);

        NetworkScanRequest newNsq = NetworkScanRequest.CREATOR.createFromParcel(p);
        assertEquals(nsq, newNsq);
    }
}
