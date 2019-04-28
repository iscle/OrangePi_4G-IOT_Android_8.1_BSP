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

package com.android.car.cluster.sample;

import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.car.cluster.renderer.NavigationRenderer;
import android.view.KeyEvent;

/**
 * Service for {@link InstrumentClusterController}. This is entry-point of the instrument cluster
 * renderer. This service will be bound from Car Service.
 */
public class InstrumentClusterRenderingServiceImpl extends InstrumentClusterRenderingService {

    private InstrumentClusterController mController;

    @Override
    public void onCreate() {
        super.onCreate();

        mController = new InstrumentClusterController(this /* context */);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mController != null) {
            mController.onDestroy();
            mController = null;
        }
    }

    @Override
    protected NavigationRenderer getNavigationRenderer() {
        return mController.getNavigationRenderer();
    }

    @Override
    protected void onKeyEvent(KeyEvent keyEvent) {
        // No need to handle key events in this implementation.
    }
}
