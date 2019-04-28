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
package com.android.car.monitoring;

import android.annotation.SystemApi;
import android.content.Context;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.SystemActivityMonitoringService;

import java.io.PrintWriter;

/**
 * Service that monitors applications resource usage.
 * @hide
 */
@SystemApi
public class CarMonitoringService implements CarServiceBase {
    private static final String TAG = CarLog.TAG_MONITORING;
    private static final Boolean DBG = true;

    private static final int MONITORING_SLEEP_TIME_MS = 30000; // Run monitoring every 30s.

    private final Context mContext;

    private final SystemActivityMonitoringService mSystemActivityMonitoringService;

    public CarMonitoringService(Context context,
            SystemActivityMonitoringService systemActivityMonitoringService) {
        mContext = context;
        mSystemActivityMonitoringService = systemActivityMonitoringService;
    }

    @Override
    public void init() {
        if (DBG) {
            Log.d(TAG, "init");
        }
        // TODO: add periodic update to setAppPriority to monitoring native service.
    }

    @Override
    public void release() {
        if (DBG) {
            Log.d(TAG, "release");
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        // TODO
    }
}
