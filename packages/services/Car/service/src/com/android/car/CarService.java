/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car;

import static android.os.SystemClock.elapsedRealtime;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IHwBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.RingBufferIndices;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.NoSuchElementException;

public class CarService extends Service {

    private static final long WAIT_FOR_VEHICLE_HAL_TIMEOUT_MS = 10_000;

    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE);

    private CanBusErrorNotifier mCanBusErrorNotifier;
    private ICarImpl mICarImpl;
    private IVehicle mVehicle;

    private String mVehicleInterfaceName;

    // If 10 crashes of Vehicle HAL occurred within 10 minutes then thrown an exception in
    // Car Service.
    private final CrashTracker mVhalCrashTracker = new CrashTracker(
            10,  // Max crash count.
            10 * 60 * 1000,  // 10 minutes - sliding time window.
            () -> {
                if (IS_USER_BUILD) {
                    Log.e(CarLog.TAG_SERVICE, "Vehicle HAL keeps crashing, notifying user...");
                    mCanBusErrorNotifier.reportFailure(CarService.this);
                } else {
                    throw new RuntimeException(
                            "Vehicle HAL crashed too many times in a given time frame");
                }
            }
    );

    private final VehicleDeathRecipient mVehicleDeathRecipient = new VehicleDeathRecipient();

    @Override
    public void onCreate() {
        Log.i(CarLog.TAG_SERVICE, "Service onCreate");
        mCanBusErrorNotifier = new CanBusErrorNotifier(this /* context */);
        mVehicle = getVehicle();

        if (mVehicle == null) {
            throw new IllegalStateException("Vehicle HAL service is not available.");
        }
        try {
            mVehicleInterfaceName = mVehicle.interfaceDescriptor();
        } catch (RemoteException e) {
            throw new IllegalStateException("Unable to get Vehicle HAL interface descriptor", e);
        }

        Log.i(CarLog.TAG_SERVICE, "Connected to " + mVehicleInterfaceName);

        mICarImpl = new ICarImpl(this, mVehicle, SystemInterface.getDefault(this),
                mCanBusErrorNotifier);
        mICarImpl.init();
        SystemProperties.set("boot.car_service_created", "1");

        linkToDeath(mVehicle, mVehicleDeathRecipient);

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.i(CarLog.TAG_SERVICE, "Service onDestroy");
        mICarImpl.release();
        mCanBusErrorNotifier.removeFailureReport(this);

        if (mVehicle != null) {
            try {
                mVehicle.unlinkToDeath(mVehicleDeathRecipient);
                mVehicle = null;
            } catch (RemoteException e) {
                // Ignore errors on shutdown path.
            }
        }

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep it alive.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mICarImpl;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump CarService from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }
        if (args == null || args.length == 0) {
            writer.println("*dump car service*");
            writer.println("Vehicle HAL Interface: " + mVehicleInterfaceName);
            mICarImpl.dump(writer);

            writer.println("**Debug info**");
            writer.println("Vehicle HAL reconnected: "
                    + mVehicleDeathRecipient.deathCount + " times.");
        } else {
            mICarImpl.execShellCmd(args, writer);
        }
    }

    @Nullable
    private IVehicle getVehicleWithTimeout(long waitMilliseconds) {
        IVehicle vehicle = getVehicle();
        long start = elapsedRealtime();
        while (vehicle == null && (start + waitMilliseconds) > elapsedRealtime()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException("Sleep was interrupted", e);
            }

            vehicle = getVehicle();
        }

        if (vehicle != null) {
            mCanBusErrorNotifier.removeFailureReport(this);
        }

        return vehicle;
    }

    @Nullable
    private static IVehicle getVehicle() {
        try {
            return android.hardware.automotive.vehicle.V2_0.IVehicle.getService();
        } catch (RemoteException e) {
            Log.e(CarLog.TAG_SERVICE, "Failed to get IVehicle service", e);
        } catch (NoSuchElementException e) {
            Log.e(CarLog.TAG_SERVICE, "IVehicle service not registered yet");
        }
        return null;
    }

    private class VehicleDeathRecipient implements DeathRecipient {
        private int deathCount = 0;

        @Override
        public void serviceDied(long cookie) {
            Log.w(CarLog.TAG_SERVICE, "Vehicle HAL died.");

            try {
                mVehicle.unlinkToDeath(this);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_SERVICE, "Failed to unlinkToDeath", e);  // Log and continue.
            }
            mVehicle = null;

            mVhalCrashTracker.crashDetected();

            Log.i(CarLog.TAG_SERVICE, "Trying to reconnect to Vehicle HAL: " +
                    mVehicleInterfaceName);
            mVehicle = getVehicleWithTimeout(WAIT_FOR_VEHICLE_HAL_TIMEOUT_MS);
            if (mVehicle == null) {
                throw new IllegalStateException("Failed to reconnect to Vehicle HAL");
            }

            linkToDeath(mVehicle, this);

            Log.i(CarLog.TAG_SERVICE, "Notifying car service Vehicle HAL reconnected...");
            mICarImpl.vehicleHalReconnected(mVehicle);
        }
    }

    private static void linkToDeath(IVehicle vehicle, DeathRecipient recipient) {
        try {
            vehicle.linkToDeath(recipient, 0);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to linkToDeath Vehicle HAL");
        }
    }

    @VisibleForTesting
    static class CrashTracker {
        private final int mMaxCrashCountLimit;
        private final int mSlidingWindowMillis;

        private final long[] mCrashTimestamps;
        private final RingBufferIndices mCrashTimestampsIndices;
        private final Runnable mCallback;

        /**
         * If maxCrashCountLimit number of crashes occurred within slidingWindowMillis time
         * frame then call provided callback function.
         */
        CrashTracker(int maxCrashCountLimit, int slidingWindowMillis, Runnable callback) {
            mMaxCrashCountLimit = maxCrashCountLimit;
            mSlidingWindowMillis = slidingWindowMillis;
            mCallback = callback;

            mCrashTimestamps = new long[maxCrashCountLimit];
            mCrashTimestampsIndices = new RingBufferIndices(mMaxCrashCountLimit);
        }

        void crashDetected() {
            long lastCrash = SystemClock.elapsedRealtime();
            mCrashTimestamps[mCrashTimestampsIndices.add()] = lastCrash;

            if (mCrashTimestampsIndices.size() == mMaxCrashCountLimit) {
                long firstCrash = mCrashTimestamps[mCrashTimestampsIndices.indexOf(0)];

                if (lastCrash - firstCrash < mSlidingWindowMillis) {
                    mCallback.run();
                }
            }
        }
    }
}
