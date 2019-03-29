/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.projection;

import android.app.Presentation;
import android.content.Context;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

/**
 * Base class for Presentations which are to be projected onto a VirtualDisplay
 */
public abstract class ProjectedPresentation extends Presentation {
    public ProjectedPresentation(Context outerContext, Display display) {
        // This theme is required to prevent an extra view from obscuring the presentation
        super(outerContext, display, android.R.style.Theme_Holo_Light_NoActionBar_TranslucentDecor);

        getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);

        // So we can control the input
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    }

    public void injectTouchEvent(MotionEvent event) {
        getWindow().setLocalFocus(true, true);
        getWindow().injectInputEvent(event);
    }

    public void injectKeyEvent(KeyEvent event) {
        getWindow().setLocalFocus(true, false);
        getWindow().injectInputEvent(event);
    }
}
