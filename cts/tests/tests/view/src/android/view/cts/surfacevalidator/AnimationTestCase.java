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
package android.view.cts.surfacevalidator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

public class AnimationTestCase {
    private final ViewFactory mViewFactory;
    private final FrameLayout.LayoutParams mLayoutParams;
    private final AnimationFactory mAnimationFactory;
    private final PixelChecker mPixelChecker;

    private FrameLayout mParent;
    private ValueAnimator mAnimator;

    public AnimationTestCase(ViewFactory viewFactory,
            FrameLayout.LayoutParams layoutParams,
            AnimationFactory animationFactory,
            PixelChecker pixelChecker) {
        mViewFactory = viewFactory;
        mLayoutParams = layoutParams;
        mAnimationFactory = animationFactory;
        mPixelChecker = pixelChecker;
    }

    PixelChecker getChecker() {
        return mPixelChecker;
    }

    public void start(Context context, FrameLayout parent) {
        mParent = parent;
        mParent.removeAllViews();
        View view = mViewFactory.createView(context);
        mParent.addView(view, mLayoutParams);
        mAnimator = mAnimationFactory.createAnimator(view);
        mAnimator.start();
    }

    public void end() {
        mAnimator.cancel();
        mParent.removeAllViews();
    }
}
