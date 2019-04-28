/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

/**
 * An AnimatorListenerAdapter subclass that conveniently sets the layer type to hardware during the
 * animation.
 */
public class HardwareLayerAnimatorListenerAdapter extends AnimatorListenerAdapter {
    private final View mView;
    private boolean mLayerTypeChanged;

    public HardwareLayerAnimatorListenerAdapter(View view) {
        mView = view;
    }

    @Override
    public void onAnimationStart(Animator animator) {
        if (mView.hasOverlappingRendering() && mView.getLayerType() == View.LAYER_TYPE_NONE) {
            mLayerTypeChanged = true;
            mView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        if (mLayerTypeChanged) {
            mLayerTypeChanged = false;
            mView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }
}
