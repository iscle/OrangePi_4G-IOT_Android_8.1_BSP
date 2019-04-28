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

import android.car.CarApiUtil;
import android.car.settings.CarSettings;
import android.car.settings.GarageModeSettingsObserver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.IMaintenanceActivityListener;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Calendar;

import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_ENABLED_URI;
import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_MAINTENANCE_WINDOW_URI;
import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_WAKE_UP_TIME_URI;

/**
 * Controls car garage mode.
 *
 * Car garage mode is a time window for the car to do maintenance work when the car is not in use.
 * The {@link com.android.car.GarageModeService} interacts with {@link com.android.car.CarPowerManagementService}
 * to start and end garage mode. A {@link com.android.car.GarageModeService.GarageModePolicy} defines
 * when the garage mode should start and how long it should last.
 */
public class GarageModeService implements CarServiceBase,
        CarPowerManagementService.PowerEventProcessingHandler,
        CarPowerManagementService.PowerServiceEventListener,
        DeviceIdleControllerWrapper.DeviceMaintenanceActivityListener {
    private static String TAG = "GarageModeService";
    private static final boolean DBG = false;

    private static final int MSG_EXIT_GARAGE_MODE_EARLY = 0;
    private static final int MSG_WRITE_TO_PREF = 1;

    private static final String KEY_GARAGE_MODE_INDEX = "garage_mode_index";

    // wait for 10 seconds to allow maintenance activities to start (e.g., connecting to wifi).
    protected static final int MAINTENANCE_ACTIVITY_START_GRACE_PERIOUD = 10 * 1000;

    private final CarPowerManagementService mPowerManagementService;
    protected final Context mContext;

    @VisibleForTesting
    @GuardedBy("this")
    protected boolean mInGarageMode;
    @VisibleForTesting
    @GuardedBy("this")
    protected boolean mMaintenanceActive;
    @VisibleForTesting
    @GuardedBy("this")
    protected int mGarageModeIndex;

    @GuardedBy("this")
    @VisibleForTesting
    protected int mMaintenanceWindow;
    @GuardedBy("this")
    private GarageModePolicy mPolicy;
    @GuardedBy("this")
    private int mWakeUpHour = 0;
    @GuardedBy("this")
    private int mWakeUpMin = 0;
    @GuardedBy("this")
    private boolean mGarageModeEnabled;


    private SharedPreferences mSharedPreferences;
    private final GarageModeSettingsObserver mContentObserver;

    private DeviceIdleControllerWrapper mDeviceIdleController;
    private final GarageModeHandler mHandler = new GarageModeHandler();

    private class GarageModeHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_EXIT_GARAGE_MODE_EARLY:
                    mPowerManagementService.notifyPowerEventProcessingCompletion(
                            GarageModeService.this);
                    break;
                case MSG_WRITE_TO_PREF:
                    writeToPref(msg.arg1);
                    break;
            }
        }
    }

    public GarageModeService(Context context, CarPowerManagementService powerManagementService) {
        this(context, powerManagementService, null);
    }

    @VisibleForTesting
    protected GarageModeService(Context context, CarPowerManagementService powerManagementService,
            DeviceIdleControllerWrapper deviceIdleController) {
        mContext = context;
        mPowerManagementService = powerManagementService;
        if (deviceIdleController == null) {
            mDeviceIdleController = new DefaultDeviceIdleController();
        } else {
            mDeviceIdleController = deviceIdleController;
        }
        mContentObserver = new GarageModeSettingsObserver(mContext, mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onSettingsChangedInternal(uri);
            }
        };
    }

    @Override
    public void init() {
        logd("init GarageMode");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                mContext.createDeviceProtectedStorageContext());
        final int index = mSharedPreferences.getInt(KEY_GARAGE_MODE_INDEX, 0);
        synchronized (this) {
            mMaintenanceActive = mDeviceIdleController.startTracking(this);
            mGarageModeIndex = index;
            readPolicyLocked();
            readFromSettingsLocked(CarSettings.Global.KEY_GARAGE_MODE_MAINTENANCE_WINDOW,
                    CarSettings.Global.KEY_GARAGE_MODE_ENABLED,
                    CarSettings.Global.KEY_GARAGE_MODE_WAKE_UP_TIME);
        }
        mContentObserver.register();
        mPowerManagementService.registerPowerEventProcessingHandler(this);
    }

    @Override
    public void release() {
        logd("release GarageModeService");
        mDeviceIdleController.stopTracking();
        mContentObserver.unregister();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("mGarageModeIndex: " + mGarageModeIndex);
        writer.println("inGarageMode? " + mInGarageMode);
        writer.println("GarageModeTime: " + mWakeUpHour + ":" + mWakeUpMin);
        writer.println("GarageModeEnabled " + mGarageModeEnabled);
        writer.println("GarageModeTimeWindow " + mMaintenanceWindow + " ms");
    }

    @Override
    public long onPrepareShutdown(boolean shuttingDown) {
        // this is the beginning of each garage mode.
        synchronized (this) {
            logd("onPrePowerEvent " + shuttingDown);
            mInGarageMode = true;
            mGarageModeIndex++;
            mHandler.removeMessages(MSG_EXIT_GARAGE_MODE_EARLY);
            if (!mMaintenanceActive) {
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_EXIT_GARAGE_MODE_EARLY),
                        MAINTENANCE_ACTIVITY_START_GRACE_PERIOUD);
            }
            // We always reserve the maintenance window first. If later, we found no
            // maintenance work active, we will exit garage mode early after
            // MAINTENANCE_ACTIVITY_START_GRACE_PERIOUD
            return mMaintenanceWindow;
        }
    }

    @Override
    public void onPowerOn(boolean displayOn) {
        synchronized (this) {
            logd("onPowerOn: " + displayOn);
            if (displayOn) {
                // the car is use now. reset the garage mode counter.
                mGarageModeIndex = 0;
            }
        }
    }

    @Override
    public int getWakeupTime() {
        synchronized (this) {
            if (!mGarageModeEnabled) {
                return 0;
            }
            return mPolicy.getNextWakeUpTime(mGarageModeIndex, mWakeUpHour, mWakeUpMin);
        }
    }

    @Override
    public void onSleepExit() {
        // ignored
    }

    @Override
    public void onSleepEntry() {
        synchronized (this) {
            mInGarageMode = false;
        }
    }

    @Override
    public void onShutdown() {
        synchronized (this) {
            mHandler.sendMessage(
                    mHandler.obtainMessage(MSG_WRITE_TO_PREF, mGarageModeIndex, 0));
        }
    }

    private void readPolicyLocked() {
        logd("readPolicy");
        // TODO: define a xml schema for policy and read it from system dir. bug: 32096969
        mPolicy = new DefaultGarageModePolicy();
    }

    private void writeToPref(int index) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putInt(KEY_GARAGE_MODE_INDEX, index);
        editor.commit();
    }

    @Override
    public void onMaintenanceActivityChanged(boolean active) {
        boolean shouldReportCompletion = false;
        synchronized (this) {
            logd("onMaintenanceActivityChanged: " + active);
            mMaintenanceActive = active;
            if (!mInGarageMode) {
                return;
            }

            if (!active) {
                shouldReportCompletion = true;
                mInGarageMode = false;
            } else {
                // we are in garage mode, and maintenance work has just begun.
                mHandler.removeMessages(MSG_EXIT_GARAGE_MODE_EARLY);
            }
        }
        if (shouldReportCompletion) {
            // we are in garage mode, and maintenance work has finished.
            mPowerManagementService.notifyPowerEventProcessingCompletion(this);
        }
    }

    public abstract static class GarageModePolicy {
        abstract public int getNextWakeUpTime(int index, int hour, int min);
        /**
         * Returns number of seconds between now to 1am {@param numDays} days later.
         */
        public static int nextWakeUpSeconds(int numDays, int hour, int min) {
            // TODO: Should select a random time within a window. bug: 32096386
            // This is to avoid all cars update at the same time.
            Calendar next = Calendar.getInstance();
            next.add(Calendar.DATE, numDays);
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, min);
            next.set(Calendar.SECOND, 0);

            Calendar now = Calendar.getInstance();
            return (next.get(Calendar.MILLISECOND) - now.get(Calendar.MILLISECOND)) / 1000;
        }
    }

    /**
     * Default garage mode policy.
     *
     * The first wake up time is set to be 1am the next day. And it keeps waking up every day for a
     * week. After that, wake up every 7 days for a month, and wake up every 30 days thereafter.
     */
    private static class DefaultGarageModePolicy extends GarageModePolicy {
        private static final int COL_INDEX = 0;
        private static final int COL_WAKEUP_TIME = 1;

        private static final int[][] WAKE_UP_TIME = new int[][] {
                {7 /*index <= 7*/, 1 /* wake up the next day */},
                {11 /* 7 < index <= 11 */, 7 /* wake up the next week */},
                {Integer.MAX_VALUE /* index > 11 */, 30 /* wake up the next month */}
        };

        @Override
        public int getNextWakeUpTime(int index, int hour, int min) {
            for (int i = 0; i < WAKE_UP_TIME.length; i++) {
                if (index <= WAKE_UP_TIME[i][COL_INDEX]) {
                    return nextWakeUpSeconds(WAKE_UP_TIME[i][COL_WAKEUP_TIME], hour, min);
                }
            }

            Log.w(TAG, "Integer.MAX number of wake ups... How long have we been sleeping? ");
            return 0;
        }
    }

    private static class DefaultDeviceIdleController extends DeviceIdleControllerWrapper {
        private IDeviceIdleController mDeviceIdleController;
        private MaintenanceActivityListener mMaintenanceActivityListener
                = new MaintenanceActivityListener();

        @Override
        public boolean startLocked() {
            mDeviceIdleController = IDeviceIdleController.Stub.asInterface(
                    ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
            boolean active = false;
            try {
                active = mDeviceIdleController
                        .registerMaintenanceActivityListener(mMaintenanceActivityListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register listener with DeviceIdleController", e);
            }
            return active;
        }

        @Override
        public void stopTracking() {
            try {
                if (mDeviceIdleController != null) {
                    mDeviceIdleController.unregisterMaintenanceActivityListener(
                            mMaintenanceActivityListener);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Fail to unregister listener.", e);
            }
        }

        private final class MaintenanceActivityListener extends IMaintenanceActivityListener.Stub {
            @Override
            public void onMaintenanceActivityChanged(final boolean active) {
                DefaultDeviceIdleController.this.setMaintenanceActivity(active);
            }
        }
    }

    private void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    private void readFromSettingsLocked(String... keys) {
        for (String key : keys) {
            switch (key) {
                case CarSettings.Global.KEY_GARAGE_MODE_ENABLED:
                    mGarageModeEnabled =
                            Settings.Global.getInt(mContext.getContentResolver(), key, 1) == 1;
                    break;
                case CarSettings.Global.KEY_GARAGE_MODE_WAKE_UP_TIME:
                    int time[] = CarApiUtil.decodeGarageTimeSetting(
                            Settings.Global.getString(mContext.getContentResolver(),
                                    CarSettings.Global.KEY_GARAGE_MODE_WAKE_UP_TIME));
                    mWakeUpHour = time[0];
                    mWakeUpMin = time[1];
                    break;
                case CarSettings.Global.KEY_GARAGE_MODE_MAINTENANCE_WINDOW:
                    mMaintenanceWindow = Settings.Global.getInt(
                            mContext.getContentResolver(), key,
                            CarSettings.DEFAULT_GARAGE_MODE_MAINTENANCE_WINDOW);
                    break;
                default:
                    Log.e(TAG, "Unknown setting key " + key);
            }
        }
    }

    private void onSettingsChangedInternal(Uri uri) {
        synchronized (this) {
            logd("Content Observer onChange: " + uri);
            if (uri.equals(GARAGE_MODE_ENABLED_URI)) {
                readFromSettingsLocked(CarSettings.Global.KEY_GARAGE_MODE_ENABLED);
            } else if (uri.equals(GARAGE_MODE_WAKE_UP_TIME_URI)) {
                readFromSettingsLocked(CarSettings.Global.KEY_GARAGE_MODE_WAKE_UP_TIME);
            } else if (uri.equals(GARAGE_MODE_MAINTENANCE_WINDOW_URI)) {
                readFromSettingsLocked(CarSettings.Global.KEY_GARAGE_MODE_MAINTENANCE_WINDOW);
            }
            logd(String.format(
                    "onSettingsChanged %s. enabled: %s, wakeUpTime: %d:%d, windowSize: %d",
                    uri, mGarageModeEnabled, mWakeUpHour, mWakeUpMin, mMaintenanceWindow));
        }
    }
}
