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

/**
 * Base class for objects that provide pose data to the app.
 */
public abstract class PoseProvider {
    protected Context mContext;
    protected PoseProviderListener mPoseProviderListener;

    protected PoseData mLatestPoseData;
    protected Intrinsics mIntrinsics;

    public static final Object POSE_LOCK = new Object();

    public interface PoseProviderListener {
        void onSetupComplete();

        void onNewPoseData(PoseData newPoseData);
    }

    public PoseProvider(Context context, PoseProviderListener listener) {
        mContext = context;
        mPoseProviderListener = listener;
    }

    public abstract void onStartPoseProviding();

    public abstract void onStopPoseProviding();

    public abstract void setup();

    protected void onNewPoseData(PoseData newPoseData){
        if (mPoseProviderListener != null) {
            mPoseProviderListener.onNewPoseData(newPoseData);
        }
    }

    public PoseData getLatestPoseData() {
        synchronized (POSE_LOCK) {
            return mLatestPoseData;
        }
    }

    public Intrinsics getIntrinsics() {
        return mIntrinsics;
    }
}
