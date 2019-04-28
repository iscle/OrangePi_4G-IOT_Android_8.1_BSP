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
package android.car.cluster;

import android.os.Bundle;

/**
 * Interface from Car Service to {@link android.car.cluster.CarInstrumentClusterManager}
 * @hide
 */
interface IInstrumentClusterManagerCallback {
    /**
     * Notifies manager about changes in the cluster activity state.
     *
     * @param category cluster activity category to which this state applies,
     *        see {@link android.car.cluster.CarInstrumentClusterManager} for details.
     * @param clusterActivityState is a {@link Bundle} object,
     *        see {@link android.car.cluster.ClusterActivityState} for how to construct the bundle.
     * @hide
     */
    oneway void setClusterActivityState(String category, in Bundle clusterActivityState);
}
