/*
 * Copyright (C) 2015 Google Inc.
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

package android.location.cts;

import junit.framework.Assert;

import android.location.GnssNavigationMessage;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used for receiving GPS satellite Navigation Messages from the GPS engine.
 */
class TestGnssNavigationMessageListener extends GnssNavigationMessage.Callback {

    // Timeout in sec for count down latch wait
    private static final int TIMEOUT_IN_SEC = 90;

    private volatile int mStatus = -1;

    private final String mTag;
    private final int mEventsToCollect;
    private final List<GnssNavigationMessage> mEvents;
    private final CountDownLatch mCountDownLatch;

    TestGnssNavigationMessageListener(String tag, int eventsToCollect) {
        mTag = tag;
        mCountDownLatch = new CountDownLatch(1);
        mEventsToCollect = eventsToCollect;
        mEvents = new CopyOnWriteArrayList<GnssNavigationMessage>();
    }

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
        mEvents.add(event);
        if (mEvents.size() > mEventsToCollect) {
            mCountDownLatch.countDown();
        }
    }

    @Override
    public void onStatusChanged(int status) {
        mStatus = status;
        if (mStatus != GnssNavigationMessage.Callback.STATUS_READY) {
            mCountDownLatch.countDown();
        }
    }

    public boolean await() throws InterruptedException {
        Log.i(mTag, "Number of GPS Navigation Message received = " + getEvents().size());
        return TestUtils.waitFor(mCountDownLatch, TIMEOUT_IN_SEC);
    }

    /**
     * Get GPS Navigation Message Status.
     *
     * @return mStatus Gps Navigation Message Status
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * @return {@code true} if the state of the test ensures that data is expected to be collected,
     *         {@code false} otherwise.
     */
    public boolean verifyState() {
        switch (getStatus()) {
            case GnssNavigationMessage.Callback.STATUS_NOT_SUPPORTED:
                SoftAssert.failAsWarning(mTag, "GnssNavigationMessage is not supported in the"
                        + " device: verifications performed by this test will be skipped.");
                return false;
            case GnssNavigationMessage.Callback.STATUS_READY:
                return true;
            case GnssNavigationMessage.Callback.STATUS_LOCATION_DISABLED:
                Log.i(mTag, "Location or GPS is disabled on the device: skipping the test.");
                return false;
            default:
                Assert.fail("GnssNavigationMessage status callback was not received.");
        }
        return false;
    }

    /**
     * Get list of GPS Navigation Message Events.
     *
     * @return mEvents list of GPS Navigation Message Events
     */
    public List<GnssNavigationMessage> getEvents() {
        return mEvents;
    }
}
