/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.support.car.navigation;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.car.CarNotConnectedException;

/**
 * @hide
 */
public class CarNavigationStatusManagerEmbedded extends CarNavigationStatusManager {

    private final android.car.navigation.CarNavigationStatusManager mManager;

    public CarNavigationStatusManagerEmbedded(Object manager) {
        mManager = (android.car.navigation.CarNavigationStatusManager) manager;
    }

    /**
     * @param status new instrument cluster navigation status.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    @Override
    public void sendNavigationStatus(int status) throws CarNotConnectedException {
        try {
            mManager.sendNavigationStatus(status);
        } catch (android.car.CarNotConnectedException e) {
           throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void sendNavigationTurnEvent(int event, CharSequence eventName, int turnAngle,
            int turnNumber, int turnSide) throws CarNotConnectedException {
        sendNavigationTurnEvent(event, eventName, turnAngle, turnNumber, null, turnSide);
    }

    @Override
    public void sendNavigationTurnEvent(int event, CharSequence eventName, int turnAngle,
            int turnNumber, Bitmap image, int turnSide) throws CarNotConnectedException {
        try {
            mManager.sendNavigationTurnEvent(event, eventName, turnAngle, turnNumber, image,
                    turnSide);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void sendNavigationTurnDistanceEvent(int distanceMeters, int timeSeconds,
            int displayDistanceMillis, int displayDistanceUnit) throws CarNotConnectedException {
        try {
            mManager.sendNavigationTurnDistanceEvent(distanceMeters, timeSeconds,
                    displayDistanceMillis, displayDistanceUnit);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void sendEvent(int eventType, Bundle bundle) throws CarNotConnectedException {
        try {
            mManager.sendEvent(eventType, bundle);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        //nothing to do
    }

    /**
     * In this implementation we just immediately call {@code listener#onInstrumentClusterStarted}
     * as we expect instrument cluster to be working all the time.
     *
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    @Override
    public void addListener(CarNavigationCallback callback)
            throws CarNotConnectedException {

        try {
            callback.onInstrumentClusterStarted(this, convert(mManager.getInstrumentClusterInfo()));
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void removeListener() {
        // Nothing to do.
    }

    private static CarNavigationInstrumentCluster convert(
            android.car.navigation.CarNavigationInstrumentCluster ic) {
        if (ic == null) {
            return null;
        }
        return new CarNavigationInstrumentCluster(ic.getMinIntervalMillis(), ic.getType(),
                ic.getImageWidth(), ic.getImageHeight(), ic.getImageColorDepthBits(),
                ic.getExtra());
    }
}
