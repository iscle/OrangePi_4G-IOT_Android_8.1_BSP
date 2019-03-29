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

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used for receiving notifications from the LocationManager when the location has changed.
 */
class TestLocationListener implements LocationListener {
    private volatile boolean mProviderEnabled;
    private volatile boolean mLocationReceived;
    // Timeout in sec for count down latch wait
    private static final int TIMEOUT_IN_SEC = 120;
    private final CountDownLatch mCountDownLatch;
    private List<Location>  mLocationList = null;

    TestLocationListener(int locationToCollect) {
        mCountDownLatch = new CountDownLatch(locationToCollect);
        mLocationList = new ArrayList<>();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLocationReceived = true;
        mLocationList.add(location);
        mCountDownLatch.countDown();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
        if (LocationManager.GPS_PROVIDER.equals(s)) {
            mProviderEnabled = true;
        }
    }

    @Override
    public void onProviderDisabled(String s) {
        if (LocationManager.GPS_PROVIDER.equals(s)) {
            mProviderEnabled = false;
        }
    }

    public boolean await() throws InterruptedException {
        return TestUtils.waitFor(mCountDownLatch, TIMEOUT_IN_SEC);
    }

    /**
     * Get the list of locations received
     *
     * @return mLocationList, List of {@link Location}.
     */
    public List<Location> getReceivedLocationList(){
        return mLocationList;
    }

    /**
     * Check if location provider is enabled.
     *
     * @return {@code true} if the location provider is enabled and {@code false}
     *         if location provider is disabled.
     */
    public boolean isProviderEnabled() {
        return mProviderEnabled;
    }

    /**
     * Check if the location is received.
     *
     * @return {@code true} if the location is received and {@code false}
     *         if location is not received.
     */
    public boolean isLocationReceived() {
        return mLocationReceived;
    }
}
