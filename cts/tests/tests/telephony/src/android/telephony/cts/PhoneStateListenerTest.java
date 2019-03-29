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
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.test.InstrumentationTestCase;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.ReadElf;
import com.android.compatibility.common.util.TestThread;

public class PhoneStateListenerTest extends  AndroidTestCase{

    public static final long WAIT_TIME = 1000;

    private boolean mOnCallForwardingIndicatorChangedCalled;
    private boolean mOnCallStateChangedCalled;
    private boolean mOnCellLocationChangedCalled;
    private boolean mOnDataActivityCalled;
    private boolean mOnDataConnectionStateChangedCalled;
    private boolean mOnMessageWaitingIndicatorChangedCalled;
    private boolean mOnServiceStateChangedCalled;
    private boolean mOnSignalStrengthChangedCalled;
    private SignalStrength mSignalStrength;
    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mListener;
    private final Object mLock = new Object();
    private static final String TAG = "android.telephony.cts.PhoneStateListenerTest";
    private static ConnectivityManager mCm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager =
                (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mCm = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    public void testPhoneStateListener() {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        Looper.prepare();
        new PhoneStateListener();
    }

    /*
     * The tests below rely on the framework to immediately call the installed listener upon
     * registration. There is no simple way to emulate state changes for testing the listeners.
     */

    public void testOnServiceStateChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        synchronized(mLock) {
                            mOnServiceStateChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE);

                Looper.loop();
            }
        });

        assertFalse(mOnServiceStateChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnServiceStateChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnServiceStateChangedCalled);
    }

    public void testOnSignalStrengthChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onSignalStrengthChanged(int asu) {
                        synchronized(mLock) {
                            mOnSignalStrengthChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTH);

                Looper.loop();
            }
        });

        assertFalse(mOnSignalStrengthChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnSignalStrengthChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnSignalStrengthChangedCalled);
    }

    public void testOnSignalStrengthsChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                        synchronized(mLock) {
                            mSignalStrength = signalStrength;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

                Looper.loop();
            }
        });

        assertTrue(mSignalStrength == null);
        t.start();

        synchronized (mLock) {
            while(mSignalStrength == null) {
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mSignalStrength != null);

        // Call SignalStrength methods to make sure they do not throw any exceptions
        mSignalStrength.getCdmaDbm();
        mSignalStrength.getCdmaEcio();
        mSignalStrength.getEvdoDbm();
        mSignalStrength.getEvdoEcio();
        mSignalStrength.getEvdoSnr();
        mSignalStrength.getGsmBitErrorRate();
        mSignalStrength.getGsmSignalStrength();
        mSignalStrength.isGsm();
        mSignalStrength.getLevel();
    }

    public void testOnMessageWaitingIndicatorChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onMessageWaitingIndicatorChanged(boolean mwi) {
                        synchronized(mLock) {
                            mOnMessageWaitingIndicatorChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(
                        mListener, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);

                Looper.loop();
            }
        });

        assertFalse(mOnMessageWaitingIndicatorChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnMessageWaitingIndicatorChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnMessageWaitingIndicatorChangedCalled);
    }

    public void testOnCallForwardingIndicatorChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onCallForwardingIndicatorChanged(boolean cfi) {
                        synchronized(mLock) {
                            mOnCallForwardingIndicatorChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(
                        mListener, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);

                Looper.loop();
            }
        });

        assertFalse(mOnCallForwardingIndicatorChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnCallForwardingIndicatorChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnCallForwardingIndicatorChangedCalled);
    }

    public void testOnCellLocationChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onCellLocationChanged(CellLocation location) {
                        synchronized(mLock) {
                            mOnCellLocationChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CELL_LOCATION);

                Looper.loop();
            }
        });

        assertFalse(mOnCellLocationChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnCellLocationChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnCellLocationChangedCalled);
    }

    public void testOnCallStateChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String incomingNumber) {
                        synchronized(mLock) {
                            mOnCallStateChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CALL_STATE);

                Looper.loop();
            }
        });

        assertFalse(mOnCallStateChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnCallStateChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnCallStateChangedCalled);
    }

    public void testOnDataConnectionStateChanged() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onDataConnectionStateChanged(int state) {
                        synchronized(mLock) {
                            mOnDataConnectionStateChangedCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(
                        mListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

                Looper.loop();
            }
        });

        assertFalse(mOnDataConnectionStateChangedCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnDataConnectionStateChangedCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnDataConnectionStateChangedCalled);
    }

    public void testOnDataActivity() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onDataActivity(int direction) {
                        synchronized(mLock) {
                            mOnDataActivityCalled = true;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_DATA_ACTIVITY);

                Looper.loop();
            }
        });

        assertFalse(mOnDataActivityCalled);
        t.start();

        synchronized (mLock) {
            while(!mOnDataActivityCalled){
                mLock.wait();
            }
        }
        t.checkException();
        assertTrue(mOnDataActivityCalled);
    }
}
