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
package android.car.cluster.renderer;

import android.car.cluster.renderer.IInstrumentClusterCallback;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.view.KeyEvent;

/**
 * Binder API for Instrument Cluster.
 *
 * @hide
 */
interface IInstrumentCluster {
    /** Returns {@link IInstrumentClusterNavigation} that will be passed to the Nav app */
    IInstrumentClusterNavigation getNavigationService();

    /** Supplies Instrument Cluster Renderer with current owner of Navigation app context */
    oneway void setNavigationContextOwner(int uid, int pid);

    /** Called when key event that was addressed to instrument cluster display has been received. */
    oneway void onKeyEvent(in KeyEvent keyEvent);
}
