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
package com.android.car.hal;

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AP_POWER_STATE;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.DISPLAY_BRIGHTNESS;

import android.annotation.Nullable;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerSetState;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerState;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.util.Log;

import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class PowerHalService extends HalServiceBase {

    public static final int STATE_OFF = VehicleApPowerState.OFF;
    public static final int STATE_DEEP_SLEEP = VehicleApPowerState.DEEP_SLEEP;
    public static final int STATE_ON_DISP_OFF = VehicleApPowerState.ON_DISP_OFF;
    public static final int STATE_ON_FULL = VehicleApPowerState.ON_FULL;
    public static final int STATE_SHUTDOWN_PREPARE = VehicleApPowerState.SHUTDOWN_PREPARE;

    @VisibleForTesting
    public static final int SET_BOOT_COMPLETE = VehicleApPowerSetState.BOOT_COMPLETE;
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_ENTRY = VehicleApPowerSetState.DEEP_SLEEP_ENTRY;
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_EXIT = VehicleApPowerSetState.DEEP_SLEEP_EXIT;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_POSTPONE = VehicleApPowerSetState.SHUTDOWN_POSTPONE;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_START = VehicleApPowerSetState.SHUTDOWN_START;
    @VisibleForTesting
    public static final int SET_DISPLAY_ON = VehicleApPowerSetState.DISPLAY_ON;
    @VisibleForTesting
    public static final int SET_DISPLAY_OFF =
            VehicleApPowerSetState.DISPLAY_OFF;

    @VisibleForTesting
    public static final int FLAG_SHUTDOWN_PARAM_CAN_SLEEP =
            VehicleApPowerStateShutdownParam.CAN_SLEEP;
    @VisibleForTesting
    public static final int FLAG_SHUTDOWN_IMMEDIATELY =
            VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY;

    public interface PowerEventListener {
        /**
         * Received power state change event.
         * @param state One of STATE_*
         */
        void onApPowerStateChange(PowerState state);
        /**
         * Received display brightness change event.
         * @param brightness in percentile. 100% full.
         */
        void onDisplayBrightnessChange(int brightness);
    }

    public static final class PowerState {
        /**
         * One of STATE_*
         */
        public final int mState;
        public final int mParam;

        public PowerState(int state, int param) {
            this.mState = state;
            this.mParam = param;
        }

        /**
         * Whether the current PowerState allows deep sleep or not. Calling this for
         * power state other than STATE_SHUTDOWN_PREPARE will trigger exception.
         * @return
         * @throws IllegalStateException
         */
        public boolean canEnterDeepSleep() {
            if (mState != STATE_SHUTDOWN_PREPARE) {
                throw new IllegalStateException("wrong state");
            }
            return (mParam & VehicleApPowerStateShutdownParam.CAN_SLEEP) != 0;
        }

        /**
         * Whether the current PowerState allows postponing or not. Calling this for
         * power state other than STATE_SHUTDOWN_PREPARE will trigger exception.
         * @return
         * @throws IllegalStateException
         */
        public boolean canPostponeShutdown() {
            if (mState != STATE_SHUTDOWN_PREPARE) {
                throw new IllegalStateException("wrong state");
            }
            return (mParam & VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY) == 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PowerState)) {
                return false;
            }
            PowerState that = (PowerState) o;
            return this.mState == that.mState && this.mParam == that.mParam;
        }

        @Override
        public String toString() {
            return "PowerState state:" + mState + ", param:" + mParam;
        }
    }

    private final HashMap<Integer, VehiclePropConfig> mProperties = new HashMap<>();
    private final VehicleHal mHal;
    private LinkedList<VehiclePropValue> mQueuedEvents;
    private PowerEventListener mListener;
    private int mMaxDisplayBrightness;

    public PowerHalService(VehicleHal hal) {
        mHal = hal;
    }

    public void setListener(PowerEventListener listener) {
        LinkedList<VehiclePropValue> eventsToDispatch = null;
        synchronized (this) {
            mListener = listener;
            if (mQueuedEvents != null && mQueuedEvents.size() > 0) {
                eventsToDispatch = mQueuedEvents;
            }
            mQueuedEvents = null;
        }
        // do this outside lock
        if (eventsToDispatch != null) {
            dispatchEvents(eventsToDispatch, listener);
        }
    }

    public void sendBootComplete() {
        Log.i(CarLog.TAG_POWER, "send boot complete");
        setPowerState(VehicleApPowerSetState.BOOT_COMPLETE, 0);
    }

    public void sendSleepEntry() {
        Log.i(CarLog.TAG_POWER, "send sleep entry");
        setPowerState(VehicleApPowerSetState.DEEP_SLEEP_ENTRY, 0);
    }

    public void sendSleepExit() {
        Log.i(CarLog.TAG_POWER, "send sleep exit");
        setPowerState(VehicleApPowerSetState.DEEP_SLEEP_EXIT, 0);
    }

    public void sendShutdownPostpone(int postponeTimeMs) {
        Log.i(CarLog.TAG_POWER, "send shutdown postpone, time:" + postponeTimeMs);
        setPowerState(VehicleApPowerSetState.SHUTDOWN_POSTPONE,
                postponeTimeMs);
    }

    public void sendShutdownStart(int wakeupTimeSec) {
        Log.i(CarLog.TAG_POWER, "send shutdown start");
        setPowerState(VehicleApPowerSetState.SHUTDOWN_START, 0);
    }

    public void sendDisplayOn() {
        Log.i(CarLog.TAG_POWER, "send display on");
        setPowerState(VehicleApPowerSetState.DISPLAY_ON, 0);
    }

    public void sendDisplayOff() {
        Log.i(CarLog.TAG_POWER, "send display off");
        setPowerState(VehicleApPowerSetState.DISPLAY_OFF, 0);
    }

    private void setPowerState(int state, int additionalParam) {
        int[] values = { state, additionalParam };
        try {
            mHal.set(VehicleProperty.AP_POWER_STATE).to(values);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_POWER, "cannot set to AP_POWER_STATE", e);
        }
    }

    @Nullable
    public PowerState getCurrentPowerState() {
        int[] state;
        try {
            state = mHal.get(int[].class, VehicleProperty.AP_POWER_STATE);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_POWER, "Cannot get AP_POWER_STATE", e);
            return null;
        }
        return new PowerState(state[VehicleApPowerStateIndex.STATE],
                state[VehicleApPowerStateIndex.ADDITIONAL]);
    }

    public synchronized boolean isPowerStateSupported() {
        VehiclePropConfig config = mProperties.get(VehicleProperty.AP_POWER_STATE);
        return config != null;
    }

    public synchronized boolean isDeepSleepAllowed() {
        VehiclePropConfig config = mProperties.get(VehicleProperty.AP_POWER_STATE);
        if (config == null) {
            return false;
        }
        return (config.configArray.get(0)
                & VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG) != 0;
    }

    public synchronized boolean isTimedWakeupAllowed() {
        VehiclePropConfig config = mProperties.get(
                AP_POWER_STATE);
        if (config == null) {
            return false;
        }
        return (config.configArray.get(0)
                & VehicleApPowerStateConfigFlag.CONFIG_SUPPORT_TIMER_POWER_ON_FLAG) != 0;
    }

    @Override
    public synchronized void init() {
        for (VehiclePropConfig config : mProperties.values()) {
            if (VehicleHal.isPropertySubscribable(config)) {
                mHal.subscribeProperty(this, config.prop);
            }
        }
        VehiclePropConfig brightnessProperty = mProperties.get(DISPLAY_BRIGHTNESS);
        if (brightnessProperty != null) {
            mMaxDisplayBrightness = brightnessProperty.areaConfigs.size() > 0
                    ? brightnessProperty.areaConfigs.get(0).maxInt32Value : 0;
            if (mMaxDisplayBrightness <= 0) {
                Log.w(CarLog.TAG_POWER, "Max display brightness from vehicle HAL is invalid:" +
                        mMaxDisplayBrightness);
                mMaxDisplayBrightness = 1;
            }
        }
    }

    @Override
    public synchronized void release() {
        mProperties.clear();
    }

    @Override
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        for (VehiclePropConfig config : allProperties) {
            switch (config.prop) {
                case AP_POWER_STATE:
                case DISPLAY_BRIGHTNESS:
                    mProperties.put(config.prop, config);
                    break;
            }
        }
        return new LinkedList<>(mProperties.values());
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        PowerEventListener listener;
        synchronized (this) {
            if (mListener == null) {
                if (mQueuedEvents == null) {
                    mQueuedEvents = new LinkedList<>();
                }
                mQueuedEvents.addAll(values);
                return;
            }
            listener = mListener;
        }
        dispatchEvents(values, listener);
    }

    private void dispatchEvents(List<VehiclePropValue> values, PowerEventListener listener) {
        for (VehiclePropValue v : values) {
            switch (v.prop) {
                case AP_POWER_STATE:
                    int state = v.value.int32Values.get(VehicleApPowerStateIndex.STATE);
                    int param = v.value.int32Values.get(VehicleApPowerStateIndex.ADDITIONAL);
                    listener.onApPowerStateChange(new PowerState(state, param));
                    break;
                case DISPLAY_BRIGHTNESS:
                    int maxBrightness;
                    synchronized (this) {
                        maxBrightness = mMaxDisplayBrightness;
                    }
                    listener.onDisplayBrightnessChange(
                            (v.value.int32Values.get(0) * 100) / maxBrightness);
                    break;
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Power HAL*");
        writer.println("isPowerStateSupported:" + isPowerStateSupported() +
                ",isDeepSleepAllowed:" + isDeepSleepAllowed());
    }
}
