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
package android.car.apitest;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarAppFocusManager.OnAppFocusOwnershipCallback;
import android.car.navigation.CarNavigationStatusManager;
import android.os.Bundle;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import com.google.android.collect.Lists;

/**
 * Unit tests for {@link CarNavigationStatusManager}
 */
@MediumTest
public class CarNavigationManagerTest extends CarApiTestBase {

    private static final String TAG = CarNavigationManagerTest.class.getSimpleName();

    private CarNavigationStatusManager mCarNavigationManager;
    private CarAppFocusManager mCarAppFocusManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarNavigationManager =
                (CarNavigationStatusManager) getCar().getCarManager(Car.CAR_NAVIGATION_SERVICE);
        mCarAppFocusManager =
                (CarAppFocusManager) getCar().getCarManager(Car.APP_FOCUS_SERVICE);
        assertNotNull(mCarAppFocusManager);
    }

    public void testStart() throws Exception {
        if (mCarNavigationManager == null) {
            Log.w(TAG, "Unable to run the test: "
                    + "car navigation manager was not created succesfully.");
            return;
        }

        try {
            mCarNavigationManager.sendNavigationStatus(1);
            fail();
        } catch (IllegalStateException expected) {
            // Expected. Client should acquire focus ownership for APP_FOCUS_TYPE_NAVIGATION.
        }

        mCarAppFocusManager.addFocusListener(new CarAppFocusManager.OnAppFocusChangedListener() {
            @Override
            public void onAppFocusChanged(int appType, boolean active) {
                // Nothing to do here.
            }
        }, CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION);
        OnAppFocusOwnershipCallback ownershipCallback = new OnAppFocusOwnershipCallback() {
            @Override
            public void onAppFocusOwnershipLost(int focus) {
                // Nothing to do here.
            }

            @Override
            public void onAppFocusOwnershipGranted(int focus) {
                // Nothing to do here.
            }
        };
        mCarAppFocusManager.requestAppFocus(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
                ownershipCallback);
        assertTrue(mCarAppFocusManager.isOwningFocus(ownershipCallback,
                CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION));

        Log.i(TAG, "Instrument cluster: " + mCarNavigationManager.getInstrumentClusterInfo());

        // TODO: we should use mocked HAL to be able to verify this, right now just make sure that
        // it is not crashing and logcat has appropriate traces.
        mCarNavigationManager.sendNavigationStatus(1);

        Bundle bundle = new Bundle();
        bundle.putInt("BUNDLE_INTEGER_VALUE", 1234);
        bundle.putFloat("BUNDLE_FLOAT_VALUE", 12.3456f);
        bundle.putStringArrayList("BUNDLE_ARRAY_OF_STRINGS",
                Lists.newArrayList("Value A", "Value B", "Value Z"));

        mCarNavigationManager.sendEvent
                (CarNavigationStatusManager.EVENT_TYPE_NEXT_MANEUVER_INFO, bundle);
    }
}
