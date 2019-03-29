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
package com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Provides pose data using Android Sensors.
 */
public class AndroidPoseProvider extends PoseProvider {
    private static final int SENSOR_TYPE_POSE = 26; //28;
    private SensorManager mSensorManager;
    private Sensor m6DoFSensor;

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            synchronized (POSE_LOCK) {
                mLatestPoseData = new PoseData(event.values, event.timestamp);
            }

            onNewPoseData(mLatestPoseData);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public AndroidPoseProvider(Context context, PoseProviderListener poseListener) {
        super(context, poseListener);
        mIntrinsics = new Intrinsics();
    }

    @Override
    public void onStartPoseProviding() {
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        m6DoFSensor = mSensorManager.getDefaultSensor(SENSOR_TYPE_POSE);
        mSensorManager.registerListener(mSensorListener, m6DoFSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onStopPoseProviding() {
        mSensorManager.unregisterListener(mSensorListener);
    }

    @Override
    public void setup() {
        // Don't need to do anything here.
        mPoseProviderListener.onSetupComplete();
    }
}
