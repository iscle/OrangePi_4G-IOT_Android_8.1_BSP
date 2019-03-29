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
package android.transition.cts;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;

import com.android.compatibility.common.util.transition.TrackingVisibility;

/**
 * Extends TrackingVisibility, but returns an animator to ensure that there is a time
 * difference between starting and ending of the transition.
 */
public class TrackingVisibilityWithAnimator extends TrackingVisibility {
    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        super.onAppear(sceneRoot, view, startValues, endValues);
        return ValueAnimator.ofFloat(0, 1);
    }
}
