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

import android.annotation.IntDef;
import android.app.UiModeManager;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.ICarSensorEventListener;
import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;


public class CarNightService implements CarServiceBase {

    public static final boolean DBG = false;

    @IntDef({FORCED_SENSOR_MODE, FORCED_DAY_MODE, FORCED_NIGHT_MODE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DayNightSensorMode {}

    public static final int FORCED_SENSOR_MODE = 0;
    public static final int FORCED_DAY_MODE = 1;
    public static final int FORCED_NIGHT_MODE = 2;

    private int mNightSetting = UiModeManager.MODE_NIGHT_YES;
    private int mForcedMode = FORCED_SENSOR_MODE;
    private final Context mContext;
    private final UiModeManager mUiModeManager;
    private CarSensorService mCarSensorService;

    private final ICarSensorEventListener mICarSensorEventListener =
            new ICarSensorEventListener.Stub() {
        @Override
        public void onSensorChanged(List<CarSensorEvent> events) {
            if (!events.isEmpty()) {
                CarSensorEvent event = events.get(events.size() - 1);
                handleSensorEvent(event);
            }
        }
    };

    public synchronized void handleSensorEvent(CarSensorEvent event) {
        if (event == null) {
            return;
        }
        if (event.sensorType == CarSensorManager.SENSOR_TYPE_NIGHT) {
            if (event.intValues[0] == 1) {
                mNightSetting = UiModeManager.MODE_NIGHT_YES;
            }
            else {
                mNightSetting = UiModeManager.MODE_NIGHT_NO;
            }
            if (mUiModeManager != null && (mForcedMode == FORCED_SENSOR_MODE)) {
                mUiModeManager.setNightMode(mNightSetting);
            }
        }
    }

    public synchronized int forceDayNightMode(@DayNightSensorMode int mode) {
        if (mUiModeManager == null) {
            return -1;
        }
        int resultMode;
        switch (mode) {
            case FORCED_SENSOR_MODE:
                resultMode = mNightSetting;
                mForcedMode = FORCED_SENSOR_MODE;
                break;
            case FORCED_DAY_MODE:
                resultMode = UiModeManager.MODE_NIGHT_NO;
                mForcedMode = FORCED_DAY_MODE;
                break;
            case FORCED_NIGHT_MODE:
                resultMode = UiModeManager.MODE_NIGHT_YES;
                mForcedMode = FORCED_NIGHT_MODE;
                break;
            default:
                Log.e(CarLog.TAG_SENSOR, "Unknown forced day/night mode " + mode);
                return -1;
        }
        mUiModeManager.setNightMode(resultMode);
        return mUiModeManager.getNightMode();
    }

    CarNightService(Context context, CarSensorService sensorService) {
        mContext = context;
        mCarSensorService = sensorService;
        mUiModeManager = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        if (mUiModeManager == null) {
            Log.w(CarLog.TAG_SENSOR,"Failed to get UI_MODE_SERVICE");
        }
    }

    @Override
    public synchronized void init() {
        if (DBG) {
            Log.d(CarLog.TAG_SENSOR,"CAR dayNight init.");
        }
        mCarSensorService.registerOrUpdateSensorListener(CarSensorManager.SENSOR_TYPE_NIGHT,
                CarSensorManager.SENSOR_RATE_NORMAL, mICarSensorEventListener);
        CarSensorEvent currentState = mCarSensorService.getLatestSensorEvent(
                CarSensorManager.SENSOR_TYPE_NIGHT);
        handleSensorEvent(currentState);
    }

    @Override
    public synchronized void release() {
    }

    @Override
    public synchronized void dump(PrintWriter writer) {
        writer.println("*DAY NIGHT POLICY*");
        writer.println("Mode:" + ((mNightSetting == UiModeManager.MODE_NIGHT_YES) ? "night" : "day")
                );
        writer.println("Forced Mode? " + (mForcedMode == FORCED_SENSOR_MODE ? "false"
                : (mForcedMode == FORCED_DAY_MODE ? "day" : "night")));
    }
}
