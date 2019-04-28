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

import android.animation.TimeInterpolator;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.dialog.FullscreenDialogFragment;

public class FullscreenDialogView extends FrameLayout
        implements FullscreenDialogFragment.DialogView {
    private static final String TAG = "FullscreenDialogView";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 400;
    private static final int FADE_OUT_DURATION_MS = 250;
    private static final int TRANSITION_INTERVAL_MS = 300;

    private MainActivity mActivity;
    private Dialog mDialog;
    private boolean mSkipEnterAlphaAnimation;
    private boolean mSkipExitAlphaAnimation;

    private final TimeInterpolator mLinearOutSlowIn;
    private final TimeInterpolator mFastOutLinearIn;

    public FullscreenDialogView(Context context) {
        this(context, null, 0);
    }

    public FullscreenDialogView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FullscreenDialogView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLinearOutSlowIn = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.linear_out_slow_in);
        mFastOutLinearIn = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.fast_out_linear_in);
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        startEnterAnimation();
                    }
                });
    }

    protected MainActivity getActivity() {
        return mActivity;
    }

    /**
     * Gets the host {@link Dialog}.
     */
    protected Dialog getDialog() {
        return mDialog;
    }

    /**
     * Dismisses the host {@link Dialog}.
     */
    protected void dismiss() {
        startExitAnimation(new Runnable() {
            @Override
            public void run() {
                mDialog.dismiss();
            }
        });
    }

    @Override
    public void initialize(MainActivity activity, Dialog dialog) {
        mActivity = activity;
        mDialog = dialog;
    }

    @Override
    public void onBackPressed() { }

    @Override
    public void onDestroy() { }

    /**
     * Transitions to another view inside the host {@link Dialog}.
     */
    public void transitionTo(final FullscreenDialogView v) {
        mSkipExitAlphaAnimation = true;
        v.mSkipEnterAlphaAnimation = true;
        v.initialize(mActivity, mDialog);
        startExitAnimation(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        v.initialize(getActivity(), getDialog());
                        getDialog().setContentView(v);
                    }
                }, TRANSITION_INTERVAL_MS);
            }
        });
    }

    /**
     * Called when an enter animation starts. Sub-view specific animation can be implemented.
     */
    protected void onStartEnterAnimation(TimeInterpolator interpolator, long duration) {
    }

    /**
     * Called when an exit animation starts. Sub-view specific animation can be implemented.
     */
    protected void onStartExitAnimation(TimeInterpolator interpolator, long duration,
            Runnable onAnimationEnded) {
    }

    private void startEnterAnimation() {
        if (DEBUG) Log.d(TAG, "start an enter animation");
        View backgroundView = findViewById(R.id.background);
        if (!mSkipEnterAlphaAnimation) {
            backgroundView.setAlpha(0);
            backgroundView.animate()
                    .alpha(1.0f)
                    .setInterpolator(mLinearOutSlowIn)
                    .setDuration(FADE_IN_DURATION_MS)
                    .withLayer()
                    .start();
        }
        onStartEnterAnimation(mLinearOutSlowIn, FADE_IN_DURATION_MS);
    }

    private void startExitAnimation(final Runnable onAnimationEnded) {
        if (DEBUG) Log.d(TAG, "start an exit animation");
        View backgroundView = findViewById(R.id.background);
        if (!mSkipExitAlphaAnimation) {
            backgroundView.animate()
                    .alpha(0.0f)
                    .setInterpolator(mFastOutLinearIn)
                    .setDuration(FADE_OUT_DURATION_MS)
                    .withLayer()
                    .start();
        }
        onStartExitAnimation(mFastOutLinearIn, FADE_OUT_DURATION_MS, onAnimationEnded);
    }
}
