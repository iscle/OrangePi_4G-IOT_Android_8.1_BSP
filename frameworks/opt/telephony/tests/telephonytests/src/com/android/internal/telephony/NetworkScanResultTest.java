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
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;

import org.junit.Test;

import java.util.ArrayList;

/** Unit tests for {@link NetworkScanResult}. */

public class NetworkScanResultTest {

    @Test
    @SmallTest
    public void testParcel() {
        ArrayList<CellInfo> infos = new ArrayList<CellInfo>();

        CellIdentityGsm cig = new CellIdentityGsm(310, 310, 1, 2, 3, 4);
        CellSignalStrengthGsm cssg = new CellSignalStrengthGsm();
        cssg.initialize(5, 6, 7);
        CellInfoGsm gsm = new CellInfoGsm();
        gsm.setRegistered(true);
        gsm.setTimeStampType(8);
        gsm.setTimeStamp(9);
        gsm.setCellIdentity(cig);
        gsm.setCellSignalStrength(cssg);
        infos.add(gsm);

        CellIdentityLte cil = new CellIdentityLte(320, 320, 11, 12, 13, 14);
        CellSignalStrengthLte cssl = new CellSignalStrengthLte();
        cssl.initialize(15, 16, 17, 18, 19, 20);
        CellInfoLte lte = new CellInfoLte();
        lte.setRegistered(false);
        lte.setTimeStampType(21);
        lte.setTimeStamp(22);
        lte.setCellIdentity(cil);
        lte.setCellSignalStrength(cssl);
        infos.add(lte);

        NetworkScanResult nsr = new NetworkScanResult(0, 0, infos);

        Parcel p = Parcel.obtain();
        nsr.writeToParcel(p, 0);
        p.setDataPosition(0);

        NetworkScanResult newNsr = NetworkScanResult.CREATOR.createFromParcel(p);
        assertEquals(nsr, newNsr);
    }
}
