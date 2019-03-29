/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.os.Looper;
import android.net.ConnectivityManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.test.AndroidTestCase;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.compatibility.common.util.ReadElf;
import com.android.compatibility.common.util.TestThread;

public class CellLocationTest extends AndroidTestCase{
    private boolean mOnCellLocationChangedCalled;
    private final Object mLock = new Object();
    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mListener;
    private static ConnectivityManager mCm;
    private static final String TAG = "android.telephony.cts.CellLocationTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager =
                (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mCm = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mListener != null) {
            // unregister listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }
        super.tearDown();
    }

    public void testCellLocation() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        // getCellLocation should never return null,
        // but that is allowed if the cell network type
        // is LTE (since there is no LteCellLocation class)
        if (mTelephonyManager.getNetworkType() != TelephonyManager.NETWORK_TYPE_LTE) {
            assertNotNull("TelephonyManager.getCellLocation() returned null!",
                mTelephonyManager.getCellLocation());
        }

        CellLocation cl = CellLocation.getEmpty();
        if (cl instanceof GsmCellLocation) {
            GsmCellLocation gcl = (GsmCellLocation) cl;
            assertNotNull(gcl);
            assertEquals(-1, gcl.getCid());
            assertEquals(-1, gcl.getLac());
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();
                mListener = new PhoneStateListener() {
                    @Override
                    public void onCellLocationChanged(CellLocation location) {
                        synchronized (mLock) {
                            mOnCellLocationChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CELL_LOCATION);

                Looper.loop();
            }
        });

        t.start();

        CellLocation.requestLocationUpdate();
        synchronized (mLock) {
            while (!mOnCellLocationChangedCalled) {
                mLock.wait();
            }
        }
        Thread.sleep(1000);
        assertTrue(mOnCellLocationChangedCalled);
        t.checkException();
    }
}
