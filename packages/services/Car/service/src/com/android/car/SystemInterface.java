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

package com.android.car;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

/**
 * Interface to abstract all system interaction.
 */
public abstract class SystemInterface {
    public abstract void setDisplayState(boolean on);
    public abstract void releaseAllWakeLocks();
    public abstract void shutdown();
    public abstract void enterDeepSleep(int wakeupTimeSec);
    public abstract void switchToPartialWakeLock();
    public abstract void switchToFullWakeLock();
    public abstract void startDisplayStateMonitoring(CarPowerManagementService service);
    public abstract void stopDisplayStateMonitoring();
    public abstract boolean isSystemSupportingDeepSleep();
    public abstract boolean isWakeupCausedByTimer();


    public static SystemInterface getDefault(Context context) {
        return new SystemInterfaceImpl(context);
    }

    private static class SystemInterfaceImpl extends SystemInterface {
        private final PowerManager mPowerManager;
        private final DisplayManager mDisplayManager;
        private final WakeLock mFullWakeLock;
        private final WakeLock mPartialWakeLock;
        private final DisplayStateListener mDisplayListener;
        private CarPowerManagementService mService;
        private boolean mDisplayStateSet;

        private SystemInterfaceImpl(Context context) {
            mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            mFullWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                    CarLog.TAG_POWER);
            mPartialWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    CarLog.TAG_POWER);
            mDisplayListener = new DisplayStateListener();
        }

        @Override
        public void startDisplayStateMonitoring(CarPowerManagementService service) {
            synchronized (this) {
                mService = service;
                mDisplayStateSet = isMainDisplayOn();
            }
            mDisplayManager.registerDisplayListener(mDisplayListener, service.getHandler());
        }

        @Override
        public void stopDisplayStateMonitoring() {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }

        @Override
        public void setDisplayState(boolean on) {
            synchronized (this) {
                mDisplayStateSet = on;
            }
            if (on) {
                switchToFullWakeLock();
                Log.i(CarLog.TAG_POWER, "on display");
                mPowerManager.wakeUp(SystemClock.uptimeMillis());
            } else {
                switchToPartialWakeLock();
                Log.i(CarLog.TAG_POWER, "off display");
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
        }

        private boolean isMainDisplayOn() {
            Display disp = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            return disp.getState() == Display.STATE_ON;
        }

        @Override
        public void shutdown() {
            mPowerManager.shutdown(false /* no confirm*/, null, true /* true */);
        }

        @Override
        public void enterDeepSleep(int wakeupTimeSec) {
            //TODO set wake up time, bug: 32061842
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            //TODO should return by checking some kernel suspend control sysfs, bug: 32061842
            return false;
        }

        @Override
        public void switchToPartialWakeLock() {
            if (!mPartialWakeLock.isHeld()) {
                mPartialWakeLock.acquire();
            }
            if (mFullWakeLock.isHeld()) {
                mFullWakeLock.release();
            }
        }

        @Override
        public void switchToFullWakeLock() {
            if (!mFullWakeLock.isHeld()) {
                mFullWakeLock.acquire();
            }
            if (mPartialWakeLock.isHeld()) {
                mPartialWakeLock.release();
            }
        }

        @Override
        public void releaseAllWakeLocks() {
            if (mPartialWakeLock.isHeld()) {
                mPartialWakeLock.release();
            }
            if (mFullWakeLock.isHeld()) {
                mFullWakeLock.release();
            }
        }

        @Override
        public boolean isWakeupCausedByTimer() {
            //TODO bug: 32061842, check wake up reason and do necessary operation information should
            // come from kernel. it can be either power on or wake up for maintenance
            // power on will involve GPIO trigger from power controller
            // its own wakeup will involve timer expiration.
            return false;
        }

        private void handleMainDisplayChanged() {
            boolean isOn = isMainDisplayOn();
            CarPowerManagementService service;
            synchronized (this) {
                if (mDisplayStateSet == isOn) { // same as what is set
                    return;
                }
                service = mService;
            }
            service.handleMainDisplayChanged(isOn);
        }

        private class DisplayStateListener implements DisplayManager.DisplayListener {

            @Override
            public void onDisplayAdded(int displayId) {
                //ignore
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    handleMainDisplayChanged();
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                //ignore
            }
        }
    }
}
