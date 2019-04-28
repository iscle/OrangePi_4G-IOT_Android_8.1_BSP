/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.car.cluster.renderer;

import android.graphics.Rect;
import android.os.Bundle;

/**
 * This interface defines the communication channel between the cluster vendor implementation and
 * Car Service.
 *
 * @hide
 */
interface IInstrumentClusterCallback {
    /**
     * Notify Car Service how to launch an activity for particular category.
     *
     * @param category cluster activity category,
     *        see {@link android.car.cluster.CarInstrumentClusterManager} for details.
     * @param activityOptions this bundle will be converted to {@link android.app.ActivityOptions}
     *        and used when starting an activity. It may contain information such as virtual display
     *        id or activity stack id where to start cluster activity.
     *
     * @hide
     */
    void setClusterActivityLaunchOptions(String category, in Bundle activityOptions);

    /**
     * Activities launched on virtual display will be in onPause state most of the time, so they
     * can't really know whether they visible on the screen or not. We need to propagate this
     * information along with unobscured bounds (and possible other info) from instrument cluster
     * vendor implementation to activity.
     *
     * @param category cluster activity category to which this state applies,
     *        see {@link android.car.cluster.CarInstrumentClusterManager} for details.
     * @param clusterActivityState is a {@link Bundle} object,
     *        see {@link android.car.cluster.ClusterActivityState} for how to construct the bundle.
     * @hide
     */
    void setClusterActivityState(String category, in Bundle clusterActivityState);
}
