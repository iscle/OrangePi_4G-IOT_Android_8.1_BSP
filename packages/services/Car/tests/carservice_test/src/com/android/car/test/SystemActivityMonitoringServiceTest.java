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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.automotive.vehicle.V2_0.VehicleDrivingStatus;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.car.SystemActivityMonitoringService;
import com.android.car.SystemActivityMonitoringService.TopTaskInfoContainer;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@MediumTest
public class SystemActivityMonitoringServiceTest extends MockedCarTestBase {
    private static final long TIMEOUT_MS = 3000;
    private static final long POLL_INTERVAL_MS = 50;
    private static final Semaphore sAvailable = new Semaphore(0);

    private final DrivingStatusHandler mDrivingStatusHandler = new DrivingStatusHandler();

    @Override
    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.DRIVING_STATUS, mDrivingStatusHandler)
                .setAccess(VehiclePropertyAccess.READ);
    }

    private void init(boolean drivingStatusRestricted) {
        // Set no restriction to driving status, to avoid CarPackageManagerService to launch a
        // blocking activity.
        mDrivingStatusHandler.setDrivingStatusRestricted(drivingStatusRestricted);

        // Due to asynchronous nature of Car Service initialization, if we won't wait we may inject
        // an event while SensorHalService is not subscribed yet.
        assertTrue(getMockedVehicleHal()
                .waitForSubscriber(VehicleProperty.DRIVING_STATUS, TIMEOUT_MS));

        VehiclePropValue injectValue =
                VehiclePropValueBuilder.newBuilder(VehicleProperty.DRIVING_STATUS)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .addIntValue(0)
                        .build();
        getMockedVehicleHal().injectEvent(injectValue);
    }

    public void testActivityLaunch() {
        init(false);
        List<TopTaskInfoContainer> taskList = new ArrayList<>();
        SystemActivityMonitoringService systemActivityMonitoringService =
                new SystemActivityMonitoringService(getContext());
        systemActivityMonitoringService.registerActivityLaunchListener(
                new SystemActivityMonitoringService.ActivityLaunchListener() {
                    @Override
                    public void onActivityLaunch(
                            SystemActivityMonitoringService.TopTaskInfoContainer topTask) {
                        taskList.add(topTask);
                    }
                });
        getContext().startActivity(new Intent(getContext(), ActivityA.class));
        verifyTopActivityPolling(taskList, 0, new ComponentName(getContext().getPackageName(),
                ActivityA.class.getName()));
        sAvailable.release();

        verifyTopActivityPolling(taskList, 1, new ComponentName(getContext().getPackageName(),
                ActivityB.class.getName()));
        sAvailable.release();

        verifyTopActivityPolling(taskList, 2, new ComponentName(getContext().getPackageName(),
                ActivityC.class.getName()));
    }

    public void testActivityBlocking() {
        init(false);
        Semaphore blocked = new Semaphore(0);
        List<TopTaskInfoContainer> taskList = new ArrayList<>();
        SystemActivityMonitoringService systemActivityMonitoringService =
                new SystemActivityMonitoringService(getContext());

        ComponentName blackListedActivity = new ComponentName(getContext().getPackageName(),
                ActivityC.class.getName());
        ComponentName blockingActivity = new ComponentName(getContext().getPackageName(),
                BlockingActivity.class.getName());
        Intent newActivityIntent = new Intent();
        newActivityIntent.setComponent(blockingActivity);

        systemActivityMonitoringService.registerActivityLaunchListener(
                new SystemActivityMonitoringService.ActivityLaunchListener() {
                    @Override
                    public void onActivityLaunch(
                            SystemActivityMonitoringService.TopTaskInfoContainer topTask) {
                        taskList.add(topTask);
                        if (topTask.topActivity.equals(blackListedActivity)) {
                            systemActivityMonitoringService.blockActivity(topTask,
                                    newActivityIntent);
                            blocked.release();
                        }
                    }
                });
        // start a black listed activity
        getContext().startActivity(new Intent(getContext(), ActivityC.class));
        // wait for the listener to call blockActivity()
        try {
            blocked.tryAcquire(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        // We should first receive the blackListedActivity launch,
        // and later the blockActivity launch
        verifyTopActivityPolling(taskList, 0, blackListedActivity);
        verifyTopActivityPolling(taskList, 1, blockingActivity);
    }

    private void verifyTopActivityPolling(
            List<TopTaskInfoContainer> topTaskList, int i, ComponentName activity) {
        boolean activityVerified = false;
        int timeElapsedMs = 0;
        try {
            while (!activityVerified && timeElapsedMs <= TIMEOUT_MS) {
                Thread.sleep(POLL_INTERVAL_MS);
                timeElapsedMs += POLL_INTERVAL_MS;
                if (topTaskList.size() <= i) continue;
                TopTaskInfoContainer topTask = topTaskList.get(i);
                if (topTask != null && topTask.topActivity.equals(activity)) {
                    activityVerified = true;
                    break;
                }
            }
            assertEquals(true, activityVerified);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    public static class ActivityA extends Activity {
        @Override
        protected void onPostResume() {
            super.onPostResume();
            // Wait until the activity launch event is consumed by the listener.
            try {
                if (!sAvailable.tryAcquire(2, TimeUnit.SECONDS)) {
                    fail("Time out");
                }
            } catch (Exception e) {
                fail(e.toString());
            }
            Intent intent = new Intent(this, ActivityB.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    public static class ActivityB extends Activity {
        @Override
        protected void onPostResume() {
            super.onPostResume();
            // Wait until the activity launch event is consumed by the listener.
            try {
                if (!sAvailable.tryAcquire(2, TimeUnit.SECONDS)) {
                    fail("Time out");
                }
            } catch (Exception e) {
                fail(e.toString());
            }
            Intent intent = new Intent(this, ActivityC.class);
            startActivity(intent);
        }
    }

    public static class ActivityC extends Activity {
    }

    public static class BlockingActivity extends Activity {
    }

    private class DrivingStatusHandler implements VehicleHalPropertyHandler {
        int mDrivingStatus = VehicleDrivingStatus.UNRESTRICTED;

        public void setDrivingStatusRestricted(boolean restricted) {
            mDrivingStatus = restricted ? VehicleDrivingStatus.NO_VIDEO
                    : VehicleDrivingStatus.UNRESTRICTED;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.DRIVING_STATUS)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(mDrivingStatus)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, int zones, float sampleRate) {
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
        }
    }
}