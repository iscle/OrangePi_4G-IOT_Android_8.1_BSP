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
package com.android.car.test;

import android.car.settings.CarSettings;
import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.car.CarPowerManagementService;
import com.android.car.DeviceIdleControllerWrapper;
import com.android.car.GarageModeService;

@MediumTest
public class GarageModeTest extends AndroidTestCase {

    public void testMaintenanceActive() throws Exception {
        MockCarPowerManagementService powerManagementService = new MockCarPowerManagementService();
        MockDeviceIdleController controller = new MockDeviceIdleController(true);
        GarageModeServiceForTest garageMode = new GarageModeServiceForTest(getContext(),
                powerManagementService,
                controller);
        garageMode.init();
        final int index1 = garageMode.getGarageModeIndex();
        assertEquals(garageMode.getMaintenanceWindow(),
                powerManagementService.doNotifyPrepareShutdown(false));
        assertEquals(true, garageMode.isInGarageMode());
        assertEquals(true, garageMode.isMaintenanceActive());

        controller.setMaintenanceActivity(false);
        assertEquals(false, garageMode.isInGarageMode());
        assertEquals(false, garageMode.isMaintenanceActive());
        final int index2 = garageMode.getGarageModeIndex();

        assertEquals(1, index2 - index1);
    }

    public void testMaintenanceInactive() throws Exception {
        MockCarPowerManagementService powerManagementService = new MockCarPowerManagementService();
        MockDeviceIdleController controller = new MockDeviceIdleController(false);
        GarageModeServiceForTest garageMode = new GarageModeServiceForTest(getContext(),
                powerManagementService,
                controller);
        garageMode.init();
        assertEquals(garageMode.getMaintenanceWindow(),
                powerManagementService.doNotifyPrepareShutdown(false));
        assertEquals(true, garageMode.isInGarageMode());
        assertEquals(false, garageMode.isMaintenanceActive());
    }

    public void testDisplayOn() throws Exception {
        MockCarPowerManagementService powerManagementService = new MockCarPowerManagementService();
        MockDeviceIdleController controller = new MockDeviceIdleController(true);
        GarageModeServiceForTest garageMode = new GarageModeServiceForTest(getContext(),
                powerManagementService,
                controller);
        garageMode.init();

        powerManagementService.doNotifyPrepareShutdown(false);
        assertTrue(garageMode.getGarageModeIndex() > 0);
        powerManagementService.doNotifyPowerOn(true);
        assertEquals(0,garageMode.getGarageModeIndex());
    }

    private static class MockCarPowerManagementService extends CarPowerManagementService {
        public long doNotifyPrepareShutdown(boolean shuttingdown) {
            return notifyPrepareShutdown(shuttingdown);
        }

        public void doNotifyPowerOn(boolean displayOn) {
            notifyPowerOn(displayOn);
        }
    }

    private static class GarageModeServiceForTest extends GarageModeService {
        public GarageModeServiceForTest(Context context,
                CarPowerManagementService powerManagementService,
                DeviceIdleControllerWrapper controllerWrapper) {
            super(context, powerManagementService, controllerWrapper);
        }

        public long getMaintenanceWindow() {
            return CarSettings.DEFAULT_GARAGE_MODE_MAINTENANCE_WINDOW;
        }

        public boolean isInGarageMode() {
            synchronized (this) {
                return mInGarageMode;
            }
        }

        public boolean isMaintenanceActive() {
            synchronized (this) {
                return mMaintenanceActive;
            }
        }

        public int getGarageModeIndex() {
            synchronized (this) {
                return mGarageModeIndex;
            }
        }
    }

    private static class MockDeviceIdleController extends DeviceIdleControllerWrapper {

        private final boolean mInitialActive;
        public MockDeviceIdleController(boolean active) {
            super();
            mInitialActive = active;
        }

        @Override
        protected boolean startLocked() {
            return mInitialActive;
        }

        @Override
        public void stopTracking() {
            // nothing to clean up
        }

        @Override
        protected void reportActiveLocked(final boolean active) {
            // directly calling the callback instead of posting to handler, to make testing easier.
            if (mListener.get() != null) {
                mListener.get().onMaintenanceActivityChanged(active);
            }
        }

        public void setMaintenanceActivity(boolean active) {
            super.setMaintenanceActivity(active);
        }
    }
}
