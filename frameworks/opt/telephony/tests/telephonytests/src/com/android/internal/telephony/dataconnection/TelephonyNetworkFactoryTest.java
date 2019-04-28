/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.StringNetworkSpecifier;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.test.filters.FlakyTest;
import android.telephony.Rlog;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.mocks.ConnectivityServiceMock;
import com.android.internal.telephony.mocks.DcTrackerMock;
import com.android.internal.telephony.mocks.PhoneSwitcherMock;
import com.android.internal.telephony.mocks.SubscriptionControllerMock;
import com.android.internal.telephony.mocks.SubscriptionMonitorMock;
import com.android.internal.telephony.mocks.TelephonyRegistryMock;

import org.junit.Ignore;

public class TelephonyNetworkFactoryTest extends AndroidTestCase {
    private final static String LOG_TAG = "TelephonyNetworkFactoryTest";

    private void waitABit() {
        try {
            Thread.sleep(250);
        } catch (Exception e) {}
    }

    private String mTestName = "";

    private void log(String str) {
        Rlog.d(LOG_TAG + " " + mTestName, str);
    }

    private class TestSetup {
        final TelephonyRegistryMock telephonyRegistryMock;
        final PhoneSwitcherMock phoneSwitcherMock;
        final SubscriptionControllerMock subscriptionControllerMock;
        final SubscriptionMonitorMock subscriptionMonitorMock;
        final HandlerThread handlerThread;
        final ConnectivityServiceMock connectivityServiceMock;
        final Looper looper;
        DcTrackerMock dcTrackerMock;
        final Context contextMock;
        private Object mLock = new Object();
        private static final int MAX_INIT_WAIT_MS = 30000; // 30 seconds

        TestSetup(int numPhones) {
            handlerThread = new HandlerThread("TelephonyNetworkFactoryTest") {
                @Override
                public void onLooperPrepared() {
                    synchronized (mLock) {
                        if (dcTrackerMock == null) dcTrackerMock = new DcTrackerMock();
                        mLock.notifyAll();
                    }
                }
            };
            handlerThread.start();
            // wait until dct created
            synchronized (mLock) {
                try {
                    mLock.wait(MAX_INIT_WAIT_MS);
                } catch (InterruptedException ie) {
                }
                if (dcTrackerMock == null) fail("failed to initialize dct");
            }
            looper = handlerThread.getLooper();

            final ContextFixture contextFixture = new ContextFixture();
            String[] networkConfigString = getContext().getResources().getStringArray(
                    com.android.internal.R.array.networkAttributes);
            contextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                    networkConfigString);
            contextMock = contextFixture.getTestDouble();

            connectivityServiceMock = new ConnectivityServiceMock(contextMock);
            ConnectivityManager cm =
                    new ConnectivityManager(contextMock, connectivityServiceMock);
            contextFixture.setSystemService(Context.CONNECTIVITY_SERVICE, cm);

            telephonyRegistryMock = new TelephonyRegistryMock();
            phoneSwitcherMock = new PhoneSwitcherMock(numPhones, looper);
            subscriptionControllerMock =
                    new SubscriptionControllerMock(contextMock, telephonyRegistryMock, numPhones);
            subscriptionMonitorMock = new SubscriptionMonitorMock(numPhones);
        }

        void die() {
            connectivityServiceMock.die();
            looper.quit();
            handlerThread.quit();
        }
    }

    private TelephonyNetworkFactory makeTnf(int phoneId, TestSetup ts) {
        return new TelephonyNetworkFactory(ts.phoneSwitcherMock, ts.subscriptionControllerMock,
                ts.subscriptionMonitorMock, ts.looper, ts.contextMock, phoneId, ts.dcTrackerMock);
    }

    private NetworkRequest makeSubSpecificDefaultRequest(TestSetup ts, int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        return ts.connectivityServiceMock.requestNetwork(netCap, null, 0, new Binder(), -1);
    }
    private NetworkRequest makeSubSpecificMmsRequest(TestSetup ts, int subId) {
        NetworkCapabilities netCap = (new NetworkCapabilities()).
                addCapability(NetworkCapabilities.NET_CAPABILITY_MMS).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        return ts.connectivityServiceMock.requestNetwork(netCap, null, 0, new Binder(), -1);
    }


    /**
     * Test that phone active changes cause the DcTracker to get poked.
     */
    @FlakyTest
    @SmallTest
    public void testActive() throws Exception {
        mTestName = "testActive";
        final int numberOfPhones = 1;
        final int phoneId = 0;
        final int subId = 0;

        TestSetup ts = new TestSetup(numberOfPhones);

        makeTnf(phoneId, ts);

        ts.subscriptionControllerMock.setDefaultDataSubId(subId);
        ts.subscriptionControllerMock.setSlotSubId(phoneId, subId);
        ts.subscriptionMonitorMock.notifySubscriptionChanged(phoneId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(phoneId);

        log("addDefaultRequest");
        ts.connectivityServiceMock.addDefaultRequest();
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("pretest of LiveRequests != 0");
        }

        log("setPhoneActive true: phoneId = " + phoneId);
        ts.phoneSwitcherMock.setPhoneActive(phoneId, true);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 1) {
            fail("post-active test of LiveRequests != 1");
        }

        log("makeSubSpecificDefaultRequest: subId = " + subId);
        NetworkRequest subSpecificDefault = makeSubSpecificDefaultRequest(ts, subId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 2) {
            fail("post-second-request test of LiveRequests != 2");
        }

        log("setPhoneActive false: phoneId = " + phoneId);
        ts.phoneSwitcherMock.setPhoneActive(phoneId, false);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("post-inactive test of LiveRequests != 0");
        }

        log("makeSubSpecificDefaultRequest: subId = " + subId);
        NetworkRequest subSpecificMms = makeSubSpecificMmsRequest(ts, subId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("post-mms-add test of LiveRequests != 0");
        }

        log("setPhoneActive true: phoneId = " + phoneId);
        ts.phoneSwitcherMock.setPhoneActive(phoneId, true);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 3) {
            fail("post-active-mms-add test of LiveRequests != 3");
        }

        log("releaseNetworkRequest: subSpecificDefault = " + subSpecificDefault);
        ts.connectivityServiceMock.releaseNetworkRequest(subSpecificDefault);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 2) {
            fail("post-remove-default test of LiveRequests != 2");
        }

        log("setPhoneActive false: phoneId = " + phoneId);
        ts.phoneSwitcherMock.setPhoneActive(phoneId, false);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 8, LiveRequests != 0");
        }

        log("releaseNetworkRequest: subSpecificMms = " + subSpecificMms);
        ts.connectivityServiceMock.releaseNetworkRequest(subSpecificMms);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 9, LiveRequests != 0");
        }

        log("setPhoneActive true: phoneId = " + phoneId);
        ts.phoneSwitcherMock.setPhoneActive(phoneId, true);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 1) {
            fail("test 10, LiveRequests != 1," + ts.dcTrackerMock.getNumberOfLiveRequests());
        }

        ts.die();
    }

    /**
     * Test that network request changes cause the DcTracker to get poked.
     */
    @SmallTest
    public void testRequests() throws Exception {
        mTestName = "testActive";
        final int numberOfPhones = 2;
        final int phoneId = 0;
        final int altPhoneId = 1;
        final int subId = 0;
        final int altSubId = 1;
        final int unusedSubId = 2;

        TestSetup ts = new TestSetup(numberOfPhones);

        makeTnf(phoneId, ts);

        ts.subscriptionControllerMock.setDefaultDataSubId(subId);
        ts.subscriptionControllerMock.setSlotSubId(phoneId, subId);
        ts.subscriptionMonitorMock.notifySubscriptionChanged(phoneId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(phoneId);
        waitABit();

        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 1, LiveRequests != 0");
        }

        ts.phoneSwitcherMock.setPhoneActive(phoneId, true);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 2, LiveRequests != 0");
        }

        ts.connectivityServiceMock.addDefaultRequest();
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 1) {
            fail("test 3, LiveRequests != 1");
        }

        ts.subscriptionControllerMock.setSlotSubId(altPhoneId, altSubId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 1) {
            fail("test 4, LiveRequests != 1");
        }

        ts.subscriptionControllerMock.setDefaultDataSubId(altSubId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(phoneId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(altPhoneId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 5, LiveRequests != 0");
        }

        makeSubSpecificMmsRequest(ts, subId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 1) {
            fail("test 6,  LiveRequests != 1");
        }

        ts.subscriptionControllerMock.setSlotSubId(phoneId, unusedSubId);
        ts.subscriptionMonitorMock.notifySubscriptionChanged(phoneId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 7,  LiveRequests != 0");
        }

        makeSubSpecificDefaultRequest(ts, subId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 0) {
            fail("test 8, LiveRequests != 0");
        }

        ts.subscriptionControllerMock.setSlotSubId(phoneId, subId);
        ts.subscriptionMonitorMock.notifySubscriptionChanged(phoneId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 2) {
            fail("test 9,  LiveRequests != 2");
        }

        ts.subscriptionControllerMock.setDefaultDataSubId(subId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(phoneId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(altPhoneId);
        ts.subscriptionMonitorMock.notifyDefaultSubscriptionChanged(phoneId);
        waitABit();
        if (ts.dcTrackerMock.getNumberOfLiveRequests() != 3) {
            fail("test 10, LiveRequests != 3");
        }
        ts.die();
    }
}
