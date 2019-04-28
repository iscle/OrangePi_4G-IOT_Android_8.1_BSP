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
package com.android.car.hal;

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.HW_KEY_INPUT;

import android.hardware.automotive.vehicle.V2_0.VehicleDisplay;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseLongArray;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.android.car.CarLog;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class InputHalService extends HalServiceBase {

    public static final int DISPLAY_MAIN = VehicleDisplay.MAIN;
    public static final int DISPLAY_INSTRUMENT_CLUSTER = VehicleDisplay.INSTRUMENT_CLUSTER;
    private final VehicleHal mHal;

    public interface InputListener {
        void onKeyEvent(KeyEvent event, int targetDisplay);
    }

    private static final boolean DBG = false;

    private boolean mKeyInputSupported = false;
    private InputListener mListener;
    private final SparseLongArray mKeyDownTimes = new SparseLongArray();

    public InputHalService(VehicleHal hal) {
        mHal = hal;
    }

    public void setInputListener(InputListener listener) {
        synchronized (this) {
            if (!mKeyInputSupported) {
                Log.w(CarLog.TAG_INPUT, "input listener set while key input not supported");
                return;
            }
            mListener = listener;
        }
        mHal.subscribeProperty(this, HW_KEY_INPUT);
    }

    public synchronized boolean isKeyInputSupported() {
        return mKeyInputSupported;
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
        synchronized (this) {
            mListener = null;
            mKeyInputSupported = false;
        }
    }

    @Override
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> supported = new LinkedList<>();
        for (VehiclePropConfig p: allProperties) {
            if (p.prop == HW_KEY_INPUT) {
                supported.add(p);
                synchronized (this) {
                    mKeyInputSupported = true;
                }
            }
        }
        return supported;
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        InputListener listener;
        synchronized (this) {
            listener = mListener;
        }
        if (listener == null) {
            Log.w(CarLog.TAG_INPUT, "Input event while listener is null");
            return;
        }
        for (VehiclePropValue v : values) {
            if (v.prop != HW_KEY_INPUT) {
                Log.e(CarLog.TAG_INPUT, "Wrong event dispatched, prop:0x" +
                        Integer.toHexString(v.prop));
                continue;
            }
            int action = (v.value.int32Values.get(0) == VehicleHwKeyInputAction.ACTION_DOWN) ?
                            KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            int code = v.value.int32Values.get(1);
            int display = v.value.int32Values.get(2);
            if (DBG) {
                Log.i(CarLog.TAG_INPUT, "hal event code:" + code + ", action:" + action +
                        ", display:" + display);
            }

            dispatchKeyEvent(listener, action, code, display);
        }
    }

    private void dispatchKeyEvent(InputListener listener, int action, int code, int display) {
        long eventTime = SystemClock.uptimeMillis();

        if (action == KeyEvent.ACTION_DOWN) {
            mKeyDownTimes.put(code, eventTime);
        }

        long downTime = action == KeyEvent.ACTION_UP
                ? mKeyDownTimes.get(code, eventTime) : eventTime;

        KeyEvent event = KeyEvent.obtain(
                downTime,
                eventTime,
                action,
                code,
                0 /* repeat */,
                0 /* meta state */,
                0 /* deviceId*/,
                0 /* scancode */,
                0 /* flags */,
                InputDevice.SOURCE_CLASS_BUTTON,
                null /* characters */);

        listener.onKeyEvent(event, display);
        event.recycle();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Input HAL*");
        writer.println("mKeyInputSupported:" + mKeyInputSupported);
    }

}
