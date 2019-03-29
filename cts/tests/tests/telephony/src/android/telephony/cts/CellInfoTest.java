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
 * limitations under the License.
 */
package android.telephony.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;

/**
 * Test TelephonyManager.getAllCellInfo()
 * <p>
 * TODO(chesnutt): test onCellInfoChanged() once the implementation
 * of async callbacks is complete (see http://b/13788638)
 */
public class CellInfoTest extends AndroidTestCase{
    private final Object mLock = new Object();
    private TelephonyManager mTelephonyManager;
    private static ConnectivityManager mCm;
    private static final String TAG = "android.telephony.cts.CellInfoTest";
    // Maximum and minimum possible RSSI values(in dbm).
    private static final int MAX_RSSI = -10;
    private static final int MIN_RSSI = -150;
    // Maximum and minimum possible RSSP values(in dbm).
    private static final int MAX_RSRP = -44;
    private static final int MIN_RSRP = -140;
    // Maximum and minimum possible RSSQ values.
    private static final int MAX_RSRQ = -3;
    private static final int MIN_RSRQ = -35;
    // Maximum and minimum possible RSSNR values.
    private static final int MAX_RSSNR = 50;
    private static final int MIN_RSSNR = 0;
    // Maximum and minimum possible CQI values.
    private static final int MAX_CQI = 30;
    private static final int MIN_CQI = 0;
    private PackageManager mPm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager =
                (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mCm = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mPm = getContext().getPackageManager();
    }

    public void testCellInfo() throws Throwable {

        if(! (mPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        // getAllCellInfo should never return null, and there should
        // be at least one entry.
        List<CellInfo> allCellInfo = mTelephonyManager.getAllCellInfo();
        assertNotNull("TelephonyManager.getAllCellInfo() returned NULL!", allCellInfo);
        assertTrue("TelephonyManager.getAllCellInfo() returned zero-length list!",
            allCellInfo.size() > 0);

        int numRegisteredCells = 0;
        for (CellInfo cellInfo : allCellInfo) {
            if (cellInfo.isRegistered()) {
                ++numRegisteredCells;
            }
            if (cellInfo instanceof CellInfoLte) {
                verifyLteInfo((CellInfoLte) cellInfo);
            } else if (cellInfo instanceof CellInfoWcdma) {
                verifyWcdmaInfo((CellInfoWcdma) cellInfo);
            } else if (cellInfo instanceof CellInfoGsm) {
                verifyGsmInfo((CellInfoGsm) cellInfo);
            } else if (cellInfo instanceof CellInfoCdma) {
                verifyCdmaInfo((CellInfoCdma) cellInfo);
            }
        }

        //FIXME: The maximum needs to be calculated based on the number of
        //       radios and the technologies used (ex SRLTE); however, we have
        //       not hit any of these cases yet.
        assertTrue("None or too many registered cells : " + numRegisteredCells,
                numRegisteredCells > 0 && numRegisteredCells <= 2);
    }

    private void verifyCdmaInfo(CellInfoCdma cdma) {
        int level = cdma.getCellSignalStrength().getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >=0 && level <= 4);
    }

    // Verify lte cell information is within correct range.
    private void verifyLteInfo(CellInfoLte lte) {
        verifyRssiDbm(lte.getCellSignalStrength().getDbm());
        // Verify LTE physical cell id information.
        // Only physical cell id is available for LTE neighbor.
        int pci = lte.getCellIdentity().getPci();
        // Physical cell id should be within [0, 503].
        assertTrue("getPci() out of range [0, 503], pci=" + pci, pci >= 0 && pci <= 503);

        int earfcn = lte.getCellIdentity().getEarfcn();
        // Reference 3GPP 36.101 Table 5.7.3-1
        assertTrue("getEarfcn() out of range [0,47000], earfcn=" + earfcn,
            earfcn >= 0 && earfcn <= 47000);
        CellSignalStrengthLte cellSignalStrengthLte = lte.getCellSignalStrength();
        //Integer.MAX_VALUE indicates an unavailable field
        int rsrp = cellSignalStrengthLte.getRsrp();
        // RSRP is being treated as RSSI in LTE (they are similar but not quite right)
        // so reusing the constants here.
        assertTrue("getRsrp() out of range, rsrp=" + rsrp, rsrp >= MIN_RSRP && rsrp <= MAX_RSRP);
        int rsrq = cellSignalStrengthLte.getRsrq();
        assertTrue("getRsrq() out of range | Integer.MAX_VALUE, rsrq=" + rsrq,
            rsrq == Integer.MAX_VALUE || (rsrq >= MIN_RSRQ && rsrq <= MAX_RSRQ));
        int rssnr = cellSignalStrengthLte.getRssnr();
        assertTrue("getRssnr() out of range | Integer.MAX_VALUE, rssnr=" + rssnr,
            rssnr == Integer.MAX_VALUE || (rssnr >= MIN_RSSNR && rssnr <= MAX_RSSNR));
        int cqi = cellSignalStrengthLte.getCqi();
        assertTrue("getCqi() out of range | Integer.MAX_VALUE, cqi=" + cqi,
            cqi == Integer.MAX_VALUE || (cqi >= MIN_CQI && cqi <= MAX_CQI));
        int ta = cellSignalStrengthLte.getTimingAdvance();
        assertTrue("getTimingAdvance() invalid [0-1282] | Integer.MAX_VALUE, ta=" + ta,
                ta == Integer.MAX_VALUE || (ta >= 0 && ta <=1282));
        int level = cellSignalStrengthLte.getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >=0 && level <= 4);
    }

    // Verify wcdma cell information is within correct range.
    private void verifyWcdmaInfo(CellInfoWcdma wcdma) {
        verifyRssiDbm(wcdma.getCellSignalStrength().getDbm());
        // Verify wcdma primary scrambling code information.
        // Primary scrambling code should be within [0, 511].
        int psc = wcdma.getCellIdentity().getPsc();
        assertTrue("getPsc() out of range [0, 511], psc=" + psc, psc >= 0 && psc <= 511);

        int uarfcn = wcdma.getCellIdentity().getUarfcn();
        // Reference 3GPP 25.101 Table 5.2
        assertTrue("getUarfcn() out of range [400,11000], uarfcn=" + uarfcn,
            uarfcn >= 400 && uarfcn <= 11000);

        int level = wcdma.getCellSignalStrength().getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >=0 && level <= 4);
    }

    // Verify gsm cell information is within correct range.
    private void verifyGsmInfo(CellInfoGsm gsm) {
        verifyRssiDbm(gsm.getCellSignalStrength().getDbm());
        // Verify gsm local area code and cellid.
        // Local area code and cellid should be with [0, 65535].
        int lac = gsm.getCellIdentity().getLac();
        assertTrue("getLac() out of range [0, 65535], lac=" + lac, !gsm.isRegistered() ||
            lac >= 0 && lac <= 65535);
        int cid = gsm.getCellIdentity().getCid();
        assertTrue("getCid() out range [0, 65535], cid=" + cid, !gsm.isRegistered() ||
            cid >= 0 && cid <= 65535);

        int arfcn = gsm.getCellIdentity().getArfcn();
        // Reference 3GPP 45.005 Table 2-2
        assertTrue("getArfcn() out of range [0,1024], arfcn=" + arfcn,
            arfcn >= 0 && arfcn <= 1024);

        int level = gsm.getCellSignalStrength().getLevel();
        assertTrue("getLevel() out of range [0,4], level=" + level, level >=0 && level <= 4);

        int bsic = gsm.getCellIdentity().getBsic();
        // TODO(b/32774471) - Bsic should always be valid
        //assertTrue("getBsic() out of range [0,63]", bsic >=0 && bsic <=63);

        int ta = gsm.getCellSignalStrength().getTimingAdvance();
        assertTrue("getTimingAdvance() out of range [0,219] | Integer.MAX_VALUE, ta=" + ta,
                ta == Integer.MAX_VALUE || (ta >= 0 && ta <= 219));
    }

    // Rssi(in dbm) should be within [MIN_RSSI, MAX_RSSI].
    private void verifyRssiDbm(int dbm) {
        assertTrue("getCellSignalStrength().getDbm() out of range, dbm=" + dbm,
                dbm >= MIN_RSSI && dbm <= MAX_RSSI);
    }
}
