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

package com.android.cts.verifier.projection.widgets;

import android.content.Context;
import android.os.Bundle;
import android.view.Display;

import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.ProjectedPresentation;
import com.android.cts.verifier.projection.ProjectionPresentationType;

/**
 * Check if widgets display and that key focus works in projected mode
 */
public class WidgetPresentation extends ProjectedPresentation {

    /**
     * @param outerContext
     * @param display
     */
    public WidgetPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pwa_buttons);
    }

}
