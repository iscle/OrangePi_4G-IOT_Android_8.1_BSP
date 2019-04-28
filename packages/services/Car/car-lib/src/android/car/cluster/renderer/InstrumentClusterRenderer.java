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

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UiThread;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.Context;

/**
 * Interface for instrument cluster rendering.
 *
 * TODO: implement instrument cluster feature list and extend API. bug: 32060603
 *
 * @hide
 */
@SystemApi
public abstract class InstrumentClusterRenderer {

    @Nullable private NavigationRenderer mNavigationRenderer;

    /**
     * Calls once when instrument cluster should be created.
     */
    abstract public void onCreate(Context context);

    abstract public void onStart();

    abstract public void onStop();

    abstract protected NavigationRenderer createNavigationRenderer();

    /** The method is thread-safe, callers should cache returned object. */
    @Nullable
    public synchronized NavigationRenderer getNavigationRenderer() {
        return mNavigationRenderer;
    }

    /**
     * This method is called by car service after onCreateView to initialize private members. The
     * method should not be overridden by subclasses.
     */
    @UiThread
    public synchronized final void initialize() {
        mNavigationRenderer = createNavigationRenderer();
    }
}
