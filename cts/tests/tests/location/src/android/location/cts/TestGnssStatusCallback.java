/*
 * Copyright (C) 2016 Google Inc.
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

import android.location.GnssStatus;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used for receiving notifications when GNSS status has changed.
 */
class TestGnssStatusCallback extends GnssStatus.Callback {

    private final String mTag;
    private volatile boolean mGpsStatusReceived;
    private GnssStatus mGnssStatus = null;
    // Timeout in sec for count down latch wait
    private static final int TIMEOUT_IN_SEC = 90;
    private final CountDownLatch mLatchStart;
    private final CountDownLatch mLatchStatus;
    private final CountDownLatch mLatchTtff;
    private final CountDownLatch mLatchStop;

    // Store list of Prn for Satellites.
    private List<List<Integer>> mGpsSatellitePrns;

    TestGnssStatusCallback(String tag, int gpsStatusCountToCollect) {
        this.mTag = tag;
        mLatchStart = new CountDownLatch(1);
        mLatchStatus = new CountDownLatch(gpsStatusCountToCollect);
        mLatchTtff = new CountDownLatch(1);
        mLatchStop = new CountDownLatch(1);
        mGpsSatellitePrns = new ArrayList<List<Integer>>();
    }

    @Override
    public void onStarted() {
        Log.i(mTag, "Gnss Status Listener Started");
        mLatchStart.countDown();
    }

    @Override
    public void onStopped() {
        Log.i(mTag, "Gnss Status Listener Stopped");
        mLatchStop.countDown();
    }

    @Override
    public void onFirstFix(int ttffMillis) {
        Log.i(mTag, "Gnss Status Listener Received TTFF");
        mLatchTtff.countDown();
    }

    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        Log.i(mTag, "Gnss Status Listener Received Status Update");
        mGnssStatus = status;
        mLatchStatus.countDown();
    }

    /**
     * Returns the list of PRNs (pseudo-random number) for the satellite.
     *
     * @return list of PRNs number
     */
    public List<List<Integer>> getGpsSatellitePrns() {
        return mGpsSatellitePrns;
    }

    /**
     * Check if GPS Status is received.
     *
     * @return {@code true} if the GPS Status is received and {@code false}
     *         if GPS Status is not received.
     */
    public boolean isGpsStatusReceived() {
        return mGpsStatusReceived;
    }

    /**
     * Get GPS Status.
     *
     * @return mGpsStatus GPS Status
     */
    public GnssStatus getGnssStatus() {
        return mGnssStatus;
    }

    public boolean awaitStart() throws InterruptedException {
        return TestUtils.waitFor(mLatchStart, TIMEOUT_IN_SEC);
    }

    public boolean awaitStatus() throws InterruptedException {
        return TestUtils.waitFor(mLatchStatus, TIMEOUT_IN_SEC);
    }

    public boolean awaitTtff() throws InterruptedException {
        return TestUtils.waitFor(mLatchTtff, TIMEOUT_IN_SEC);
    }

    public boolean awaitStop() throws InterruptedException {
        return TestUtils.waitFor(mLatchStop, TIMEOUT_IN_SEC);
    }
}
