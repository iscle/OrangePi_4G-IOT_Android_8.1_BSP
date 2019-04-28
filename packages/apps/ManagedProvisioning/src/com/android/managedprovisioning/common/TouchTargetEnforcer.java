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
package com.android.managedprovisioning.common;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.TouchDelegate;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Allows for expanding touch area of a {@link View} element, so it's compliant with
 * accessibility guidelines, while not modifying the UI appearance.
 * @see <a href="https://goo.gl/FcU5gX">Android Accessibility Guide</a>
 */
public class TouchTargetEnforcer {
    /** Value taken from Android Accessibility Guide */
    @VisibleForTesting static final int MIN_TARGET_DP = 48;

    /** @see DisplayMetrics#density */
    private final float mDensity;

    private final TouchDelegateProvider mTouchDelegateProvider;

    /**
     * Allows for expanding touch area of a {@link View} element, so it's compliant with
     * accessibility guidelines, while not modifying the UI appearance.
     * @param density {@link DisplayMetrics#density}
     * @see <a href="https://goo.gl/FcU5gX">Android Accessibility Guide</a>
     */
    public TouchTargetEnforcer(float density) {
        this(density, TouchDelegate::new);
    }

    /**
     * Allows for expanding touch area of a {@link View} element, so it's compliant with
     * accessibility guidelines, while not modifying the UI appearance.
     * @param density {@link DisplayMetrics#density}
     * @see <a href="https://goo.gl/FcU5gX">Android Accessibility Guide</a>
     */
    TouchTargetEnforcer(float density, TouchDelegateProvider touchDelegateProvider) {
        mDensity = density;
        mTouchDelegateProvider = touchDelegateProvider;
    }

    /**
     * Compares target's touch area to required minimum, and expands it if necessary.
     * <p>FIXME: Does not honor screen boundaries, so might set touch areas outside of the screen.
     * <p>FIXME: Does not honor ancestor boundaries, so might not work if ancestor too small.
     * <p>FIXME: Does not work if ancestor has more than one TouchTarget set.
     * @param target element to check for accessibility compliance
     * @param ancestor target's ancestor - only one target per ancestor allowed
     */
    public void enforce(View target, View ancestor) {
        target.getViewTreeObserver().addOnGlobalLayoutListener( // avoids some subtle bugs
                () -> {
                    int minTargetPx = (int) Math.ceil(dpToPx(MIN_TARGET_DP));
                    int deltaHeight = Math.max(0, minTargetPx - target.getHeight());
                    int deltaWidth = Math.max(0, minTargetPx - target.getWidth());
                    if (deltaHeight <= 0 && deltaWidth <= 0) {
                        return;
                    }

                    ancestor.post(() -> {
                        Rect bounds = createNewBounds(target, minTargetPx, deltaWidth, deltaHeight);

                        synchronized (ancestor) {
                            if (ancestor.getTouchDelegate() == null) {
                                ancestor.setTouchDelegate(
                                        mTouchDelegateProvider.getInstance(bounds, target));
                                ProvisionLogger.logd(String.format(
                                        "Successfully set touch delegate on ancestor %s "
                                                + "delegating to target %s.",
                                        ancestor, target));
                            } else {
                                ProvisionLogger.logd(String.format(
                                        "Ancestor %s already has an assigned touch delegate %s. "
                                                + "Unable to assign another one. Ignoring target.",
                                        ancestor, target));
                            }
                        }
                    });
                });
    }

    private Rect createNewBounds(View target, int minTargetPx, int deltaWidth, int deltaHeight) {
        int deltaWidthHalf = deltaWidth / 2;
        int deltaHeightHalf = deltaHeight / 2;

        Rect result = new Rect();
        target.getHitRect(result);
        result.top -= deltaHeightHalf;
        result.bottom += deltaHeightHalf;
        result.left -= deltaWidthHalf;
        result.right += deltaWidthHalf;

        // fix rounding errors
        int deltaHeightRemaining = minTargetPx - (result.bottom - result.top);
        if (deltaHeightRemaining > 0) {
            result.bottom += deltaHeightRemaining;
        }
        int deltaWidthRemaining = minTargetPx - (result.right - result.left);
        if (deltaWidthRemaining > 0) {
            result.right += deltaWidthRemaining;
        }
        return result;
    }

    private float dpToPx(int dp) {
        return dp * mDensity;
    }

    interface TouchDelegateProvider {
        /**
         * @param bounds New touch bounds
         * @param delegateView The view that should receive motion events (target)
         */
        TouchDelegate getInstance(Rect bounds, View delegateView);
    }
}
